package com.newagedevs.gesturevolume.ui.screens.permission

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.utils.DeviceToggles
import com.newagedevs.gesturevolume.utils.OverlayHostMode
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import androidx.compose.ui.res.stringResource
import com.newagedevs.gesturevolume.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    viewModel: MainViewModel = hiltViewModel(),
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
    var locationGranted by remember {
        mutableStateOf(PermissionNeeds.hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    // Which of the optional permissions this user's own configuration has made necessary. Read
    // as state so it re-reads on resume alongside everything else — an action changed on the
    // Actions screen has to be reflected here the moment the user comes back.
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
                locationGranted = PermissionNeeds.hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                needs = PermissionNeeds.read(context, viewModel.preference)
                viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { contactsGranted = it }
    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { phoneGranted = it }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { locationGranted = it }

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

            // Overlay Permission. Not needed while the accessibility service draws the bar, and
            // the card says so rather than nagging for a permission nothing would use.
            val overlayCoveredByAccessibility =
                viewModel.preference.getOverlayHostMode() == OverlayHostMode.ACCESSIBILITY &&
                    accessibilityEnabled
            PermissionCard(
                title = stringResource(R.string.overlay_permission),
                description = stringResource(
                    if (overlayCoveredByAccessibility) R.string.overlay_permission_covered_desc
                    else R.string.overlay_permission_desc
                ),
                icon = Icons.Default.Settings,
                isGranted = overlayPermissionGranted || overlayCoveredByAccessibility,
                borderColor = if (overlayPermissionGranted) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFFF97316)
                },
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

            // Modify system settings — only needed for the brightness actions.
            PermissionCard(
                title = stringResource(R.string.write_settings_permission),
                description = stringResource(R.string.write_settings_permission_desc),
                icon = Icons.Default.BrightnessHigh,
                isGranted = writeSettingsGranted,
                isOptional = true,
                warning = if (needs.writeSettingsMissing) {
                    stringResource(R.string.permission_needed_brightness)
                } else null,
                borderColor = if (writeSettingsGranted) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFF8B5CF6)
                },
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

            // The accessibility service: the system actions, the no-notification host, and the
            // opt-in clipboard capture. Granted on the system's own list, after the disclosure.
            PermissionCard(
                title = stringResource(R.string.accessibility_permission),
                description = stringResource(R.string.accessibility_permission_desc),
                icon = Icons.Default.Accessibility,
                isGranted = accessibilityEnabled,
                isOptional = true,
                warning = if (needs.accessibilityMissing) {
                    stringResource(R.string.permission_needed_accessibility)
                } else null,
                borderColor = if (accessibilityEnabled) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFF8B5CF6)
                },
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

            // Do Not Disturb access — only the Do Not Disturb toggle uses it.
            PermissionCard(
                title = stringResource(R.string.dnd_permission),
                description = stringResource(R.string.dnd_permission_desc),
                icon = Icons.Default.DoNotDisturbOn,
                isGranted = dndAccessGranted,
                isOptional = true,
                warning = if (needs.dndMissing) {
                    stringResource(R.string.permission_needed_dnd)
                } else null,
                borderColor = if (dndAccessGranted) {
                    Color(0xFF10B981)
                } else {
                    Color(0xFF8B5CF6)
                },
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
                    warning = if (needs.notificationMissing) {
                        stringResource(R.string.permission_needed_notification)
                    } else null,
                    borderColor = if (notificationsGranted) {
                        Color(0xFF10B981)
                    } else {
                        Color(0xFF8B5CF6)
                    },
                    onRequestPermission = {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contacts — only the Deck's search uses it.
            PermissionCard(
                title = stringResource(R.string.contacts_permission),
                description = stringResource(R.string.contacts_permission_desc),
                icon = Icons.Default.Contacts,
                isGranted = contactsGranted,
                isOptional = true,
                warning = if (needs.contactsMissing) stringResource(R.string.permission_needed_contacts) else null,
                borderColor = if (contactsGranted) Color(0xFF10B981) else Color(0xFF8B5CF6),
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
                warning = if (needs.phoneMissing) stringResource(R.string.permission_needed_phone) else null,
                borderColor = if (phoneGranted) Color(0xFF10B981) else Color(0xFF8B5CF6),
                onRequestPermission = { phoneLauncher.launch(Manifest.permission.CALL_PHONE) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Location — only the weather tile uses it, and only coarsely.
            PermissionCard(
                title = stringResource(R.string.location_permission),
                description = stringResource(R.string.location_permission_desc),
                icon = Icons.Default.LocationOn,
                isGranted = locationGranted,
                isOptional = true,
                warning = if (needs.locationMissing) stringResource(R.string.permission_needed_location) else null,
                borderColor = if (locationGranted) Color(0xFF10B981) else Color(0xFF8B5CF6),
                onRequestPermission = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // All Set Card
            if (overlayPermissionGranted || overlayCoveredByAccessibility) {
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