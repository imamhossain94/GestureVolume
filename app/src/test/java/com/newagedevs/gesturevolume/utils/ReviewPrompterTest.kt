package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The review policy is the one piece of this app whose bugs are invisible in testing: every gate
 * fails *closed*, so a mistake shows up as "nobody is ever asked", which looks identical to "the
 * conditions haven't been met yet". These tests are what distinguish the two.
 */
class ReviewPrompterTest {

    private val now = TimeUnit.DAYS.toMillis(400)

    /** A user who has earned the prompt on every axis. Each test spoils exactly one thing. */
    private fun eligible(
        serviceRunning: Boolean = true,
        updateFlowActive: Boolean = false,
        askCount: Int = 0,
        lastAskVersion: Int = 0,
        versionCode: Int = 30,
        launchCount: Int = ReviewPrompter.MIN_LAUNCHES,
        installedAt: Long = now - ReviewPrompter.MIN_AGE_MS - 1,
        lastAskAt: Long = 0L,
        lastAdAt: Long = 0L,
        at: Long = now
    ) = ReviewPrompter.Signals(
        serviceRunning = serviceRunning,
        updateFlowActive = updateFlowActive,
        askCount = askCount,
        lastAskVersion = lastAskVersion,
        versionCode = versionCode,
        launchCount = launchCount,
        installedAt = installedAt,
        lastAskAt = lastAskAt,
        lastAdAt = lastAdAt,
        now = at
    )

    @Test
    fun `asks when every condition is met`() {
        assertTrue(ReviewPrompter.shouldAsk(eligible()))
    }

    // ---- "seems like the user likes the app" ---------------------------------------------------

    @Test
    fun `never asks while the service is off`() {
        assertFalse(ReviewPrompter.shouldAsk(eligible(serviceRunning = false)))
    }

    @Test
    fun `never asks during the first days after install`() {
        assertFalse(
            ReviewPrompter.shouldAsk(
                eligible(installedAt = now - ReviewPrompter.MIN_AGE_MS + 1)
            )
        )
    }

    @Test
    fun `never asks before enough launches`() {
        assertFalse(
            ReviewPrompter.shouldAsk(eligible(launchCount = ReviewPrompter.MIN_LAUNCHES - 1))
        )
    }

    // ---- "several times, systematically" -------------------------------------------------------

    @Test
    fun `asks again once the gap has passed`() {
        val lastAsk = now - ReviewPrompter.MIN_GAP_MS - 1
        assertTrue(
            ReviewPrompter.shouldAsk(eligible(askCount = 1, lastAskAt = lastAsk))
        )
    }

    @Test
    fun `stays quiet inside the gap`() {
        val lastAsk = now - ReviewPrompter.MIN_GAP_MS + 1
        assertFalse(
            ReviewPrompter.shouldAsk(eligible(askCount = 1, lastAskAt = lastAsk))
        )
    }

    @Test
    fun `stops permanently once the budget is spent`() {
        assertFalse(
            ReviewPrompter.shouldAsk(
                eligible(
                    askCount = ReviewPrompter.MAX_ASKS,
                    lastAskAt = now - ReviewPrompter.MIN_GAP_MS * 10
                )
            )
        )
    }

    @Test
    fun `never asks twice on the same version`() {
        assertFalse(
            ReviewPrompter.shouldAsk(
                eligible(
                    askCount = 1,
                    lastAskVersion = 30,
                    versionCode = 30,
                    lastAskAt = now - ReviewPrompter.MIN_GAP_MS * 10
                )
            )
        )
    }

    // ---- "not weirdly" -------------------------------------------------------------------------

    @Test
    fun `never races the in-app update dialog`() {
        assertFalse(ReviewPrompter.shouldAsk(eligible(updateFlowActive = true)))
    }

    @Test
    fun `never asks immediately after an ad`() {
        assertFalse(
            ReviewPrompter.shouldAsk(eligible(lastAdAt = now - ReviewPrompter.AD_QUIET_MS + 1))
        )
    }

    @Test
    fun `asks once the ad is far enough behind`() {
        assertTrue(
            ReviewPrompter.shouldAsk(eligible(lastAdAt = now - ReviewPrompter.AD_QUIET_MS - 1))
        )
    }

    // ---- degenerate inputs ---------------------------------------------------------------------

    @Test
    fun `a missing install stamp blocks rather than opens the gate`() {
        assertFalse(ReviewPrompter.shouldAsk(eligible(installedAt = 0L)))
    }

    @Test
    fun `a clock moved backwards does not open every gate at once`() {
        // Every elapsed subtraction goes negative here, which compares as "less than the minimum"
        // on each gate — without the explicit guard this is the one input that prompts everybody.
        assertFalse(
            ReviewPrompter.shouldAsk(
                eligible(installedAt = now + TimeUnit.DAYS.toMillis(30))
            )
        )
        assertFalse(
            ReviewPrompter.shouldAsk(
                eligible(askCount = 1, lastAskAt = now + TimeUnit.DAYS.toMillis(30))
            )
        )
    }
}
