package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.quick_slider.QuickSliderScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.SliderFill
import com.newagedevs.gesturevolume.utils.SurgeFill
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Quick slider screen with the Surge fill chosen: the preview running it, and its settings — the
 * row of looks and the sliders — opened and scrolled to. The fill the test app had is put back
 * afterwards. Left as surge_screen_*.png.
 */
@RunWith(AndroidJUnit4::class)
class SurgeScreenRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val preference by lazy { SharedPref(compose.activity) }
    private val viewModel by lazy { MainViewModel(preference, BillingManager(compose.activity, preference)) }
    private var fillBefore: String? = null

    @After
    fun restore() {
        fillBefore?.let { preference.slider.setFillStyle(it) }
    }

    @Test
    fun settings() {
        assumeTrue(Build.VERSION.SDK_INT >= SurgeFill.MIN_SDK)
        fillBefore = preference.slider.getFillStyle()
        preference.slider.setFillStyle(SliderFill.SURGE)
        compose.setContent { GestureVolumeTheme { QuickSliderScreen(viewModel = viewModel, onNavigateBack = {}) } }
        Thread.sleep(1500)
        save("surge_screen_0")
        compose.onAllNodesWithText("Animation")[0].performScrollTo().performClick()
        Thread.sleep(600)
        listOf("Surge", "Look", "Edge", "Above the level").forEachIndexed { i, text ->
            compose.onAllNodesWithText(text)[0].performScrollTo()
            Thread.sleep(700)
            save("surge_screen_${i + 1}")
        }
        // Another look, picked from its row, to see the preview take it. One in view: the row is
        // lazy, and a tile scrolled off its end is not there to be tapped.
        compose.onAllNodesWithText("Honeycomb")[0].performScrollTo().performClick()
        Thread.sleep(1200)
        save("surge_screen_honeycomb")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
