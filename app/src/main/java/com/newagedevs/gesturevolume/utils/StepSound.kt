package com.newagedevs.gesturevolume.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.RawRes
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore

/**
 * The tick the Quick panel makes on each step, when the user has asked for one.
 *
 * One short sample, played through a [SoundPool] rather than generated: a pool plays with next to
 * no latency once loaded, and its playback rate is a pitch control for free, which is all the
 * adaptive sound needs. The pool is built for one [AudioAttributes] usage at a time, because the
 * usage is what decides whose volume the tick is played at — the stream being set, for the
 * adaptive sound on a volume panel, so a tick at a quarter volume is a quarter as loud.
 *
 * The sample is one of the app's own ([StepSoundLibrary]) or a sound the user picked from the
 * phone. One from the phone that cannot be read any more — deleted, or its access taken back — is
 * played as the app's tick rather than as silence.
 */
class StepSound(private val context: Context) {

    private var pool: SoundPool? = null
    private var poolUsage = -1
    private var poolSource: String? = null
    private var soundId = 0

    /** Whether the sample has finished loading. */
    @Volatile
    private var loaded = false

    /**
     * A step asked for before the sample had loaded: played the moment it has, unless that is too
     * late to still read as the step's. Without this the first step after choosing a sound, or the
     * first press of a key, was silent.
     */
    private var pendingAt = 0L
    private var pendingLevel = 0f
    private var pendingAdaptive = false

    /** Loads the sample [source] for [usage], reusing the pool already built for both. */
    fun prepare(usage: Int, source: String = StepSoundLibrary.DEFAULT) {
        val wanted = StepSoundLibrary.sanitize(source)
        if (pool != null && poolUsage == usage && poolSource == wanted) return
        release()
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val fromPhone = StepSoundLibrary.isDevice(wanted)
        val next = SoundPool.Builder()
            // A sound from the phone can be long. One at a time, so each step cuts the last short
            // instead of piling a dozen of them up during one swipe.
            .setMaxStreams(if (fromPhone) 1 else MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        var fellBack = false
        next.setOnLoadCompleteListener { loadedPool, id, status ->
            if (loadedPool !== pool || id != soundId) return@setOnLoadCompleteListener
            if (status == 0) {
                loaded = true
                playPending()
            } else if (fromPhone && !fellBack) {
                // Opened, but it would not decode: the tick instead.
                fellBack = true
                soundId = loadedPool.load(context, R.raw.slider_tick, 1)
            }
        }
        pool = next
        poolUsage = usage
        poolSource = wanted
        soundId = load(next, wanted) ?: next.load(context, R.raw.slider_tick, 1)
    }

    private fun load(pool: SoundPool, source: String): Int? {
        val uri = StepSoundLibrary.deviceUri(source) ?: return pool.load(context, rawFor(source), 1)
        return canRead(context, Uri.parse(uri)) { afd, length ->
            pool.load(afd.fileDescriptor, afd.startOffset, length, 1).takeIf { it != 0 }
        }
    }

    /**
     * One tick for a step that landed at [level], 0..1.
     *
     * @param adaptive whether the pitch follows the level: low near empty, high near full. A plain
     *   click keeps one note, a little quieter, so it reads as a detent rather than a readout.
     */
    fun play(level: Float, adaptive: Boolean) {
        val p = pool ?: return
        if (!loaded) {
            pendingAt = SystemClock.uptimeMillis()
            pendingLevel = level
            pendingAdaptive = adaptive
            return
        }
        playNow(p, level, adaptive)
    }

    private fun playPending() {
        val p = pool ?: return
        val at = pendingAt
        pendingAt = 0L
        if (at != 0L && SystemClock.uptimeMillis() - at <= PENDING_MS) playNow(p, pendingLevel, pendingAdaptive)
    }

    private fun playNow(p: SoundPool, level: Float, adaptive: Boolean) {
        val rate = if (adaptive) {
            MIN_RATE + (MAX_RATE - MIN_RATE) * level.coerceIn(0f, 1f)
        } else {
            1f
        }
        val volume = if (adaptive) 1f else CLICK_VOLUME
        p.play(soundId, volume, volume, 1, 0, rate)
    }

    fun release() {
        pool?.release()
        pool = null
        poolUsage = -1
        poolSource = null
        loaded = false
        pendingAt = 0L
    }

    companion object {
        private const val MAX_STREAMS = 3

        /** How late a step that waited for its sample may still be played. */
        private const val PENDING_MS = 600L

        /** The pitch range of the adaptive tick, as playback rates: a little under an octave either side. */
        private const val MIN_RATE = 0.6f
        private const val MAX_RATE = 1.8f

        private const val CLICK_VOLUME = 0.7f

        /** The file in res/raw for one of the app's own sounds. See tool/sounds/step_sounds.py. */
        @RawRes
        fun rawFor(id: String): Int = when (id) {
            "tap" -> R.raw.step_tap
            "dial" -> R.raw.step_dial
            "keys" -> R.raw.step_keys
            "wood" -> R.raw.step_wood
            "pop" -> R.raw.step_pop
            "drop" -> R.raw.step_drop
            "bubble" -> R.raw.step_bubble
            "snap" -> R.raw.step_snap
            "thud" -> R.raw.step_thud
            "blip" -> R.raw.step_blip
            "marimba" -> R.raw.step_marimba
            "glass" -> R.raw.step_glass
            "chime" -> R.raw.step_chime
            else -> R.raw.slider_tick
        }

        /**
         * Opens the sound at [uri] and hands it to [use] with its length, or returns null when it
         * cannot be read: gone, its access taken back, empty, or longer than a step sound has any
         * business being. Also how the screen checks a sound before choosing it.
         */
        fun <T> canRead(context: Context, uri: Uri, use: (android.content.res.AssetFileDescriptor, Long) -> T?): T? =
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val length = if (afd.length >= 0) afd.length else afd.parcelFileDescriptor.statSize
                    if (length <= 0 || length > MAX_DEVICE_BYTES) null else use(afd, length)
                }
            } catch (_: Exception) {
                // SecurityException for access that has gone, FileNotFoundException for a file that
                // has, IllegalArgumentException for a provider that does not know the uri.
                null
            }

        /** A step sound is a moment long; a file this size is a song, and would decode to nothing useful. */
        const val MAX_DEVICE_BYTES = 4L * 1024 * 1024

        /**
         * The usage that plays a tick at the volume of what is being set.
         *
         * Brightness has no volume of its own, so its tick is a system sound like any other.
         */
        fun usageFor(target: String, stream: Int?): Int = when {
            target == QuickSliderStore.TARGET_BRIGHTNESS -> AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
            stream == AudioManager.STREAM_RING -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
            stream == AudioManager.STREAM_ALARM -> AudioAttributes.USAGE_ALARM
            stream == AudioManager.STREAM_NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
            stream == AudioManager.STREAM_VOICE_CALL -> AudioAttributes.USAGE_VOICE_COMMUNICATION
            else -> AudioAttributes.USAGE_MEDIA
        }
    }
}
