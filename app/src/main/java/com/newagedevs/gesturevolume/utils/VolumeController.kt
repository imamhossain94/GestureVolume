package com.newagedevs.gesturevolume.utils

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Volume control for the floating handler, and the counterpart to [BrightnessController].
 *
 * Before 1.3.5 there was no such object: [android.media.AudioManager] was fetched separately by the
 * overlay service and by the appearance preview, and every stream operation was written inline at
 * the call site against a hardcoded `STREAM_MUSIC` — fourteen times across two files. That is why
 * swiping the bar during a call moved media volume, and why the preview and the live bar could
 * disagree. One object now owns the domain and both surfaces share it.
 *
 * The choice of *which* stream to drive lives in [AudioStreamResolver], which is a pure function so
 * it can be tested. This class is the Android half: it gathers the signals, applies the resolver's
 * decision, and owns the index arithmetic.
 *
 * Every framework call is inside `runCatching`. Audio is one of the most heavily OEM-modified parts
 * of Android and several of these methods are documented to throw on some paths and known to throw
 * on others; a volume bar must degrade rather than crash the overlay it lives in.
 *
 * Every write is read back (see [VolumeWrite]): a step the system refused is reported as refused
 * rather than as the level that was asked for, and is handed once to the system's own relative
 * step, which is what shows the hearing-safety warning that holds the top of the range. A route
 * that puts absolute levels back is remembered through [routes] and driven by relative steps.
 *
 * @param routes where the routes that put levels back are remembered, across restarts. Null keeps
 *   them for the life of this object only.
 */
class VolumeController(
    private val context: Context,
    private val routes: RouteMemory? = null,
) {

    /** Somewhere durable to keep the routes that only take relative steps. See [VolumeWrite.routeKey]. */
    interface RouteMemory {
        fun relativeRoutes(): List<String>
        fun setRelativeRoutes(routes: List<String>)
    }

    /**
     * How writes are made. See [VolumeWrite.METHOD_AUTO] and its siblings. Read by every write, so
     * a change on the settings screen applies from the next step.
     */
    @Volatile
    var method: String = VolumeWrite.METHOD_AUTO
        set(value) {
            field = VolumeWrite.sanitizeMethod(value)
        }

    /**
     * The routes found putting levels back, for when there is no [routes] to keep them in. A
     * concurrent set: the Quick panel writes from its own audio thread.
     */
    private val sessionRoutes: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /**
     * Whether this gesture has already handed a refused step to the system. Once is enough: the
     * system's answer — its warning, its own panel — is on screen, and asking again on every notch
     * of a finger still pushing against the limit would only flash it.
     */
    @Volatile
    private var escalatedThisGesture = false

    /** Set when the last step was refused at the top of the range, for the bar to say why. */
    @Volatile
    var lastRaiseRefused: Boolean = false
        private set

    /** A new swipe, a new panel, a new press: the escalation above may be offered again. */
    fun beginGesture() {
        escalatedThisGesture = false
        lastRaiseRefused = false
    }

    private companion object {
        /**
         * How long after media stops it is still what the user means by "volume".
         *
         * Between tracks, and while a video buffers, nothing is playing. Without a grace window the
         * bar would flip to the ringer mid-playlist.
         */
        const val MEDIA_GRACE_MS = 5_000L

        /**
         * `AudioService` refuses index 0 on the call stream — a call you cannot hear is treated as
         * a bug rather than a setting — and every device reports a floor of 1.
         */
        const val VOICE_CALL_FLOOR = 1

        /** [AudioManager.MODE_CALL_SCREENING], which only exists from Android 11. */
        const val MODE_CALL_SCREENING = 4

        /**
         * Outputs in the order the system prefers them for media, most specific first, for naming
         * the route below Android 13. The LE Audio types are plain numbers (26 and 27, Android 12)
         * so this list compiles against every SDK the app supports.
         */
        val ROUTE_PRIORITY: List<Int> = listOf(
            26, // AudioDeviceInfo.TYPE_BLE_HEADSET
            27, // AudioDeviceInfo.TYPE_BLE_SPEAKER
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
    }

    /**
     * Nullable on purpose. `getSystemService` is documented to return null for a service that is
     * unavailable, and the appearance preview's existing unchecked cast is a latent crash on the
     * settings screen. Every method here no-ops safely when this is null.
     */
    private val audio: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * The last snapshot of what is playing.
     *
     * Kept as a field and refreshed by a callback rather than queried on the touch path:
     * `getActivePlaybackConfigurations()` is a binder round-trip, and the gesture detector calls
     * into here on every accepted step while the user's finger is moving.
     */
    @Volatile
    private var configs: List<AudioPlaybackConfiguration> = emptyList()

    /** Elapsed-realtime of the last moment media was seen playing. Drives [MEDIA_GRACE_MS]. */
    @Volatile
    private var lastMusicActiveAt: Long = 0L

    /** Only read below API 31, where there is no mode-changed listener to keep a field warm. */
    @Volatile
    private var cachedMode: Int = AudioStreamResolver.MODE_NORMAL

    /**
     * Told, on the main thread, whenever what is playing or the audio mode may have changed: for
     * the bar that shows itself only while something plays or a call is on. Null when nobody asks.
     */
    @Volatile
    var onActivityChanged: (() -> Unit)? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            this@VolumeController.configs = configs?.toList() ?: emptyList()
            noteMusicActivity()
            onActivityChanged?.invoke()
        }
    }

    private val modeListener: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioManager.OnModeChangedListener { mode ->
                cachedMode = mode
                onActivityChanged?.invoke()
            }
        } else {
            null
        }

    /**
     * Starts watching. Must be paired with [stop] — an unregistered callback leaks into
     * `AudioService` and outlives the overlay window it was created for.
     */
    fun start() {
        val manager = audio ?: return
        runCatching {
            configs = manager.activePlaybackConfigurations?.toList() ?: emptyList()
        }
        noteMusicActivity()
        runCatching { manager.registerAudioPlaybackCallback(playbackCallback, mainHandler) }
        runCatching { cachedMode = manager.mode }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = modeListener as? AudioManager.OnModeChangedListener ?: return
            runCatching { manager.addOnModeChangedListener(context.mainExecutor, listener) }
        }
    }

    fun stop() {
        val manager = audio ?: return
        runCatching { manager.unregisterAudioPlaybackCallback(playbackCallback) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = modeListener as? AudioManager.OnModeChangedListener ?: return
            runCatching { manager.removeOnModeChangedListener(listener) }
        }
    }

    private fun noteMusicActivity() {
        val playing = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
        if (playing) lastMusicActiveAt = SystemClock.elapsedRealtime()
    }

    /** Whether media is playing this moment. One binder call; not for the touch path. */
    fun isMusicActiveNow(): Boolean = runCatching { audio?.isMusicActive == true }.getOrDefault(false)

    /**
     * Whether a call is on — a phone call, or a call app holding the audio — or the phone is
     * ringing, or a call is being screened.
     *
     * From the audio mode, which says all of that without the phone-state permission the telephony
     * APIs want. Kept warm by the mode listener from Android 12; asked for directly before it.
     */
    fun isCallOrRinging(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cachedMode
        } else {
            runCatching { audio?.mode ?: AudioManager.MODE_NORMAL }.getOrDefault(AudioManager.MODE_NORMAL)
        }
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE ||
            // AudioManager.MODE_CALL_SCREENING, from Android 11; a plain number, since it is never
            // reported before then anyway.
            mode == MODE_CALL_SCREENING
    }

    /**
     * A resolved stream together with everything the caller needs to act on it.
     *
     * Bounds are computed once, here. The code this replaces re-queried `getStreamMaxVolume` on
     * every single step of a swipe.
     */
    data class Resolution(
        val stream: Int,
        val source: AudioStreamResolver.Source,
        val opaque: Boolean,
        val minIndex: Int,
        val maxIndex: Int,
    ) {
        /** Discrete steps across the range, for the gesture detector's step sizing. */
        val stepCount: Int get() = (maxIndex - minIndex).coerceAtLeast(1)
    }

    /**
     * Resolves the stream to drive for one gesture.
     *
     * Call this once, when a swipe begins, and hold the result for the whole gesture. Re-resolving
     * per step would let a track ending mid-swipe move the user's finger onto a different stream.
     */
    fun resolve(mode: String): Resolution {
        val decision = AudioStreamResolver.resolve(gatherSignals(mode))
        return withBounds(decision.stream, decision.source, decision.opaque)
    }

    /** Media, unconditionally — for the paths that are about media by definition. */
    fun media(): Resolution = withBounds(
        AudioStreamResolver.STREAM_MUSIC,
        AudioStreamResolver.Source.MEDIA_ONLY,
        opaque = false,
    )

    /** One named stream, for the Deck's per-stream sliders. Ring keeps its floor of 1. */
    fun forStream(stream: Int): Resolution = withBounds(
        stream,
        AudioStreamResolver.Source.MEDIA_ONLY,
        opaque = false,
    )

    /** Whether the ring stream can be written right now — see [ringWritable]. */
    fun canWriteRing(): Boolean = ringWritable()

    private fun withBounds(stream: Int, source: AudioStreamResolver.Source, opaque: Boolean): Resolution {
        val max = runCatching { audio?.getStreamMaxVolume(stream) ?: 15 }.getOrDefault(15)
        return Resolution(
            stream = stream,
            source = source,
            opaque = opaque,
            minIndex = minIndexFor(stream),
            maxIndex = max.coerceAtLeast(1),
        )
    }

    private fun gatherSignals(mode: String): AudioStreamResolver.Signals {
        // Both asked for afresh, once per gesture, rather than trusted to the listeners that keep
        // them warm. A mode-change or playback callback that never arrives — a VoIP app that left
        // its call mode set and cleared it while the callback was being re-registered, an OEM build
        // that drops one — left the bar driving the call volume, or an alarm's, over every video
        // until the service was restarted: "not working on some apps, I have to stop and start it".
        // One binder call each, once when a swipe begins, never per step.
        val audioMode = runCatching { audio?.mode }.getOrNull()
            ?.also { cachedMode = it }
            ?: cachedMode
        audio?.let { manager ->
            runCatching { manager.activePlaybackConfigurations?.toList() }.getOrNull()
                ?.let { configs = it }
        }

        val musicActive = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
        if (musicActive) lastMusicActiveAt = SystemClock.elapsedRealtime()

        return AudioStreamResolver.Signals(
            mode = mode,
            audioMode = audioMode,
            activeStreams = configs.mapNotNull { streamFor(it) },
            musicActive = musicActive,
            musicRecent = SystemClock.elapsedRealtime() - lastMusicActiveAt < MEDIA_GRACE_MS,
            ringWritable = ringWritable(),
            communicationRouteOpaque = communicationRouteOpaque(audioMode),
        )
    }

    /**
     * The stream behind one playback configuration, or null when it is not one this app will drive.
     *
     * [android.media.AudioAttributes.getVolumeControlStream] is the platform's own mapping from
     * usage to stream, which is why it is used here instead of a hand-written `when` over the
     * `USAGE_*` constants — that hand-written table is exactly the kind of thing that silently goes
     * stale when a new usage is added.
     *
     * The `runCatching` is load-bearing rather than defensive: the method throws
     * `IllegalArgumentException` on a usage it does not recognise.
     *
     * What is filtered out matters as much as what is kept. `STREAM_ACCESSIBILITY` in particular is
     * dropped **silently** by `AudioService` without `CHANGE_ACCESSIBILITY_VOLUME`, so a write
     * would report success having moved nothing — which would break [step]'s contract that a
     * non-null return means the volume actually changed.
     */
    private fun streamFor(config: AudioPlaybackConfiguration): Int? {
        val stream = runCatching { config.audioAttributes.volumeControlStream }.getOrNull() ?: return null
        // Covers STREAM_DEFAULT (-1) and the Integer.MIN_VALUE sentinel alike. An `== -1` test
        // would miss the latter.
        if (stream < 0) return AudioStreamResolver.STREAM_MUSIC
        return when (stream) {
            AudioStreamResolver.STREAM_MUSIC,
            AudioStreamResolver.STREAM_VOICE_CALL,
            AudioStreamResolver.STREAM_ALARM,
            AudioStreamResolver.STREAM_RING -> stream
            else -> null
        }
    }

    /**
     * Whether writing [AudioStreamResolver.STREAM_RING] would actually do something.
     *
     * Two gates. The ringer must be in its normal mode — on a silent or vibrating phone the ring
     * index is not what the user perceives as "volume" — and Do Not Disturb must not be filtering,
     * because crossing the zero boundary on the ring stream while a filter is active throws
     * `SecurityException` without `ACCESS_NOTIFICATION_POLICY`, a permission this app will not add.
     *
     * An unreadable filter is treated as writable: the write itself is guarded, and a ROM whose
     * read throws must not lose the feature permanently.
     */
    private fun ringWritable(): Boolean {
        val normalRinger = runCatching {
            audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL
        }.getOrDefault(false)
        if (!normalRinger) return false

        return runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching true
            when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL,
                NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> true
                else -> false
            }
        }.getOrDefault(true)
    }

    /**
     * True when a call is routed to a Bluetooth headset, whose live index lives on the hidden SCO
     * stream this app will not hardcode. The caller must then use a relative adjust and must not
     * claim a percentage.
     */
    private fun communicationRouteOpaque(audioMode: Int): Boolean {
        if (audioMode != AudioStreamResolver.MODE_IN_CALL &&
            audioMode != AudioStreamResolver.MODE_IN_COMMUNICATION
        ) {
            return false
        }
        @Suppress("DEPRECATION")
        return runCatching { audio?.isBluetoothScoOn == true }.getOrDefault(false)
    }

    /**
     * The floor for a stream.
     *
     * `getStreamMinVolume` arrived in API 28; below it, zero. The two hardcoded floors above that
     * are deliberate: silencing a call is not a volume operation, and the ring floor of 1 is the
     * single line that keeps `ACCESS_NOTIFICATION_POLICY` off the manifest — a bar that can reach
     * index 0 on the ringer is a bar that can trip the Do Not Disturb `SecurityException`.
     */
    private fun minIndexFor(stream: Int): Int {
        val reported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { audio?.getStreamMinVolume(stream) ?: 0 }.getOrDefault(0)
        } else {
            0
        }
        return when (stream) {
            AudioStreamResolver.STREAM_VOICE_CALL -> reported.coerceAtLeast(VOICE_CALL_FLOOR)
            AudioStreamResolver.STREAM_RING -> reported.coerceAtLeast(1)
            else -> reported.coerceAtLeast(0)
        }
    }

    /**
     * Moves the resolved stream one step.
     *
     * @return the new level as a 0..100 percentage, or `null` when nothing moved — because the
     *   value is already pinned at an end, or because the write was refused. This mirrors
     *   [BrightnessController.step]'s contract exactly: a `null` tells the gesture detector to stop
     *   banking travel, so the user does not have to un-swipe before the control responds again.
     *   `-1` means the level moved but cannot honestly be read: a call on a Bluetooth headset.
     */
    fun step(res: Resolution, direction: Int, showUi: Boolean): Int? =
        stepBy(res, direction, 1, showUi)

    /**
     * Moves the resolved stream [indices] steps at once: one, for a swipe that steps by its length,
     * or several, for a swipe set to move the volume by a fixed amount. The same contract as [step].
     *
     * The level reported is the level read back after the write, never the one that was asked for.
     * A write the system refused — the hearing-safety limit holding the top of the range is the
     * usual one — is handed once to the system's own relative step, which is what brings up the
     * warning the user has to accept before going louder; if that is refused too, the step is
     * reported as refused, so the bar stops claiming a level the audio never reached.
     */
    fun stepBy(res: Resolution, direction: Int, indices: Int, showUi: Boolean): Int? {
        val manager = audio ?: return null
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        val dir = if (direction > 0) 1 else -1

        if (res.opaque) {
            // No readable index, so no percentage can be told truthfully. Hand the step to the
            // platform and report a sentinel that never pins. One relative step whatever the
            // amount: there is no honest way to count out more on a level that cannot be read.
            return if (adjust(manager, res.stream, dir, flags)) -1 else null
        }

        val current = runCatching { manager.getStreamVolume(res.stream) }.getOrNull() ?: return null
        val target = (current + dir * indices.coerceAtLeast(1)).coerceIn(res.minIndex, res.maxIndex)
        if (target == current) return null

        // As the volume keys do it: the system applies these in its own time, so the level is the
        // one predicted, and the watchers and the check after the swipe put the readout right.
        if (method == VolumeWrite.METHOD_KEYS) {
            val presses = abs(target - current)
            val sent = (0 until presses).all { pressKey(manager, res.stream, dir, flags) }
            if (!sent) return null
            return VolumeWrite.percentOf(target, res.minIndex, res.maxIndex)
        }

        // A route already found to put absolute levels back takes relative steps, read back.
        if (method == VolumeWrite.METHOD_AUTO && isRelativeRoute(res)) {
            val after = relativeTowards(manager, res, current, target, flags)
            return if (after != null && after != current) percentOf(res, after) else null
        }

        // An absolute write, deliberately: it is the only way to produce a truthful percentage and
        // an honest null. (The relative form is what the system forwards to an absolute-volume
        // Bluetooth headset, which is why the opaque branch above uses it instead.)
        val written = runCatching {
            manager.setStreamVolume(res.stream, target, flags); true
        }.getOrDefault(false)
        val after = if (written) level(res) else current

        return when (VolumeWrite.outcome(current, target, after)) {
            VolumeWrite.Outcome.LANDED, VolumeWrite.Outcome.PARTIAL -> percentOf(res, after ?: target)
            // Unreadable straight after a write that did not throw: trust the write, as the bar
            // always has, rather than pinning a control that may well have moved.
            VolumeWrite.Outcome.UNKNOWN -> if (written) percentOf(res, target) else null
            VolumeWrite.Outcome.REFUSED -> refused(manager, res, current, dir, flags)
        }
    }

    /**
     * What a refused step comes to: once per gesture, the system's own relative step — with its UI
     * for a raise, so the hearing-safety warning that holds the top of the range can be seen and
     * accepted — and otherwise nothing.
     */
    private fun refused(manager: AudioManager, res: Resolution, current: Int, dir: Int, flags: Int): Int? {
        if (method == VolumeWrite.METHOD_PRECISE || escalatedThisGesture) {
            if (dir > 0) lastRaiseRefused = true
            return null
        }
        escalatedThisGesture = true
        val uiFlags = if (dir > 0) flags or AudioManager.FLAG_SHOW_UI else flags
        adjust(manager, res.stream, dir, uiFlags)
        val after = level(res)
        if (after != null && after != current && (after > current) == (dir > 0)) {
            // The relative step went where the absolute one could not: this route only takes
            // those. Remembered, so the rest of the swipe and the next ones do not keep asking.
            rememberRelativeRoute(res)
            return percentOf(res, after)
        }
        if (dir > 0) lastRaiseRefused = true
        return null
    }

    /** The current index of a resolved stream, or null when it cannot be read. */
    fun level(res: Resolution): Int? =
        runCatching { audio?.getStreamVolume(res.stream) }.getOrNull()

    /** The current level of a resolved stream as 0..100, or null when it cannot be read. */
    fun percent(res: Resolution): Int? {
        val current = level(res) ?: return null
        return percentOf(res, current)
    }

    private fun percentOf(res: Resolution, index: Int): Int =
        VolumeWrite.percentOf(index, res.minIndex, res.maxIndex)

    /**
     * Writes an absolute index, and returns the index that landed.
     *
     * The finest control the platform offers. A stream's volume is an integer index and nothing
     * else — there is no public API for anything in between — so one index is as smooth as audio
     * can get, and a caller that wants a smooth *control* has to be smooth on its own side and
     * quantise only here.
     *
     * Distinct from [setPercent], which takes a whole-number percentage and converts. That extra
     * hop is lossy in both directions on a stream with few steps: a ring stream with seven of them
     * moves 14 points per index, so consecutive percentages map to the same index and consecutive
     * indices are unreachable from some percentages. A slider driven through it appears to stick
     * and then jump. [setPercent] stays for the callers that genuinely start from a percentage.
     *
     * Read back like [stepBy]: a refused write is walked there in relative steps instead, which is
     * how the keys reach a level the system would not take in one go, and reports where it got to.
     *
     * @return the index the level is at afterwards, or null when nothing could be written.
     */
    fun setIndex(res: Resolution, index: Int, showUi: Boolean): Int? {
        val manager = audio ?: return null
        val target = index.coerceIn(res.minIndex, res.maxIndex)
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        val current = level(res)

        if (current != null && current != target) {
            val dir = if (target > current) 1 else -1
            if (method == VolumeWrite.METHOD_KEYS) {
                val presses = abs(target - current)
                val sent = (0 until presses).all { pressKey(manager, res.stream, dir, flags) }
                return if (sent) target else null
            }
            if (method == VolumeWrite.METHOD_AUTO && isRelativeRoute(res)) {
                return relativeTowards(manager, res, current, target, flags)
            }
        }

        val written = runCatching {
            manager.setStreamVolume(res.stream, target, flags); true
        }.getOrDefault(false)
        if (current == null || current == target) return if (written) target else null
        val after = if (written) level(res) else current

        return when (VolumeWrite.outcome(current, target, after)) {
            VolumeWrite.Outcome.LANDED, VolumeWrite.Outcome.PARTIAL -> after
            VolumeWrite.Outcome.UNKNOWN -> if (written) target else null
            VolumeWrite.Outcome.REFUSED -> {
                if (method == VolumeWrite.METHOD_PRECISE) {
                    if (target > current) lastRaiseRefused = true
                    current
                } else {
                    val dir = if (target > current) 1 else -1
                    val walked = relativeTowards(manager, res, current, target, flags)
                    if (walked != null && walked != current && (walked > current) == (dir > 0)) {
                        rememberRelativeRoute(res)
                    } else if (dir > 0) {
                        lastRaiseRefused = true
                    }
                    walked ?: current
                }
            }
        }
    }

    /**
     * Sets a resolved stream to a 0..100 percentage, for the Deck's slider.
     *
     * @return the percentage actually applied, or null when the write was refused.
     */
    fun setPercent(res: Resolution, percent: Int, showUi: Boolean): Int? {
        val span = (res.maxIndex - res.minIndex).coerceAtLeast(1)
        val target = (res.minIndex + percent.coerceIn(0, 100) * span / 100f).roundToInt()
            .coerceIn(res.minIndex, res.maxIndex)
        val landed = setIndex(res, target, showUi) ?: return null
        return percentOf(res, landed)
    }

    // ---- relative steps, and the routes that need them ------------------------------------------

    /** One relative step on [stream], straight to the audio service, so it can be read back at once. */
    private fun adjust(manager: AudioManager, stream: Int, dir: Int, flags: Int): Boolean = runCatching {
        manager.adjustStreamVolume(
            stream,
            if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            flags,
        )
        true
    }.getOrDefault(false)

    /**
     * One press of a volume key, as near as an app can make one: through the media session service,
     * which hands it to a casting session or a remote speaker when one owns the volume, exactly as
     * the keys are handed. Applied in the system's own time, so it cannot be read back at once.
     */
    private fun pressKey(manager: AudioManager, stream: Int, dir: Int, flags: Int): Boolean = runCatching {
        manager.adjustSuggestedStreamVolume(
            if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            stream,
            flags,
        )
        true
    }.getOrDefault(false)

    /**
     * Walks the level from [current] towards [target] one relative step at a time, reading each
     * back, and stops at the first that does not move it.
     *
     * @return where the level got to, or null when it could not be read.
     */
    private fun relativeTowards(
        manager: AudioManager,
        res: Resolution,
        current: Int,
        target: Int,
        flags: Int,
    ): Int? {
        val dir = if (target > current) 1 else -1
        var at = current
        repeat(abs(target - current).coerceAtMost(res.stepCount)) {
            if (!adjust(manager, res.stream, dir, flags)) return at
            val now = level(res) ?: return null
            if (now == at) return at
            at = now
            if ((dir > 0 && at >= target) || (dir < 0 && at <= target)) return at
        }
        return at
    }

    /**
     * After a swipe has finished: whether the level stayed where the swipe left it, and if the
     * route put it back, takes it there again in relative steps and remembers the route.
     *
     * This is the "falls back to a fixed level" report. Some routes answer an absolute write a few
     * hundred milliseconds later with the level they had — a headset that keeps its own volume, an
     * app that pins the level it last saw — while a relative step, which is all the volume keys
     * ever send, is kept.
     *
     * @return the level as 0..100 once put right, or null when nothing needed doing.
     */
    fun confirmLanded(res: Resolution, start: Int, end: Int): Int? {
        if (res.opaque || method != VolumeWrite.METHOD_AUTO) return null
        val manager = audio ?: return null
        val now = level(res)
        if (!VolumeWrite.isRevert(start, end, now)) return null
        rememberRelativeRoute(res)
        val after = relativeTowards(manager, res, now ?: return null, end, 0) ?: return null
        return percentOf(res, after)
    }

    /**
     * Whether the volume keys should be left to the system for [res]: the user chose the keys' own
     * way, or this route has been found to keep only their relative steps.
     */
    fun prefersSystemKeys(res: Resolution): Boolean =
        method == VolumeWrite.METHOD_KEYS || (method == VolumeWrite.METHOD_AUTO && isRelativeRoute(res))

    /** Forgets every route found putting levels back, for a fresh start after the setting changes. */
    fun forgetRelativeRoutes() {
        sessionRoutes.clear()
        runCatching { routes?.setRelativeRoutes(emptyList()) }
    }

    private fun isRelativeRoute(res: Resolution): Boolean {
        val key = routeKey(res) ?: return false
        if (key in sessionRoutes) return true
        return runCatching { routes?.relativeRoutes()?.contains(key) == true }.getOrDefault(false)
    }

    private fun rememberRelativeRoute(res: Resolution) {
        val key = routeKey(res) ?: return
        sessionRoutes += key
        runCatching {
            val memory = routes ?: return@runCatching
            val known = memory.relativeRoutes()
            if (key !in known) memory.setRelativeRoutes(VolumeWrite.remember(known, key))
        }
    }

    /**
     * Which device [res]'s stream is going to, as [VolumeWrite.routeKey] spells it.
     *
     * From Android 13 the platform answers this directly. Before it, the most specific output that
     * is connected stands in: a headset when there is one, the speaker when there is not, which is
     * how the system routes media too.
     */
    private fun routeKey(res: Resolution): String? {
        val manager = audio ?: return null
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                manager.getAudioDevicesForAttributes(attributesFor(res.stream)).firstOrNull()
            }.getOrNull()
        } else {
            null
        } ?: runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.filter { it.type in ROUTE_PRIORITY }
                ?.minByOrNull { ROUTE_PRIORITY.indexOf(it.type) }
        }.getOrNull()
        return VolumeWrite.routeKey(res.stream, device?.type ?: AudioDeviceInfo.TYPE_UNKNOWN, device?.productName)
    }

    private fun attributesFor(stream: Int): AudioAttributes = AudioAttributes.Builder()
        .setUsage(
            when (stream) {
                AudioStreamResolver.STREAM_VOICE_CALL -> AudioAttributes.USAGE_VOICE_COMMUNICATION
                AudioStreamResolver.STREAM_RING -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
                AudioStreamResolver.STREAM_ALARM -> AudioAttributes.USAGE_ALARM
                else -> AudioAttributes.USAGE_MEDIA
            }
        )
        .build()

    /**
     * Shows the system volume panel for the resolved stream without changing anything.
     *
     * When the resolution is a guess rather than a reading — nothing playing, or an unreadable
     * route — the stream-agnostic form is used instead, handing the guess to the platform, whose
     * own `getActiveStreamType` is the same guess the hardware rocker makes.
     */
    fun panel(res: Resolution) {
        val manager = audio ?: return
        val confident = !res.opaque &&
            res.source != AudioStreamResolver.Source.IDLE
        runCatching {
            if (confident) {
                manager.adjustStreamVolume(res.stream, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            } else {
                manager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    /**
     * Silences the resolved stream by writing its floor.
     *
     * Level-based rather than `ADJUST_MUTE`, which is a silent no-op on the call stream — it is not
     * in `AudioService`'s default mute-affected set — and which on the ring stream can trip the Do
     * Not Disturb `SecurityException` this class works to avoid.
     *
     * @return the level that was in effect before, for the caller to remember, or `null` if nothing
     *   changed.
     */
    fun mute(res: Resolution): Int? {
        val manager = audio ?: return null
        val current = runCatching { manager.getStreamVolume(res.stream) }.getOrNull() ?: return null
        if (current <= res.minIndex) return null
        val ok = runCatching {
            manager.setStreamVolume(res.stream, res.minIndex, AudioManager.FLAG_SHOW_UI); true
        }.getOrDefault(false)
        return if (ok) current else null
    }

    /**
     * Restores a muted stream.
     *
     * A remembered level at or below the floor is not a level worth restoring — it would unmute to
     * silence — so a sensible fraction of the range is used instead.
     */
    fun unmute(res: Resolution, remembered: Int) {
        val manager = audio ?: return
        val target = remembered.takeIf { it > res.minIndex }
            ?: (res.maxIndex * 0.4f).roundToInt().coerceAtLeast(res.minIndex + 1)
        runCatching {
            manager.setStreamVolume(
                res.stream,
                target.coerceIn(res.minIndex, res.maxIndex),
                AudioManager.FLAG_SHOW_UI,
            )
        }
    }

    /** Whether a stream is currently silenced, by index or by the system's own mute flag. */
    fun isMuted(res: Resolution): Boolean {
        val manager = audio ?: return false
        val byFlag = runCatching { manager.isStreamMute(res.stream) }.getOrDefault(false)
        if (byFlag) return true
        val current = runCatching { manager.getStreamVolume(res.stream) }.getOrNull() ?: return false
        return current <= res.minIndex
    }

    /**
     * True on devices with a fixed output level — a TV or dock where the host controls volume — on
     * which every write here is ignored. Worth telling the user rather than letting the bar look
     * broken.
     */
    fun isVolumeFixed(): Boolean =
        runCatching { audio?.isVolumeFixed == true }.getOrDefault(false)
}
