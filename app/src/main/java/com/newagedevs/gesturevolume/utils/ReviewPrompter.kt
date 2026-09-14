package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.SharedPref
import java.util.concurrent.TimeUnit

/**
 * Decides *whether* to ask for a Play in-app review. Deliberately knows nothing about Activities
 * or the Play library, so the policy can be reasoned about — and tested — on its own.
 *
 * Two things drive the design, and both come from how the Play API actually behaves:
 *
 *  1. **The API never tells you whether the user saw anything.** `launchReviewFlow` completes
 *     successfully whether Play showed the rating sheet, silently swallowed it for exceeding the
 *     per-user quota, or decided the user had already rated. So "we asked" can never be treated as
 *     "the user was asked". Burning a single lifetime opportunity on one such call means most
 *     users are never really prompted at all. Hence a small budget of spaced attempts rather than
 *     one shot.
 *
 *  2. **Asking is only worth doing when the answer is likely to be positive.** A prompt fired at
 *     a random moment samples a random mood. So it is never fired on launch or resume: it is only
 *     ever considered at a *happy moment* — the user just finished something and it worked (see
 *     MainEffect.HappyMoment) — and even then only with evidence the app is working out for them.
 *
 * The evidence used, in order of how much it means:
 *
 *  - **The service is running right now.** Turning the overlay on requires clearing a system
 *    permission screen, and leaving it on means the user kept a permanent floating control on
 *    their screen. Nobody does that for an app they dislike.
 *  - **Happy moments**, counted: saves, gestures assigned, contacts added. One is a try; several
 *    are someone making the app their own.
 *  - **No recent trouble.** A visit to Troubleshoot or Feedback in the last week means something
 *    went wrong; that is the worst week to ask for stars.
 *  - **Days since install**, not launches alone. Someone who opened the app five times in one
 *    evening is setting it up, not enjoying it; the same five launches across a week is a habit.
 *  - **Launch count**, as a floor under the above.
 */
object ReviewPrompter {

    /** Long enough that setup — permissions, placing the bar, trying gestures — is behind them. */
    val MIN_AGE_MS = TimeUnit.DAYS.toMillis(3)

    /** Enough returns to distinguish a keeper from a try-once install. */
    const val MIN_LAUNCHES = 5

    /** Finished, successful actions before the first ask. The current one counts. */
    const val MIN_HAPPY_MOMENTS = 3

    /** Quiet period after the user went looking for help. Shared with [SupportPrompter]. */
    val TROUBLE_QUIET_MS = TimeUnit.DAYS.toMillis(7)

    /**
     * Gap between attempts.
     *
     * Long on purpose. Play's own quota already suppresses most repeat prompts, so asking more
     * often mostly wastes attempts invisibly — and for the minority who do see it twice, a
     * month and a half apart is the difference between a reminder and nagging.
     */
    val MIN_GAP_MS = TimeUnit.DAYS.toMillis(45)

    /** Lifetime budget. After this the app stops asking, whatever happens. */
    const val MAX_ASKS = 3

    /**
     * Quiet period after any ad.
     *
     * An interstitial followed by "enjoying the app?" reads as a shakedown and reliably earns one
     * star.
     */
    val AD_QUIET_MS = TimeUnit.MINUTES.toMillis(3)

    /**
     * Everything the decision depends on, as plain values.
     *
     * Extracted from [SharedPref] at the call site rather than read inside the predicate, so the
     * policy is a pure function of its inputs and can be exercised without a device, a Context, or
     * a clock.
     */
    data class Signals(
        val serviceRunning: Boolean,
        val updateFlowActive: Boolean,
        val askCount: Int,
        val lastAskVersion: Int,
        val versionCode: Int,
        val launchCount: Int,
        val happyMoments: Int,
        val installedAt: Long,
        val lastAskAt: Long,
        val lastAdAt: Long,
        val lastTroubleAt: Long,
        val now: Long
    )

    fun shouldAsk(signals: Signals): Boolean = with(signals) {
        if (updateFlowActive) return false
        if (!serviceRunning) return false

        if (askCount >= MAX_ASKS) return false
        if (lastAskVersion == versionCode) return false
        if (launchCount < MIN_LAUNCHES) return false
        if (happyMoments < MIN_HAPPY_MOMENTS) return false

        // Zero means the stamp was never written. Treated as "too new" rather than "infinitely
        // old", so a missing stamp can never be the reason a prompt fires.
        if (installedAt <= 0L) return false

        // A clock moved backwards (timezone change, manual set) would otherwise make every
        // elapsed check negative and open all the gates at once. Checked before the elapsed
        // comparisons below, not after.
        if (now < installedAt) return false
        if (lastAskAt > 0L && now < lastAskAt) return false
        if (lastTroubleAt > 0L && now < lastTroubleAt) return false

        if (now - installedAt < MIN_AGE_MS) return false
        if (lastAskAt > 0L && now - lastAskAt < MIN_GAP_MS) return false
        if (lastAdAt > 0L && now - lastAdAt < AD_QUIET_MS) return false
        if (lastTroubleAt > 0L && now - lastTroubleAt < TROUBLE_QUIET_MS) return false

        return true
    }

    /**
     * @param serviceRunning whether the overlay service is on *right now*.
     * @param updateFlowActive whether a Play in-app update is on screen or pending; two Play
     *   dialogs racing each other is the "weird" case this is here to stop.
     */
    fun shouldAsk(
        preference: SharedPref,
        serviceRunning: Boolean,
        updateFlowActive: Boolean,
        versionCode: Int,
        now: Long = System.currentTimeMillis()
    ): Boolean = shouldAsk(
        Signals(
            serviceRunning = serviceRunning,
            updateFlowActive = updateFlowActive,
            askCount = preference.getReviewAskCount(),
            lastAskVersion = preference.getReviewLastAskVersion(),
            versionCode = versionCode,
            launchCount = preference.getAppLaunchCount(),
            happyMoments = preference.getHappyMomentCount(),
            installedAt = preference.getFirstInstallTimeMillis(),
            lastAskAt = preference.getReviewLastAskTime(),
            lastAdAt = preference.getLastAnyAdTimeMillis(),
            lastTroubleAt = preference.getLastTroubleTime(),
            now = now
        )
    )
}
