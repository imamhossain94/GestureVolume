package com.newagedevs.gesturevolume.ui.screens.main

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.HandlerShape
import androidx.compose.ui.res.stringResource

// Data class to hold preset configuration for preview
private data class PresetConfig(
    /**
     * Stable, unlocalised key for [com.newagedevs.gesturevolume.utils.HandlerPresets.byId].
     *
     * The click used to navigate with `getString(nameRes)`, the *translated* name, which was then
     * matched against English literals — so tapping a preset did nothing in any of the 14
     * translated locales.
     */
    val id: String,
    val nameRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val gradientColors: List<ComposeColor>,
    val previewWidth: Dp,
    val previewCorner: Dp,
    val previewColor: ComposeColor,
    val previewAlpha: Float,
    /** See [PresetCard]'s parameter of the same name. Null keeps the swatch symmetric. */
    val outerCorner: Dp? = null,
    /** See [PresetCard]. Rounded unless the preset is cut to something else. */
    val shape: String = HandlerShape.ROUNDED,
    val flare: Float = HandlerShape.DEFAULT_FLARE
)

@Composable
fun PresetCardsGrid(
    viewModel: MainViewModel,
    context: Context,
    onNavigateToAppearance: (String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val presets = remember(primary, onSurface) {
        listOf(
            // First: the default.
            // The swatch is drawn from the shape's own geometry, so the corner radius here is
            // carried only to satisfy the constructor — a tab has no corners to round.
            PresetConfig("Dock", R.string.preset_dock_title, R.string.preset_dock_subtitle, Icons.Default.Bookmark,
                listOf(primary, primary), 14.dp, 0.dp, ComposeColor.Black, 0.9f,
                shape = HandlerShape.TAB, flare = 0.29f),
            // Black rather than a theme colour, and narrow rather than wide: the Edge bar is a slim
            // opaque pill, and a swatch that showed the old indigo slab would be advertising a
            // preset that no longer exists.
            // 12dp wide, rounded on the inside and softened — not squared — on the edge side.
            // The preset itself uses 1dp there, which at swatch scale is a hard corner and reads
            // as a mistake rather than as a bar meeting the edge of a screen; 4dp is the same
            // idea drawn at a size where it is legible.
            PresetConfig("Edge", R.string.preset_edge_title, R.string.preset_edge_subtitle, Icons.Default.Settings,
                listOf(primary, primary), 12.dp, 10.dp, ComposeColor.Black, 0.9f, outerCorner = 4.dp),
            PresetConfig("Minimal", R.string.preset_minimal_title, R.string.preset_minimal_subtitle, Icons.Default.LinearScale,
                listOf(primary, primary), 8.dp, 6.dp, onSurface, 0.4f),
            // A circle, because Bold is one now. Equal width and a radius of half it is what the
            // swatch has to say; PresetCard draws the height, so the two are matched there.
            PresetConfig("Bold", R.string.preset_bold_title, R.string.preset_bold_subtitle, Icons.Default.Adjust,
                listOf(primary, primary), 34.dp, 17.dp, onSurface, 0.55f),
            PresetConfig("Night", R.string.preset_night_title, R.string.preset_night_subtitle, Icons.Default.DarkMode,
                listOf(primary, primary), 22.dp, 10.dp, onSurface, 0.7f),
            PresetConfig("Ghost", R.string.preset_ghost_title, R.string.preset_ghost_subtitle, Icons.Default.HideSource,
                listOf(primary, primary), 16.dp, 8.dp, onSurface, 0.1f),
        )
    }

    // Chunked rather than unrolled. The rows used to be written out by hand against fixed
    // indices — `presets[6]`, `presets[4]` — which is a layout that silently reorders itself when
    // a preset is inserted and crashes outright when one is removed. Two removals is exactly what
    // happened, so the grid now follows whatever the list holds.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { preset ->
                    PresetCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(preset.nameRes),
                        subtitle = stringResource(preset.subtitleRes),
                        icon = preset.icon,
                        gradientColors = preset.gradientColors,
                        previewWidth = preset.previewWidth,
                        previewCorner = preset.previewCorner,
                        previewColor = preset.previewColor,
                        previewAlpha = preset.previewAlpha,
                        previewOuterCorner = preset.outerCorner,
                        previewShape = preset.shape,
                        previewFlare = preset.flare,
                        onClick = { onNavigateToAppearance(preset.id) }
                    )
                }
                // An odd count leaves the last card half-width rather than stretched across the
                // row, so every card in the grid is the same size.
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
