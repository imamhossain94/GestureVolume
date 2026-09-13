package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.SharedPref
import java.util.concurrent.TimeUnit

/**
 * Decides whether an interstitial may take the screen *right now*. Like [ReviewPrompter], it knows
 * nothing about Activities or the ad SDK, so the whole policy is one pure function that can be
 * read — and tested — on its own.
 *
 * The rule the design starts from: **an interstitial is only ever the answer to something the
 * user just finished.** It used to fire on six forward screen transitions from the home screen,
 * which meant the ad appeared the moment a card was tapped — before the screen the user asked for
 * — and, with navigation that fast, close enough to a Back press to read as "the ad came from
 * pressing Back". Play treats both as unexpected, disruptive placements.
 *
 * ### The break points (the only [Trigger]s that can ever pass)
 *
 *  - [Trigger.SERVICE_STARTED] — the user switched the bar on and the host confirmed it is up.
 *    Setup is done; the user's next move is to leave the app and use the bar.
 *  - [Trigger.SETTINGS_APPLIED] — the user pressed the Save tick on the Appearance screen. The
 *    change is written and the toast says so; nothing is in flight.
 *
 * Deliberately *not* break points, and rejected by name so that no future call site can quietly
 * reintroduce them: [Trigger.SCREEN_TRANSITION] (opening a screen is the start of a task, not the
 * end of one) and [Trigger.BACK_NAVIGATION] (system back, predictive back, the toolbar arrow, or the
 * "Apply" in a discard dialog that Back opened — the user is leaving, and an ad there feels like a
 * trap on the exit).
 *
 * ### Hard guards, applied to every break point
 *
 *  - Never for Pro, never in the post-install grace period, at most [MAX_PER_SESSION] per process.
 *  - Never while a permission / system-settings / consent flow is in progress — the same
 *    `isAppOpenAdPaused` flag the app-open ad already honours.
 *  - Never while another full-screen ad is on screen.
 *  - Never within [FOREGROUND_QUIET_MS] of the app coming to the foreground: that window belongs
 *    to the app-open ad, the update check and the user getting their bearings.
 *  - Never within [AFTER_APP_OPEN_QUIET_MS] of an app-open ad being shown *or* dismissed, so two
 *    full-screen ads can never arrive back to back.
 *  - The existing cooldowns: [INTERSTITIAL_COOLDOWN_MS] since the last interstitial and
 *    [MIN_GAP_AFTER_ANY_AD_MS] since any ad.
 */
object AdPacing {

    /** Why the caller wants to show an interstitial. See the class KDoc for which ones can pass. */
    enum class Trigger {
        SERVICE_STARTED,
        SETTINGS_APPLIED,
        SCREEN_TRANSITION,
        BACK_NAVIGATION
    }

    /** The triggers that are genuine break points. Everything else is refused outright. */
    val BREAK_POINTS = setOf(Trigger.SERVICE_STARTED, Trigger.SETTINGS_APPLIED)

    /** Quiet period after the app comes to the foreground. */
    val FOREGROUND_QUIET_MS = TimeUnit.SECONDS.toMillis(60)

    /** Quiet period after an app-open ad was shown or dismissed. */
    val AFTER_APP_OPEN_QUIET_MS = TimeUnit.SECONDS.toMillis(60)

    /** Mirrors SharedPref's INTERSTITIAL_AD_COOLDOWN (3 minutes). */
    val INTERSTITIAL_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(3)

    /** Mirrors SharedPref's MIN_TIME_BETWEEN_ANY_ADS (90 seconds). */
    val MIN_GAP_AFTER_ANY_AD_MS = TimeUnit.SECONDS.toMillis(90)

    /** Mirrors SharedPref's MAX_INTERSTITIALS_PER_SESSION. */
    const val MAX_PER_SESSION = 5

    /**
     * Everything the decision depends on, as plain values. Timestamps are epoch millis, 0 when
     * the event has never happened.
     */
    data class Signals(
        val trigger: Trigger,
        val isPro: Boolean,
        val inGracePeriod: Boolean,
        /** A permission, system-settings or consent flow is up (SharedPref.isAppOpenAdPaused). */
        val systemFlowInProgress: Boolean,
        val fullScreenAdShowing: Boolean,
        val sessionInterstitialCount: Int,
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
            lastInterstitialAt = preference.getLastInterstitialAdTimeMillis(),
            lastAnyAdAt = preference.getLastAnyAdTimeMillis(),
            lastAppOpenAt = maxOf(preference.getLastAppOpenAdTimeMillis(), Session.appOpenHiddenAt),
            foregroundAt = Session.foregroundAt,
            now = now
        )
    )

    /**
     * Process-lifetime facts nothing persists: when the app last came to the foreground, when the
     * last app-open ad was dismissed, and whether a full-screen ad is up. In memory on purpose — a
     * fresh process is a fresh foreground, and a stale "ad showing" flag must not survive a crash.
     */
    object Session {
        @Volatile
        var foregroundAt: Long = 0L
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

        fun onAppOpenAdShowing(showing: Boolean, now: Long = System.currentTimeMillis()) {
            if (appOpenShowing && !showing) appOpenHiddenAt = now
            appOpenShowing = showing
        }

        fun onInterstitialShowing(showing: Boolean) {
            interstitialShowing = showing
        }
    }
}
