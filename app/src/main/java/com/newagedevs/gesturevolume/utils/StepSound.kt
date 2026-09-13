package com.newagedevs.gesturevolume.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
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
 */
class StepSound(private val context: Context) {

    private var pool: SoundPool? = null
    private var poolUsage = -1
    private var soundId = 0

    /** Whether the sample has finished loading. A step before that is silent rather than late. */
    @Volatile
    private var loaded = false

    /** Loads the sample for [usage], reusing the pool already built for it. */
    fun prepare(usage: Int) {
        if (pool != null && poolUsage == usage) return
        release()
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val next = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        next.setOnLoadCompleteListener { _, id, status ->
            if (status == 0 && id == soundId) loaded = true
        }
        pool = next
        poolUsage = usage
        soundId = next.load(context, R.raw.slider_tick, 1)
    }

    /**
     * One tick for a step that landed at [level], 0..1.
     *
     * @param adaptive whether the pitch follows the level: low near empty, high near full. A plain
     *   click keeps one note, a little quieter, so it reads as a detent rather than a readout.
     */
    fun play(level: Float, adaptive: Boolean) {
        val p = pool ?: return
        if (!loaded) return
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
        loaded = false
    }

    companion object {
        private const val MAX_STREAMS = 3

        /** The pitch range of the adaptive tick, as playback rates: a little under an octave either side. */
        private const val MIN_RATE = 0.6f
        private const val MAX_RATE = 1.8f

        private const val CLICK_VOLUME = 0.7f

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
