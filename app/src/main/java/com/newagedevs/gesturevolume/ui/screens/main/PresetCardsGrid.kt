package com.newagedevs.gesturevolume.ui.screens.main

import android.content.Context
import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Row 1 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Default",
                subtitle = "Standard settings",
                icon = Icons.Default.Settings,
                gradientColors = listOf(
                    ComposeColor(0xFF3B82F6),
                    ComposeColor(0xFF2563EB)
                ),
                onClick = {
                    applyDefaultPreset(viewModel)
                    onNavigateToAppearance()
                }
            )
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Minimal",
                subtitle = "Slim & discrete",
                icon = Icons.Default.Face,
                gradientColors = listOf(
                    ComposeColor(0xFF8B5CF6),
                    ComposeColor(0xFF7C3AED)
                ),
                onClick = {
                    applyMinimalPreset(viewModel)
                    onNavigateToAppearance()
                }
            )
        }

        // --- Row 2 ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Bold",
                subtitle = "Large & visible",
                icon = Icons.Default.Star,
                gradientColors = listOf(
                    ComposeColor(0xFFEC4899),
                    ComposeColor(0xFFDB2777)
                ),
                onClick = {
                    applyBoldPreset(viewModel)
                    onNavigateToAppearance()
                }
            )
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Night",
                subtitle = "Dark theme",
                icon = Icons.Default.DarkMode,
                gradientColors = listOf(
                    ComposeColor(0xFF1F2937),
                    ComposeColor(0xFF111827)
                ),
                onClick = {
                    applyNightPreset(viewModel)
                    onNavigateToAppearance()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetCard(
                modifier = Modifier.weight(1f),
                title = "Transparent",
                subtitle = "Slim & invisible",
                icon = Icons.Default.HideSource,
                gradientColors = listOf(
                    ComposeColor(0xFF06B6D4),
                    ComposeColor(0xFF3B82F6)
                ),
                onClick = {
                    applyTransparentPreset(viewModel)
                    onNavigateToAppearance()
                }
            )

            Spacer(modifier = Modifier.weight(1f))

        }
    }
}

private fun applyDefaultPreset(viewModel: MainViewModel) {
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
}

private fun applyMinimalPreset(viewModel: MainViewModel) {
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
}

private fun applyBoldPreset(viewModel: MainViewModel) {
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
}

private fun applyNightPreset(viewModel: MainViewModel) {
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
}

private fun applyTransparentPreset(viewModel: MainViewModel) {
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
}
