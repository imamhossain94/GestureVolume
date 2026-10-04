package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Config
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Feature
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Grants
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Need
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Permission
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings in, missing permissions out. Every warning in the app (the home cards, the notes beside
 * settings, the Permissions screen) reads this list, so a case missed here is a feature that fails
 * silently.
 */
class PermissionNeedsTest {

    private val nothingGranted = Grants(
        overlay = false, writeSettings = false, accessibility = false, notificationPolicy = false,
        notifications = false, phone = false
    )

    /** Everything refused except the overlay, so the overlay need does not crowd each assertion. */
    private val onlyOverlay = nothingGranted.copy(overlay = true)

    private fun compute(config: Config = Config(), grants: Grants = Grants()) =
        PermissionNeeds.compute(config, grants).all

    @Test
    fun `nothing switched on and everything granted needs nothing`() {
        assertTrue(compute().isEmpty())
    }

    @Test
    fun `the overlay is needed even by a bare configuration`() {
        assertEquals(
            listOf(Need(Permission.OVERLAY, Feature.FLOATING_BAR)),
            compute(Config(), nothingGranted)
        )
    }

    @Test
    fun `a brightness swipe needs modify system settings until it is granted`() {
        val config = Config(slotActions = mapOf(Feature.SWIPE_UP to HandlerActions.INCREASE_BRIGHTNESS))
        assertEquals(
            listOf(Need(Permission.WRITE_SETTINGS, Feature.SWIPE_UP)),
            compute(config, Grants(writeSettings = false))
        )
        assertTrue(compute(config, Grants()).isEmpty())
    }

    @Test
    fun `auto rotate and auto brightness toggles need modify system settings`() {
        val config = Config(
            slotActions = mapOf(
                Feature.DOUBLE_TAP to HandlerActions.TOGGLE_AUTO_ROTATE,
                Feature.TRIPLE_TAP to HandlerActions.TOGGLE_AUTO_BRIGHTNESS,
            )
        )
        assertEquals(
            listOf(
                Need(Permission.WRITE_SETTINGS, Feature.DOUBLE_TAP),
                Need(Permission.WRITE_SETTINGS, Feature.TRIPLE_TAP),
            ),
            compute(config, Grants(writeSettings = false))
        )
    }

    @Test
    fun `every accessibility action in a slot needs the service`() {
        HandlerActions.ACCESSIBILITY_ACTIONS.forEach { action ->
            assertEquals(
                action,
                listOf(Need(Permission.ACCESSIBILITY, Feature.LONG_PRESS)),
                compute(Config(slotActions = mapOf(Feature.LONG_PRESS to action)), Grants(accessibility = false))
            )
        }
    }

    @Test
    fun `a do not disturb action needs do not disturb access`() {
        assertEquals(
            listOf(Need(Permission.NOTIFICATION_POLICY, Feature.SINGLE_TAP)),
            compute(Config(slotActions = mapOf(Feature.SINGLE_TAP to HandlerActions.TOGGLE_DND)), onlyOverlay)
        )
    }

    @Test
    fun `a long-press menu entry is attributed to the menu`() {
        val config = Config(
            menuActions = listOf(HandlerActions.OPEN_DECK, HandlerActions.LOCK, HandlerActions.TOGGLE_DND)
        )
        assertEquals(
            listOf(
                Need(Permission.ACCESSIBILITY, Feature.LONG_PRESS_MENU_ENTRY),
                Need(Permission.NOTIFICATION_POLICY, Feature.LONG_PRESS_MENU_ENTRY),
            ),
            compute(config, onlyOverlay)
        )
    }

    @Test
    fun `a quick slider on brightness needs modify system settings, and so does the slot opening it`() {
        val config = Config(
            slotActions = mapOf(Feature.DOUBLE_TAP to HandlerActions.OPEN_QUICK_SLIDER),
            sliderTarget = QuickSliderStore.TARGET_BRIGHTNESS,
        )
        assertEquals(
            listOf(
                Need(Permission.WRITE_SETTINGS, Feature.DOUBLE_TAP),
                Need(Permission.WRITE_SETTINGS, Feature.QUICK_SLIDER_BRIGHTNESS),
            ),
            compute(config, Grants(writeSettings = false))
        )
        // Driving the media volume, the same slot needs nothing.
        assertTrue(
            compute(config.copy(sliderTarget = QuickSliderStore.TARGET_MEDIA), Grants(writeSettings = false)).isEmpty()
        )
    }

    @Test
    fun `only instant volume keys need the accessibility service`() {
        val grants = Grants(accessibility = false)
        assertEquals(
            listOf(Need(Permission.ACCESSIBILITY, Feature.QUICK_SLIDER_INSTANT_KEYS)),
            compute(Config(volumeKeyMode = QuickSliderStore.VOLUME_KEYS_INSTANT), grants)
        )
        assertTrue(compute(Config(volumeKeyMode = QuickSliderStore.VOLUME_KEYS_FOLLOW), grants).isEmpty())
        assertTrue(compute(Config(volumeKeyMode = QuickSliderStore.VOLUME_KEYS_OFF), grants).isEmpty())
    }

    @Test
    fun `hiding the bar in apps needs the accessibility service`() {
        val needs = PermissionNeeds.compute(Config(hideInApps = true), Grants(accessibility = false))
        assertEquals(listOf(Feature.HIDE_IN_APPS), needs.featuresFor(Permission.ACCESSIBILITY))
        assertEquals(1, needs.missingCount)
        assertEquals(1, needs.forScreen(Screen.VISIBILITY).size)
        assertTrue(compute(Config(hideInApps = false), Grants(accessibility = false)).isEmpty())
    }

    @Test
    fun `deck tiles need what their action needs`() {
        val config = Config(
            deckTiles = setOf(
                DeckTiles.SEARCH, DeckTiles.DND, DeckTiles.ROTATION, DeckTiles.BRIGHTNESS,
                DeckTiles.LOCK, DeckTiles.SCREENSHOT, DeckTiles.FLASHLIGHT
            )
        )
        val needs = PermissionNeeds.compute(config, onlyOverlay)
        assertEquals(
            listOf(Permission.WRITE_SETTINGS, Permission.ACCESSIBILITY, Permission.NOTIFICATION_POLICY),
            needs.permissions
        )
        assertTrue(needs.all.all { it.feature == Feature.DECK_TILE })
        assertTrue(compute(Config(deckTiles = setOf(DeckTiles.SEARCH, DeckTiles.TIMER)), onlyOverlay).isEmpty())
    }

    @Test
    fun `deck direct call needs phone`() {
        assertEquals(
            listOf(Need(Permission.PHONE, Feature.DECK_DIRECT_CALL)),
            compute(Config(directCall = true), Grants(phone = false))
        )
        assertTrue(compute(Config(), Grants(phone = false)).isEmpty())
    }

    @Test
    fun `notification controls need the notification permission`() {
        assertEquals(
            listOf(Need(Permission.NOTIFICATIONS, Feature.NOTIFICATION_CONTROLS)),
            compute(Config(notificationControls = true), Grants(notifications = false))
        )
        assertTrue(compute(Config(notificationControls = false), Grants(notifications = false)).isEmpty())
    }

    @Test
    fun `needs are ordered by permission with the overlay first`() {
        val config = Config(
            slotActions = mapOf(Feature.SWIPE_UP to HandlerActions.INCREASE_BRIGHTNESS),
            volumeKeyMode = QuickSliderStore.VOLUME_KEYS_INSTANT,
            notificationControls = true,
            directCall = true,
        )
        val needs = PermissionNeeds.compute(config, nothingGranted)
        assertEquals(
            listOf(
                Permission.OVERLAY, Permission.WRITE_SETTINGS, Permission.ACCESSIBILITY,
                Permission.NOTIFICATIONS, Permission.PHONE
            ),
            needs.permissions
        )
        assertEquals(Need(Permission.OVERLAY, Feature.FLOATING_BAR), needs.first)
        assertEquals(listOf(Feature.SWIPE_UP), needs.forScreen(Screen.ACTIONS).map { it.feature })
        assertEquals(
            listOf(Feature.NOTIFICATION_CONTROLS),
            needs.forScreen(Screen.PERMISSIONS).map { it.feature }
        )
        assertTrue(needs.forScreen(Screen.APPEARANCE).isEmpty())
    }

    @Test
    fun `the same feature wanting a permission twice is listed once`() {
        val config = Config(menuActions = listOf(HandlerActions.LOCK, HandlerActions.SCREENSHOT))
        assertEquals(
            listOf(Need(Permission.ACCESSIBILITY, Feature.LONG_PRESS_MENU_ENTRY)),
            compute(config, Grants(accessibility = false))
        )
    }

    @Test
    fun `an app's own gestures need the accessibility service, and whatever their actions need`() {
        val config = Config(appGestureActions = listOf(HandlerActions.INCREASE_BRIGHTNESS, HandlerActions.MUTE))
        assertEquals(
            listOf(
                Need(Permission.WRITE_SETTINGS, Feature.APP_GESTURES),
                Need(Permission.ACCESSIBILITY, Feature.APP_GESTURES),
            ),
            compute(config, Grants(writeSettings = false, accessibility = false))
        )
        assertTrue(compute(config, Grants()).isEmpty())
    }

    @Test
    fun `no app with gestures of its own needs nothing for them`() {
        assertTrue(compute(Config(appGestureActions = emptyList()), Grants(accessibility = false)).isEmpty())
    }

    @Test
    fun `plain actions and unknown tiles need nothing`() {
        assertTrue(PermissionNeeds.permissionsFor(HandlerActions.MUTE, QuickSliderStore.TARGET_BRIGHTNESS).isEmpty())
        assertTrue(PermissionNeeds.permissionsFor(HandlerActions.OPEN_DECK, QuickSliderStore.TARGET_BRIGHTNESS).isEmpty())
        assertNull(PermissionNeeds.permissionForDeckTile("no such tile"))
    }
}
