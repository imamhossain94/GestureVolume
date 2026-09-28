package com.newagedevs.gesturevolume.ui.screens.walkthrough

import com.newagedevs.gesturevolume.utils.UserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which pages the walkthrough shows each style, and in what order. */
class WalkthroughPagesTest {

    private val modes = listOf(UserMode.REGULAR, UserMode.ADVANCED)

    @Test
    fun `simple shows its own page, moving the bar, and both permissions`() {
        assertEquals(
            listOf(
                WalkPage.Intro, WalkPage.Style, WalkPage.Simple, WalkPage.Move,
                WalkPage.Permission, WalkPage.Notifications,
            ),
            pagesFor(UserMode.REGULAR, askNotifications = true),
        )
    }

    @Test
    fun `advanced shows its three pages, moving the bar, and accessibility last`() {
        assertEquals(
            listOf(
                WalkPage.Intro, WalkPage.Style, WalkPage.QuickSlider, WalkPage.Deck, WalkPage.LongPress,
                WalkPage.Move, WalkPage.Permission, WalkPage.Notifications, WalkPage.Accessibility,
            ),
            pagesFor(UserMode.ADVANCED, askNotifications = true),
        )
    }

    @Test
    fun `below Android 13 neither style has a notifications page`() {
        modes.forEach { mode ->
            val pages = pagesFor(mode, askNotifications = false)
            assertFalse(mode, WalkPage.Notifications in pages)
            assertTrue(mode, WalkPage.Permission in pages)
        }
    }

    @Test
    fun `moving the bar is the last lesson before the permissions`() {
        // The page whose button says Got it.
        modes.forEach { mode ->
            listOf(true, false).forEach { ask ->
                val pages = pagesFor(mode, ask)
                assertEquals(mode, WalkPage.Move, pages[pages.indexOf(WalkPage.Permission) - 1])
            }
        }
    }

    @Test
    fun `every list runs in the order the pages slide`() {
        // A page slides in from the side its place in WalkPage puts it on, so a list out of that
        // order would slide a step forward in from behind.
        modes.forEach { mode ->
            listOf(true, false).forEach { ask ->
                val pages = pagesFor(mode, ask)
                pages.zipWithNext().forEach { (a, b) -> assertTrue("$mode: $a before $b", a.ordinal < b.ordinal) }
                assertEquals(mode, pages.size, pages.toSet().size)
            }
        }
    }

    @Test
    fun `a tour leaves out the permissions already granted, and only those`() {
        val all = setOf(WalkPage.Permission, WalkPage.Notifications, WalkPage.Accessibility)
        assertEquals(
            listOf(
                WalkPage.Intro, WalkPage.Style, WalkPage.QuickSlider, WalkPage.Deck, WalkPage.LongPress, WalkPage.Move,
            ),
            pagesFor(UserMode.ADVANCED, askNotifications = true, granted = all),
        )
        assertEquals(
            listOf(WalkPage.Intro, WalkPage.Style, WalkPage.Simple, WalkPage.Move, WalkPage.Notifications),
            pagesFor(UserMode.REGULAR, askNotifications = true, granted = setOf(WalkPage.Permission)),
        )
        // Only permission pages are ever left out, whatever the set holds.
        modes.forEach { mode ->
            val lessons = pagesFor(mode, askNotifications = true).filterNot { it.asksPermission }
            assertEquals(mode, lessons, pagesFor(mode, askNotifications = true, granted = WalkPage.entries.toSet()))
        }
    }

    @Test
    fun `got it is on the last lesson, whether or not permission pages follow it`() {
        modes.forEach { mode ->
            listOf(emptySet(), setOf(WalkPage.Permission, WalkPage.Notifications, WalkPage.Accessibility)).forEach { granted ->
                val pages = pagesFor(mode, askNotifications = true, granted = granted)
                assertEquals("$mode $granted", WalkPage.Move, lastTutorialOf(pages))
            }
        }
    }
}
