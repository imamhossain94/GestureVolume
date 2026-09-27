package com.newagedevs.gesturevolume.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The page's background with a cool wash at the top fading into it: a pale periwinkle on light, a
 * faint deep blue on dark — the blues of the phone's wallpaper in the previews and the app's indigo,
 * so the top of the page belongs with them. The walkthrough's first, and the screens that show a
 * preview after it, so arriving on one from the other is the same room.
 */
@Composable
fun Modifier.screenWash(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val wash = if (background.luminance() > 0.5f) LIGHT_WASH else DARK_WASH
    return background(Brush.verticalGradient(listOf(wash, background)))
}

/**
 * The same wash behind a screen's header only — its top bar and its preview — rather than down the
 * whole page: deepest at the top, lighter towards the preview's words, and ending under them in a
 * soft curve, so the settings under it sit on the plain page.
 */
@Composable
fun Modifier.headerWash(): Modifier {
    val background = MaterialTheme.colorScheme.background
    val wash = if (background.luminance() > 0.5f) LIGHT_WASH else DARK_WASH
    return clip(HeaderShape).background(Brush.verticalGradient(listOf(wash, lerp(wash, background, 0.45f))))
}

private val HeaderShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

private val LIGHT_WASH = Color(0xFFE2E7FF)
private val DARK_WASH = Color(0xFF1F2847)
