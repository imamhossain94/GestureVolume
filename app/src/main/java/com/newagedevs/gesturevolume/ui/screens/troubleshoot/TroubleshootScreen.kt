package com.newagedevs.gesturevolume.ui.screens.troubleshoot

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.Chevron
import com.newagedevs.gesturevolume.ui.components.GroupHeading
import com.newagedevs.gesturevolume.ui.components.HeroCard
import com.newagedevs.gesturevolume.ui.components.IconTile
import com.newagedevs.gesturevolume.ui.components.KitRow
import com.newagedevs.gesturevolume.ui.components.RowGroup
import com.newagedevs.gesturevolume.ui.components.cardShape
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.utils.DeviceCareSettings

/**
 * Keeping the service alive on phones that stop it: the two settings that decide it, each one tap
 * away and saying whether it is already done; the other things to check by hand; and the site that
 * has the steps for each maker.
 *
 * Laid out as the Actions screen is: a card at the top saying what this is for, then groups of
 * rows, each a picture, what it is, and where it leads. A fix that is done is green and ticked, and
 * one still to do is orange, so the screen can be read at a glance before any of it is read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                HeroCard(
                    title = stringResource(R.string.troubleshoot_desc),
                    text = stringResource(R.string.troubleshoot_info),
                    leading = { IconTile(Icons.Filled.HealthAndSafety, lit = true, size = 56.dp) },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ---- the two settings that decide whether the service survives ------------------
                GroupHeading(stringResource(R.string.troubleshoot_group_fixes))
                RowGroup {
                    FixRow(
                        icon = Icons.Filled.BatteryFull,
                        title = stringResource(R.string.fix_battery_title),
                        description = stringResource(R.string.fix_battery_desc),
                        done = batteryUnrestricted,
                        status = if (batteryUnrestricted) stringResource(R.string.fix_battery_allowed) else null,
                        index = 0,
                        onClick = { safeStart(DeviceCareSettings.batteryOptimizationIntent()) },
                    )
                    FixRow(
                        icon = Icons.Filled.RestartAlt,
                        title = stringResource(R.string.fix_autostart_title),
                        description = if (autoStartIntent != null) {
                            stringResource(R.string.fix_autostart_desc)
                        } else {
                            stringResource(R.string.autostart_unavailable)
                        },
                        done = false,
                        status = null,
                        index = 1,
                        onClick = autoStartIntent?.let { intent -> { safeStart(intent) } },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---- by hand ------------------------------------------------------------------
                val steps = listOf(
                    stringResource(R.string.step_auto_start),
                    stringResource(R.string.step_battery),
                    stringResource(R.string.step_recents),
                    stringResource(R.string.step_popup),
                )
                GroupHeading(stringResource(R.string.troubleshoot_group_steps))
                RowGroup {
                    steps.forEachIndexed { index, step ->
                        // The number is on the tile; what the step is, before its colon, is the
                        // row's name, and how to do it the line under.
                        val parts = step.replace(LEADING_NUMBER, "").split(STEP_COLON, limit = 2)
                        KitRow(
                            title = parts[0].trim(),
                            description = parts.getOrNull(1)?.trim()?.ifEmpty { null },
                            shape = cardShape(index, steps.size),
                            leading = { NumberTile(index + 1) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---- elsewhere -----------------------------------------------------------------
                GroupHeading(stringResource(R.string.troubleshoot_group_more), hint = stringResource(R.string.troubleshoot_footer))
                KitRow(
                    title = stringResource(R.string.visit_website),
                    summary = DONT_KILL_MY_APP.removePrefix("https://").trimEnd('/'),
                    shape = cardShape(0, 1),
                    leading = { IconTile(Icons.Filled.Public) },
                    onClick = { safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(DONT_KILL_MY_APP))) },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * One setting to fix: green and ticked once it is done, orange while it is not, and greyed with no
 * way through where this phone has no screen for it.
 */
@Composable
private fun FixRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    done: Boolean,
    status: String?,
    index: Int,
    onClick: (() -> Unit)?,
) {
    val accent = if (done) DONE else TO_DO
    KitRow(
        title = title,
        description = description,
        shape = cardShape(index, 2),
        leading = { IconTile(icon, container = accent.copy(alpha = 0.16f), tint = accent) },
        onClick = onClick,
        trailing = when {
            status != null -> {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(status, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
                    }
                }
            }
            onClick != null -> {
                { Chevron() }
            }
            else -> null
        },
    )
}

/** A step's number on a tile, where the others have a picture. */
@Composable
private fun NumberTile(number: Int) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "1. " at the start of a step, which its tile now says. */
private val LEADING_NUMBER = Regex("""^\s*\d+\s*[.)．、]\s*""")

/** Where a step's name ends and its instructions begin, in either width. */
private val STEP_COLON = Regex("[:：]")

private const val DONT_KILL_MY_APP = "https://dontkillmyapp.com/"

private val DONE = Color(0xFF10B981)
private val TO_DO = Color(0xFFF97316)

/** The home screen's widest column. */
private val CONTENT_MAX_WIDTH = 920.dp
