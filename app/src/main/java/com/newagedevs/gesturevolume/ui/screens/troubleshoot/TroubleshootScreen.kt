package com.newagedevs.gesturevolume.ui.screens.troubleshoot

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.DeviceCareSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var batteryUnrestricted by remember {
        mutableStateOf(DeviceCareSettings.isIgnoringBatteryOptimizations(context))
    }
    val autoStartIntent = remember { DeviceCareSettings.autoStartIntent(context) }

    // The user leaves the app to change these, so re-read them when they come back.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = DeviceCareSettings.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun safeStart(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // OEM screens get renamed between ROM builds; fall back to this app's settings page.
            try {
                context.startActivity(DeviceCareSettings.appDetailsIntent(context))
            } catch (_: Exception) {
                // Nothing else to try.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.troubleshoot)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_left),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.troubleshoot_info),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
            )

            // The two settings that actually decide whether the service survives, each one tap away.
            FixCard(
                icon = Icons.Default.BatteryFull,
                title = stringResource(R.string.fix_battery_title),
                description = stringResource(R.string.fix_battery_desc),
                statusLabel = if (batteryUnrestricted) {
                    stringResource(R.string.fix_battery_allowed)
                } else {
                    null
                },
                satisfied = batteryUnrestricted,
                onClick = { safeStart(DeviceCareSettings.batteryOptimizationIntent()) }
            )

            FixCard(
                icon = Icons.Default.PlayArrow,
                title = stringResource(R.string.fix_autostart_title),
                description = if (autoStartIntent != null) {
                    stringResource(R.string.fix_autostart_desc)
                } else {
                    stringResource(R.string.autostart_unavailable)
                },
                statusLabel = null,
                satisfied = false,
                enabled = autoStartIntent != null,
                onClick = { autoStartIntent?.let { safeStart(it) } }
            )

            TroubleshootCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TroubleshootStep(stringResource(R.string.step_auto_start))
                    TroubleshootStep(stringResource(R.string.step_battery))
                    TroubleshootStep(stringResource(R.string.step_recents))
                    TroubleshootStep(stringResource(R.string.step_popup))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.troubleshoot_footer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Button(
                onClick = {
                    safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/")))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.visit_website),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FixCard(
    icon: ImageVector,
    title: String,
    description: String,
    statusLabel: String?,
    satisfied: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = if (satisfied) Color(0xFF10B981) else Color(0xFFF97316)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            if (statusLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (satisfied) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun TroubleshootStep(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TroubleshootCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        content()
    }
}
