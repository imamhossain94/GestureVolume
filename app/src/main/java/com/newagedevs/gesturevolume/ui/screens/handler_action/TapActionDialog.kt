package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog

/**
 * Picks an action for a tap, a long press or a horizontal swipe.
 *
 * Read from the shared catalog rather than a second list kept in step by hand: this dialog, the
 * context-menu picker and the menu the overlay draws all offer the same actions, and a copy here
 * is how an action ends up assignable in one place and invisible in another.
 *
 * Grouped, because forty tiles in one grid is a wall. The groups are the catalog's, in its order,
 * and the entries the accessibility service performs carry a small badge so the user knows what
 * choosing one will ask of them before they choose it.
 */
@Composable
fun TapActionDialog(
    title: String,
    currentAction: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    /**
     * Offers "Move handler". Long press only: repositioning needs a gesture the user holds, so it
     * would be meaningless — and unusable — on a tap or a swipe.
     */
    allowReposition: Boolean = false
) {
    val groups = remember(allowReposition) { HandlerActionCatalog.grouped(allowReposition) }

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
                    .padding(top = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                groups.forEach { (group, entries) ->
                    Text(
                        text = stringResource(group.labelRes),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp, start = 2.dp)
                    )
                    entries.chunked(3).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowActions.forEach { entry ->
                                ActionGridItem(
                                    icon = entry.icon,
                                    actionName = stringResource(entry.labelRes),
                                    isSelected = entry.action == currentAction,
                                    needsAccessibility = entry.needsAccessibility,
                                    onClick = { onSelect(entry.action) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill remaining space if row is not complete
                            repeat(3 - rowActions.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
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
private fun ActionGridItem(
    icon: ActionIcon,
    actionName: String,
    isSelected: Boolean,
    needsAccessibility: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ActionIconImage(
                icon = icon,
                contentDescription = actionName,
                modifier = Modifier.size(30.dp),
                tint = content
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = actionName,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = content,
                maxLines = 2,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center
            )
            if (needsAccessibility) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Accessibility,
                        contentDescription = stringResource(R.string.action_needs_accessibility_badge),
                        modifier = Modifier.size(10.dp),
                        tint = content.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = stringResource(R.string.action_needs_accessibility_badge),
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        color = content.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
