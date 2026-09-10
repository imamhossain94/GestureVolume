package com.newagedevs.gesturevolume.utils

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 */
class VolumeController(private val context: Context) {

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

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            this@VolumeController.configs = configs?.toList() ?: emptyList()
            noteMusicActivity()
        }
    }

    private val modeListener: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioManager.OnModeChangedListener { mode -> cachedMode = mode }
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
        val audioMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cachedMode
        } else {
            runCatching { audio?.mode ?: AudioStreamResolver.MODE_NORMAL }
                .getOrDefault(AudioStreamResolver.MODE_NORMAL)
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
     */
    fun step(res: Resolution, direction: Int, showUi: Boolean): Int? {
        val manager = audio ?: return null
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0

        if (res.opaque) {
            // No readable index, so no percentage can be told truthfully. Hand the step to the
            // platform and report a sentinel that never pins.
            val dir = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            val ok = runCatching {
                manager.adjustStreamVolume(res.stream, dir, flags); true
            }.getOrDefault(false)
            return if (ok) -1 else null
        }

        val current = runCatching { manager.getStreamVolume(res.stream) }.getOrNull() ?: return null
        val target = (current + direction).coerceIn(res.minIndex, res.maxIndex)
        if (target == current) return null

        // An absolute write rather than adjustStreamVolume, deliberately: it is the only way to
        // produce a truthful percentage and an honest null. (The relative form is what the system
        // forwards to an absolute-volume Bluetooth headset, which is why the opaque branch above
        // uses it instead.)
        val written = runCatching {
            manager.setStreamVolume(res.stream, target, flags); true
        }.getOrDefault(false)
        if (!written) return null

        val span = (res.maxIndex - res.minIndex).coerceAtLeast(1)
        return ((target - res.minIndex) * 100f / span).roundToInt().coerceIn(0, 100)
    }

    /** The current index of a resolved stream, or null when it cannot be read. */
    fun level(res: Resolution): Int? =
        runCatching { audio?.getStreamVolume(res.stream) }.getOrNull()

    /** The current level of a resolved stream as 0..100, or null when it cannot be read. */
    fun percent(res: Resolution): Int? {
        val current = level(res) ?: return null
        val span = (res.maxIndex - res.minIndex).coerceAtLeast(1)
        return ((current - res.minIndex) * 100f / span).roundToInt().coerceIn(0, 100)
    }

    /**
     * Sets a resolved stream to a 0..100 percentage, for the Deck's slider.
     *
     * @return the percentage actually applied, or null when the write was refused.
     */
    fun setPercent(res: Resolution, percent: Int, showUi: Boolean): Int? {
        val manager = audio ?: return null
        val span = (res.maxIndex - res.minIndex).coerceAtLeast(1)
        val target = (res.minIndex + percent.coerceIn(0, 100) * span / 100f).roundToInt()
            .coerceIn(res.minIndex, res.maxIndex)
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        val written = runCatching {
            manager.setStreamVolume(res.stream, target, flags); true
        }.getOrDefault(false)
        if (!written) return null
        return ((target - res.minIndex) * 100f / span).roundToInt().coerceIn(0, 100)
    }

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
