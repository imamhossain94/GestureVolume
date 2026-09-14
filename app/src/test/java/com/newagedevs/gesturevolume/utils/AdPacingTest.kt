package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.utils.AdPacing.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Ad pacing fails *open* in the way users notice: a missed guard is an ad on the Back button, on
 * top of a cold start, or on every return from the settings page. Each test below spoils exactly
 * one condition of an otherwise allowed moment, so a guard that stops guarding shows up here rather
 * than in a policy strike.
 */
class AdPacingTest {

    private val now = TimeUnit.DAYS.toMillis(400)

    /** A moment where an interstitial is allowed on every axis. */
    private fun allowed(
        trigger: Trigger = Trigger.SETTINGS_APPLIED,
        isPro: Boolean = false,
        inGracePeriod: Boolean = false,
        systemFlowInProgress: Boolean = false,
        fullScreenAdShowing: Boolean = false,
        sessionInterstitialCount: Int = 0,
        interstitialsToday: Int = 0,
        lastInterstitialAt: Long = 0L,
        lastAnyAdAt: Long = 0L,
        lastAppOpenAt: Long = 0L,
        foregroundAt: Long = now - TimeUnit.MINUTES.toMillis(10),
        at: Long = now
    ) = AdPacing.Signals(
        trigger = trigger,
        isPro = isPro,
        inGracePeriod = inGracePeriod,
        systemFlowInProgress = systemFlowInProgress,
        fullScreenAdShowing = fullScreenAdShowing,
        sessionInterstitialCount = sessionInterstitialCount,
        interstitialsToday = interstitialsToday,
        lastInterstitialAt = lastInterstitialAt,
        lastAnyAdAt = lastAnyAdAt,
        lastAppOpenAt = lastAppOpenAt,
        foregroundAt = foregroundAt,
        now = at
    )

    /** A return to the app where an app-open ad is allowed on every axis. */
    private fun appOpen(
        isPro: Boolean = false,
        inGracePeriod: Boolean = false,
        systemFlowInProgress: Boolean = false,
        fullScreenAdShowing: Boolean = false,
        appOpenToday: Int = 0,
        lastAppOpenAt: Long = 0L,
        lastAnyAdAt: Long = 0L,
        backgroundAt: Long = now - TimeUnit.MINUTES.toMillis(10),
        at: Long = now
    ) = AdPacing.AppOpenSignals(
        isPro = isPro,
        inGracePeriod = inGracePeriod,
        systemFlowInProgress = systemFlowInProgress,
        fullScreenAdShowing = fullScreenAdShowing,
        appOpenToday = appOpenToday,
        lastAppOpenAt = lastAppOpenAt,
        lastAnyAdAt = lastAnyAdAt,
        backgroundAt = backgroundAt,
        now = at
    )

    // ---- the allowed case --------------------------------------------------------------------

    @Test
    fun `shows after an applied setting when every condition is met`() {
        assertTrue(AdPacing.mayShowInterstitial(allowed()))
    }

    @Test
    fun `shows at every major completed interaction`() {
        listOf(
            Trigger.SERVICE_STARTED,
            Trigger.SETTINGS_APPLIED,
            Trigger.GESTURE_ASSIGNED,
            Trigger.QUICK_DIAL_ADDED
        ).forEach { assertTrue(it.name, AdPacing.mayShowInterstitial(allowed(trigger = it))) }
    }

    // ---- break points ------------------------------------------------------------------------

    @Test
    fun `never on back navigation`() {
        assertFalse(AdPacing.mayShowInterstitial(allowed(trigger = Trigger.BACK_NAVIGATION)))
    }

    @Test
    fun `never on a mere screen transition`() {
        assertFalse(AdPacing.mayShowInterstitial(allowed(trigger = Trigger.SCREEN_TRANSITION)))
    }

    // ---- launch window -----------------------------------------------------------------------

    @Test
    fun `never inside the launch window`() {
        assertFalse(
            AdPacing.mayShowInterstitial(
                allowed(foregroundAt = now - AdPacing.FOREGROUND_QUIET_MS + 1)
            )
        )
    }

    @Test
    fun `shows once the launch window has passed`() {
        assertTrue(
            AdPacing.mayShowInterstitial(allowed(foregroundAt = now - AdPacing.FOREGROUND_QUIET_MS))
        )
    }

    @Test
    fun `an unknown foreground time blocks rather than opens the gate`() {
        assertFalse(AdPacing.mayShowInterstitial(allowed(foregroundAt = 0L)))
    }

    // ---- other ads ---------------------------------------------------------------------------

    @Test
    fun `never right after an app-open ad`() {
        // Any-ad gap satisfied on its own, so this isolates the app-open quiet period.
        assertFalse(
            AdPacing.mayShowInterstitial(
                allowed(lastAppOpenAt = now - AdPacing.AFTER_APP_OPEN_QUIET_MS + 1)
            )
        )
    }

    @Test
    fun `never while another full-screen ad is showing`() {
        assertFalse(AdPacing.mayShowInterstitial(allowed(fullScreenAdShowing = true)))
    }

    // ---- cooldowns, caps, grace --------------------------------------------------------------

    @Test
    fun `respects the interstitial cooldown`() {
        assertFalse(
            AdPacing.mayShowInterstitial(
                allowed(lastInterstitialAt = now - AdPacing.INTERSTITIAL_COOLDOWN_MS + 1)
            )
        )
        assertTrue(
            AdPacing.mayShowInterstitial(
                allowed(lastInterstitialAt = now - AdPacing.INTERSTITIAL_COOLDOWN_MS)
            )
        )
    }

    @Test
    fun `respects the gap after any ad`() {
        assertFalse(
            AdPacing.mayShowInterstitial(
                allowed(lastAnyAdAt = now - AdPacing.MIN_GAP_AFTER_ANY_AD_MS + 1)
            )
        )
    }

    @Test
    fun `respects the per-session cap`() {
        assertFalse(
            AdPacing.mayShowInterstitial(
                allowed(sessionInterstitialCount = AdPacing.MAX_PER_SESSION)
            )
        )
    }

    @Test
    fun `respects the daily interstitial cap`() {
        assertFalse(
            AdPacing.mayShowInterstitial(
                allowed(interstitialsToday = AdPacing.MAX_INTERSTITIALS_PER_DAY)
            )
        )
        assertTrue(
            AdPacing.mayShowInterstitial(
                allowed(interstitialsToday = AdPacing.MAX_INTERSTITIALS_PER_DAY - 1)
            )
        )
    }

    @Test
    fun `never during the post-install grace period`() {
        assertFalse(AdPacing.mayShowInterstitial(allowed(inGracePeriod = true)))
    }

    @Test
    fun `never while a permission or consent flow is in progress`() {
        assertFalse(AdPacing.mayShowInterstitial(allowed(systemFlowInProgress = true)))
    }

    @Test
    fun `never for pro users`() {
        assertFalse(AdPacing.mayShowInterstitial(allowed(isPro = true)))
    }

    @Test
    fun `a clock moved backwards does not open the gates`() {
        assertFalse(
            AdPacing.mayShowInterstitial(allowed(foregroundAt = now + TimeUnit.HOURS.toMillis(1)))
        )
        assertFalse(
            AdPacing.mayShowInterstitial(
                allowed(lastInterstitialAt = now + TimeUnit.HOURS.toMillis(1))
            )
        )
    }

    // ---- app-open ads ------------------------------------------------------------------------

    @Test
    fun `app-open shows on a real return when every condition is met`() {
        assertTrue(AdPacing.mayShowAppOpen(appOpen()))
    }

    @Test
    fun `app-open shows on a cold start, which has no background stamp`() {
        assertTrue(AdPacing.mayShowAppOpen(appOpen(backgroundAt = 0L)))
    }

    @Test
    fun `app-open never on a quick hop out and back`() {
        assertFalse(
            AdPacing.mayShowAppOpen(appOpen(backgroundAt = now - AdPacing.MIN_TIME_AWAY_MS + 1))
        )
        assertTrue(
            AdPacing.mayShowAppOpen(appOpen(backgroundAt = now - AdPacing.MIN_TIME_AWAY_MS))
        )
    }

    @Test
    fun `app-open respects its cooldown`() {
        assertFalse(
            AdPacing.mayShowAppOpen(appOpen(lastAppOpenAt = now - AdPacing.APP_OPEN_COOLDOWN_MS + 1))
        )
        assertTrue(
            AdPacing.mayShowAppOpen(appOpen(lastAppOpenAt = now - AdPacing.APP_OPEN_COOLDOWN_MS))
        )
    }

    @Test
    fun `app-open respects the gap after any ad`() {
        assertFalse(
            AdPacing.mayShowAppOpen(appOpen(lastAnyAdAt = now - AdPacing.MIN_GAP_AFTER_ANY_AD_MS + 1))
        )
    }

    @Test
    fun `app-open respects the daily cap`() {
        assertFalse(AdPacing.mayShowAppOpen(appOpen(appOpenToday = AdPacing.MAX_APP_OPEN_PER_DAY)))
    }

    @Test
    fun `app-open never over another ad, in grace, mid permission flow or for pro`() {
        assertFalse(AdPacing.mayShowAppOpen(appOpen(fullScreenAdShowing = true)))
        assertFalse(AdPacing.mayShowAppOpen(appOpen(inGracePeriod = true)))
        assertFalse(AdPacing.mayShowAppOpen(appOpen(systemFlowInProgress = true)))
        assertFalse(AdPacing.mayShowAppOpen(appOpen(isPro = true)))
    }

    @Test
    fun `app-open a clock moved backwards does not open the gates`() {
        assertFalse(
            AdPacing.mayShowAppOpen(appOpen(lastAppOpenAt = now + TimeUnit.HOURS.toMillis(1)))
        )
    }
}
