package com.newagedevs.gesturevolume.ui.screens.handler_action

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.utils.HandlerActions
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import androidx.compose.ui.res.stringResource
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.AudioStreamCatalog
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog

/** Whether Android currently lets this app post notifications. Always true below Android 13. */
private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

/** The fallback route once the system prompt has been exhausted. */
private fun openAppNotificationSettings(context: android.content.Context, viewModel: MainViewModel) {
    viewModel.preference.setAppOpenAdPaused(true)
    try {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    } catch (_: Exception) {
        viewModel.preference.setAppOpenAdPaused(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerActionsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
    var showVolumePercent by remember { mutableStateOf(viewModel.preference.getShowVolumePercent()) }
    var volumeStreamMode by remember { mutableStateOf(viewModel.preference.getVolumeStreamMode()) }
    var edgeSwipeMenu by remember { mutableStateOf(viewModel.preference.getHandlerEdgeSwipeMenu()) }
    var showVolumeStreamDialog by remember { mutableStateOf(false) }
    var contextMenuItems by remember { mutableStateOf(viewModel.preference.getContextMenuItems()) }
    var showNotification by remember { mutableStateOf(viewModel.preference.getShowNotification()) }
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    var showNotificationWarning by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsAllowed = granted
        if (!granted) openAppNotificationSettings(context, viewModel)
    }

    /**
     * Asks, or sends the user to system settings when asking is no longer possible.
     *
     * After two refusals Android stops showing the dialog and the request returns immediately —
     * a button that appears to do nothing. The launcher's callback covers that case too, so this
     * is correct whether the prompt is still available or not.
     */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings(context, viewModel)
        }
    }

    // Coming back from a system screen this screen sent the user to — the notification channel
    // settings, or the WRITE_SETTINGS grant — has to lift the app-open ad pause those set. Without
    // this the pause was set and never cleared, silencing app-open ads for the rest of the install.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.preference.setAppOpenAdPaused(false)
                notificationsAllowed = hasNotificationPermission(context)
                viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Re-applies the action the user picked, once they come back from the system settings screen.
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.preference.setAppOpenAdPaused(false)
        viewModel.onEvent(MainEvent.WriteSettingsResult(context))
    }

    if (showNotificationWarning) {
        AlertDialog(
            onDismissRequest = { showNotificationWarning = false },
            title = {
                Text(
                    text = stringResource(R.string.notification_permission_needed_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.notification_permission_needed_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationWarning = false
                        requestNotificationPermission()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.grant_permission_action))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showNotificationWarning = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.not_now))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
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

    if (showVolumeStreamDialog) {
        VolumeStreamDialog(
            selected = volumeStreamMode,
            onDismiss = { showVolumeStreamDialog = false },
            onConfirm = { picked ->
                volumeStreamMode = picked
                viewModel.preference.setVolumeStreamMode(picked)
                showVolumeStreamDialog = false
            }
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
                        // Counted through the catalog, so the summary matches the menu the user
                        // will actually see — pinned entries included.
                        value = stringResource(
                            R.string.context_menu_count,
                            HandlerActionCatalog.contextMenuEntries(contextMenuItems).size
                        ),
                        icon = R.drawable.ic_move,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showContextMenuDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingSwitchItem(
                        title = stringResource(R.string.edge_swipe_menu_title),
                        description = stringResource(R.string.edge_swipe_menu_desc),
                        checked = edgeSwipeMenu,
                        onCheckedChange = {
                            edgeSwipeMenu = it
                            // No service round-trip: the overlay reads this preference live, at
                            // the moment a horizontal swipe is classified.
                            viewModel.preference.setHandlerEdgeSwipeMenu(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ActionSettingItem(
                        label = stringResource(R.string.volume_stream_title),
                        description = stringResource(R.string.volume_stream_desc),
                        value = stringResource(AudioStreamCatalog.labelForMode(volumeStreamMode)),
                        icon = R.drawable.ic_music_ui,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showVolumeStreamDialog = true }
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

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    SettingSwitchItem(
                        title = stringResource(R.string.show_notification_title),
                        description = stringResource(R.string.show_notification_desc),
                        checked = showNotification,
                        onCheckedChange = {
                            showNotification = it
                            viewModel.preference.setShowNotification(it)
                            // The service owns the notification, so it is the only thing that can
                            // re-post it on the other channel. Notification only: a full update
                            // would rebuild the handler and pop it up over this screen.
                            viewModel.refreshServiceNotification(context)
                            // Switching the controls on while Android is blocking notifications
                            // produces nothing at all, with no hint as to why. Say so at the
                            // moment the switch is flipped.
                            if (it && !notificationsAllowed) showNotificationWarning = true
                        }
                    )

                    // And keep saying so afterwards, because the dialog above is dismissible and
                    // the permission can be revoked from system settings long after this switch
                    // was last touched.
                    if (showNotification && !notificationsAllowed) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { requestNotificationPermission() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.notification_permission_warning_inline),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    // Only once the switch is off is there anything left to explain: Android will
                    // not run a foreground service with no notification at all, so the last step
                    // belongs to the system's own channel settings.
                    if (!showNotification) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.preference.setAppOpenAdPaused(true)
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    viewModel.preference.setAppOpenAdPaused(false)
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.show_notification_off_hint),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
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