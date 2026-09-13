package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The preset catalogue, and the behaviour each preset sets beyond the bar's look. */
class HandlerPresetsTest {

    @Test
    fun `the catalogue is Dock, Edge and Bold, and Dock is the default`() {
        assertEquals(listOf("Dock", "Edge", "Bold"), HandlerPresets.ALL.map { it.id })
        assertSame(HandlerPresets.byId("Dock"), HandlerPresets.DEFAULT)
        assertSame(HandlerPresets.byId("Edge"), HandlerPresets.EDGE)
    }

    @Test
    fun `a retired or unknown id applies nothing`() {
        listOf("Minimal", "Night", "Ghost", "Default", "", null).forEach {
            assertNull("$it", HandlerPresets.byId(it))
        }
    }

    @Test
    fun `every preset puts the bar a fifth of the way down with the shared gestures`() {
        HandlerPresets.ALL.forEach { preset ->
            val b = preset.behaviour
            val id = preset.id
            assertEquals(id, 0.20f, preset.positionFraction, 0f)
            assertEquals(id, HandlerActions.OPEN_VOLUME_UI, b.singleTap)
            assertEquals(id, HandlerActions.NONE, b.doubleTap)
            assertEquals(id, HandlerActions.NONE, b.tripleTap)
            assertEquals(id, HandlerActions.REPOSITION, b.longPress)
            assertEquals(id, HandlerActions.OPEN_QUICK_SLIDER, b.swipeUp)
            assertEquals(id, HandlerActions.OPEN_QUICK_SLIDER, b.swipeDown)
            assertEquals(id, HandlerActions.OPEN_DECK, b.swipeIn)
            assertEquals(id, HandlerActions.NONE, b.swipeOut)

            assertEquals(id, QuickSliderStore.TARGET_MEDIA, b.slider.target)
            assertTrue(id, b.slider.showValue)
            assertTrue(id, b.slider.showIcon)
            assertEquals(id, 220f, b.slider.lengthDp, 0f)
            assertTrue(id, b.slider.followHandlerShape)
            assertEquals(id, QuickSliderStore.VOLUME_KEYS_INSTANT, b.slider.volumeKeys)

            assertEquals(id, PanelAnimation.SLIDE, b.panelAnimation)
            assertEquals(id, ContextMenuLayout.GRID, b.menuLayout)
            assertEquals(id, 9, b.menuPerPage)
            // Every value is one the stores accept as it is, rather than one they would sanitise away.
            assertEquals(id, b.panelAnimation, PanelAnimation.sanitize(b.panelAnimation))
            assertEquals(id, b.menuPerPage, ContextMenuLayout.sanitizePerPage(b.menuPerPage))
            listOf(b.singleTap, b.doubleTap, b.tripleTap, b.longPress, b.swipeUp, b.swipeDown, b.swipeIn, b.swipeOut)
                .forEach { assertEquals(id, it, HandlerActions.sanitize(it)) }
        }
    }

    @Test
    fun `Dock and Edge follow the phone round with a thin flush panel`() {
        listOf(HandlerPresets.DEFAULT, HandlerPresets.EDGE).forEach { preset ->
            val b = preset.behaviour
            assertTrue(preset.id, b.dynamicPosition)
            assertEquals(preset.id, 24f, b.slider.thicknessDp, 0f)
            assertEquals(preset.id, 0f, b.slider.edgeOffsetDp, 0f)
            assertEquals(preset.id, 0f, preset.edgeMargin, 0f)
        }
    }

    @Test
    fun `Bold holds still and opens a panel as thick as the bubble`() {
        val bold = HandlerPresets.byId("Bold")!!
        assertFalse(bold.behaviour.dynamicPosition)
        assertEquals(8f, bold.edgeMargin, 0f)
        assertEquals(46f, bold.width, 0f)
        assertEquals(46f, bold.height, 0f)
        assertEquals(46f, bold.behaviour.slider.thicknessDp, 0f)
        assertEquals(8f, bold.behaviour.slider.edgeOffsetDp, 0f)
    }

    @Test
    fun `nine to a page is a three by three grid at the default menu width`() {
        assertEquals(3, ContextMenuLayout.columnsFor(ContextMenuLayout.DEFAULT_WIDTH_DP))
        assertEquals(9, 3 * ContextMenuLayout.columnsFor(ContextMenuLayout.DEFAULT_WIDTH_DP))
    }

    @Test
    fun `the Quick panel store's fresh-install defaults are the default preset's`() {
        val slider = HandlerPresets.DEFAULT.behaviour.slider
        assertEquals(
            HandlerPresets.SliderBehaviour(
                target = QuickSliderStore.DEFAULT_TARGET,
                showValue = QuickSliderStore.DEFAULT_SHOW_VALUE,
                showIcon = QuickSliderStore.DEFAULT_SHOW_ICON,
                lengthDp = QuickSliderStore.DEFAULT_LENGTH,
                thicknessDp = QuickSliderStore.DEFAULT_THICKNESS,
                edgeOffsetDp = QuickSliderStore.DEFAULT_EDGE_OFFSET,
                followHandlerShape = QuickSliderStore.DEFAULT_FOLLOW_HANDLER_SHAPE,
                volumeKeys = QuickSliderStore.DEFAULT_VOLUME_KEYS,
            ),
            slider,
        )
    }
}
