package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
 * The four screens with a preview, whole, while their "How it works" demo plays: a frame every
 * 300ms from arrival, and one once it has finished. Left in the app's cache as screen_*.png.
 *
 * The screens are the real ones, on a view model built from the app's own preferences, so what is
 * drawn is what the settings on this phone make them.
 */
@RunWith(AndroidJUnit4::class)
class ScreensDemoRenderTest {

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
    fun quickSlider() = film("slider") { QuickSliderScreen(viewModel = viewModel, onNavigateBack = {}) }

    @Test
    fun deck() = film("deck") { DeckScreen(viewModel = viewModel, onNavigateBack = {}, onNavigate = {}) }

    @Test
    fun menu() = film("menu") { LongPressMenuScreen(viewModel = viewModel, onNavigateBack = {}) }

    private fun film(name: String, screen: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { GestureVolumeTheme { screen() } }
        for (i in 0 until FRAMES) {
            compose.mainClock.advanceTimeBy(STEP_MS)
            save(name, i)
        }
        // Well past both passes: the resting preview. With the clock running again, so the capture
        // is not left waiting on a frame nobody is advancing to.
        compose.mainClock.advanceTimeBy(8_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        save(name, FRAMES)
    }

    private fun save(name: String, i: Int) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "screen_${name}_$i.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val FRAMES = 14
        const val STEP_MS = 300L
    }
}
