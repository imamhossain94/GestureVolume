package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.AppGestureStore
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.handler_action.ActionPickerRoute
import com.newagedevs.gesturevolume.ui.screens.handler_action.ActionPickerScreen
import com.newagedevs.gesturevolume.ui.screens.handler_action.HandlerActionsScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.HandlerActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Actions screen, its picker, and the try-it pad answering a real swipe, on this phone's own
 * settings. Left in the app's cache as actions_*.png.
 */
@RunWith(AndroidJUnit4::class)
class ActionsRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel by lazy {
        val preference = SharedPref(compose.activity)
        MainViewModel(preference, BillingManager(compose.activity, preference))
    }

    @Test
    fun screen() {
        compose.setContent {
            GestureVolumeTheme { HandlerActionsScreen(viewModel = viewModel, onNavigateBack = {}) }
        }
        compose.waitForIdle()
        save("actions_screen_0")
        // A swipe up on the pad: it should say which gesture that was, and what it does.
        val title = compose.onNodeWithText("Try it").getBoundsInRoot()
        compose.onRoot().performTouchInput {
            val x = width - 60.dp.toPx()
            val top = title.bottom.toPx() + 30.dp.toPx()
            swipe(Offset(x, top + 120.dp.toPx()), Offset(x, top + 30.dp.toPx()), durationMillis = 250)
        }
        Thread.sleep(600)
        compose.waitForIdle()
        save("actions_screen_swiped")
        for (i in 1..5) {
            compose.onRoot().performTouchInput {
                swipe(Offset(width * 0.03f, height * 0.8f), Offset(width * 0.03f, height * 0.4f), durationMillis = 400)
            }
            Thread.sleep(400)
            compose.waitForIdle()
            save("actions_screen_$i")
        }
    }

    /** Two taps and three taps on the pad, on this phone's settings, whatever those two are set to. */
    @Test
    fun padTaps() {
        compose.setContent {
            GestureVolumeTheme { HandlerActionsScreen(viewModel = viewModel, onNavigateBack = {}) }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithText("Try it").getBoundsInRoot()
        fun at(scope: androidx.compose.ui.test.TouchInjectionScope) = with(scope) {
            Offset(width - 60.dp.toPx(), title.bottom.toPx() + 110.dp.toPx())
        }
        compose.onRoot().performTouchInput { val p = at(this); click(p); click(p) }
        Thread.sleep(1200)
        compose.waitForIdle()
        save("actions_pad_double")
        compose.onRoot().performTouchInput { val p = at(this); click(p); click(p); click(p) }
        Thread.sleep(1200)
        compose.waitForIdle()
        save("actions_pad_triple")
    }

    /** The screen and the picker on their side: the test's own activity turned, nothing else. */
    @Test
    fun landscape() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        Thread.sleep(1500)
        var picking by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            GestureVolumeTheme {
                if (picking) {
                    ActionPickerRoute(viewModel = viewModel, slot = AppGestureStore.Slot.DOUBLE_TAP, onDone = {})
                } else {
                    HandlerActionsScreen(viewModel = viewModel, onNavigateBack = {})
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(500)
        save("actions_land_screen")
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.03f, height * 0.8f), Offset(width * 0.03f, height * 0.3f), durationMillis = 300)
        }
        Thread.sleep(300)
        compose.runOnIdle { picking = true }
        compose.waitForIdle()
        Thread.sleep(500)
        save("actions_land_picker")
    }

    @Test
    fun picker() {
        compose.setContent {
            GestureVolumeTheme { ActionPickerRoute(viewModel = viewModel, slot = AppGestureStore.Slot.SWIPE_UP, onDone = {}) }
        }
        compose.waitForIdle()
        save("actions_picker_0")
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.2f) }
        Thread.sleep(400)
        compose.waitForIdle()
        save("actions_picker_1")
    }

    @Test
    fun pickerForAnApp() {
        compose.setContent {
            GestureVolumeTheme {
                ActionPickerScreen(
                    slot = AppGestureStore.Slot.SWIPE_IN,
                    currentAction = HandlerActions.OPEN_DECK,
                    onLeft = false,
                    onBack = {},
                    onSelect = {},
                    subtitle = "Chrome",
                    everywhereAction = HandlerActions.OPEN_DECK,
                    followsEverywhere = true,
                    onUseEverywhere = {},
                )
            }
        }
        compose.waitForIdle()
        save("actions_picker_app")
    }

    private fun save(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
