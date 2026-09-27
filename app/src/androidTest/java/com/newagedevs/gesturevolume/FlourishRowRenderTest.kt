package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.quick_slider.QuickSliderScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.LevelFeedback
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Quick slider screen scrolled to the flourish at the top: its switch, and the row of tiles
 * under it, each looping its flourish on the fill as it is set. Then Ripple picked from the row, one
 * near its start: the row is lazy, and a tile further along may not be there yet to be tapped.
 * The level feedback the test app had is put back afterwards. Left as flourish_row_*.png.
 */
@RunWith(AndroidJUnit4::class)
class FlourishRowRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val preference by lazy { SharedPref(compose.activity) }
    private val viewModel by lazy { MainViewModel(preference, BillingManager(compose.activity, preference)) }
    private var before: LevelFeedback.Style? = null

    @After
    fun restore() {
        before?.let { preference.slider.setLevelFeedback(it) }
    }

    @Test
    fun row() {
        before = preference.slider.getLevelFeedback()
        preference.slider.setLevelFeedback(before!!.copy(full = true))
        compose.setContent { GestureVolumeTheme { QuickSliderScreen(viewModel = viewModel, onNavigateBack = {}) } }
        Thread.sleep(1200)
        compose.onAllNodesWithText("Animation")[0].performScrollTo().performClick()
        Thread.sleep(600)
        // To the heading, then a swipe on, so the row under it is in view too.
        compose.onAllNodesWithText("At the top")[0].performScrollTo()
        Thread.sleep(400)
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.04f, height * 0.9f), Offset(width * 0.04f, height * 0.72f), durationMillis = 400)
        }
        Thread.sleep(400)
        listOf(0L, 700L, 1500L).forEachIndexed { i, wait ->
            Thread.sleep(wait)
            save("flourish_row_$i")
        }
        compose.onAllNodesWithText("Ripple")[0].performScrollTo().performClick()
        Thread.sleep(800)
        save("flourish_row_picked")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
