package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
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
import com.newagedevs.gesturevolume.ui.screens.main.MainScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The home screen on this phone's own settings, light and dark, from the top and scrolled through.
 * Left in the app's cache as home_*.png.
 */
@RunWith(AndroidJUnit4::class)
class HomeRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel by lazy {
        val preference = SharedPref(compose.activity)
        MainViewModel(preference, BillingManager(compose.activity, preference))
    }

    @Test
    fun light() = film("home_light", dark = false)

    @Test
    fun dark() = film("home_dark", dark = true)

    /** The advanced rows opened from their folded state. */
    @Test
    fun opened() {
        compose.setContent { GestureVolumeTheme { screen() } }
        Thread.sleep(1000)
        compose.onAllNodesWithText("Show advanced features")[0].performScrollTo().performClick()
        Thread.sleep(900)
        compose.onAllNodesWithText("Visibility")[0].performScrollTo()
        Thread.sleep(500)
        save("home_opened")
    }

    /** On its side: turned first, as the other landscape tests do, since turning recreates the activity. */
    @Test
    fun wide() {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        Thread.sleep(1500)
        compose.setContent { GestureVolumeTheme { screen() } }
        Thread.sleep(1200)
        save("home_wide")
    }

    @androidx.compose.runtime.Composable
    private fun screen() = MainScreen(
        viewModel = viewModel,
        onNavigateToAppearance = {}, onNavigateToActions = {}, onNavigateToPermissions = {},
        onNavigateToDeck = {}, onNavigateToQuickPanel = {}, onNavigateToLongPressMenu = {},
        onNavigateToFaq = {}, onNavigateToUpgrade = {}, onNavigateToVisibility = {},
    )

    private fun film(name: String, dark: Boolean) {
        compose.setContent {
            GestureVolumeTheme(darkTheme = dark) {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToAppearance = {}, onNavigateToActions = {}, onNavigateToPermissions = {},
                    onNavigateToDeck = {}, onNavigateToQuickPanel = {}, onNavigateToLongPressMenu = {},
                    onNavigateToFaq = {}, onNavigateToUpgrade = {}, onNavigateToVisibility = {},
                )
            }
        }
        Thread.sleep(1200)
        save("${name}_0")
        for (i in 1..3) {
            compose.onRoot().performTouchInput {
                swipe(Offset(width * 0.5f, height * 0.85f), Offset(width * 0.5f, height * 0.35f), durationMillis = 400)
            }
            Thread.sleep(600)
            save("${name}_$i")
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
