package com.newagedevs.gesturevolume.ui.screens.handler_action

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import androidx.compose.ui.res.stringResource
import com.newagedevs.gesturevolume.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerActionsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var showClickActionDialog by remember { mutableStateOf(false) }
    var showDoubleClickActionDialog by remember { mutableStateOf(false) }
    var showLongClickActionDialog by remember { mutableStateOf(false) }
    var showSwipeUpDialog by remember { mutableStateOf(false) }
    var showSwipeDownDialog by remember { mutableStateOf(false) }
    var showContextMenuDialog by remember { mutableStateOf(false) }

    // Read once into local state rather than on every recomposition: these are plain SharedPref
    // booleans with no observable wrapper, so the switch's own state is what drives the UI and the
    // preference is written behind it.
    var snapToEdges by remember { mutableStateOf(viewModel.preference.getSnapToEdges()) }
    var showVolumePercent by remember { mutableStateOf(viewModel.preference.getShowVolumePercent()) }
    var contextMenuItems by remember { mutableStateOf(viewModel.preference.getContextMenuItems()) }
    // Re-applies the action the user picked, once they come back from the system settings screen.
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.preference.setAppOpenAdPaused(false)
        viewModel.onEvent(MainEvent.WriteSettingsResult(context))
    }

    // Asked only when a brightness action is actually chosen — never at startup.
    if (state.pendingWriteSettingsRequest) {
        AlertDialog(
            onDismissRequest = {
                viewModel.onEvent(MainEvent.CancelPendingBrightnessAction)
            },
            title = {
                Text(
                    text = stringResource(R.string.brightness_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.brightness_permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                        viewModel.preference.setAppOpenAdPaused(true)
                        try {
                            writeSettingsLauncher.launch(intent)
                        } catch (_: Exception) {
                            viewModel.preference.setAppOpenAdPaused(false)
                            viewModel.onEvent(MainEvent.CancelPendingBrightnessAction)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.onEvent(MainEvent.CancelPendingBrightnessAction)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showContextMenuDialog) {
        ContextMenuItemsDialog(
            selected = contextMenuItems,
            onDismiss = { showContextMenuDialog = false },
            onConfirm = { picked ->
                contextMenuItems = picked
                viewModel.preference.setContextMenuItems(picked)
                showContextMenuDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.handler_actions)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Tap Actions Section
            SectionTitle(stringResource(R.string.tap_actions), MaterialTheme.colorScheme.primary)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ActionSettingItem(
                        label = stringResource(R.string.single_tap_action),
                        description = stringResource(R.string.single_tap_desc),
                        value = state.clickAction,
                        icon = state.clickActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showClickActionDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ActionSettingItem(
                        label = stringResource(R.string.double_tap_action),
                        description = stringResource(R.string.double_tap_desc),
                        value = state.doubleClickAction,
                        icon = state.doubleClickActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = {
                            showDoubleClickActionDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ActionSettingItem(
                        label = stringResource(R.string.long_press_action),
                        description = stringResource(R.string.long_press_desc),
                        value = state.longClickAction,
                        icon = state.longClickActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showLongClickActionDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gesture Actions Section
            SectionTitle(stringResource(R.string.gesture_actions), MaterialTheme.colorScheme.primary)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ActionSettingItem(
                        label = stringResource(R.string.swipe_up_action),
                        description = stringResource(R.string.swipe_up_desc),
                        value = state.swipeUpAction,
                        icon = state.swipeUpActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showSwipeUpDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ActionSettingItem(
                        label = stringResource(R.string.swipe_down_action),
                        description = stringResource(R.string.swipe_down_desc),
                        value = state.swipeDownAction,
                        icon = state.swipeDownActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showSwipeDownDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Behaviour Section
            SectionTitle(stringResource(R.string.behaviour_section), MaterialTheme.colorScheme.primary)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ActionSettingItem(
                        label = stringResource(R.string.context_menu_section),
                        description = stringResource(R.string.context_menu_desc),
                        value = if (contextMenuItems.isEmpty()) {
                            stringResource(R.string.context_menu_none)
                        } else {
                            stringResource(R.string.context_menu_count, contextMenuItems.size)
                        },
                        icon = R.drawable.ic_move,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showContextMenuDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingSwitchItem(
                        title = stringResource(R.string.snap_to_edges_title),
                        description = stringResource(R.string.snap_to_edges_desc),
                        checked = snapToEdges,
                        onCheckedChange = {
                            snapToEdges = it
                            viewModel.preference.setSnapToEdges(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingSwitchItem(
                        title = stringResource(R.string.show_volume_percent_title),
                        description = stringResource(R.string.show_volume_percent_desc),
                        checked = showVolumePercent,
                        onCheckedChange = {
                            showVolumePercent = it
                            viewModel.preference.setShowVolumePercent(it)
                        }
                    )

                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.customize_how_you_interact_with_the_volume_handler_through_taps_and_gestures),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialogs
    if (showClickActionDialog) {
        TapActionDialog(
            title = stringResource(R.string.single_tap_action),
            currentAction = state.clickAction,
            onDismiss = { showClickActionDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetClickAction(action, context))
                showClickActionDialog = false
            }
        )
    }

    if (showDoubleClickActionDialog) {
        TapActionDialog(
            title = stringResource(R.string.double_tap_action),
            currentAction = state.doubleClickAction,
            onDismiss = { showDoubleClickActionDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetDoubleClickAction(action, context))
                showDoubleClickActionDialog = false
            }
        )
    }

    if (showLongClickActionDialog) {
        TapActionDialog(
            title = stringResource(R.string.long_press_action),
            currentAction = state.longClickAction,
            allowReposition = true,
            onDismiss = { showLongClickActionDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetLongClickAction(action, context))
                showLongClickActionDialog = false
            }
        )
    }

    if (showSwipeUpDialog) {
        SwipeActionDialog(
            title = stringResource(R.string.swipe_up_action),
            currentAction = state.swipeUpAction,
            isSwipeUp = true,
            onDismiss = { showSwipeUpDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetSwipeUpAction(action, context))
                showSwipeUpDialog = false
            }
        )
    }

    if (showSwipeDownDialog) {
        SwipeActionDialog(
            title = stringResource(R.string.swipe_down_action),
            currentAction = state.swipeDownAction,
            isSwipeUp = false,
            onDismiss = { showSwipeDownDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetSwipeDownAction(action, context))
                showSwipeDownDialog = false
            }
        )
    }
}