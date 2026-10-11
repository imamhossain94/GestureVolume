package com.newagedevs.gesturevolume.ui.screens.handler_action

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.AppGestureStore.Slot
import com.newagedevs.gesturevolume.ui.components.HowItWorksAction
import com.newagedevs.gesturevolume.ui.components.Tutorial
import com.newagedevs.gesturevolume.ui.components.TutorialAction
import com.newagedevs.gesturevolume.ui.components.rememberGestureDemoState
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.components.DndAccessDialog
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.PreviewSettingsLayout
import com.newagedevs.gesturevolume.ui.components.isLandscape
import com.newagedevs.gesturevolume.ui.components.actionDisplayName
import com.newagedevs.gesturevolume.ui.motion.Button
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import com.newagedevs.gesturevolume.ui.motion.TextButton
import com.newagedevs.gesturevolume.ui.screens.app_gestures.appGesturesSummary
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.screens.quick_slider.sliderTargetLabel
import com.newagedevs.gesturevolume.ui.view.TapTiming
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.AudioStreamCatalog
import com.newagedevs.gesturevolume.utils.BarBehaviour
import com.newagedevs.gesturevolume.utils.DeviceToggles
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import com.newagedevs.gesturevolume.utils.VolumeStreamMode

/** Starts a system settings screen, pausing the app-open ad for the round trip. */
private fun openSystemScreen(context: android.content.Context, viewModel: MainViewModel, intent: Intent) {
    viewModel.preference.setAppOpenAdPaused(true)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        viewModel.preference.setAppOpenAdPaused(false)
    }
}

/**
 * What the bar does: its eight gestures, and the settings that shape them.
 *
 * Laid out as a list of gestures rather than a list of settings. Each gesture is one line — a
 * picture of it, its name, and what it does — and the whole line opens a screen to choose from
 * ([ActionPickerScreen]). It used to be a heading, a sentence, and a box to tap for each of them,
 * which was three things to read to learn one, and a dialog of tiles to choose in.
 *
 * At the top, the bar itself to try them on ([GestureTryPad]), which answers the question the
 * list cannot: "which of these did I just do?"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerActionsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onPickAction: (Slot) -> Unit = {},
    onOpenQuickSlider: () -> Unit = {},
    onOpenLongPressMenu: () -> Unit = {},
    onOpenAppGestures: () -> Unit = {},
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val preference = viewModel.preference
    val onLeft = remember { preference.getHandlerPosition() == "Left" }

    // Summaries of the screens these rows lead to, re-read on return: those screens write straight
    // through to preferences, which this one cannot hear.
    var sliderTarget by remember { mutableStateOf(preference.slider.getTarget()) }
    var contextMenuItems by remember { mutableStateOf(preference.getContextMenuOrder()) }

    // Read once into local state: plain SharedPref values with no observable wrapper, so the
    // controls' own state drives the UI and the preference is written behind it.
    var volumeStreamMode by remember { mutableStateOf(preference.getVolumeStreamMode()) }
    var swipeStepPercent by remember { mutableIntStateOf(preference.getSwipeStepPercent()) }
    var doubleTapMs by remember { mutableIntStateOf(preference.getDoubleTapMs()) }
    var longPressMs by remember { mutableIntStateOf(preference.getLongPressMs()) }

    // Bumped on every return, so the notes under the actions re-read what has been granted since:
    // every one of those permissions is given on a system screen this one cannot hear back from.
    var permissionTick by remember { mutableIntStateOf(0) }
    val actionNote: @Composable ColumnScope.(String) -> Unit = { action ->
        @Suppress("UNUSED_VARIABLE") val tick = permissionTick
        PermissionNeeds.missingFor(context, preference, action)?.let {
            PermissionNote(
                missing = it,
                onOpenPermissions = onOpenPermissions,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }

    // Coming back from a system screen this screen sent the user to — the WRITE_SETTINGS grant,
    // the Do Not Disturb access, the accessibility list — has to lift the app-open ad pause those
    // set. Without this the pause was set and never cleared, silencing app-open ads for the rest
    // of the install.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                preference.setAppOpenAdPaused(false)
                sliderTarget = preference.slider.getTarget()
                contextMenuItems = preference.getContextMenuOrder()
                permissionTick++
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
        preference.setAppOpenAdPaused(false)
        viewModel.onEvent(MainEvent.WriteSettingsResult(context))
    }

    // Asked only when a brightness action is actually chosen — never at startup. The picker is
    // a screen of its own, and comes back here with the question pending.
    if (state.pendingWriteSettingsRequest) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(MainEvent.CancelPendingBrightnessAction) },
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
                        preference.setAppOpenAdPaused(true)
                        try {
                            writeSettingsLauncher.launch(intent)
                        } catch (_: Exception) {
                            preference.setAppOpenAdPaused(false)
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
                    onClick = { viewModel.onEvent(MainEvent.CancelPendingBrightnessAction) },
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
    // system action chosen while the service is off.
    if (state.showAccessibilityPrompt) {
        AccessibilityDisclosureDialog(
            onAccept = { viewModel.openAccessibilitySettings() },
            onDismiss = { viewModel.onEvent(MainEvent.DismissAccessibilityPrompt) }
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

    // The finger acting the gestures out on the pad, from the ? as on the preview screens. Only on
    // request: the pad is for the user's own tries, and this screen is the one they come back to
    // whenever they change what a gesture does.
    val gestureDemo = rememberGestureDemoState(ACTIONS_DEMO_STEPS, autoPlays = 0)

    Scaffold(
        // The top bar is clear, on the plain page as the preview under it is.
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.handler_actions)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TutorialAction(Tutorial.ACTIONS)
                    HowItWorksAction(gestureDemo)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        val tryPad: @Composable (Modifier, Boolean) -> Unit = { padModifier, fillHeight ->
            GestureTryPad(
                preference = preference,
                actions = Slot.entries.associateWith { actionFor(state, it) },
                doubleTapMs = doubleTapMs,
                longPressMs = longPressMs,
                onLeft = onLeft,
                onChange = onPickAction,
                modifier = padModifier,
                demo = gestureDemo,
                fillHeight = fillHeight,
            )
        }
        val groups: @Composable ColumnScope.() -> Unit = {
            // ---- taps -----------------------------------------------------------------------
            GroupLabel(stringResource(R.string.actions_taps))
            Segments {
                TAP_SLOTS.forEachIndexed { index, slot ->
                    SlotRow(state = state, slot = slot, onLeft = onLeft, shape = segmentShape(index, TAP_SLOTS.size),
                        onClick = { onPickAction(slot) }, note = actionNote)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- swipes ---------------------------------------------------------------------
            // The amount sits between the two swipes it tunes and the two it does not: only the
            // volume and brightness bindings move by an amount, and only up and down hold them.
            GroupLabel(stringResource(R.string.actions_swipes))
            Segments {
                SlotRow(state, Slot.SWIPE_UP, onLeft, segmentShape(0, 5), { onPickAction(Slot.SWIPE_UP) }, actionNote)
                SlotRow(state, Slot.SWIPE_DOWN, onLeft, segmentShape(1, 5), { onPickAction(Slot.SWIPE_DOWN) }, actionNote)
                SwipeAmount(
                    percent = swipeStepPercent,
                    shape = segmentShape(2, 5),
                    onChange = {
                        swipeStepPercent = it
                        preference.setSwipeStepPercent(it)
                    },
                )
                SlotRow(state, Slot.SWIPE_IN, onLeft, segmentShape(3, 5), { onPickAction(Slot.SWIPE_IN) }, actionNote)
                SlotRow(state, Slot.SWIPE_OUT, onLeft, segmentShape(4, 5), { onPickAction(Slot.SWIPE_OUT) }, actionNote)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the screens these gestures lead on to ------------------------------------------
            GroupLabel(stringResource(R.string.actions_more))
            Segments {
                // The Quick panel is opened by one of the gestures above; what it adjusts, how big
                // it is and how hard it buzzes are its own screen's.
                LinkRow(
                    icon = ActionIcon.QuickPanel,
                    title = stringResource(R.string.quick_slider_title),
                    summary = stringResource(sliderTargetLabel(sliderTarget)),
                    shape = segmentShape(0, 3),
                    onClick = onOpenQuickSlider,
                ) { actionNote(HandlerActions.OPEN_QUICK_SLIDER) }
                LinkRow(
                    icon = ActionIcon.Vector(Icons.Filled.Menu),
                    title = stringResource(R.string.context_menu_section),
                    // Counted through the catalog, so the summary matches the menu the user will
                    // actually see — pinned entries included.
                    summary = stringResource(
                        R.string.context_menu_count,
                        HandlerActionCatalog.contextMenuEntries(contextMenuItems).size
                    ),
                    shape = segmentShape(1, 3),
                    onClick = onOpenLongPressMenu,
                )
                @Suppress("UNUSED_VARIABLE") val tick = permissionTick
                LinkRow(
                    icon = ActionIcon.Vector(Icons.Filled.Apps),
                    title = stringResource(R.string.app_gestures_title),
                    summary = appGesturesSummary(preference.appGestures),
                    shape = segmentShape(2, 3),
                    onClick = onOpenAppGestures,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- behaviour ------------------------------------------------------------------
            GroupLabel(stringResource(R.string.behaviour_section))
            Segments {
                VolumeStream(
                    mode = volumeStreamMode,
                    shape = segmentShape(0, 2),
                    onChange = {
                        volumeStreamMode = it
                        preference.setVolumeStreamMode(it)
                    },
                )
                // Read by the bar when it is next put up, which is the moment this screen is left;
                // and by the pad at the top straight away, which is where to feel the difference.
                TapTimingCard(
                    doubleTapMs = doubleTapMs,
                    longPressMs = longPressMs,
                    shape = segmentShape(1, 2),
                    onDoubleTapChange = {
                        doubleTapMs = it
                        preference.setDoubleTapMs(it)
                    },
                    onLongPressChange = {
                        longPressMs = it
                        preference.setLongPressMs(it)
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
        // The pad where the preview screens put their preview, and pinned as theirs is: above the
        // gestures upright, only the gestures scrolling under it; beside them on its side, two
        // fifths of the width and as tall as the screen allows — so trying a gesture and changing
        // it are both in view, whichever way the phone is held.
        PreviewSettingsLayout(contentPadding = padding, preview = tryPad, content = groups)
    }
}

/** Rows grouped into one card, a hairline of the page between them. See [segmentShape]. */
@Composable
private fun Segments(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

/** [slot] and what it does everywhere, with the permission that action still waits for. */
@Composable
private fun SlotRow(
    state: com.newagedevs.gesturevolume.ui.viewmodels.MainState,
    slot: Slot,
    onLeft: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    note: @Composable ColumnScope.(String) -> Unit,
) {
    val action = actionFor(state, slot)
    val nothing = HandlerActions.isDisabled(action)
    GestureRow(
        slot = slot,
        onLeft = onLeft,
        actionIcon = actionIconFor(state, slot),
        actionText = if (nothing) stringResource(R.string.actions_not_set) else actionDisplayName(action),
        dimmed = nothing,
        shape = shape,
        onClick = onClick,
    ) { note(action) }
}

/** A row that leads to another screen: its icon, its name, and what is set there now. */
@Composable
private fun LinkRow(
    icon: ActionIcon,
    title: String,
    summary: String,
    shape: Shape,
    onClick: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val colours = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colours.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colours.secondaryContainer.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                ActionIconImage(
                    icon = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colours.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colours.onSurface,
                )
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = colours.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colours.outline,
                modifier = Modifier.size(22.dp),
            )
        }
        footer()
    }
}

/** A card of the settings list: a title, the line under it, and the control. */
@Composable
private fun SettingCard(
    title: String,
    description: String,
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        content()
    }
}

/**
 * How far one swipe up or down moves the volume, as chips rather than behind a dialog: four short
 * answers, all of them visible, and what the chosen one means said under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipeAmount(percent: Int, shape: Shape, onChange: (Int) -> Unit) {
    SettingCard(
        title = stringResource(R.string.swipe_amount_title),
        description = stringResource(R.string.swipe_amount_desc),
        shape = shape,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            BarBehaviour.SWIPE_STEP_PERCENTS.forEach { option ->
                val selected = option == percent
                FilterChip(
                    selected = selected,
                    onClick = { onChange(option) },
                    label = { Text(swipeAmountLabel(option)) },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        Text(
            text = if (percent == BarBehaviour.SWIPE_STEP_BY_LENGTH) {
                stringResource(R.string.swipe_amount_by_length_desc)
            } else {
                stringResource(R.string.swipe_amount_percent_desc, percent)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * Which volume the bar changes, as the three choices themselves with what each one means. They are
 * genuinely three intentions — see `VolumeStreamMode` — and each carries its consequence under it,
 * which a dialog hid until it was opened.
 */
@Composable
private fun VolumeStream(mode: String, shape: Shape, onChange: (String) -> Unit) {
    val chosen = VolumeStreamMode.sanitize(mode)
    SettingCard(
        title = stringResource(R.string.volume_stream_title),
        description = stringResource(R.string.volume_stream_desc),
        shape = shape,
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            VolumeStreamMode.ALL.forEach { option ->
                val selected = option == chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
                        )
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(option) })
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // The whole row is the target; a second one inside it would let a screen reader
                    // announce the same choice twice.
                    RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = stringResource(AudioStreamCatalog.labelForMode(option)),
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(AudioStreamCatalog.descriptionForMode(option)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The tap timings: the automatic figures until moved, then the user's own, each with the way back
 * to automatic once it has been moved — which there was not, so a slider nudged once stayed the
 * user's for good.
 */
@Composable
private fun TapTimingCard(
    doubleTapMs: Int,
    longPressMs: Int,
    shape: Shape,
    onDoubleTapChange: (Int) -> Unit,
    onLongPressChange: (Int) -> Unit,
) {
    SettingCard(
        title = stringResource(R.string.tap_timing_title),
        description = stringResource(R.string.tap_timing_desc),
        shape = shape,
    ) {
        TimingSlider(
            label = stringResource(R.string.tap_timing_double),
            ms = doubleTapMs,
            automatic = TapTiming.automaticDoubleTapMs.toInt(),
            range = TapTiming.MIN_DOUBLE_TAP_MS..TapTiming.MAX_DOUBLE_TAP_MS,
            step = 25f,
            onChange = onDoubleTapChange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TimingSlider(
            label = stringResource(R.string.tap_timing_long),
            ms = longPressMs,
            automatic = TapTiming.automaticLongPressMs.toInt(),
            range = TapTiming.MIN_LONG_PRESS_MS..TapTiming.MAX_LONG_PRESS_MS,
            step = 50f,
            onChange = onLongPressChange,
        )
        Text(
            text = stringResource(R.string.tap_timing_try),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** One timing: 0 is automatic, shown as the figure it stands for. */
@Composable
private fun TimingSlider(
    label: String,
    ms: Int,
    automatic: Int,
    range: IntRange,
    step: Float,
    onChange: (Int) -> Unit,
) {
    val custom = ms > 0
    val shown = if (custom) ms else automatic
    SliderControl(
        label = label,
        value = shown.toFloat(),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        valueDisplay = if (custom) {
            stringResource(R.string.tap_timing_value, shown)
        } else {
            stringResource(R.string.tap_timing_auto, shown)
        },
        borderColor = MaterialTheme.colorScheme.primary,
        step = step,
        onValueChange = { onChange(it.toInt()) },
    )
    if (custom) {
        TextButton(onClick = { onChange(0) }) {
            Text(stringResource(R.string.tap_timing_use_auto))
        }
    }
}

/** "By how far you swipe", or "10% per swipe". */
@Composable
private fun swipeAmountLabel(percent: Int): String =
    if (percent == BarBehaviour.SWIPE_STEP_BY_LENGTH) {
        stringResource(R.string.swipe_amount_by_length)
    } else {
        stringResource(R.string.swipe_amount_percent, percent)
    }
