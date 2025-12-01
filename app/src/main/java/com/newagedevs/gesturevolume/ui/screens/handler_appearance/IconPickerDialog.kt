package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R

@Composable
fun IconPickerDialog(
    selectedIconRes: Int,
    onIconSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // EXPANDED: More icon options to choose from
    val iconOptions = remember {
        listOf(
            R.drawable.ic_vol_increase to "Increase",
            R.drawable.ic_vol_decrease to "Decrease",
            R.drawable.ic_vol_plus to "Boost",
            R.drawable.ic_vol_minus to "Reduce",
            R.drawable.ic_bug to "Bug",
            R.drawable.ic_check to "Check",
            R.drawable.ic_color_palette to "Palette",
            R.drawable.ic_crown_2 to "Crown",
            R.drawable.ic_edit to "Edit",
            R.drawable.ic_feedback to "Feedback",
            R.drawable.ic_github to "GitHub",
            R.drawable.ic_lock to "Lock",
            R.drawable.ic_move to "Move",
            R.drawable.ic_music_ui to "Music",
            R.drawable.ic_nothing to "None",
            R.drawable.ic_plugin to "Plugin",
            R.drawable.ic_power to "Power",
            R.drawable.ic_share to "Share",
            R.drawable.ic_star to "Star",
            R.drawable.ic_visibility_hide to "Hide",
            R.drawable.ic_x_close to "Close",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Select Icon",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${iconOptions.size} icons available",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                iconOptions.chunked(3).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowIcons.forEach { (iconRes, iconName) ->
                            IconGridItem(
                                iconRes = iconRes,
                                iconName = iconName,
                                isSelected = iconRes == selectedIconRes,
                                onClick = { onIconSelected(iconRes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining space if row is not complete
                        repeat(3 - rowIcons.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(10.dp)
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
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color.Transparent
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) {
                        Modifier.background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
                            )
                        )
                    } else Modifier
                )
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
                    tint = if (isSelected)
                        Color.White
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = iconName.take(8),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected)
                        Color.White
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}


@Composable
fun IconPickerDialogList(
    selectedIconRes: Int,
    onIconSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val iconOptions = remember {
        listOf(
            R.drawable.ic_vol_increase to "Increase",
            R.drawable.ic_vol_decrease to "Decrease",
            R.drawable.ic_vol_plus to "Boost",
            R.drawable.ic_vol_minus to "Reduce",
            R.drawable.ic_bug to "Bug",
            R.drawable.ic_check to "Check",
            R.drawable.ic_color_palette to "Palette",
            R.drawable.ic_crown_2 to "Crown",
            R.drawable.ic_edit to "Edit",
            R.drawable.ic_feedback to "Feedback",
            R.drawable.ic_github to "GitHub",
            R.drawable.ic_lock to "Lock",
            R.drawable.ic_move to "Move",
            R.drawable.ic_music_ui to "Music",
            R.drawable.ic_nothing to "None",
            R.drawable.ic_plugin to "Plugin",
            R.drawable.ic_power to "Power",
            R.drawable.ic_share to "Share",
            R.drawable.ic_star to "Star",
            R.drawable.ic_visibility_hide to "Hide",
            R.drawable.ic_x_close to "Close",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Icon",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                iconOptions.forEach { (iconRes, iconName) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIconSelected(iconRes) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (iconRes == selectedIconRes)
                                Color.Transparent
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (iconRes == selectedIconRes) {
                                        Modifier.background(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFF3B82F6),
                                                    Color(0xFF2563EB)
                                                )
                                            )
                                        )
                                    } else Modifier
                                )
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (iconRes == selectedIconRes)
                                                Color.White.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.primaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = iconName,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (iconRes == selectedIconRes)
                                            Color.White
                                        else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = iconName,
                                    fontWeight = if (iconRes == selectedIconRes)
                                        FontWeight.Bold
                                    else FontWeight.Normal,
                                    color = if (iconRes == selectedIconRes)
                                        Color.White
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(10.dp)
    )
}