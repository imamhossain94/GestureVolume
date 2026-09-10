package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which stream the bar drives is the whole of the adaptive-volume feature, and it is the part that
 * cannot be checked by looking at it: every wrong answer still moves *a* volume, so a mistake shows
 * up in the field as "it changed the wrong thing during a call" rather than as anything a build
 * would catch.
 *
 * The tier ordering in [AudioStreamResolver.resolve] is what these pin down — particularly that
 * telephony is consulted before the playback list, which is the ordering the feature turns on.
 */
class AudioStreamResolverTest {

    /** A quiet, idle phone on the default mode. Each test spoils exactly one axis. */
    private fun signals(
        mode: String = VolumeStreamMode.FOLLOW_PLAYBACK,
        audioMode: Int = AudioStreamResolver.MODE_NORMAL,
        activeStreams: List<Int> = emptyList(),
        musicActive: Boolean = false,
        musicRecent: Boolean = false,
        ringWritable: Boolean = true,
        communicationRouteOpaque: Boolean = false,
    ) = AudioStreamResolver.Signals(
        mode = mode,
        audioMode = audioMode,
        activeStreams = activeStreams,
        musicActive = musicActive,
        musicRecent = musicRecent,
        ringWritable = ringWritable,
        communicationRouteOpaque = communicationRouteOpaque,
    )

    // ---- tier 0: the kill switch ------------------------------------------------------------

    @Test
    fun `media only short-circuits every other signal`() {
        val d = AudioStreamResolver.resolve(
            signals(
                mode = VolumeStreamMode.MEDIA_ONLY,
                audioMode = AudioStreamResolver.MODE_IN_CALL,
                activeStreams = listOf(AudioStreamResolver.STREAM_ALARM),
            )
        )
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
        assertEquals(AudioStreamResolver.Source.MEDIA_ONLY, d.source)
    }

    // ---- tier 1: telephony -------------------------------------------------------------------

    @Test
    fun `a circuit-switched call resolves to the call stream despite an empty playback list`() {
        val d = AudioStreamResolver.resolve(signals(audioMode = AudioStreamResolver.MODE_IN_CALL))
        assertEquals(AudioStreamResolver.STREAM_VOICE_CALL, d.stream)
        assertEquals(AudioStreamResolver.Source.TELEPHONY, d.source)
    }

    @Test
    fun `a voip call resolves to the call stream`() {
        val d = AudioStreamResolver.resolve(
            signals(audioMode = AudioStreamResolver.MODE_IN_COMMUNICATION)
        )
        assertEquals(AudioStreamResolver.STREAM_VOICE_CALL, d.stream)
    }

    @Test
    fun `a call outranks music that is still playing underneath it`() {
        val d = AudioStreamResolver.resolve(
            signals(
                audioMode = AudioStreamResolver.MODE_IN_CALL,
                activeStreams = listOf(AudioStreamResolver.STREAM_MUSIC),
                musicActive = true,
            )
        )
        assertEquals(AudioStreamResolver.STREAM_VOICE_CALL, d.stream)
    }

    @Test
    fun `an opaque route is reported so the caller stops claiming a percentage`() {
        val d = AudioStreamResolver.resolve(
            signals(
                audioMode = AudioStreamResolver.MODE_IN_CALL,
                communicationRouteOpaque = true,
            )
        )
        assertEquals(true, d.opaque)
    }

    @Test
    fun `call screening is not treated as a call`() {
        // MODE_CALL_SCREENING is 4. The user is listening to a screening prompt, not talking.
        val d = AudioStreamResolver.resolve(signals(audioMode = 4, musicActive = true))
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
    }

    @Test
    fun `an unknown future audio mode does not become a call`() {
        val d = AudioStreamResolver.resolve(signals(audioMode = 99, musicActive = true))
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
    }

    // ---- tier 2: ringing ---------------------------------------------------------------------

    @Test
    fun `a ringing phone resolves to the ring stream when the ringer is writable`() {
        val d = AudioStreamResolver.resolve(signals(audioMode = AudioStreamResolver.MODE_RINGTONE))
        assertEquals(AudioStreamResolver.STREAM_RING, d.stream)
        assertEquals(AudioStreamResolver.Source.RINGING, d.source)
    }

    @Test
    fun `a ringing phone falls back to media when the ringer cannot be written`() {
        val d = AudioStreamResolver.resolve(
            signals(audioMode = AudioStreamResolver.MODE_RINGTONE, ringWritable = false)
        )
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
    }

    // ---- tier 3: what is actually playing -----------------------------------------------------

    @Test
    fun `an alarm outranks background media regardless of list order`() {
        val d = AudioStreamResolver.resolve(
            signals(
                activeStreams = listOf(
                    AudioStreamResolver.STREAM_MUSIC,
                    AudioStreamResolver.STREAM_ALARM,
                ),
                musicActive = true,
            )
        )
        assertEquals(AudioStreamResolver.STREAM_ALARM, d.stream)
        assertEquals(AudioStreamResolver.Source.ACTIVE_PLAYBACK, d.source)
    }

    @Test
    fun `ring outranks media`() {
        val d = AudioStreamResolver.resolve(
            signals(
                activeStreams = listOf(
                    AudioStreamResolver.STREAM_MUSIC,
                    AudioStreamResolver.STREAM_RING,
                )
            )
        )
        assertEquals(AudioStreamResolver.STREAM_RING, d.stream)
    }

    @Test
    fun `an active ring stream drops to media when the ringer cannot be written`() {
        val d = AudioStreamResolver.resolve(
            signals(
                activeStreams = listOf(AudioStreamResolver.STREAM_RING),
                ringWritable = false,
            )
        )
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
    }

    @Test
    fun `an unrecognised stream in the active list is ignored rather than driven`() {
        // 5 is STREAM_NOTIFICATION, which this app deliberately will not drive.
        val d = AudioStreamResolver.resolve(signals(activeStreams = listOf(5)))
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
        assertEquals(AudioStreamResolver.Source.IDLE, d.source)
    }

    // ---- tier 4: the media grace window -------------------------------------------------------

    @Test
    fun `music playing with an empty configuration list still resolves to media`() {
        val d = AudioStreamResolver.resolve(signals(musicActive = true))
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
        assertEquals(AudioStreamResolver.Source.MEDIA_RECENT, d.source)
    }

    @Test
    fun `media that stopped within the grace window still resolves to media`() {
        val d = AudioStreamResolver.resolve(signals(musicRecent = true))
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
        assertEquals(AudioStreamResolver.Source.MEDIA_RECENT, d.source)
    }

    // ---- tier 5: idle ------------------------------------------------------------------------

    @Test
    fun `an idle swipe stays on media under follow playback`() {
        val d = AudioStreamResolver.resolve(signals())
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
        assertEquals(AudioStreamResolver.Source.IDLE, d.source)
    }

    @Test
    fun `an idle swipe reaches for the ringer under match volume keys`() {
        val d = AudioStreamResolver.resolve(signals(mode = VolumeStreamMode.MATCH_VOLUME_KEYS))
        assertEquals(AudioStreamResolver.STREAM_RING, d.stream)
        assertEquals(AudioStreamResolver.Source.IDLE, d.source)
    }

    @Test
    fun `match volume keys falls back to media when the ringer cannot be written`() {
        val d = AudioStreamResolver.resolve(
            signals(mode = VolumeStreamMode.MATCH_VOLUME_KEYS, ringWritable = false)
        )
        assertEquals(AudioStreamResolver.STREAM_MUSIC, d.stream)
    }

    // ---- the persistence vocabulary -----------------------------------------------------------

    @Test
    fun `an unknown stored mode sanitises to the default rather than disabling the feature`() {
        assertEquals(VolumeStreamMode.FOLLOW_PLAYBACK, VolumeStreamMode.sanitize("Follow the money"))
        assertEquals(VolumeStreamMode.FOLLOW_PLAYBACK, VolumeStreamMode.sanitize(null))
    }

    @Test
    fun `every known mode survives sanitising`() {
        VolumeStreamMode.ALL.forEach { assertEquals(it, VolumeStreamMode.sanitize(it)) }
    }
}
