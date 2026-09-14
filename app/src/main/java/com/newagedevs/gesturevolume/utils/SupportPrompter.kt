package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.SharedPref
import java.util.concurrent.TimeUnit

/**
 * Decides whether to show the "support the developer" nudge towards Pro — the dialog that says the
 * app is free, the ads are what keep it that way, and one small purchase removes them.
 *
 * The same shape as [ReviewPrompter], and the same reasoning: asked of someone who has just got
 * something done and has been using the app for a while, it reads as an invitation; asked on
 * launch, it reads as a toll. So it is only considered at a happy moment (MainEffect.HappyMoment),
 * after a review has had its chance at that moment, and only for someone who clearly uses the app.
 *
 * And it is rarer than a review, because it asks for money: a month apart, four times in the life
 * of an install, never in the days after a review ask or a visit to Troubleshoot, and never again
 * once the user says so.
 */
object SupportPrompter {

    /** A week in: past setup, and past the review's own earliest ask. */
    val MIN_AGE_MS = TimeUnit.DAYS.toMillis(7)

    const val MIN_LAUNCHES = 8

    /** More than the review needs: this is the bigger ask. */
    const val MIN_HAPPY_MOMENTS = 5

    /** Between nudges. */
    val MIN_GAP_MS = TimeUnit.DAYS.toMillis(30)

    /** Lifetime budget. */
    const val MAX_ASKS = 4

    /** Never in the days after a review ask: one favour at a time. */
    val AFTER_REVIEW_QUIET_MS = TimeUnit.DAYS.toMillis(3)

    /** Never straight after an ad, which would make the nudge read as "pay to stop this". */
    val AD_QUIET_MS = TimeUnit.MINUTES.toMillis(3)

    data class Signals(
        val isPro: Boolean,
        val optedOut: Boolean,
        val askCount: Int,
        val lastAskAt: Long,
        val launchCount: Int,
        val happyMoments: Int,
        val installedAt: Long,
        val lastReviewAskAt: Long,
        val lastAdAt: Long,
        val lastTroubleAt: Long,
        val now: Long
    )

    fun shouldAsk(signals: Signals): Boolean = with(signals) {
        if (isPro) return false
        if (optedOut) return false
        if (askCount >= MAX_ASKS) return false
        if (launchCount < MIN_LAUNCHES) return false
        if (happyMoments < MIN_HAPPY_MOMENTS) return false

        // A missing install stamp is "too new", never "long ago".
        if (installedAt <= 0L) return false

        // Backwards clocks block rather than open every gate.
        if (now < installedAt) return false
        if (lastAskAt > 0L && now < lastAskAt) return false
        if (lastReviewAskAt > 0L && now < lastReviewAskAt) return false
        if (lastTroubleAt > 0L && now < lastTroubleAt) return false

        if (now - installedAt < MIN_AGE_MS) return false
        if (lastAskAt > 0L && now - lastAskAt < MIN_GAP_MS) return false
        if (lastReviewAskAt > 0L && now - lastReviewAskAt < AFTER_REVIEW_QUIET_MS) return false
        if (lastAdAt > 0L && now - lastAdAt < AD_QUIET_MS) return false
        if (lastTroubleAt > 0L && now - lastTroubleAt < ReviewPrompter.TROUBLE_QUIET_MS) return false

        return true
    }

    fun shouldAsk(
        preference: SharedPref,
        isPro: Boolean,
        now: Long = System.currentTimeMillis()
    ): Boolean = shouldAsk(
        Signals(
            isPro = isPro,
            optedOut = preference.isSupportOptedOut(),
            askCount = preference.getSupportAskCount(),
            lastAskAt = preference.getSupportLastAskTime(),
            launchCount = preference.getAppLaunchCount(),
            happyMoments = preference.getHappyMomentCount(),
            installedAt = preference.getFirstInstallTimeMillis(),
            lastReviewAskAt = preference.getReviewLastAskTime(),
            lastAdAt = preference.getLastAnyAdTimeMillis(),
            lastTroubleAt = preference.getLastTroubleTime(),
            now = now
        )
    )
}
