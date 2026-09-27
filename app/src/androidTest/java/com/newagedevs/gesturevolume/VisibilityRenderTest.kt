package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.visibility.VisibilityScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Visibility screen on this phone's own settings, upright and scrolled, and on its side: the
 * test's own activity turned, nothing else. Nothing is tapped. Left as visibility_*.png.
 */
@RunWith(AndroidJUnit4::class)
class VisibilityRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel by lazy {
        val preference = SharedPref(compose.activity)
        MainViewModel(preference, BillingManager(compose.activity, preference))
    }

    @Test
    fun upright() {
        compose.setContent { GestureVolumeTheme { VisibilityScreen(viewModel = viewModel, onNavigateBack = {}) } }
        Thread.sleep(1200)
        save("visibility_0")
        for (i in 1..2) {
            compose.onRoot().performTouchInput {
                swipe(Offset(width * 0.03f, height * 0.85f), Offset(width * 0.03f, height * 0.3f), durationMillis = 400)
            }
            Thread.sleep(600)
            save("visibility_$i")
        }
    }

    @Test
    fun landscape() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        Thread.sleep(1500)
        compose.setContent { GestureVolumeTheme { VisibilityScreen(viewModel = viewModel, onNavigateBack = {}) } }
        Thread.sleep(1200)
        save("visibility_land")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
