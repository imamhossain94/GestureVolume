package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.walkthrough.WalkthroughScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The walkthrough's first page, whole, light and dark. Nothing is tapped. Left as walkscreen_*.png. */
@RunWith(AndroidJUnit4::class)
class WalkthroughScreenRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel by lazy {
        val preference = SharedPref(compose.activity)
        MainViewModel(preference, BillingManager(compose.activity, preference))
    }

    @Test
    fun firstPage() {
        var dark by mutableStateOf(false)
        compose.setContent { GestureVolumeTheme(darkTheme = dark) { WalkthroughScreen(viewModel = viewModel, onComplete = {}) } }
        Thread.sleep(1200)
        save("walkscreen_light")
        compose.runOnIdle { dark = true }
        Thread.sleep(800)
        save("walkscreen_dark")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
