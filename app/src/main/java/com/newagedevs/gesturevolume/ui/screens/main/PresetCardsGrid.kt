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

// Data class to hold preset configuration for preview
private data class PresetConfig(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradientColors: List<ComposeColor>,
    val previewWidth: Dp,
    val previewCorner: Dp,
    val previewColor: ComposeColor,
    val previewAlpha: Float
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
            PresetConfig("Default", "Standard handler", Icons.Default.Settings,
                listOf(primary, primary), 22.dp, 10.dp, primary, 0.5f),
            PresetConfig("Minimal", "Slim & discrete", Icons.Default.LinearScale,
                listOf(primary, primary), 8.dp, 6.dp, onSurface, 0.4f),
            PresetConfig("Bold", "Large & visible", Icons.Default.VerticalAlignCenter,
                listOf(primary, primary), 36.dp, 12.dp, primary, 0.85f),
            PresetConfig("Night", "Dark & subtle", Icons.Default.DarkMode,
                listOf(primary, primary), 22.dp, 10.dp, onSurface, 0.7f),
            PresetConfig("Ghost", "Nearly invisible", Icons.Default.HideSource,
                listOf(primary, primary), 16.dp, 8.dp, onSurface, 0.1f)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- Row 1 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets[0].let { preset ->
                PresetCard(
                    modifier = Modifier.weight(1f),
                    title = preset.name,
                    subtitle = preset.subtitle,
                    icon = preset.icon,
                    gradientColors = preset.gradientColors,
                    previewWidth = preset.previewWidth,
                    previewCorner = preset.previewCorner,
                    previewColor = preset.previewColor,
                    previewAlpha = preset.previewAlpha,
                    onClick = { onNavigateToAppearance(preset.name) }
                )
            }
            presets[1].let { preset ->
                PresetCard(
                    modifier = Modifier.weight(1f),
                    title = preset.name,
                    subtitle = preset.subtitle,
                    icon = preset.icon,
                    gradientColors = preset.gradientColors,
                    previewWidth = preset.previewWidth,
                    previewCorner = preset.previewCorner,
                    previewColor = preset.previewColor,
                    previewAlpha = preset.previewAlpha,
                    onClick = { onNavigateToAppearance(preset.name) }
                )
            }
        }

        // --- Row 2 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets[2].let { preset ->
                PresetCard(
                    modifier = Modifier.weight(1f),
                    title = preset.name,
                    subtitle = preset.subtitle,
                    icon = preset.icon,
                    gradientColors = preset.gradientColors,
                    previewWidth = preset.previewWidth,
                    previewCorner = preset.previewCorner,
                    previewColor = preset.previewColor,
                    previewAlpha = preset.previewAlpha,
                    onClick = { onNavigateToAppearance(preset.name) }
                )
            }
            presets[3].let { preset ->
                PresetCard(
                    modifier = Modifier.weight(1f),
                    title = preset.name,
                    subtitle = preset.subtitle,
                    icon = preset.icon,
                    gradientColors = preset.gradientColors,
                    previewWidth = preset.previewWidth,
                    previewCorner = preset.previewCorner,
                    previewColor = preset.previewColor,
                    previewAlpha = preset.previewAlpha,
                    onClick = { onNavigateToAppearance(preset.name) }
                )
            }
        }

        // --- Row 3 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets[4].let { preset ->
                PresetCard(
                    modifier = Modifier.weight(1f),
                    title = preset.name,
                    subtitle = preset.subtitle,
                    icon = preset.icon,
                    gradientColors = preset.gradientColors,
                    previewWidth = preset.previewWidth,
                    previewCorner = preset.previewCorner,
                    previewColor = preset.previewColor,
                    previewAlpha = preset.previewAlpha,
                    onClick = { onNavigateToAppearance(preset.name) }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}