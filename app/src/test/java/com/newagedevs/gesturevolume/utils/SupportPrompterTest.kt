package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The support nudge asks for money, so a guard that stops guarding is a nag. Each test spoils
 * exactly one condition of an otherwise eligible moment.
 */
class SupportPrompterTest {

    private val now = TimeUnit.DAYS.toMillis(400)

    private fun eligible(
        isPro: Boolean = false,
        optedOut: Boolean = false,
        askCount: Int = 0,
        lastAskAt: Long = 0L,
        launchCount: Int = SupportPrompter.MIN_LAUNCHES,
        happyMoments: Int = SupportPrompter.MIN_HAPPY_MOMENTS,
        installedAt: Long = now - SupportPrompter.MIN_AGE_MS,
        lastReviewAskAt: Long = 0L,
        lastAdAt: Long = 0L,
        lastTroubleAt: Long = 0L,
        at: Long = now
    ) = SupportPrompter.Signals(
        isPro = isPro,
        optedOut = optedOut,
        askCount = askCount,
        lastAskAt = lastAskAt,
        launchCount = launchCount,
        happyMoments = happyMoments,
        installedAt = installedAt,
        lastReviewAskAt = lastReviewAskAt,
        lastAdAt = lastAdAt,
        lastTroubleAt = lastTroubleAt,
        now = at
    )

    @Test
    fun `asks when every condition is met`() {
        assertTrue(SupportPrompter.shouldAsk(eligible()))
    }

    @Test
    fun `never for pro users or after opting out`() {
        assertFalse(SupportPrompter.shouldAsk(eligible(isPro = true)))
        assertFalse(SupportPrompter.shouldAsk(eligible(optedOut = true)))
    }

    @Test
    fun `waits for a week, enough launches and enough happy moments`() {
        assertFalse(SupportPrompter.shouldAsk(eligible(installedAt = now - SupportPrompter.MIN_AGE_MS + 1)))
        assertFalse(SupportPrompter.shouldAsk(eligible(launchCount = SupportPrompter.MIN_LAUNCHES - 1)))
        assertFalse(SupportPrompter.shouldAsk(eligible(happyMoments = SupportPrompter.MIN_HAPPY_MOMENTS - 1)))
        assertFalse(SupportPrompter.shouldAsk(eligible(installedAt = 0L)))
    }

    @Test
    fun `spaced a month apart, within a lifetime budget`() {
        assertFalse(SupportPrompter.shouldAsk(eligible(askCount = 1, lastAskAt = now - SupportPrompter.MIN_GAP_MS + 1)))
        assertTrue(SupportPrompter.shouldAsk(eligible(askCount = 1, lastAskAt = now - SupportPrompter.MIN_GAP_MS)))
        assertFalse(
            SupportPrompter.shouldAsk(
                eligible(askCount = SupportPrompter.MAX_ASKS, lastAskAt = now - SupportPrompter.MIN_GAP_MS * 10)
            )
        )
    }

    @Test
    fun `never close to a review ask, an ad or trouble`() {
        assertFalse(
            SupportPrompter.shouldAsk(eligible(lastReviewAskAt = now - SupportPrompter.AFTER_REVIEW_QUIET_MS + 1))
        )
        assertFalse(SupportPrompter.shouldAsk(eligible(lastAdAt = now - SupportPrompter.AD_QUIET_MS + 1)))
        assertFalse(
            SupportPrompter.shouldAsk(eligible(lastTroubleAt = now - ReviewPrompter.TROUBLE_QUIET_MS + 1))
        )
    }

    @Test
    fun `a clock moved backwards does not open the gates`() {
        assertFalse(SupportPrompter.shouldAsk(eligible(installedAt = now + TimeUnit.DAYS.toMillis(30))))
        assertFalse(SupportPrompter.shouldAsk(eligible(askCount = 1, lastAskAt = now + TimeUnit.DAYS.toMillis(30))))
    }
}
