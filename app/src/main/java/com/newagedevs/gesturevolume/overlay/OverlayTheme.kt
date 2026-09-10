package com.newagedevs.gesturevolume.overlay

import androidx.compose.runtime.Composable
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme

/**
 * The theme every overlay surface uses.
 *
 * Always the dark palette, whatever the app's own theme is set to. The overlays are drawn over
 * other apps — on wallpaper, on video, on a white document — and a light card there is a glare;
 * the fixed dark surface the long-press menu has always used is the one look that reads on all
 * of them. The app's settings screens keep following the theme setting; this is the Deck's skin.
 */
@Composable
fun OverlayTheme(content: @Composable () -> Unit) {
    GestureVolumeTheme(darkTheme = true, content = content)
}
