package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.newagedevs.gesturevolume.ui.screens.deck.InstalledApp
import com.newagedevs.gesturevolume.ui.screens.deck.loadLaunchableApps
import com.newagedevs.gesturevolume.utils.HandlerActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.newagedevs.gesturevolume.ui.motion.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
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
    allowReposition: Boolean = false,
    /** Offered only where a slot can follow another setting: an app's own gestures. */
    onUseEverywhere: (() -> Unit)? = null,
) {
    val groups = remember(allowReposition) { HandlerActionCatalog.grouped(allowReposition) }
    ActionPickerDialog(title, currentAction, groups, onDismiss, onSelect, onUseEverywhere)
}

/**
 * The grouped grid both pickers draw: [TapActionDialog] with the catalog as it is, and
 * [SwipeActionDialog] with the swipe's own group of steered bindings on top.
 *
 * @param onUseEverywhere when set, a button that puts the slot back to what it does everywhere
 *   else. For an app's own gestures, where "the same as usual" is a choice the grid cannot show.
 */
@Composable
internal fun ActionPickerDialog(
    title: String,
    currentAction: String,
    groups: List<Pair<HandlerActionCatalog.Group, List<HandlerActionCatalog.Entry>>>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onUseEverywhere: (() -> Unit)? = null,
) {
    // The Launch app tile asks which app, in the same dialog: the grid gives way to a list.
    var pickingApp by remember { mutableStateOf(false) }
    val launched = HandlerActions.launchedPackage(currentAction)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (pickingApp) stringResource(R.string.app_gestures_pick_app) else title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            if (pickingApp) {
                AppChooser(selected = launched, onPick = { onSelect(HandlerActions.launchApp(it)) })
            } else Column(
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
                                    isSelected = entry.action == currentAction ||
                                        (entry.action == HandlerActions.LAUNCH_APP && launched != null),
                                    needsAccessibility = entry.needsAccessibility,
                                    onClick = {
                                        if (entry.action == HandlerActions.LAUNCH_APP) {
                                            pickingApp = true
                                        } else {
                                            onSelect(entry.action)
                                        }
                                    },
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
        dismissButton = when {
            // Back to the grid, from the app list.
            pickingApp -> {
                {
                    OutlinedButton(onClick = { pickingApp = false }, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
            onUseEverywhere != null -> {
                {
                    OutlinedButton(onClick = onUseEverywhere, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.app_gestures_use_everywhere))
                    }
                }
            }
            else -> null
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
                        tint = content
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

/** Every launchable app, searchable, for the Launch app action. The one already chosen is marked. */
@Composable
private fun AppChooser(selected: String?, onPick: (String) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        val list = apps
        if (list == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val q = query.trim()
            val shown = if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(shown, key = { it.packageName }) { app ->
                    val chosen = app.packageName == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onPick(app.packageName) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(9.dp))
                            )
                        } else {
                            Spacer(modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = app.label,
                            fontSize = 15.sp,
                            fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
