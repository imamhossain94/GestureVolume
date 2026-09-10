package com.newagedevs.gesturevolume.utils

/**
 * How hard the bar should try to follow the audio the device is actually playing.
 *
 * Like [HandlerActions], these strings are a **persistence format**: they are written verbatim into
 * SharedPreferences and read back on every launch. Existing values must never be renamed — only
 * added to.
 *
 * This is deliberately a separate object from [HandlerActions] rather than three more entries in
 * it. These are not assignable actions: they never appear in a gesture slot, so
 * [HandlerActionCatalog.ALL], `SwipeActionDialog` and `MainViewModel`'s swipe-icon lookups — the
 * three hand-synced lists that have already shipped one bug between them — need no change at all.
 */
object VolumeStreamMode {

    /**
     * Never adapt. The bar drives media volume and nothing else, which is what every build before
     * 1.3.5 did.
     */
    const val MEDIA_ONLY = "Media only"

    /**
     * Adapt to a call, a ringing phone, or an alarm, and otherwise stay on media.
     *
     * The default. It fixes the cases that are plainly wrong — a swipe during a call moving media
     * volume — without changing what an idle swipe does for anyone who has already learned this app.
     */
    const val FOLLOW_PLAYBACK = "Follow playback"

    /**
     * Behave like the hardware volume rocker, including reaching for the ringer when nothing is
     * playing.
     *
     * Opt-in, because it changes the meaning of an idle swipe.
     */
    const val MATCH_VOLUME_KEYS = "Match volume keys"

    val ALL: List<String> = listOf(MEDIA_ONLY, FOLLOW_PLAYBACK, MATCH_VOLUME_KEYS)

    /**
     * Maps a stored value onto one this build understands.
     *
     * Applied on read rather than as a one-shot upgrade migration, for the reason
     * [HandlerActions.sanitize] already records: `allowBackup="true"` means the preferences can
     * arrive from a cloud restore long after any migration would have run.
     */
    fun sanitize(mode: String?): String = if (mode in ALL) mode!! else FOLLOW_PLAYBACK
}
