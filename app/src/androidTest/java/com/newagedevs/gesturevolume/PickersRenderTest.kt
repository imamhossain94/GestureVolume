package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.ui.components.PanelAnimationSelector
import com.newagedevs.gesturevolume.ui.components.PanelThemeSelector
import com.newagedevs.gesturevolume.ui.components.SliderFillSelector
import com.newagedevs.gesturevolume.ui.screens.visibility.KeyboardMotionSelector
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.utils.QuickSliderIcons
import com.newagedevs.gesturevolume.utils.SliderFill
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The rows the animations are picked from, and every fill with the number and the icon in the ink
 * the panel picks for them. Left in the app's cache as pick_*.png.
 */
@RunWith(AndroidJUnit4::class)
class PickersRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rows() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GestureVolumeTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    PanelThemeSelector(theme = "frosted", onThemeChange = {})
                    PanelThemeSelector(theme = "amoled", onThemeChange = {})
                    PanelAnimationSelector(animation = "grow", onAnimationChange = {})
                    SliderFillSelector(style = SliderFill.LIQUID, onStyleChange = {})
                    KeyboardMotionSelector(motion = "spring", onMotionChange = {})
                }
            }
        }
        for (i in 0 until 8) {
            compose.mainClock.advanceTimeBy(450)
            save("pick_rows_$i")
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun fillsHigh() = fills("high", 0.95f)

    @Test
    fun fillsLow() = fills("low", 0.45f)

    @Test
    fun fillsLight() = fills("light", 0.95f, track = 0xFFE9E6F2.toInt(), fill = 0xFF6750A4.toInt())

    @OptIn(ExperimentalLayoutApi::class)
    private fun fills(name: String, level: Float, track: Int = QuickSliderStore.DEFAULT_TRACK_COLOR, fill: Int = QuickSliderStore.DEFAULT_FILL_COLOR) {
        val icon = QuickSliderIcons.resolve(compose.activity, QuickSliderStore.ICON_AUTO, QuickSliderStore.TARGET_MEDIA)
        compose.setContent {
            GestureVolumeTheme {
                FlowRow(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color(0xFF9DB2FA))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SliderFill.ALL.forEach { id -> Panel(id, level, icon, track, fill) }
                }
            }
        }
        compose.waitForIdle()
        save("pick_fills_$name")
    }

    @Composable
    private fun Panel(id: String, level: Float, icon: Int, track: Int, fill: Int) {
        AndroidView(
            factory = { context ->
                QuickSliderView(context).apply {
                    setColors(track, fill)
                    setCornerRadiusDp(14f)
                    setShowValue(true)
                    setIcon(icon)
                    setFillStyle(id)
                    setExpansion(1f)
                    setCommitted()
                    setValue(level)
                }
            },
            modifier = Modifier.size(width = 42.dp, height = 140.dp),
        )
    }

    private fun save(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
