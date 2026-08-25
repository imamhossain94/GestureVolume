package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.HandlerActions

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
                Triple(R.drawable.ic_nothing, HandlerActions.NONE, R.string.action_none),
                Triple(R.drawable.ic_vol_increase, HandlerActions.INCREASE_VOLUME_UI, R.string.action_increase_vol_ui),
                Triple(R.drawable.ic_vol_plus, HandlerActions.INCREASE_VOLUME, R.string.action_increase_vol),
                Triple(R.drawable.ic_brightness_up, HandlerActions.INCREASE_BRIGHTNESS, R.string.action_increase_brightness)
            )
        } else {
            listOf(
                Triple(R.drawable.ic_nothing, HandlerActions.NONE, R.string.action_none),
                Triple(R.drawable.ic_vol_decrease, HandlerActions.DECREASE_VOLUME_UI, R.string.action_decrease_vol_ui),
                Triple(R.drawable.ic_vol_minus, HandlerActions.DECREASE_VOLUME, R.string.action_decrease_vol),
                Triple(R.drawable.ic_brightness_down, HandlerActions.DECREASE_BRIGHTNESS, R.string.action_decrease_brightness)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionOptions.forEach { (iconRes, identifier, labelRes) ->
                    SwipeActionListItem(
                        iconRes = iconRes,
                        actionName = stringResource(labelRes),
                        isSelected = identifier == currentAction,
                        onClick = { onSelect(identifier) }
                    )
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
private fun SwipeActionListItem(
    iconRes: Int,
    actionName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = actionName,
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = actionName,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}