package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.ui.components.DemoGesture
import com.newagedevs.gesturevolume.ui.components.GestureDemoState
import com.newagedevs.gesturevolume.ui.components.PREVIEW_STAGE_MAX_HEIGHT
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.AppearanceStateHolder
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.GestureCaption
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.HandlerPreviewEffects
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.HandlerPreviewSurface
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.PreviewEffects
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.PreviewGestures
import com.newagedevs.gesturevolume.utils.BarBehaviour
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.HandlerShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Appearance preview, stopped part-way through its demo, drawn on this phone: the number on
 * the bar, Android's volume panel, the brightness readout and the caption, each where the settings
 * say. The frames are left in the app's cache as PNGs (appearance_*.png), to be looked at.
 */
@RunWith(AndroidJUnit4::class)
class HandlerPreviewEffectsRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val steps = listOf(DemoGesture.TAP, DemoGesture.SWIPE_UP, DemoGesture.SWIPE_DOWN)

    @Test
    fun aSwipeUpShowsTheLevelRisingOnTheBarAndInThePanel() {
        show(
            "swipe_up_panel", step = 1, fraction = 0.9f,
            PreviewGestures(HandlerActions.OPEN_VOLUME_UI, HandlerActions.INCREASE_VOLUME_UI, HandlerActions.DECREASE_VOLUME_UI, BarBehaviour.SWIPE_STEP_BY_LENGTH),
        )
        compose.onNodeWithText("Swipe up · Increase volume and show UI").assertExists()
        // 40% and four of fifteen steps up.
        assertEquals("67%", barLabel())
    }

    @Test
    fun withThePercentageOffTheBarKeepsItsIcon() {
        show(
            "swipe_up_no_percent", step = 1, fraction = 0.9f,
            PreviewGestures(HandlerActions.OPEN_VOLUME_UI, HandlerActions.INCREASE_VOLUME, HandlerActions.DECREASE_VOLUME, BarBehaviour.SWIPE_STEP_BY_LENGTH),
            showPercent = false,
        )
        compose.onNodeWithText("Swipe up · Increase volume").assertExists()
        assertEquals(null, barLabel())
    }

    @Test
    fun aFixedStepMovesOnceAndSwipeDownBringsItBack() {
        show(
            "swipe_down_fixed", step = 2, fraction = 0.5f,
            PreviewGestures(HandlerActions.OPEN_VOLUME_UI, HandlerActions.INCREASE_VOLUME, HandlerActions.DECREASE_VOLUME, 10),
        )
        compose.onNodeWithText("Swipe down · Decrease volume").assertExists()
        assertEquals("40%", barLabel())
    }

    @Test
    fun aBrightnessSwipeDimsTheScreenAndReadsOut() {
        show(
            "swipe_up_brightness", step = 1, fraction = 0.9f,
            PreviewGestures(HandlerActions.OPEN_VOLUME_UI, HandlerActions.INCREASE_BRIGHTNESS, HandlerActions.DECREASE_BRIGHTNESS, BarBehaviour.SWIPE_STEP_BY_LENGTH),
        )
        compose.onNodeWithText("Brightness 67%").assertExists()
        assertEquals("67%", barLabel())
    }

    @Test
    fun aTapOpensThePanelAndASwitchedOffSwipeSaysSo() {
        show(
            "tap_panel", step = 0, fraction = 0.6f,
            PreviewGestures(HandlerActions.OPEN_VOLUME_UI, HandlerActions.INCREASE_VOLUME, HandlerActions.NONE, BarBehaviour.SWIPE_STEP_BY_LENGTH),
        )
        compose.onNodeWithText("Tap · Open volume UI").assertExists()
        assertEquals(null, barLabel())
    }

    @Test
    fun atRestThereIsOnlyTheBar() {
        show(
            "rest", step = 2, fraction = 1f,
            PreviewGestures(HandlerActions.OPEN_VOLUME_UI, HandlerActions.INCREASE_VOLUME_UI, HandlerActions.DECREASE_VOLUME_UI, BarBehaviour.SWIPE_STEP_BY_LENGTH),
            passes = 0,
        )
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("·", substring = true)).fetchSemanticsNodes().isEmpty())
        assertEquals(null, barLabel())
    }

    /** The preview with its demo held at [fraction] of [step], saved as appearance_[name].png. */
    private fun show(
        name: String,
        step: Int,
        fraction: Float,
        gestures: PreviewGestures,
        showPercent: Boolean = true,
        passes: Int = 1,
    ) {
        val demo = GestureDemoState(steps, passes).apply {
            stepIndex = step
            stepFraction = fraction
        }
        val effects = PreviewEffects(demo, gestures)
        val state = draft()
        compose.setContent {
            // As the Appearance screen lays it out: the effects on the phone's screen, over the bar,
            // and the caption on the stage. Room under the stage for its description.
            Box(modifier = Modifier.size(360.dp, PREVIEW_STAGE_MAX_HEIGHT + 60.dp)) {
                HandlerPreviewSurface(
                    state = state,
                    barLabel = { effects.barLabel(showPercent) },
                    demo = demo,
                    caption = { gesture -> GestureCaption(gesture, effects.actionOf(gesture)) },
                ) {
                    HandlerPreviewEffects(
                        effects = effects,
                        barAtStart = false,
                        barReach = 34.dp,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }
        // Past the panels' and the caption's fade-ins.
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "appearance_$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** The Simple button: 30 by 100 dp, a translucent indigo, its icon in white. */
    private fun draft() = AppearanceStateHolder(
        initialGravity = Gravity.END,
        initialWidth = 30f,
        initialHeight = 100f,
        initialBgColor = Color(0xFF4F46E5),
        initialBgAlpha = 128,
        initialStrokeColor = Color.White,
        initialStrokeWidth = 1f,
        initialStrokeAlpha = 255,
        initialCornerTL = 15f,
        initialCornerTR = 15f,
        initialCornerBL = 15f,
        initialCornerBR = 15f,
        initialShape = HandlerShape.ROUNDED,
        initialFlare = HandlerShape.DEFAULT_FLARE,
        initialIconRes = R.drawable.ic_vol_increase,
        initialIconSize = 18f,
        initialIconColor = Color.White,
        initialShowIcon = true,
        initialVibrate = false,
        initialEdgeMargin = 4f,
        initialSnapToEdge = true,
        initialPositionFraction = 0.5f,
        initialPosXFraction = 1f,
    )

    /** The number the bar is showing in place of its icon, or null while it shows the icon. */
    private fun barLabel(): String? {
        var found: String? = null
        compose.runOnIdle {
            fun walk(view: View) {
                if (view is TextView && view.visibility == View.VISIBLE && view.text.endsWith("%")) found = view.text.toString()
                if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
            walk(compose.activity.window.decorView)
        }
        return found
    }
}
