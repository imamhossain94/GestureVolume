package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions

/**
 * Picks which actions the long-press menu offers.
 *
 * Multi-select rather than a single choice: the menu is a list, and the question is which rows it
 * contains. Selecting none is allowed and meaningful — it strips the menu back to the one entry
 * that cannot be removed, leaving the long press to do little but reposition, which is close to
 * how the bar behaved before the menu existed.
 *
 * That one entry is "Hide handler", shown checked and not switchable. Since the floating ✕ that
 * used to appear mid-drag was removed, this menu is the only way to put the bar away from the bar
 * itself, and a picker that let the user delete their last route out would be a trap.
 */
@Composable
fun ContextMenuItemsDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    // Held locally so the menu is not rewritten on every tick of the user's mind — the overlay
    // reads this set live, and a half-made selection would be a half-built menu.
    var working by remember(selected) { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.context_menu_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.context_menu_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                HandlerActionCatalog.CONTEXT_MENU_CANDIDATES.forEach { entry ->
                    val pinned = entry.action in HandlerActions.ALWAYS_IN_CONTEXT_MENU
                    val checked = pinned || entry.action in working
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !pinned) {
                                working = if (checked) {
                                    working - entry.action
                                } else {
                                    working + entry.action
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIconImage(
                            icon = entry.icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(entry.labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (pinned) {
                                Text(
                                    text = stringResource(R.string.context_menu_always),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            enabled = !pinned
                        )
                    }
                }

                if (working.none { it !in HandlerActions.ALWAYS_IN_CONTEXT_MENU }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.context_menu_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(working) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
