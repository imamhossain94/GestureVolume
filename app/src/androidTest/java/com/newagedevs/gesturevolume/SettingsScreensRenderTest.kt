package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.deck.DeckScreen
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.HandlerAppearanceScreen
import com.newagedevs.gesturevolume.ui.screens.menu.LongPressMenuScreen
import com.newagedevs.gesturevolume.ui.screens.quick_slider.QuickSliderScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The four screens with a preview, on this phone's own settings: the settings as they open, then
 * with Size & shape and Colours opened too, scrolled through. Only sections are opened; nothing is
 * changed. Left as settings_*.png.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreensRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel by lazy {
        val preference = SharedPref(compose.activity)
        MainViewModel(preference, BillingManager(compose.activity, preference))
    }

    @Test
    fun appearance() = film("appearance") {
        HandlerAppearanceScreen(viewModel = viewModel, presetId = null, onNavigateBack = {})
    }

    @Test
    fun deck() = film("deck") { DeckScreen(viewModel = viewModel, onNavigateBack = {}, onNavigate = {}) }

    @Test
    fun slider() = film("slider") { QuickSliderScreen(viewModel = viewModel, onNavigateBack = {}) }

    @Test
    fun menu() = film("menu") { LongPressMenuScreen(viewModel = viewModel, onNavigateBack = {}) }

    /** Appearance and the long-press menu in the dark theme, as they open and while the demo plays. */
    @Test
    fun dark() {
        var which by androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent {
            GestureVolumeTheme(darkTheme = true) {
                if (which == 0) {
                    HandlerAppearanceScreen(viewModel = viewModel, presetId = null, onNavigateBack = {})
                } else {
                    LongPressMenuScreen(viewModel = viewModel, onNavigateBack = {})
                }
            }
        }
        Thread.sleep(1300)
        save("settings_dark_appearance_demo")
        // The demo runs on the test's clock, which sleeping does not move.
        compose.mainClock.advanceTimeBy(15_000)
        save("settings_dark_appearance")
        compose.runOnIdle { which = 1 }
        Thread.sleep(1300)
        save("settings_dark_menu_demo")
        compose.mainClock.advanceTimeBy(15_000)
        save("settings_dark_menu")
    }

    private fun film(name: String, screen: @Composable () -> Unit) {
        compose.setContent { GestureVolumeTheme { screen() } }
        Thread.sleep(1500)
        save("settings_${name}_0")
        // At rest: the demo runs on the test's clock, which sleeping does not move.
        compose.mainClock.advanceTimeBy(15_000)
        save("settings_${name}_rest")
        for (title in listOf("Size & shape", "Colours")) {
            val nodes = compose.onAllNodesWithText(title).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                compose.onAllNodesWithText(title)[0].performScrollTo().performClick()
                Thread.sleep(500)
            }
        }
        compose.onAllNodesWithText("Size & shape").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onAllNodesWithText("Size & shape")[0].performScrollTo()
        }
        Thread.sleep(400)
        save("settings_${name}_1")
        for (i in 2..4) {
            compose.onRoot().performTouchInput {
                swipe(Offset(width * 0.03f, height * 0.9f), Offset(width * 0.03f, height * 0.6f), durationMillis = 400)
            }
            Thread.sleep(500)
            save("settings_${name}_$i")
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
