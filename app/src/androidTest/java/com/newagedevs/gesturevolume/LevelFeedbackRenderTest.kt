package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.utils.LevelFeedback
import com.newagedevs.gesturevolume.utils.SliderFill
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Eight fills at five levels — off, low, a third, three quarters, full — each column reached from
 * just below it, so the flash of a fifth passed and the flourish at the top are both on screen.
 * Left in the app's cache as feedback_*.png: just after the moves, and once they have settled.
 */
@RunWith(AndroidJUnit4::class)
class LevelFeedbackRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fills = listOf(
        SliderFill.SOLID, SliderFill.LIQUID, SliderFill.SUNRISE, SliderFill.NEBULA,
        SliderFill.PIXELS, SliderFill.GLIMMER, SliderFill.STRIPES, SliderFill.AURORA,
    )
    private val levels = floatArrayOf(0f, 0.06f, 0.35f, 0.75f, 1f)
    private val from = floatArrayOf(0f, 0.06f, 0.35f, 0.55f, 0.9f)

    @Test
    fun levels() {
        val views = ArrayList<Pair<QuickSliderView, Int>>()
        compose.setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF9DB2FA)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (row in 0 until 4) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (f in 0 until 2) {
                            val fill = fills[row * 2 + f]
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                for (i in levels.indices) {
                                    AndroidView(
                                        factory = { ctx ->
                                            QuickSliderView(ctx).apply {
                                                setColors(0xFF1C1C20.toInt(), 0xFFFFFFFF.toInt())
                                                setShowValue(true)
                                                setIcon(null)
                                                setCornerRadiusDp(10f)
                                                setFillStyle(fill)
                                                setExpansion(1f)
                                                setCommitted()
                                                setValue(from[i])
                                                views.add(this to i)
                                            }
                                        },
                                        modifier = Modifier.size(width = 30.dp, height = 170.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Thread.sleep(700)
        compose.runOnUiThread { views.forEach { (view, i) -> view.setValue(levels[i]) } }
        Thread.sleep(220)
        save("feedback_moved")
        Thread.sleep(1800)
        save("feedback_settled")
    }

    /**
     * The seven flourishes at the top, one a column, on three fills a row — the plain fill in white,
     * the Surge and the Aurora — each reached from 90%. Frames through the arrival and into the
     * loop that follows, as feedback_max_*.png.
     */
    @Test
    fun flourishesAtTheTop() {
        val maxFills = listOf(SliderFill.SOLID, SliderFill.SURGE, SliderFill.AURORA)
        val views = ArrayList<QuickSliderView>()
        compose.setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF9DB2FA)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (fill in maxFills) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (max in LevelFeedback.MAX_STYLES) {
                            AndroidView(
                                factory = { ctx ->
                                    QuickSliderView(ctx).apply {
                                        setColors(0xFF1C1C20.toInt(), 0xFFFFFFFF.toInt())
                                        setShowValue(true)
                                        setIcon(null)
                                        setCornerRadiusDp(14f)
                                        setLevelFeedback(LevelFeedback.Style(max = max))
                                        setFillStyle(fill)
                                        setExpansion(1f)
                                        setCommitted()
                                        setValue(0.9f)
                                        views.add(this)
                                    }
                                },
                                modifier = Modifier.size(width = 36.dp, height = 190.dp),
                            )
                        }
                    }
                }
            }
        }
        Thread.sleep(700)
        compose.runOnUiThread { views.forEach { it.setValue(1f) } }
        var last = 0L
        listOf(90L, 300L, 600L, 1000L, 1600L, 2600L, 3400L).forEachIndexed { i, at ->
            Thread.sleep(at - last)
            last = at
            save("feedback_max_$i")
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
