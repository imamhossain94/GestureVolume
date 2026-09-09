package com.newagedevs.gesturevolume.utils

/**
 * Decides *which* audio stream the bar should drive, given a snapshot of what the device is doing.
 *
 * This is deliberately a pure function over plain integers, holding no [android.media.AudioManager]
 * and no [android.content.Context]. Two reasons, both practical:
 *
 *  1. **It is the part that can be wrong.** Reading the volume of a stream is trivial; choosing the
 *     stream is the whole feature. Keeping the choice free of Android types means it can be tested
 *     on the host JVM, and the entire test classpath of this project is `junit:junit:4.13.2` — no
 *     Robolectric, no mocking library — so a decision function that touched a framework class could
 *     not be tested at all.
 *  2. **The ordering is argued, not obvious.** Every tier below exists because some other ordering
 *     produces a visibly wrong result, and those arguments belong next to the code that encodes
 *     them rather than in a commit message nobody reads again.
 *
 * [VolumeController] owns the Android half: it gathers [Signals], calls [resolve], and turns the
 * [Decision] into index bounds and writes.
 */
object AudioStreamResolver {

    // Framework stream types, inlined rather than imported so this file stays host-testable.
    // These are public API constants and have been stable since API 1.
    const val STREAM_VOICE_CALL = 0
    const val STREAM_RING = 2
    const val STREAM_MUSIC = 3
    const val STREAM_ALARM = 4

    // Framework audio modes, likewise inlined.
    const val MODE_NORMAL = 0
    const val MODE_RINGTONE = 1
    const val MODE_IN_CALL = 2
    const val MODE_IN_COMMUNICATION = 3

    /**
     * The streams this app is willing to drive, in priority order, most important first.
     *
     * Rank, not list order, is what tier 3 compares. [android.media.AudioManager.getActivePlaybackConfigurations]
     * makes no promise about the order of what it returns, so picking "the first one" would be a
     * coin flip between a media player and an alarm. An alarm over music resolves to the alarm
     * because silencing the alarm is why the user reached for the bar.
     */
    private val PRIORITY = listOf(STREAM_VOICE_CALL, STREAM_RING, STREAM_ALARM, STREAM_MUSIC)

    /** Where a [Decision] came from. Carried so the UI can name the stream and the caller can log. */
    enum class Source {
        /** The user pinned the bar to media in settings; no system signal was consulted. */
        MEDIA_ONLY,

        /** A call is up, by audio mode. */
        TELEPHONY,

        /** The phone is ringing. */
        RINGING,

        /** Something is actually playing right now. */
        ACTIVE_PLAYBACK,

        /** Nothing is playing, but media was playing recently enough to still be what the user means. */
        MEDIA_RECENT,

        /** Nothing is playing and nothing was recently. */
        IDLE,
    }

    /**
     * A snapshot of everything the decision depends on.
     *
     * @param mode the user's [VolumeStreamMode] preference.
     * @param audioMode [android.media.AudioManager.getMode].
     * @param activeStreams stream types currently backed by an active playback configuration,
     *   already filtered to the four this app will drive.
     * @param musicActive [android.media.AudioManager.isMusicActive].
     * @param musicRecent media stopped within the grace window — see [Source.MEDIA_RECENT].
     * @param ringWritable false when the ringer is silent/vibrate or Do Not Disturb is filtering,
     *   which is when writing [STREAM_RING] would either be ignored or throw.
     * @param communicationRouteOpaque true when the call is routed somewhere whose live index this
     *   app cannot read — a Bluetooth headset on the hidden SCO stream.
     */
    data class Signals(
        val mode: String,
        val audioMode: Int,
        val activeStreams: List<Int>,
        val musicActive: Boolean,
        val musicRecent: Boolean,
        val ringWritable: Boolean,
        val communicationRouteOpaque: Boolean,
    )

    /**
     * @param opaque the resolved stream's live index cannot be read, so the caller must use a
     *   relative adjust and must not claim to know a percentage.
     */
    data class Decision(
        val stream: Int,
        val source: Source,
        val opaque: Boolean,
    )

    /**
     * First match wins. Each tier's placement is load-bearing:
     *
     * **Tier 0 — the kill switch, before any system signal.** A user who has chosen "Media only"
     * has said the bar must never surprise them, and a surprise is exactly what consulting the
     * system first would risk.
     *
     * **Tier 1 — telephony, before playback configurations.** This is the ordering the whole
     * feature turns on. A circuit-switched call is a hardware audio path with no `AudioTrack`
     * behind it, so `getActivePlaybackConfigurations()` returns an *empty list* during a normal
     * phone call. A resolver that consulted playback first would be blind in precisely the
     * situation the feature exists to fix, and would quietly adjust media volume mid-call.
     *
     * Note which modes are absent: `MODE_CALL_SCREENING`, `MODE_CALL_REDIRECT` and
     * `MODE_COMMUNICATION_REDIRECT` are deliberately *not* treated as calls — during screening the
     * user is listening to a screening prompt, not talking — and there is no `else` arm, so a mode
     * added by a future platform release falls through to the playback signal rather than being
     * guessed at as a call.
     *
     * **Tier 2 — ringing.** Only when the ringer can actually be written; see [Signals.ringWritable].
     *
     * **Tier 3 — what is actually playing**, by [PRIORITY] rank rather than list order.
     *
     * **Tier 4 — the media grace window.** Applied to media *only*. Extending a grace period to a
     * just-stopped alarm would mean the swipe after dismissing an alarm still moved alarm volume,
     * which is not what the user means.
     *
     * **Tier 5 — idle**, where the two remaining modes differ: "Follow playback" stays on media so
     * an idle swipe does what it has always done, while "Match volume keys" mirrors the hardware
     * rocker and reaches for the ringer.
     */
    fun resolve(s: Signals): Decision {
        // Tier 0
        if (s.mode == VolumeStreamMode.MEDIA_ONLY) {
            return Decision(STREAM_MUSIC, Source.MEDIA_ONLY, opaque = false)
        }

        // Tier 1
        if (s.audioMode == MODE_IN_CALL || s.audioMode == MODE_IN_COMMUNICATION) {
            return Decision(STREAM_VOICE_CALL, Source.TELEPHONY, opaque = s.communicationRouteOpaque)
        }

        // Tier 2
        if (s.audioMode == MODE_RINGTONE) {
            return if (s.ringWritable) {
                Decision(STREAM_RING, Source.RINGING, opaque = false)
            } else {
                Decision(STREAM_MUSIC, Source.RINGING, opaque = false)
            }
        }

        // Tier 3
        val ranked = s.activeStreams
            .filter { it in PRIORITY }
            .minByOrNull { PRIORITY.indexOf(it) }
        if (ranked != null) {
            if (ranked == STREAM_RING && !s.ringWritable) {
                return Decision(STREAM_MUSIC, Source.ACTIVE_PLAYBACK, opaque = false)
            }
            return Decision(ranked, Source.ACTIVE_PLAYBACK, opaque = false)
        }

        // Tier 4
        if (s.musicActive || s.musicRecent) {
            return Decision(STREAM_MUSIC, Source.MEDIA_RECENT, opaque = false)
        }

        // Tier 5
        return if (s.mode == VolumeStreamMode.MATCH_VOLUME_KEYS && s.ringWritable) {
            Decision(STREAM_RING, Source.IDLE, opaque = false)
        } else {
            Decision(STREAM_MUSIC, Source.IDLE, opaque = false)
        }
    }
}
