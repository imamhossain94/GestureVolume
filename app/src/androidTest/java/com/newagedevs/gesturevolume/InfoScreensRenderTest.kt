package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.about.AboutScreen
import com.newagedevs.gesturevolume.ui.screens.faq.FaqScreen
import com.newagedevs.gesturevolume.ui.screens.feedback.FeedbackScreen
import com.newagedevs.gesturevolume.ui.screens.permission.PermissionsScreen
import com.newagedevs.gesturevolume.ui.screens.troubleshoot.TroubleshootScreen
import com.newagedevs.gesturevolume.ui.screens.whats_new.WhatsNewScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FAQ, Feedback, Troubleshooting, Permissions, What's new and About, as they open and scrolled
 * through. Nothing is changed; the What's new screen is not marked as seen. Left as info_*.png.
 */
@RunWith(AndroidJUnit4::class)
class InfoScreensRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel by lazy {
        val preference = SharedPref(compose.activity)
        MainViewModel(preference, BillingManager(compose.activity, preference))
    }

    @Test
    fun faq() = film("faq") {
        FaqScreen(onNavigateBack = {}, onNavigateToTroubleshoot = {})
    }

    @Test
    fun feedback() = film("feedback") { FeedbackScreen(onNavigateBack = {}) }

    @Test
    fun troubleshoot() = film("troubleshoot") { TroubleshootScreen(onNavigateBack = {}) }

    @Test
    fun permissions() = film("permissions") { PermissionsScreen(viewModel = viewModel, onNavigateBack = {}) }

    @Test
    fun whatsNew() = film("whatsnew") { WhatsNewScreen(onNavigateBack = {}, onSeen = {}) }

    @Test
    fun about() = film("about") {
        AboutScreen(isProActivated = false, onOpenPrivacyChoices = {}, onNavigateBack = {})
    }

    private fun film(name: String, screen: @Composable () -> Unit) {
        compose.setContent { GestureVolumeTheme { screen() } }
        Thread.sleep(1200)
        if (name == "faq") {
            compose.onNodeWithText(compose.activity.getString(R.string.faq_q_move_bar)).performClick()
            Thread.sleep(500)
        }
        save("info_${name}_0")
        for (i in 1..2) {
            compose.onRoot().performTouchInput {
                swipe(Offset(width * 0.03f, height * 0.85f), Offset(width * 0.03f, height * 0.3f), durationMillis = 400)
            }
            Thread.sleep(500)
            save("info_${name}_$i")
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
