package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.SharedPref
import java.util.concurrent.TimeUnit

/**
 * Decides whether a full-screen ad — an interstitial or an app-open ad — may take the screen
 * *right now*. Like [ReviewPrompter], it knows nothing about Activities or the ad SDK, so the whole
 * policy is two pure functions that can be read — and tested — on their own.
 *
 * ### Interstitials: only ever the answer to something the user just finished
 *
 * They used to fire on six forward screen transitions from the home screen, which meant the ad
 * appeared the moment a card was tapped — before the screen the user asked for — and, with
 * navigation that fast, close enough to a Back press to read as "the ad came from pressing Back".
 * Play treats both as unexpected, disruptive placements. The break points, the only [Trigger]s that
 * can ever pass, are the app's major completed interactions:
 *
 *  - [Trigger.SERVICE_STARTED] — the user switched the bar on and the host confirmed it is up.
 *  - [Trigger.SETTINGS_APPLIED] — the user pressed the Save tick on the Appearance screen.
 *  - [Trigger.GESTURE_ASSIGNED] — the user picked an action for a gesture and it was written, with
 *    no permission prompt raised by it.
 *  - [Trigger.QUICK_DIAL_ADDED] — the user added a contact to Quick Dial.
 *
 * Deliberately *not* break points, and rejected by name so that no future call site can quietly
 * reintroduce them: [Trigger.SCREEN_TRANSITION] (opening a screen is the start of a task, not the
 * end of one) and [Trigger.BACK_NAVIGATION] (the user is leaving, and an ad there feels like a trap
 * on the exit). Settings that save as they are dragged or toggled are not break points either: one
 * slider move is not a finished task.
 *
 * ### App-open ads: a return, not a hop
 *
 * Only when the user has really been away ([MIN_TIME_AWAY_MS]) — not back from the system settings
 * page, the share sheet or a contact picker — and at most once per [APP_OPEN_COOLDOWN_MS] and
 * [MAX_APP_OPEN_PER_DAY] a day.
 *
 * ### Limits on both
 *
 *  - Never for Pro, never in the post-install grace period.
 *  - Never while a permission / system-settings / consent flow is in progress.
 *  - Never while another full-screen ad is on screen, and never within [MIN_GAP_AFTER_ANY_AD_MS] of
 *    any ad, so two full-screen ads can never arrive back to back.
 *  - Interstitials: [INTERSTITIAL_COOLDOWN_MS] apart, [MAX_PER_SESSION] per process,
 *    [MAX_INTERSTITIALS_PER_DAY] a day, and never within [FOREGROUND_QUIET_MS] of the app coming to
 *    the foreground or [AFTER_APP_OPEN_QUIET_MS] of an app-open ad.
 */
object AdPacing {

    /** Why the caller wants to show an interstitial. See the class KDoc for which ones can pass. */
    enum class Trigger {
        SERVICE_STARTED,
        SETTINGS_APPLIED,
        GESTURE_ASSIGNED,
        QUICK_DIAL_ADDED,
        SCREEN_TRANSITION,
        BACK_NAVIGATION
    }

    /** The triggers that are genuine break points. Everything else is refused outright. */
    val BREAK_POINTS = setOf(
        Trigger.SERVICE_STARTED,
        Trigger.SETTINGS_APPLIED,
        Trigger.GESTURE_ASSIGNED,
        Trigger.QUICK_DIAL_ADDED
    )

    /** Quiet period after the app comes to the foreground. */
    val FOREGROUND_QUIET_MS = TimeUnit.SECONDS.toMillis(60)

    /** Quiet period after an app-open ad was shown or dismissed. */
    val AFTER_APP_OPEN_QUIET_MS = TimeUnit.SECONDS.toMillis(60)

    /** Between two interstitials. */
    val INTERSTITIAL_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(3)

    /** Between any two full-screen ads, of either kind. */
    val MIN_GAP_AFTER_ANY_AD_MS = TimeUnit.MINUTES.toMillis(2)

    /** Interstitials per app process. */
    const val MAX_PER_SESSION = 3

    /** Interstitials per calendar day. */
    const val MAX_INTERSTITIALS_PER_DAY = 6

    /** Between two app-open ads. */
    val APP_OPEN_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(30)

    /** App-open ads per calendar day. */
    const val MAX_APP_OPEN_PER_DAY = 3

    /** How long the app must have been in the background for a return to count as an app open. */
    val MIN_TIME_AWAY_MS = TimeUnit.SECONDS.toMillis(30)

    /**
     * Everything the interstitial decision depends on, as plain values. Timestamps are epoch
     * millis, 0 when the event has never happened.
     */
    data class Signals(
        val trigger: Trigger,
        val isPro: Boolean,
        val inGracePeriod: Boolean,
        /** A permission, system-settings or consent flow is up (SharedPref.isAppOpenAdPaused). */
        val systemFlowInProgress: Boolean,
        val fullScreenAdShowing: Boolean,
        val sessionInterstitialCount: Int,
        val interstitialsToday: Int,
        val lastInterstitialAt: Long,
        val lastAnyAdAt: Long,
        val lastAppOpenAt: Long,
        val foregroundAt: Long,
        val now: Long
    )

    fun mayShowInterstitial(signals: Signals): Boolean = with(signals) {
        if (trigger !in BREAK_POINTS) return false

        if (isPro) return false
        if (inGracePeriod) return false
        if (systemFlowInProgress) return false
        if (fullScreenAdShowing) return false
        if (sessionInterstitialCount >= MAX_PER_SESSION) return false
        if (interstitialsToday >= MAX_INTERSTITIALS_PER_DAY) return false

        // No foreground stamp means we cannot prove the launch window is over. Fail closed.
        if (foregroundAt <= 0L) return false

        // A clock moved backwards would make every elapsed check negative, which the comparisons
        // below would read as "long ago". Checked first, so it blocks instead.
        if (now < foregroundAt) return false
        if (lastInterstitialAt > 0L && now < lastInterstitialAt) return false
        if (lastAnyAdAt > 0L && now < lastAnyAdAt) return false
        if (lastAppOpenAt > 0L && now < lastAppOpenAt) return false

        if (now - foregroundAt < FOREGROUND_QUIET_MS) return false
        if (lastAppOpenAt > 0L && now - lastAppOpenAt < AFTER_APP_OPEN_QUIET_MS) return false
        if (lastInterstitialAt > 0L && now - lastInterstitialAt < INTERSTITIAL_COOLDOWN_MS) return false
        if (lastAnyAdAt > 0L && now - lastAnyAdAt < MIN_GAP_AFTER_ANY_AD_MS) return false

        return true
    }

    /** Reads the persisted and in-process facts and applies [mayShowInterstitial]. */
    fun mayShowInterstitial(
        preference: SharedPref,
        trigger: Trigger,
        isPro: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean = mayShowInterstitial(
        Signals(
            trigger = trigger,
            isPro = isPro,
            inGracePeriod = preference.isInAdGracePeriod(),
            systemFlowInProgress = preference.isAppOpenAdPaused(),
            fullScreenAdShowing = Session.fullScreenAdShowing,
            sessionInterstitialCount = preference.getSessionInterstitialCount(),
            interstitialsToday = preference.getInterstitialsToday(now),
            lastInterstitialAt = preference.getLastInterstitialAdTimeMillis(),
            lastAnyAdAt = preference.getLastAnyAdTimeMillis(),
            lastAppOpenAt = maxOf(preference.getLastAppOpenAdTimeMillis(), Session.appOpenHiddenAt),
            foregroundAt = Session.foregroundAt,
            now = now
        )
    )

    /** Everything the app-open decision depends on. Timestamps are epoch millis, 0 for never. */
    data class AppOpenSignals(
        val isPro: Boolean,
        val inGracePeriod: Boolean,
        val systemFlowInProgress: Boolean,
        val fullScreenAdShowing: Boolean,
        val appOpenToday: Int,
        val lastAppOpenAt: Long,
        val lastAnyAdAt: Long,
        /** When the app last went to the background; 0 on a cold start of this process. */
        val backgroundAt: Long,
        val now: Long
    )

    fun mayShowAppOpen(signals: AppOpenSignals): Boolean = with(signals) {
        if (isPro) return false
        if (inGracePeriod) return false
        if (systemFlowInProgress) return false
        if (fullScreenAdShowing) return false
        if (appOpenToday >= MAX_APP_OPEN_PER_DAY) return false

        // Backwards clocks block, as for interstitials.
        if (lastAppOpenAt > 0L && now < lastAppOpenAt) return false
        if (lastAnyAdAt > 0L && now < lastAnyAdAt) return false
        if (backgroundAt > 0L && now < backgroundAt) return false

        // A cold start has no background stamp and is a real open. A warm return has to have been
        // away long enough not to be a hop out to a system page and back.
        if (backgroundAt > 0L && now - backgroundAt < MIN_TIME_AWAY_MS) return false
        if (lastAppOpenAt > 0L && now - lastAppOpenAt < APP_OPEN_COOLDOWN_MS) return false
        if (lastAnyAdAt > 0L && now - lastAnyAdAt < MIN_GAP_AFTER_ANY_AD_MS) return false

        return true
    }

    /** Reads the persisted and in-process facts and applies [mayShowAppOpen]. */
    fun mayShowAppOpen(
        preference: SharedPref,
        isPro: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean = mayShowAppOpen(
        AppOpenSignals(
            isPro = isPro,
            inGracePeriod = preference.isInAdGracePeriod(),
            systemFlowInProgress = preference.isAppOpenAdPaused(),
            fullScreenAdShowing = Session.fullScreenAdShowing,
            appOpenToday = preference.getAppOpenAdsToday(now),
            lastAppOpenAt = preference.getLastAppOpenAdTimeMillis(),
            lastAnyAdAt = preference.getLastAnyAdTimeMillis(),
            backgroundAt = Session.backgroundAt,
            now = now
        )
    )

    /**
     * Process-lifetime facts nothing persists: when the app last came to the foreground and went
     * to the background, when the last app-open ad was dismissed, and whether a full-screen ad is
     * up. In memory on purpose — a fresh process is a fresh foreground, and a stale "ad showing"
     * flag must not survive a crash.
     */
    object Session {
        @Volatile
        var foregroundAt: Long = 0L
            private set

        @Volatile
        var backgroundAt: Long = 0L
            private set

        @Volatile
        var appOpenHiddenAt: Long = 0L
            private set

        @Volatile
        private var appOpenShowing = false

        @Volatile
        private var interstitialShowing = false

        val fullScreenAdShowing: Boolean get() = appOpenShowing || interstitialShowing

        /** Process ON_START. */
        fun onAppForeground(now: Long = System.currentTimeMillis()) {
            foregroundAt = now
        }

        /** Process ON_STOP. */
        fun onAppBackground(now: Long = System.currentTimeMillis()) {
            backgroundAt = now
        }

        fun onAppOpenAdShowing(showing: Boolean, now: Long = System.currentTimeMillis()) {
            if (appOpenShowing && !showing) appOpenHiddenAt = now
            appOpenShowing = showing
        }

        fun onInterstitialShowing(showing: Boolean) {
            interstitialShowing = showing
        }
    }
}
