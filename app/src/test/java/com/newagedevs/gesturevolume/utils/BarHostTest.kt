package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.utils.PermissionNeeds.Config
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Feature
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Grants
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Need
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Permission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What draws the bar: the stored choice, the host actually used for it, and the one permission the
 * bar then cannot do without. Every start — the switch, the boot receiver, the repair on launch,
 * the accessibility service connecting — routes through [BarHost.effective].
 */
class BarHostTest {

    @Test
    fun `the overlay is the default, and unknown or old values read as it`() {
        assertEquals(BarHost.OVERLAY, BarHost.fromStored(null))
        assertEquals(BarHost.OVERLAY, BarHost.fromStored("NOTIFICATION"))
        // The 1.4.0 test builds stored the enum name under another key; even read here it must
        // not switch anyone to the accessibility service.
        assertEquals(BarHost.OVERLAY, BarHost.fromStored("ACCESSIBILITY"))
    }

    @Test
    fun `stored values round trip`() {
        BarHost.entries.forEach { assertEquals(it, BarHost.fromStored(it.stored)) }
    }

    @Test
    fun `the accessibility service draws the bar once it is switched on`() {
        assertEquals(
            BarHost.ACCESSIBILITY,
            BarHost.effective(BarHost.ACCESSIBILITY, accessibilityEnabled = true, canDrawOverlays = false)
        )
        assertEquals(
            BarHost.ACCESSIBILITY,
            BarHost.effective(BarHost.ACCESSIBILITY, accessibilityEnabled = true, canDrawOverlays = true)
        )
    }

    @Test
    fun `chosen but switched off, the overlay carries on when it can`() {
        assertEquals(
            BarHost.OVERLAY,
            BarHost.effective(BarHost.ACCESSIBILITY, accessibilityEnabled = false, canDrawOverlays = true)
        )
        assertNull(BarHost.effective(BarHost.ACCESSIBILITY, accessibilityEnabled = false, canDrawOverlays = false))
    }

    @Test
    fun `the overlay never hands the bar to the accessibility service on its own`() {
        assertEquals(
            BarHost.OVERLAY,
            BarHost.effective(BarHost.OVERLAY, accessibilityEnabled = true, canDrawOverlays = true)
        )
        assertNull(BarHost.effective(BarHost.OVERLAY, accessibilityEnabled = true, canDrawOverlays = false))
    }

    private val nothingGranted = Grants(
        overlay = false, writeSettings = false, accessibility = false, notificationPolicy = false,
        notifications = false, phone = false
    )

    @Test
    fun `the bar needs the accessibility service, not the overlay, when that draws it`() {
        assertEquals(
            listOf(Need(Permission.ACCESSIBILITY, Feature.FLOATING_BAR)),
            PermissionNeeds.compute(Config(barHost = BarHost.ACCESSIBILITY), nothingGranted).all
        )
        assertTrue(
            PermissionNeeds.compute(
                Config(barHost = BarHost.ACCESSIBILITY),
                nothingGranted.copy(accessibility = true)
            ).all.isEmpty()
        )
    }

    @Test
    fun `notification controls need nothing while there is no notification`() {
        val config = Config(notificationControls = true)
        assertEquals(
            listOf(Need(Permission.NOTIFICATIONS, Feature.NOTIFICATION_CONTROLS)),
            PermissionNeeds.compute(config, Grants(notifications = false)).all
        )
        assertTrue(
            PermissionNeeds.compute(
                config.copy(barHost = BarHost.ACCESSIBILITY),
                Grants(notifications = false)
            ).all.isEmpty()
        )
    }
}
