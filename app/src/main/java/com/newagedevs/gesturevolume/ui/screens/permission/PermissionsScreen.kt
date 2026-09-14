package com.newagedevs.gesturevolume.ui.screens.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.DeviceToggles
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import com.newagedevs.gesturevolume.utils.PermissionNeeds.Permission
import kotlinx.coroutines.delay

/**
 * Every permission the app can use, each with where it stands and the button that grants it.
 *
 * @param highlight the permission the user was sent here for, from a note beside a setting or the
 *   home screen's card. Its card is scrolled into view and flashed — a primary border and tint that
 *   pulse three times over 1.8 seconds and then settle to a steady outline — so the user sees at a
 *   glance which button is theirs to press. Once per arrival: coming back from the system screen
 *   the button opened, or turning the phone, does not flash it again. With animations switched off
 *   in the system settings the card goes straight to the steady outline.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PermissionsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    highlight: Permission? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        viewModel.preference.setAppOpenAdPaused(true)
        onDispose {
            viewModel.preference.setAppOpenAdPaused(false)
        }
    }

    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var writeSettingsGranted by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var notificationsGranted by remember {
        mutableStateOf(PermissionNeeds.hasNotificationPermission(context))
    }
    var accessibilityEnabled by remember {
        mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context))
    }
    var dndAccessGranted by remember {
        mutableStateOf(PermissionNeeds.hasNotificationPolicyAccess(context))
    }
    var contactsGranted by remember {
        mutableStateOf(PermissionNeeds.hasPermission(context, Manifest.permission.READ_CONTACTS))
    }
    var phoneGranted by remember {
        mutableStateOf(PermissionNeeds.hasPermission(context, Manifest.permission.CALL_PHONE))
    }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showNotification by remember { mutableStateOf(viewModel.preference.getShowNotification()) }

    // Which permissions this user's own configuration has made necessary, and what for. Read as
    // state so it re-reads on resume alongside everything else — an action changed on the Actions
    // screen has to be reflected here the moment the user comes back.
    var needs by remember { mutableStateOf(PermissionNeeds.read(context, viewModel.preference)) }

    // Everything on this screen is granted outside the app, so the only honest moment to re-read
    // it is when the user comes back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = Settings.canDrawOverlays(context)
                writeSettingsGranted = Settings.System.canWrite(context)
                notificationsGranted = PermissionNeeds.hasNotificationPermission(context)
                accessibilityEnabled = OverlayRuntime.isAccessibilityEnabled(context)
                dndAccessGranted = PermissionNeeds.hasNotificationPolicyAccess(context)
                contactsGranted = PermissionNeeds.hasPermission(context, Manifest.permission.READ_CONTACTS)
                phoneGranted = PermissionNeeds.hasPermission(context, Manifest.permission.CALL_PHONE)
                needs = PermissionNeeds.read(context, viewModel.preference)
                viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ---- the highlight -------------------------------------------------------------------------
    var highlightShown by rememberSaveable { mutableStateOf(false) }
    val flash = remember { Animatable(if (highlightShown) HIGHLIGHT_REST else 0f) }
    val requesters = remember { Permission.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(highlight) {
        val target = highlight ?: return@LaunchedEffect
        if (highlightShown) return@LaunchedEffect
        highlightShown = true
        // Past the screen's own slide in, so the scroll and the flash are both seen.
        delay(HIGHLIGHT_START_DELAY_MS)
        requesters.getValue(target).bringIntoView()
        if (animationsDisabled(context)) {
            flash.snapTo(HIGHLIGHT_REST)
            return@LaunchedEffect
        }
        repeat(HIGHLIGHT_PULSES) { pulse ->
            flash.animateTo(1f, tween(HIGHLIGHT_HALF_PULSE_MS, easing = FastOutSlowInEasing))
            val low = if (pulse == HIGHLIGHT_PULSES - 1) HIGHLIGHT_REST else HIGHLIGHT_TROUGH
            flash.animateTo(low, tween(HIGHLIGHT_HALF_PULSE_MS, easing = FastOutSlowInEasing))
        }
    }
    fun highlightOf(permission: Permission): Float = if (permission == highlight) flash.value else 0f
    fun cardModifier(permission: Permission): Modifier =
        Modifier.bringIntoViewRequester(requesters.getValue(permission))

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { contactsGranted = it }
    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { phoneGranted = it }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        // Denied twice, Android stops showing the dialog and the request returns instantly. The
        // app notification settings are then the only route, so send the user there rather than
        // leaving a button that appears to do nothing.
        if (!granted) {
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
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Resume app open ads now that the user has returned from the overlay permission screen
        viewModel.preference.setAppOpenAdPaused(false)
        overlayPermissionGranted = Settings.canDrawOverlays(context)
        viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showAccessibilityDisclosure = false
                viewModel.preference.setAcceptedAccessibilityDisclosure(true)
                viewModel.preference.setAppOpenAdPaused(true)
                try {
                    context.startActivity(OverlayRuntime.accessibilitySettingsIntent())
                } catch (_: Exception) {
                    viewModel.preference.setAppOpenAdPaused(false)
                }
            },
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions)) },
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
                .padding(horizontal = 16.dp)
        ) {
            // Header Info
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
                        text = stringResource(R.string.permissions_header_info),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Required Permissions Section
            Text(
                text = stringResource(R.string.required_permissions),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )

            // Overlay Permission. The bar is always drawn by the foreground service, which
            // cannot draw it without this.
            PermissionCard(
                title = stringResource(R.string.overlay_permission),
                description = stringResource(R.string.overlay_permission_desc),
                icon = Icons.Default.Settings,
                isGranted = overlayPermissionGranted,
                borderColor = if (overlayPermissionGranted) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFFF97316)
                },
                warning = neededBy(needs, Permission.OVERLAY),
                modifier = cardModifier(Permission.OVERLAY),
                highlight = highlightOf(Permission.OVERLAY),
                onRequestPermission = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    // Pause ads while the user is in the overlay permission screen
                    viewModel.preference.setAppOpenAdPaused(true)
                    overlayPermissionLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Optional Permissions Section
            Text(
                text = stringResource(R.string.optional_permissions),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )

            // Modify system settings — the brightness actions, the brightness panel and tile, and
            // auto-rotate.
            PermissionCard(
                title = stringResource(R.string.write_settings_permission),
                description = stringResource(R.string.write_settings_permission_desc),
                icon = Icons.Default.BrightnessHigh,
                isGranted = writeSettingsGranted,
                isOptional = true,
                warning = neededBy(needs, Permission.WRITE_SETTINGS),
                borderColor = if (writeSettingsGranted) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFF8B5CF6)
                },
                modifier = cardModifier(Permission.WRITE_SETTINGS),
                highlight = highlightOf(Permission.WRITE_SETTINGS),
                onRequestPermission = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        "package:${context.packageName}".toUri()
                    )
                    viewModel.preference.setAppOpenAdPaused(true)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        viewModel.preference.setAppOpenAdPaused(false)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // The accessibility service: the system actions, the opt-in volume keys, and the
            // opt-in app watching. Granted on the system's own list, after the disclosure.
            PermissionCard(
                title = stringResource(R.string.accessibility_permission),
                description = stringResource(R.string.accessibility_permission_desc),
                icon = Icons.Default.Accessibility,
                isGranted = accessibilityEnabled,
                isOptional = true,
                warning = neededBy(needs, Permission.ACCESSIBILITY),
                borderColor = if (accessibilityEnabled) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFF8B5CF6)
                },
                modifier = cardModifier(Permission.ACCESSIBILITY),
                highlight = highlightOf(Permission.ACCESSIBILITY),
                onRequestPermission = { showAccessibilityDisclosure = true },
                onDisablePermission = {
                    // Switched off on the same system list it was switched on.
                    viewModel.preference.setAppOpenAdPaused(true)
                    try {
                        context.startActivity(OverlayRuntime.accessibilitySettingsIntent())
                    } catch (_: Exception) {
                        viewModel.preference.setAppOpenAdPaused(false)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Do Not Disturb access — the Do Not Disturb action and tile.
            PermissionCard(
                title = stringResource(R.string.dnd_permission),
                description = stringResource(R.string.dnd_permission_desc),
                icon = Icons.Default.DoNotDisturbOn,
                isGranted = dndAccessGranted,
                isOptional = true,
                warning = neededBy(needs, Permission.NOTIFICATION_POLICY),
                borderColor = if (dndAccessGranted) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFF8B5CF6)
                },
                modifier = cardModifier(Permission.NOTIFICATION_POLICY),
                highlight = highlightOf(Permission.NOTIFICATION_POLICY),
                onRequestPermission = {
                    viewModel.preference.setAppOpenAdPaused(true)
                    try {
                        context.startActivity(DeviceToggles(context).dndAccessIntent())
                    } catch (_: Exception) {
                        viewModel.preference.setAppOpenAdPaused(false)
                    }
                }
            )

            // Notifications — the shade row carrying Show, Settings and Stop. Optional in the
            // strict sense: the service runs without it. It is, however, the only way to bring
            // back a bar hidden from the long-press menu without opening the app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(modifier = Modifier.height(16.dp))

                PermissionCard(
                    title = stringResource(R.string.notification_permission),
                    description = stringResource(R.string.notification_permission_desc),
                    icon = Icons.Default.Notifications,
                    isGranted = notificationsGranted,
                    isOptional = true,
                    warning = neededBy(needs, Permission.NOTIFICATIONS),
                    borderColor = if (notificationsGranted) {
                        Color(0xFF10B981)
                    } else {
                        Color(0xFF8B5CF6)
                    },
                    modifier = cardModifier(Permission.NOTIFICATIONS),
                    highlight = highlightOf(Permission.NOTIFICATIONS),
                    onRequestPermission = {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                )
            }

            // What the notification carries, under the permission that lets it be posted. It was
            // a switch among the gesture settings on the Actions screen, where nothing beside it
            // was about notifications. Shown on every version: below Android 13 there is no
            // permission card above it, but there is still a notification.
            Spacer(modifier = Modifier.height(16.dp))
            NotificationControlsCard(
                checked = showNotification,
                onCheckedChange = { on ->
                    showNotification = on
                    viewModel.preference.setShowNotification(on)
                    // The service owns the notification, so it is the only thing that can re-post
                    // it on the other channel. Notification only: a full update would rebuild the
                    // bar for nothing.
                    viewModel.refreshServiceNotification(context)
                    // On with notifications blocked is now a need, named on the card above.
                    needs = PermissionNeeds.read(context, viewModel.preference)
                    viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
                },
                onOpenChannelSettings = {
                    // The app-open ad is already paused for as long as this screen is open.
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Contacts — only the Deck's search uses it.
            PermissionCard(
                title = stringResource(R.string.contacts_permission),
                description = stringResource(R.string.contacts_permission_desc),
                icon = Icons.Default.Contacts,
                isGranted = contactsGranted,
                isOptional = true,
                warning = neededBy(needs, Permission.CONTACTS),
                borderColor = if (contactsGranted) Color(0xFF10B981) else Color(0xFF8B5CF6),
                modifier = cardModifier(Permission.CONTACTS),
                highlight = highlightOf(Permission.CONTACTS),
                onRequestPermission = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone — only "call directly" uses it.
            PermissionCard(
                title = stringResource(R.string.phone_permission),
                description = stringResource(R.string.phone_permission_desc),
                icon = Icons.Default.Phone,
                isGranted = phoneGranted,
                isOptional = true,
                warning = neededBy(needs, Permission.PHONE),
                borderColor = if (phoneGranted) Color(0xFF10B981) else Color(0xFF8B5CF6),
                modifier = cardModifier(Permission.PHONE),
                highlight = highlightOf(Permission.PHONE),
                onRequestPermission = { phoneLauncher.launch(Manifest.permission.CALL_PHONE) }
            )


            Spacer(modifier = Modifier.height(32.dp))

            // All Set Card
            if (overlayPermissionGranted) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.all_set),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.all_required_permissions_granted),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Whether the ongoing notification carries Show, Settings and Stop, and, once it is off, the way to
 * the system's channel settings: Android will not run a foreground service without a notification,
 * so switching the controls off leaves a silent placeholder that only the system can hide.
 */
@Composable
private fun NotificationControlsCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenChannelSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingSwitchItem(
                title = stringResource(R.string.show_notification_title),
                description = stringResource(R.string.show_notification_desc),
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            if (!checked) {
                TextButton(
                    onClick = onOpenChannelSettings,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.show_notification_off_hint),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Why an ungranted permission matters to this user right now: every setting they have switched on
 * that waits on it, one per line. Null when nothing does.
 */
@Composable
private fun neededBy(needs: PermissionNeeds.Needs, permission: Permission): String? {
    val features = needs.featuresFor(permission)
    if (features.isEmpty()) return null
    val names = features.map { stringResource(it.labelRes) }
    return stringResource(R.string.permission_needed_by_list) + names.joinToString(separator = "") { "\n• $it" }
}

/** True when the system's animator duration scale is off: the highlight then simply appears. */
private fun animationsDisabled(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

/** Long enough for the navigation's own transition to finish before the list scrolls. */
private const val HIGHLIGHT_START_DELAY_MS = 350L

/** Three pulses of 300 ms up and 300 ms down: 1.8 seconds in all. */
private const val HIGHLIGHT_PULSES = 3
private const val HIGHLIGHT_HALF_PULSE_MS = 300

/** How far each pulse falls back between peaks. */
private const val HIGHLIGHT_TROUGH = 0.1f

/** Where the highlight settles once the pulses are done: a steady outline on the card. */
private const val HIGHLIGHT_REST = 0.45f
