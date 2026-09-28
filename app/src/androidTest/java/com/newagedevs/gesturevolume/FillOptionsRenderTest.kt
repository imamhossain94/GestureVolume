package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.ui.components.EffortLookRow
import com.newagedevs.gesturevolume.ui.components.FillTileLook
import com.newagedevs.gesturevolume.ui.components.GlimmerLookRow
import com.newagedevs.gesturevolume.ui.components.LevelFeedbackControls
import com.newagedevs.gesturevolume.ui.components.PixelPatternRow
import com.newagedevs.gesturevolume.ui.components.ShaderEffectRow
import com.newagedevs.gesturevolume.ui.components.SurgeLookRow
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.utils.EffortFill
import com.newagedevs.gesturevolume.utils.GlimmerFill
import com.newagedevs.gesturevolume.utils.LevelFeedback
import com.newagedevs.gesturevolume.utils.PixelFill
import com.newagedevs.gesturevolume.utils.ShaderFill
import com.newagedevs.gesturevolume.utils.SliderFill
import com.newagedevs.gesturevolume.utils.SurgeFill
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The rows inside the fills that have choices, and the level feedback's controls. Left as options_*.png. */
@RunWith(AndroidJUnit4::class)
class FillOptionsRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rows() {
        val look = FillTileLook()
        compose.setContent {
            GestureVolumeTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    PixelPatternRow(PixelFill.Style(), look) {}
                    ShaderEffectRow(ShaderFill.Style(), look) {}
                    SurgeLookRow(SurgeFill.Style(), look) {}
                    EffortLookRow(EffortFill.Style(), look) {}
                    GlimmerLookRow(GlimmerFill.Style(), look) {}
                    LevelFeedbackControls(
                        LevelFeedback.Style(), MaterialTheme.colorScheme.primary, showSpeed = true, onChange = {},
                        fillStyle = SliderFill.SOLID, look = look,
                    )
                }
            }
        }
        Thread.sleep(800)
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "options_rows.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
