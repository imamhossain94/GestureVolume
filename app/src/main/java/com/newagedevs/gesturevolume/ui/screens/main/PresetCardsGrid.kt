package com.newagedevs.gesturevolume.ui.screens.main

import android.content.Context
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel

@Composable
fun PresetCardsGrid(
    viewModel: MainViewModel,
    context: Context,
    onNavigateToAppearance: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- Row 1 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Default: medium-width, rounded, semi-transparent
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Default",
                subtitle = "Standard handler",
                icon = Icons.Default.Settings,
                gradientColors = listOf(primary, primary),
                previewWidth = 22.dp,
                previewCorner = 10.dp,
                previewColor = primary,
                previewAlpha = 0.5f,
                onClick = {
                    applyDefaultPreset(viewModel, context)
                    onNavigateToAppearance()
                }
            )
            // Minimal: thin, pill-shaped, very subtle
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Minimal",
                subtitle = "Slim & discrete",
                icon = Icons.Default.LinearScale,
                gradientColors = listOf(primary, primary),
                previewWidth = 8.dp,
                previewCorner = 6.dp,
                previewColor = onSurface,
                previewAlpha = 0.4f,
                onClick = {
                    applyMinimalPreset(viewModel, context)
                    onNavigateToAppearance()
                }
            )
        }

        // --- Row 2 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bold: wide, very visible, fully opaque
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Bold",
                subtitle = "Large & visible",
                icon = Icons.Default.VerticalAlignCenter,
                gradientColors = listOf(primary, primary),
                previewWidth = 36.dp,
                previewCorner = 12.dp,
                previewColor = primary,
                previewAlpha = 0.85f,
                onClick = {
                    applyBoldPreset(viewModel, context)
                    onNavigateToAppearance()
                }
            )
            // Night: dark handler, dark preview strip
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Night",
                subtitle = "Dark & subtle",
                icon = Icons.Default.DarkMode,
                gradientColors = listOf(primary, primary),
                previewWidth = 22.dp,
                previewCorner = 10.dp,
                previewColor = onSurface,
                previewAlpha = 0.7f,
                onClick = {
                    applyNightPreset(viewModel, context)
                    onNavigateToAppearance()
                }
            )
        }

        // --- Row 3 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Transparent: very faint preview — barely visible
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Ghost",
                subtitle = "Nearly invisible",
                icon = Icons.Default.HideSource,
                gradientColors = listOf(primary, primary),
                previewWidth = 16.dp,
                previewCorner = 8.dp,
                previewColor = onSurface,
                previewAlpha = 0.1f,
                onClick = {
                    applyTransparentPreset(viewModel, context)
                    onNavigateToAppearance()
                }
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun applyDefaultPreset(viewModel: MainViewModel, context: Context) {
    val preference = viewModel.preference

    // Position
    preference.setHandlerPosition("Right")
    preference.setHandlerLockPosition(true)

    // Dimensions
    preference.setHandlerWidthDp(30f)
    preference.setHandlerHeightDp(100f)

    // Background
    preference.setHandlerColor("#FFFFFF".toColorInt())
    preference.setHandlerBackgroundAlpha(50)

    // Stroke
    preference.setHandlerStrokeColor("#FFFFFF".toColorInt())
    preference.setHandlerStrokeWidth(1f)
    preference.setHandlerStrokeAlpha(200)

    // Corner Radius
    preference.setAllCornerRadii(15f)

    // Icon
    preference.setHandlerIconRes(R.drawable.ic_vol_increase)
    preference.setHandlerIconSize(18f)
    preference.setHandlerIconColor(Color.WHITE)
    preference.setHandlerShowIcon(true)

    // Behavior
    preference.setHandlerVibrateOnClick(false)

    // Update live service
    viewModel.sendUpdateToService(context)
}

private fun applyMinimalPreset(viewModel: MainViewModel, context: Context) {
    val preference = viewModel.preference

    // Position
    preference.setHandlerPosition("Right")
    preference.setHandlerLockPosition(true)

    // Dimensions
    preference.setHandlerWidthDp(10f)
    preference.setHandlerHeightDp(100f)

    // Background
    preference.setHandlerColor("#FFFFFF".toColorInt())
    preference.setHandlerBackgroundAlpha(50)

    // Stroke
    preference.setHandlerStrokeColor("#FFFFFF".toColorInt())
    preference.setHandlerStrokeWidth(1f)
    preference.setHandlerStrokeAlpha(200)

    // Corner Radius
    preference.setAllCornerRadii(5f)

    // Icon
    preference.setHandlerIconRes(R.drawable.ic_vol_increase)
    preference.setHandlerIconSize(18f)
    preference.setHandlerIconColor(Color.WHITE)
    preference.setHandlerShowIcon(false)

    // Behavior
    preference.setHandlerVibrateOnClick(false)

    // Update live service
    viewModel.sendUpdateToService(context)
}

private fun applyBoldPreset(viewModel: MainViewModel, context: Context) {
    val preference = viewModel.preference

    // Position
    preference.setHandlerPosition("Right")
    preference.setHandlerLockPosition(true)

    // Dimensions
    preference.setHandlerWidthDp(40f)
    preference.setHandlerHeightDp(100f)

    // Background
    preference.setHandlerColor("#FFFFFF".toColorInt())
    preference.setHandlerBackgroundAlpha(50)

    // Stroke
    preference.setHandlerStrokeColor("#FFFFFF".toColorInt())
    preference.setHandlerStrokeWidth(1f)
    preference.setHandlerStrokeAlpha(255)

    // Corner Radius
    preference.setAllCornerRadii(15f)

    // Icon
    preference.setHandlerIconRes(R.drawable.ic_move)
    preference.setHandlerIconSize(32f)
    preference.setHandlerIconColor(Color.WHITE)
    preference.setHandlerShowIcon(true)

    // Behavior
    preference.setHandlerVibrateOnClick(true)

    // Update live service
    viewModel.sendUpdateToService(context)
}

private fun applyNightPreset(viewModel: MainViewModel, context: Context) {
    val preference = viewModel.preference

    // Position
    preference.setHandlerPosition("Right")
    preference.setHandlerLockPosition(true)

    // Dimensions
    preference.setHandlerWidthDp(30f)
    preference.setHandlerHeightDp(100f)

    // Background
    preference.setHandlerColor("#1F2937".toColorInt())
    preference.setHandlerBackgroundAlpha(230)

    // Stroke
    preference.setHandlerStrokeColor("#374151".toColorInt())
    preference.setHandlerStrokeWidth(1f)
    preference.setHandlerStrokeAlpha(200)

    // Corner Radius
    preference.setAllCornerRadii(15f)

    // Icon
    preference.setHandlerIconRes(R.drawable.ic_vol_increase)
    preference.setHandlerIconSize(22f)
    preference.setHandlerIconColor("#9CA3AF".toColorInt())
    preference.setHandlerShowIcon(true)

    // Behavior
    preference.setHandlerVibrateOnClick(true)

    // Update live service
    viewModel.sendUpdateToService(context)
}

private fun applyTransparentPreset(viewModel: MainViewModel, context: Context) {
    val preference = viewModel.preference

    // Position
    preference.setHandlerPosition("Right")
    preference.setHandlerLockPosition(true)

    // Dimensions
    preference.setHandlerWidthDp(20f)
    preference.setHandlerHeightDp(100f)

    // Background
    preference.setHandlerColor("#FFFFFF".toColorInt())
    preference.setHandlerBackgroundAlpha(5)

    // Stroke
    preference.setHandlerStrokeColor("#FFFFFF".toColorInt())
    preference.setHandlerStrokeWidth(0.5f)
    preference.setHandlerStrokeAlpha(5)

    // Corner Radius
    preference.setAllCornerRadii(12f)

    // Icon
    preference.setHandlerIconRes(R.drawable.ic_vol_increase)
    preference.setHandlerIconSize(16f)
    preference.setHandlerIconColor(Color.WHITE)
    preference.setHandlerShowIcon(false)

    // Behavior
    preference.setHandlerVibrateOnClick(false)

    // Update live service
    viewModel.sendUpdateToService(context)
}