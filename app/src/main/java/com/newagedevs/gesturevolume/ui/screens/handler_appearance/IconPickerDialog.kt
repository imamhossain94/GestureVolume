package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R

@Composable
fun IconPickerDialog(
    selectedIconRes: Int,
    onIconSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val iconOptions = remember {
        listOf(
            R.drawable.ic_vol_increase to R.string.icon_increase,
            R.drawable.ic_vol_decrease to R.string.icon_decrease,
            R.drawable.ic_vol_plus to R.string.icon_boost,
            R.drawable.ic_vol_minus to R.string.icon_reduce,
            R.drawable.ic_bug to R.string.icon_bug,
            R.drawable.ic_check to R.string.icon_check,
            R.drawable.ic_color_palette to R.string.icon_palette,
            R.drawable.ic_crown_2 to R.string.icon_crown,
            R.drawable.ic_edit to R.string.icon_edit,
            R.drawable.ic_feedback to R.string.icon_feedback,
            R.drawable.ic_github to R.string.icon_github,
            R.drawable.ic_lock to R.string.icon_lock,
            R.drawable.ic_move to R.string.icon_move,
            R.drawable.ic_music_ui to R.string.icon_music,
            R.drawable.ic_nothing to R.string.icon_none,
            R.drawable.ic_plugin to R.string.icon_plugin,
            R.drawable.ic_power to R.string.icon_power,
            R.drawable.ic_share to R.string.icon_share,
            R.drawable.ic_star to R.string.icon_star,
            R.drawable.ic_visibility_hide to R.string.icon_hide,
            R.drawable.ic_x_close to R.string.icon_close,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.select_icon),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                iconOptions.chunked(3).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowIcons.forEach { (iconRes, iconNameRes) ->
                            IconGridItem(
                                iconRes = iconRes,
                                iconName = stringResource(iconNameRes),
                                isSelected = iconRes == selectedIconRes,
                                onClick = { onIconSelected(iconRes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowIcons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.close))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun IconGridItem(
    iconRes: Int,
    iconName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = iconName,
                    modifier = Modifier.size(32.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = iconName,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}