package com.newagedevs.gesturevolume.ui.screens.handler_action

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.ui.components.DndAccessDialog
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.ui.screens.quick_slider.sliderTargetLabel
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.AudioStreamCatalog
import com.newagedevs.gesturevolume.utils.DeviceToggles
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.OverlayHostMode

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

/** Starts a system settings screen, pausing the app-open ad for the round trip. */
private fun openSystemScreen(context: android.content.Context, viewModel: MainViewModel, intent: Intent) {
    viewModel.preference.setAppOpenAdPaused(true)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        viewModel.preference.setAppOpenAdPaused(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerActionsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenQuickSlider: () -> Unit = {},
    onOpenLongPressMenu: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()

    var showClickActionDialog by remember { mutableStateOf(false) }
    var showDoubleClickActionDialog by remember { mutableStateOf(false) }
    var showTripleClickActionDialog by remember { mutableStateOf(false) }
    var showLongClickActionDialog by remember { mutableStateOf(false) }
    var showSwipeUpDialog by remember { mutableStateOf(false) }
    var showSwipeDownDialog by remember { mutableStateOf(false) }
    var showSwipeInDialog by remember { mutableStateOf(false) }
    var showSwipeOutDialog by remember { mutableStateOf(false) }

    // Summary of the slider's own screen, so the row says what a long swipe will actually do
    // rather than only that the feature exists. Refreshed on resume below, because the screen
    // that changes these is a separate destination and writes straight through to preferences.
    var sliderTarget by remember { mutableStateOf(viewModel.preference.slider.getTarget()) }

    // Read once into local state rather than on every recomposition: these are plain SharedPref
    // booleans with no observable wrapper, so the switch's own state is what drives the UI and the
    // preference is written behind it.
    var showVolumePercent by remember { mutableStateOf(viewModel.preference.getShowVolumePercent()) }
    var volumeStreamMode by remember { mutableStateOf(viewModel.preference.getVolumeStreamMode()) }
    var showVolumeStreamDialog by remember { mutableStateOf(false) }
    var contextMenuItems by remember { mutableStateOf(viewModel.preference.getContextMenuOrder()) }
    var contextMenuLayout by remember { mutableStateOf(viewModel.preference.getContextMenuLayout()) }
    var showNotification by remember { mutableStateOf(viewModel.preference.getShowNotification()) }
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    var showNotificationWarning by remember { mutableStateOf(false) }
    var showHostDisclosure by remember { mutableStateOf(false) }

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
    // settings, the WRITE_SETTINGS grant, the accessibility list — has to lift the app-open ad
    // pause those set. Without this the pause was set and never cleared, silencing app-open ads
    // for the rest of the install.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.preference.setAppOpenAdPaused(false)
                notificationsAllowed = hasNotificationPermission(context)
                sliderTarget = viewModel.preference.slider.getTarget()
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

    // The action is already saved; this only asks the user to switch the service on. Shown for a
    // system action chosen while the service is off, and for "run without a notification".
    if (state.showAccessibilityPrompt || showHostDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showHostDisclosure = false
                viewModel.openAccessibilitySettings()
            },
            onDismiss = {
                showHostDisclosure = false
                viewModel.onEvent(MainEvent.DismissAccessibilityPrompt)
            }
        )
    }

    if (state.showDndPrompt) {
        DndAccessDialog(
            onAccept = {
                viewModel.onEvent(MainEvent.DismissDndPrompt)
                openSystemScreen(context, viewModel, DeviceToggles(context).dndAccessIntent())
            },
            onDismiss = { viewModel.onEvent(MainEvent.DismissDndPrompt) }
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
                        value = actionLabel(state.clickAction),
                        icon = state.clickActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showClickActionDialog = true }
                    )

                    RowDivider()

                    ActionSettingItem(
                        label = stringResource(R.string.double_tap_action),
                        description = stringResource(R.string.double_tap_desc),
                        value = actionLabel(state.doubleClickAction),
                        icon = state.doubleClickActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showDoubleClickActionDialog = true }
                    )

                    RowDivider()

                    ActionSettingItem(
                        label = stringResource(R.string.triple_tap_action),
                        description = stringResource(R.string.triple_tap_desc),
                        value = actionLabel(state.tripleClickAction),
                        icon = state.tripleClickActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showTripleClickActionDialog = true }
                    )

                    RowDivider()

                    ActionSettingItem(
                        label = stringResource(R.string.long_press_action),
                        description = stringResource(R.string.long_press_desc),
                        value = actionLabel(state.longClickAction),
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

                    RowDivider()

                    ActionSettingItem(
                        label = stringResource(R.string.swipe_down_action),
                        description = stringResource(R.string.swipe_down_desc),
                        value = state.swipeDownAction,
                        icon = state.swipeDownActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showSwipeDownDialog = true }
                    )

                    RowDivider()

                    ActionSettingItem(
                        label = stringResource(R.string.swipe_in_action),
                        description = stringResource(R.string.swipe_in_desc),
                        value = actionLabel(state.swipeInAction),
                        icon = state.swipeInActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showSwipeInDialog = true }
                    )

                    RowDivider()

                    ActionSettingItem(
                        label = stringResource(R.string.swipe_out_action),
                        description = stringResource(R.string.swipe_out_desc),
                        value = actionLabel(state.swipeOutAction),
                        icon = state.swipeOutActionIcon,
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showSwipeOutDialog = true }
                    )

                    RowDivider()

                    // Sits with the gestures because the panel is opened by one — whichever slot
                    // above the user has put "Quick panel" in. What this row leads to is the
                    // panel's own settings: what it drives, how big it is, how hard it buzzes.
                    ActionSettingItem(
                        label = stringResource(R.string.quick_slider_title),
                        description = stringResource(R.string.quick_slider_desc),
                        value = stringResource(sliderTargetLabel(sliderTarget)),
                        icon = ActionIcon.Res(R.drawable.ic_brightness_up),
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = onOpenQuickSlider
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
                        icon = ActionIcon.Res(R.drawable.ic_move),
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = onOpenLongPressMenu
                    )

                    RowDivider()

                    ActionSettingItem(
                        label = stringResource(R.string.volume_stream_title),
                        description = stringResource(R.string.volume_stream_desc),
                        value = stringResource(AudioStreamCatalog.labelForMode(volumeStreamMode)),
                        icon = ActionIcon.Res(R.drawable.ic_music_ui),
                        borderColor = MaterialTheme.colorScheme.primary,
                        showProBadge = false,
                        onClick = { showVolumeStreamDialog = true }
                    )

                    RowDivider()

                    SettingSwitchItem(
                        title = stringResource(R.string.show_volume_percent_title),
                        description = stringResource(R.string.show_volume_percent_desc),
                        checked = showVolumePercent,
                        onCheckedChange = {
                            showVolumePercent = it
                            viewModel.preference.setShowVolumePercent(it)
                        }
                    )

                    RowDivider()

                    // Who draws the bar. On, the accessibility service does and there is no
                    // notification at all; off, the foreground service does, with the row below.
                    val accessibilityHost = state.overlayHostMode == OverlayHostMode.ACCESSIBILITY
                    SettingSwitchItem(
                        title = stringResource(R.string.overlay_host_title),
                        description = stringResource(R.string.overlay_host_desc),
                        checked = accessibilityHost,
                        onCheckedChange = { on ->
                            viewModel.onEvent(
                                MainEvent.SetOverlayHostMode(
                                    if (on) OverlayHostMode.ACCESSIBILITY else OverlayHostMode.NOTIFICATION,
                                    context
                                )
                            )
                        }
                    )

                    if (accessibilityHost) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val enabled = state.isAccessibilityEnabled
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !enabled) { showHostDisclosure = true },
                            shape = RoundedCornerShape(12.dp),
                            color = if (enabled) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (enabled) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(
                                        if (enabled) R.string.overlay_host_active
                                        else R.string.overlay_host_needs_accessibility
                                    ),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    }
                                )
                            }
                        }
                    }

                    // The notification only exists on the notification route.
                    if (!accessibilityHost) {
                        RowDivider()

                        SettingSwitchItem(
                            title = stringResource(R.string.show_notification_title),
                            description = stringResource(R.string.show_notification_desc),
                            checked = showNotification,
                            onCheckedChange = {
                                showNotification = it
                                viewModel.preference.setShowNotification(it)
                                // The service owns the notification, so it is the only thing that
                                // can re-post it on the other channel. Notification only: a full
                                // update would rebuild the handler and pop it up over this screen.
                                viewModel.refreshServiceNotification(context)
                                // Switching the controls on while Android is blocking
                                // notifications produces nothing at all, with no hint as to why.
                                // Say so at the moment the switch is flipped.
                                if (it && !notificationsAllowed) showNotificationWarning = true
                            }
                        )

                        // And keep saying so afterwards, because the dialog above is dismissible
                        // and the permission can be revoked from system settings long after this
                        // switch was last touched.
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

                        // Only once the switch is off is there anything left to explain: Android
                        // will not run a foreground service with no notification at all, so the
                        // last step belongs to the system's own channel settings.
                        if (!showNotification) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    openSystemScreen(
                                        context, viewModel,
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    )
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

    if (showTripleClickActionDialog) {
        TapActionDialog(
            title = stringResource(R.string.triple_tap_action),
            currentAction = state.tripleClickAction,
            onDismiss = { showTripleClickActionDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetTripleClickAction(action, context))
                showTripleClickActionDialog = false
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

    if (showSwipeInDialog) {
        TapActionDialog(
            title = stringResource(R.string.swipe_in_action),
            currentAction = state.swipeInAction,
            onDismiss = { showSwipeInDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetSwipeInAction(action, context))
                showSwipeInDialog = false
            }
        )
    }

    if (showSwipeOutDialog) {
        TapActionDialog(
            title = stringResource(R.string.swipe_out_action),
            currentAction = state.swipeOutAction,
            onDismiss = { showSwipeOutDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetSwipeOutAction(action, context))
                showSwipeOutDialog = false
            }
        )
    }
}

/**
 * The translated name of an action, for the summary rows.
 *
 * The rows used to show the raw identifier — "Mute or Unmute", in English, in every locale —
 * because the identifier is what the preference stores. The catalog knows the label.
 */
@Composable
private fun actionLabel(action: String): String =
    HandlerActionCatalog.entryFor(action)?.let { stringResource(it.labelRes) } ?: action

@Composable
private fun RowDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(16.dp))
}
