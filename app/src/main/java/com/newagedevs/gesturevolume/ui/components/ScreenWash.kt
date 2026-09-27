package com.newagedevs.gesturevolume.ui.components

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

private val LIGHT_WASH = Color(0xFFE2E7FF)
private val DARK_WASH = Color(0xFF1F2847)
