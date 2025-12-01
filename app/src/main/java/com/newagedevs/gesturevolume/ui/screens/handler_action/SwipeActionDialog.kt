package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R

@Composable
fun SwipeActionDialog(
    title: String,
    currentAction: String,
    isSwipeUp: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val actionOptions = remember(isSwipeUp) {
        if (isSwipeUp) {
            listOf(
                R.drawable.ic_nothing to "None",
                R.drawable.ic_vol_increase to "Increase volume and show UI",
                R.drawable.ic_vol_plus to "Increase volume"
            )
        } else {
            listOf(
                R.drawable.ic_nothing to "None",
                R.drawable.ic_vol_decrease to "Decrease volume and show UI",
                R.drawable.ic_vol_minus to "Decrease volume"
            )
        }
    }

    val gradientColors = remember(isSwipeUp) {
        if (isSwipeUp) {
            listOf(Color(0xFF10B981), Color(0xFF059669))
        } else {
            listOf(Color(0xFF10B981), Color(0xFF059669))
//            listOf(Color(0xFFEF4444), Color(0xFFDC2626))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${actionOptions.size} actions available",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionOptions.forEach { (iconRes, actionName) ->
                    SwipeActionListItem(
                        iconRes = iconRes,
                        actionName = actionName,
                        isSelected = actionName == currentAction,
                        gradientColors = gradientColors,
                        onClick = { onSelect(actionName) }
                    )
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
private fun SwipeActionListItem(
    iconRes: Int,
    actionName: String,
    isSelected: Boolean,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.background(
                            brush = Brush.horizontalGradient(
                                colors = gradientColors
                            )
                        )
                    } else Modifier
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = actionName,
                    modifier = Modifier.size(32.dp),
                    tint = if (isSelected)
                        Color.White
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = actionName,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected)
                        Color.White
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}