package com.newagedevs.gesturevolume.overlay

import androidx.compose.runtime.Composable
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme

/**
 * The theme every overlay surface uses.
 *
 * Dark, whatever the app's own theme is set to. The overlays are drawn over other apps — on
 * wallpaper, on video, on a white document — and a light card there is a glare; the fixed dark
 * surface the long-press menu has always used is the one look that reads on all of them. The
 * app's settings screens keep following the theme setting; this is the overlays' skin.
 *
 * @param light the deliberate exception: the pale materials in
 *   [com.newagedevs.gesturevolume.utils.PanelTheme]. They are pale on purpose, and everything a
 *   panel does not colour by hand — a text field's container, a switch's unchecked track, a
 *   checkbox when it is off, a ripple, any label whose composable forgot to pass a colour — comes
 *   from this scheme. Leaving it dark under a pale pane leaves white ink on white glass in exactly
 *   the places nobody thinks to check.
 */
@Composable
fun OverlayTheme(light: Boolean = false, content: @Composable () -> Unit) {
    GestureVolumeTheme(darkTheme = !light, content = content)
}
