package com.newagedevs.gesturevolume.utils

import androidx.annotation.StringRes
import com.newagedevs.gesturevolume.R

/**
 * The user-facing name of an audio stream.
 *
 * Its own file, and separate from [AudioStreamResolver], for the same reason
 * [HandlerActionCatalog] is separate from [HandlerActions]: the resolver holds behaviour and
 * imports no resources so it stays testable on the host JVM, while presentation lives here. Both
 * the overlay and the settings screen call this, so a stream never gets a name in two places.
 */
object AudioStreamCatalog {

    @StringRes
    fun labelFor(stream: Int): Int = when (stream) {
        AudioStreamResolver.STREAM_VOICE_CALL -> R.string.volume_stream_call
        AudioStreamResolver.STREAM_ALARM -> R.string.volume_stream_alarm
        AudioStreamResolver.STREAM_RING -> R.string.volume_stream_ring
        else -> R.string.volume_stream_media
    }

    /** The label for a mode identifier, for the settings row's current-value summary. */
    @StringRes
    fun labelForMode(mode: String): Int = when (mode) {
        VolumeStreamMode.MEDIA_ONLY -> R.string.volume_stream_media_only
        VolumeStreamMode.MATCH_VOLUME_KEYS -> R.string.volume_stream_match_keys
        else -> R.string.volume_stream_follow_playback
    }

    @StringRes
    fun descriptionForMode(mode: String): Int = when (mode) {
        VolumeStreamMode.MEDIA_ONLY -> R.string.volume_stream_media_only_desc
        VolumeStreamMode.MATCH_VOLUME_KEYS -> R.string.volume_stream_match_keys_desc
        else -> R.string.volume_stream_follow_playback_desc
    }
}
