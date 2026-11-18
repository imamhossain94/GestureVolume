package com.newagedevs.gesturevolume.ui.screens.button_feedback

import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore.Button
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.service.ButtonBumpOverlay
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.ChoiceChip
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.PreviewSettingsLayout
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import com.newagedevs.gesturevolume.ui.motion.TextButton
import com.newagedevs.gesturevolume.ui.screens.handler_action.segmentShape
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.view.ButtonBumpView
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ButtonBump
import com.newagedevs.gesturevolume.utils.HandlerShape
import com.newagedevs.gesturevolume.utils.PanelTheme
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The button animation: the glass dents at the very button that was pressed — volume up where
 * volume up is, volume down where it is, the power button where that is — and the volume slider
 * grows out of the dent, the way a phone whose screen answers its buttons opens into its slider.
 *
 * At the top, a phone with all three buttons on it to press, playing the press and the slider
 * exactly as the glass will; under it, the animations — every one of them free — then the slider,
 * where each button is (lined up on the real glass while it is being set), the power button, the
 * look and the feel. Every change is written as it is made: the overlay reads the settings at each
 * press, so the next press of a real button already shows it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ButtonFeedbackScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onOpenQuickSlider: () -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val store = viewModel.preference.bump

    var enabled by remember { mutableStateOf(store.isEnabled()) }
    var style by remember { mutableStateOf(store.getStyle()) }
    var showSlider by remember { mutableStateOf(store.getShowSlider()) }
    var powerMenu by remember { mutableStateOf(store.getPowerMenu()) }
    var powerWake by remember { mutableStateOf(store.getPowerWake()) }
    var places by remember { mutableStateOf(Button.entries.associateWith { store.getPlace(it) }) }
    var placing by remember { mutableStateOf(Button.VOLUME_UP) }
    var depth by remember { mutableFloatStateOf(store.getDepthDp()) }
    var color by remember { mutableIntStateOf(store.getColor()) }
    var accent by remember { mutableIntStateOf(store.getAccent()) }
    var showLevel by remember { mutableStateOf(store.getShowLevel()) }
    var showPercent by remember { mutableStateOf(store.getShowPercent()) }
    var haptic by remember { mutableStateOf(store.getHaptic()) }
    var holdMs by remember { mutableIntStateOf(store.getHoldMs()) }
    var speed by remember { mutableFloatStateOf(store.getSpeed()) }
    var accessibilityOn by remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }

    // Back from the accessibility settings: look again.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = OverlayRuntime.isAccessibilityEnabled(context)
                viewModel.preference.setAppOpenAdPaused(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** What a press of [button] looks like with the settings on screen: the store's own rule. */
    fun settingsFor(button: Button): ButtonBumpStore.Settings {
        val place = places.getValue(button)
        val power = button == Button.POWER
        val slider = showSlider && !power
        return ButtonBumpStore.Settings(
            style = style,
            left = place.left,
            position = place.position,
            lengthDp = place.lengthDp,
            depthDp = depth,
            color = color,
            accent = accent,
            showLevel = showLevel && !slider && !power,
            showPercent = showPercent && !slider && !power,
            haptic = haptic,
            holdMs = holdMs,
            speed = speed,
        )
    }

    // The press on the real glass, for lining a button up: through the bar's own controller when it
    // is running, so it is exactly what a press will be, and through a window of this screen's own
    // otherwise. Taken away with the screen.
    val localOverlay = remember { mutableStateOf<ButtonBumpOverlay?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            localOverlay.value?.destroy()
            localOverlay.value = null
        }
    }
    var lastGlassMs by remember { mutableLongStateOf(0L) }
    fun showOnPhone(button: Button, throttled: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (throttled && now - lastGlassMs < GLASS_PREVIEW_GAP_MS) return
        lastGlassMs = now
        val controller = OverlayRuntime.activeController
        if (controller != null) {
            controller.previewBump(button)
            return
        }
        val a11y = OverlayRuntime.accessibilityService
        val overlay = localOverlay.value ?: when {
            a11y != null -> ButtonBumpOverlay(a11y, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            Settings.canDrawOverlays(context) -> ButtonBumpOverlay(context, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            else -> null
        }?.also { localOverlay.value = it } ?: return
        overlay.press(store.settings(button), if (button == Button.VOLUME_DOWN) -1 else 1, 0.6f, atLimit = false)
    }

    // Presses for the preview: a counter, so the same button twice still plays twice.
    var pressCount by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(Button.VOLUME_UP) }
    var previewLevel by remember { mutableFloatStateOf(0.45f) }
    /** Whether the press being played brings the slider out: not while a place is being dragged. */
    var pressWithSlider by remember { mutableStateOf(true) }
    fun pressPreview(button: Button, withSlider: Boolean = true) {
        pressed = button
        pressWithSlider = withSlider
        when (button) {
            Button.VOLUME_UP -> previewLevel = (previewLevel + PREVIEW_STEP).coerceAtMost(1f)
            Button.VOLUME_DOWN -> previewLevel = (previewLevel - PREVIEW_STEP).coerceAtLeast(0f)
            Button.POWER -> Unit
        }
        pressCount++
    }

    fun setPlace(button: Button, place: ButtonBumpStore.Place) {
        places = places + (button to place)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bump_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { padding ->
        PreviewSettingsLayout(
            contentPadding = padding,
            preview = { previewModifier, fillHeight ->
                BumpPreview(
                    settingsFor = ::settingsFor,
                    pressed = pressed,
                    placing = placing,
                    pressCount = pressCount,
                    level = previewLevel,
                    slider = showSlider && pressWithSlider,
                    holdMs = holdMs,
                    preference = viewModel.preference,
                    onPress = { pressPreview(it) },
                    modifier = previewModifier,
                    fillHeight = fillHeight,
                )
            },
        ) {
            // ---- on or off ------------------------------------------------------------------------
            Segments {
                ToggleRow(
                    icon = Icons.Filled.Animation,
                    title = stringResource(R.string.bump_enable_title),
                    description = stringResource(R.string.bump_enable_desc),
                    checked = enabled,
                    shape = segmentShape(0, 1),
                    onCheckedChange = {
                        enabled = it
                        store.setEnabled(it)
                        // The keys, and the power menu, are asked for only while something wants them.
                        OverlayRuntime.accessibilityService?.applyEventSubscription()
                        if (it) pressPreview(Button.VOLUME_UP)
                    },
                )
            }
            if (enabled && !accessibilityOn) {
                PermissionNote(
                    text = stringResource(R.string.bump_needs_accessibility),
                    onClick = { onOpenPermissions(PermissionNeeds.Permission.ACCESSIBILITY) },
                )
            }
            if (enabled && !state.isRunning) {
                Note(stringResource(R.string.bump_needs_service))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the animations -------------------------------------------------------------------
            Heading(stringResource(R.string.bump_style_title), stringResource(R.string.bump_style_desc))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                items(ButtonBump.STYLES, key = { it }) { id ->
                    StyleTile(
                        style = id,
                        selected = id == style,
                        color = color,
                        accent = accent,
                        onClick = {
                            style = id
                            store.setStyle(id)
                            pressPreview(if (pressed == Button.POWER) Button.VOLUME_UP else pressed)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the slider out of the press ----------------------------------------------------
            Heading(stringResource(R.string.bump_slider_title), stringResource(R.string.bump_slider_desc))
            Segments {
                SwitchCard(
                    title = stringResource(R.string.bump_show_slider),
                    description = stringResource(R.string.bump_show_slider_desc),
                    checked = showSlider,
                    shape = segmentShape(0, 1),
                    onCheckedChange = {
                        showSlider = it
                        store.setShowSlider(it)
                        pressPreview(Button.VOLUME_UP)
                    },
                )
            }
            OutlinedButton(
                onClick = onOpenQuickSlider,
                modifier = Modifier.padding(top = 10.dp, start = 2.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.bump_open_quick_slider))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- where each button is -------------------------------------------------------------
            Heading(stringResource(R.string.bump_place_title), stringResource(R.string.bump_place_desc))
            val place = places.getValue(placing)
            Card(shape = segmentShape(0, 1)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp).selectableGroup(),
                ) {
                    Button.entries.forEach { button ->
                        ChoiceChip(
                            label = stringResource(buttonLabel(button)),
                            selected = placing == button,
                            onClick = {
                                placing = button
                                pressPreview(button, withSlider = false)
                                showOnPhone(button)
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.bump_side),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp).selectableGroup(),
                ) {
                    listOf(true, false).forEach { left ->
                        ChoiceChip(
                            label = stringResource(if (left) R.string.bump_side_left else R.string.bump_side_right),
                            selected = place.left == left,
                            onClick = {
                                setPlace(placing, place.copy(left = left))
                                store.setSide(placing, left)
                                pressPreview(placing, withSlider = false)
                                showOnPhone(placing)
                            },
                        )
                    }
                }
                SliderControl(
                    label = stringResource(R.string.bump_position),
                    value = place.position * 100f,
                    valueRange = 0f..100f,
                    valueDisplay = stringResource(R.string.bump_value_percent, (place.position * 100f).roundToInt()),
                    borderColor = MaterialTheme.colorScheme.primary,
                    step = 0.5f,
                    onValueChange = {
                        val position = it / 100f
                        setPlace(placing, place.copy(position = position))
                        // Written as it moves, so the press on the glass follows the slider.
                        store.setPosition(placing, position)
                        pressPreview(placing, withSlider = false)
                        showOnPhone(placing, throttled = true)
                    },
                    onValueChangeFinished = { showOnPhone(placing) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SliderControl(
                    label = stringResource(R.string.bump_length),
                    value = place.lengthDp,
                    valueRange = ButtonBumpStore.MIN_LENGTH..ButtonBumpStore.MAX_LENGTH,
                    valueDisplay = stringResource(R.string.bump_value_dp, place.lengthDp.roundToInt()),
                    borderColor = MaterialTheme.colorScheme.primary,
                    step = 2f,
                    onValueChange = {
                        setPlace(placing, place.copy(lengthDp = it))
                        store.setLengthDp(placing, it)
                        pressPreview(placing, withSlider = false)
                        showOnPhone(placing, throttled = true)
                    },
                    onValueChangeFinished = { showOnPhone(placing) },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    OutlinedButton(
                        onClick = { showOnPhone(placing) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.bump_try))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        store.resetPlaces()
                        places = Button.entries.associateWith { store.getPlace(it) }
                        pressPreview(placing, withSlider = false)
                        showOnPhone(placing)
                    }) { Text(stringResource(R.string.bump_reset_places)) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the power button -----------------------------------------------------------------
            Heading(stringResource(R.string.bump_power_title), stringResource(R.string.bump_power_desc))
            Segments {
                SwitchCard(
                    title = stringResource(R.string.bump_power_menu),
                    description = stringResource(R.string.bump_power_menu_desc),
                    checked = powerMenu,
                    shape = segmentShape(0, 2),
                    onCheckedChange = {
                        powerMenu = it
                        store.setPowerMenu(it)
                        OverlayRuntime.accessibilityService?.applyEventSubscription()
                        if (it) pressPreview(Button.POWER)
                    },
                )
                SwitchCard(
                    title = stringResource(R.string.bump_power_wake),
                    description = stringResource(R.string.bump_power_wake_desc),
                    checked = powerWake,
                    shape = segmentShape(1, 2),
                    onCheckedChange = {
                        powerWake = it
                        store.setPowerWake(it)
                        if (it) pressPreview(Button.POWER)
                    },
                )
            }
            if (enabled && (powerMenu || powerWake) && !accessibilityOn) {
                Note(stringResource(R.string.bump_power_needs_accessibility))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the look ---------------------------------------------------------------------------
            Heading(stringResource(R.string.bump_look_title), stringResource(R.string.bump_look_desc))
            Segments {
                Card(shape = segmentShape(0, 3)) {
                    SliderControl(
                        label = stringResource(R.string.bump_depth),
                        value = depth,
                        valueRange = ButtonBumpStore.MIN_DEPTH..ButtonBumpStore.MAX_DEPTH,
                        valueDisplay = stringResource(R.string.bump_value_dp, depth.roundToInt()),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = {
                            depth = it
                            pressPreview(pressed, withSlider = false)
                        },
                        onValueChangeFinished = { store.setDepthDp(depth) },
                    )
                    Swatches(
                        label = stringResource(R.string.bump_color),
                        colours = ButtonBumpStore.BODY_COLORS,
                        chosen = color,
                        onPick = {
                            color = it
                            store.setColor(it)
                            pressPreview(pressed)
                        },
                    )
                    Swatches(
                        label = stringResource(R.string.bump_accent),
                        colours = ButtonBumpStore.ACCENT_COLORS,
                        chosen = accent,
                        onPick = {
                            accent = it
                            store.setAccent(it)
                            pressPreview(pressed)
                        },
                    )
                }
                SwitchCard(
                    title = stringResource(R.string.bump_show_level),
                    description = stringResource(R.string.bump_show_level_desc),
                    checked = showLevel,
                    shape = segmentShape(1, 3),
                    onCheckedChange = {
                        showLevel = it
                        store.setShowLevel(it)
                        pressPreview(pressed)
                    },
                )
                SwitchCard(
                    title = stringResource(R.string.bump_show_percent),
                    description = stringResource(R.string.bump_show_percent_desc),
                    checked = showPercent,
                    shape = segmentShape(2, 3),
                    onCheckedChange = {
                        showPercent = it
                        store.setShowPercent(it)
                        pressPreview(pressed)
                    },
                )
            }
            if (showSlider) Note(stringResource(R.string.bump_level_with_slider))

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the feel -----------------------------------------------------------------------------
            Heading(stringResource(R.string.bump_feel_title), stringResource(R.string.bump_feel_desc))
            Card(shape = segmentShape(0, 1)) {
                Text(
                    text = stringResource(R.string.bump_haptic),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp).selectableGroup(),
                ) {
                    ButtonBumpStore.HAPTICS.forEach { option ->
                        ChoiceChip(
                            label = stringResource(hapticLabel(option)),
                            selected = haptic == option,
                            onClick = {
                                haptic = option
                                store.setHaptic(option)
                            },
                        )
                    }
                }
                val decimals = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
                SliderControl(
                    label = stringResource(R.string.bump_hold),
                    value = holdMs.toFloat(),
                    valueRange = ButtonBumpStore.MIN_HOLD_MS.toFloat()..ButtonBumpStore.MAX_HOLD_MS.toFloat(),
                    valueDisplay = stringResource(R.string.bump_hold_value, decimals.format(holdMs / 1000f)),
                    borderColor = MaterialTheme.colorScheme.primary,
                    step = 100f,
                    onValueChange = { holdMs = it.roundToInt() },
                    onValueChangeFinished = {
                        store.setHoldMs(holdMs)
                        pressPreview(pressed)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SliderControl(
                    label = stringResource(R.string.bump_speed),
                    value = speed,
                    valueRange = ButtonBumpStore.MIN_SPEED..ButtonBumpStore.MAX_SPEED,
                    valueDisplay = stringResource(R.string.bump_speed_value, decimals.format(speed)),
                    borderColor = MaterialTheme.colorScheme.primary,
                    step = 0.1f,
                    onValueChange = { speed = it },
                    onValueChangeFinished = {
                        store.setSpeed(speed)
                        pressPreview(pressed)
                    },
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ---- the preview ---------------------------------------------------------------------------------

/**
 * A phone, upright, with all three buttons on its sides where the user has put them, and the same
 * three to press under it. A press plays the dent at that button and, for the volume buttons with
 * the slider on, the Quick panel growing out of it: both the real views, scaled to the drawing.
 */
@Composable
private fun BumpPreview(
    settingsFor: (Button) -> ButtonBumpStore.Settings,
    pressed: Button,
    placing: Button,
    pressCount: Int,
    level: Float,
    slider: Boolean,
    holdMs: Int,
    preference: SharedPref,
    onPress: (Button) -> Unit,
    modifier: Modifier,
    fillHeight: Boolean,
) {
    val colours = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(280.dp)),
        shape = RoundedCornerShape(28.dp),
        color = colours.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                PhoneWithBump(
                    settingsFor = settingsFor,
                    pressed = pressed,
                    placing = placing,
                    pressCount = pressCount,
                    level = level,
                    slider = slider,
                    holdMs = holdMs,
                    preference = preference,
                    onPress = onPress,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                PressButton(Icons.Filled.Remove, stringResource(R.string.bump_preview_down)) { onPress(Button.VOLUME_DOWN) }
                PressButton(Icons.Filled.Add, stringResource(R.string.bump_preview_up)) { onPress(Button.VOLUME_UP) }
                PressButton(Icons.Filled.PowerSettingsNew, stringResource(R.string.bump_button_power)) { onPress(Button.POWER) }
            }
        }
    }
}

@Composable
private fun PressButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun PhoneWithBump(
    settingsFor: (Button) -> ButtonBumpStore.Settings,
    pressed: Button,
    placing: Button,
    pressCount: Int,
    level: Float,
    slider: Boolean,
    holdMs: Int,
    preference: SharedPref,
    onPress: (Button) -> Unit,
) {
    val latestSettingsFor by rememberUpdatedState(settingsFor)
    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        // The phone: a frame, and the screen inside it with a wallpaper the press can be seen on.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(PHONE_ASPECT)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF16161A))
                .padding(4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFA9A49E), Color(0xFFE2A55A), Color(0xFFF3961E))
                    )
                ),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val screenW = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
                val screenH = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
                // The real press on a real phone, in proportion: 411dp is a typical phone's width.
                val scale = (maxWidth.value / REAL_PHONE_WIDTH_DP).coerceIn(0.1f, 1f)

                fun scaled(settings: ButtonBumpStore.Settings) = settings.copy(
                    lengthDp = settings.lengthDp * scale,
                    depthDp = (settings.depthDp * scale).coerceAtLeast(4f),
                    showPercent = false,
                )

                fun placementOf(settings: ButtonBumpStore.Settings) = ButtonBump.placement(
                    left = settings.left,
                    along = settings.position,
                    turns = 0,
                    screenWidth = screenW,
                    screenHeight = screenH,
                    span = ButtonBump.windowSpan(settings.style, settings.lengthDp * density.density, density.density),
                    reach = ButtonBump.windowReach(settings.style, settings.depthDp * density.density, density.density, false),
                )

                val dentSettings = scaled(settingsFor(pressed))
                val dentPlacement = placementOf(dentSettings)
                val dent = remember { mutableStateOf<ButtonBumpView?>(null) }

                // The slider's window: against the pressed button's edge, centred on the button and
                // kept on the glass, as the real one is. Its collapsed shape is the dent itself.
                val quick = preference.slider
                val pressedPlace = settingsFor(pressed)
                val sliderLength = (quick.getLengthDp() * scale * density.density).coerceAtMost(screenH * 0.86f)
                val sliderThickness = (quick.getThicknessDp() * scale * density.density).coerceAtLeast(6f)
                val buttonCentre = pressedPlace.position * screenH
                val sliderTop = (buttonCentre - sliderLength / 2f).coerceIn(0f, (screenH - sliderLength).coerceAtLeast(0f))
                val sliderLeft = if (pressedPlace.left) 0f else screenW - sliderThickness
                val dentLength = dentSettings.lengthDp * density.density
                val dentDepth = dentSettings.depthDp * density.density
                val sliderView = remember { mutableStateOf<QuickSliderView?>(null) }

                AndroidView(
                    factory = { ctx ->
                        QuickSliderView(ctx).also {
                            it.visibility = View.INVISIBLE
                            dressPreviewSlider(it, preference, scale)
                            sliderView.value = it
                        }
                    },
                    update = { view ->
                        val onLeft = pressedPlace.left
                        val corner = dentSettings.depthDp / 2f
                        view.setCollapsedAppearance(dentSettings.color, corner, corner, corner, corner)
                        val q = preference.slider
                        if (q.getFollowHandlerShape()) {
                            val pill = q.getThicknessDp() * scale / 2f
                            view.setExpandedCorners(pill, pill, pill, pill)
                            view.setShapes(HandlerShape.ROUNDED, HandlerShape.DEFAULT_FLARE, HandlerShape.ROUNDED, HandlerShape.DEFAULT_FLARE, onLeft)
                        } else {
                            view.setExpandedCorners(
                                q.getCornerTL() * scale, q.getCornerTR() * scale,
                                q.getCornerBL() * scale, q.getCornerBR() * scale,
                            )
                            view.setShapes(q.getShape(), q.getShapeFlare(), HandlerShape.ROUNDED, HandlerShape.DEFAULT_FLARE, onLeft)
                        }
                        view.setDrawnThickness(sliderThickness, onLeft)
                        val cy = buttonCentre - sliderTop
                        if (onLeft) {
                            view.setCollapsedRect(0f, cy - dentLength / 2f, dentDepth, cy + dentLength / 2f)
                        } else {
                            view.setCollapsedRect(sliderThickness - dentDepth, cy - dentLength / 2f, sliderThickness, cy + dentLength / 2f)
                        }
                    },
                    modifier = Modifier
                        .offset { IntOffset(sliderLeft.roundToInt(), sliderTop.roundToInt()) }
                        .size(
                            width = with(density) { sliderThickness.toDp() },
                            height = with(density) { sliderLength.toDp() },
                        ),
                )

                AndroidView(
                    factory = { ctx -> ButtonBumpView(ctx).also { dent.value = it } },
                    update = { it.configure(dentSettings, dentPlacement) },
                    modifier = Modifier
                        .offset { IntOffset(dentPlacement.x, dentPlacement.y) }
                        .size(
                            width = with(density) { dentPlacement.width.toDp() },
                            height = with(density) { dentPlacement.height.toDp() },
                        ),
                )

                LaunchedEffect(pressCount) {
                    val bump = dent.value ?: return@LaunchedEffect
                    val panel = sliderView.value
                    val now = scaled(latestSettingsFor(pressed))
                    bump.configure(now, placementOf(now))
                    if (pressCount == 0) {
                        // Shown once on arrival, so the screen opens on the animation, not a blank phone.
                        delay(350)
                    }
                    val direction = if (pressed == Button.VOLUME_DOWN) -1 else 1
                    bump.press(direction, level, atLimit = pressed != Button.POWER && (level <= 0f || level >= 1f))
                    if (panel == null) return@LaunchedEffect
                    if (!slider || pressed == Button.POWER) {
                        if (panel.visibility == View.VISIBLE) {
                            animate(panel.expansion(), 0f, animationSpec = tween(PANEL_CLOSE_MS)) { v, _ -> panel.setExpansion(v) }
                            panel.visibility = View.INVISIBLE
                        }
                        return@LaunchedEffect
                    }
                    if (panel.visibility != View.VISIBLE || panel.expansion() <= 0f) {
                        panel.setValue(level)
                        panel.setExpansion(0f)
                        panel.visibility = View.VISIBLE
                        // A beat for the dent, so the slider is seen coming out of it.
                        delay(BUTTON_SLIDER_DELAY_MS)
                        panel.setCommitted()
                        animate(0f, 1f, animationSpec = tween(PANEL_OPEN_MS, easing = FastOutSlowInEasing)) { v, _ ->
                            panel.setExpansion(v)
                        }
                    } else {
                        panel.animateValue(level)
                    }
                    delay(holdMs.toLong())
                    animate(panel.expansion(), 0f, animationSpec = tween(PANEL_CLOSE_MS)) { v, _ -> panel.setExpansion(v) }
                    panel.visibility = View.INVISIBLE
                }
            }
        }
        // The three buttons on the phone's sides, where the user has put them: presses too. The one
        // being placed is in the accent.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(PHONE_ASPECT),
        ) {
            val scale = (maxWidth.value / REAL_PHONE_WIDTH_DP).coerceIn(0.1f, 1f)
            Button.entries.forEach { button ->
                val place = settingsFor(button)
                val height = (place.lengthDp * scale).dp.coerceAtLeast(10.dp)
                val centre = maxHeight * place.position.coerceIn(0f, 1f)
                val x: Dp = if (place.left) (-3).dp else maxWidth - 1.dp
                val colour = when {
                    button == placing -> MaterialTheme.colorScheme.primary
                    button == Button.POWER -> Color(0xFF5A5A62)
                    else -> Color(0xFF3A3A40)
                }
                SideButton(x, centre - height / 2, height, colour) { onPress(button) }
            }
        }
    }
}

/**
 * Dresses the preview's slider the way the live panel is dressed — see
 * `OverlayController.openQuickSliderWindow` — from the Quick panel's own settings: its material,
 * its colours, and its fill with every one of its animations.
 */
private fun dressPreviewSlider(view: QuickSliderView, preference: SharedPref, scale: Float) {
    val q = preference.slider
    val theme = preference.getPanelTheme()
    val forced = PanelTheme.panelSurface(theme)
    if (forced != null) {
        val light = PanelTheme.isLight(theme)
        val chosen = q.getFillColor()
        val pale = abs(ColorUtils.calculateLuminance(chosen) - ColorUtils.calculateLuminance(forced.toInt())) < 0.25
        val fill = when {
            !pale -> chosen
            light -> PREVIEW_LIGHT_INK
            else -> android.graphics.Color.WHITE
        }
        view.setColors(forced.toInt(), fill)
        view.setPanelTheme(1f, PanelTheme.hasLitEdge(theme), light = light)
    } else {
        view.setColors(q.getTrackColor(), q.getFillColor())
        view.setPanelTheme(PanelTheme.surfaceAlpha(theme), PanelTheme.hasLitEdge(theme))
    }
    view.setContentMargins(q.getValueMarginDp() * scale, q.getIconMarginDp() * scale)
    view.setPixelStyle(q.getPixelStyle())
    view.setShaderStyle(q.getShaderStyle())
    view.setSurgeStyle(q.getSurgeStyle())
    view.setEffortStyle(q.getEffortStyle())
    view.setGlimmerStyle(q.getGlimmerStyle())
    view.setLevelFeedback(q.getLevelFeedback())
    view.setFillStyle(q.getFillStyle())
    view.setFillColors(q.getEffectiveFillColors())
    // The number is drawn at its own size, which on a phone drawn this small would not fit.
    view.setShowValue(false)
    view.setContentColors(q.getEffectiveValueColor(), q.getEffectiveIconColor())
    view.setIcon(null)
    view.setExpansion(0f)
}

@Composable
private fun SideButton(x: Dp, y: Dp, height: Dp, colour: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width = 4.dp, height = height)
            .clip(RoundedCornerShape(2.dp))
            .background(colour)
            .clickable(onClick = onClick),
    )
}

/** One animation in the gallery: a still of it on a sliver of screen, and its name. */
@Composable
private fun StyleTile(
    style: String,
    selected: Boolean,
    color: Int,
    accent: Int,
    onClick: () -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val border = if (selected) BorderStroke(2.dp, colours.primary) else BorderStroke(1.dp, colours.outlineVariant)
    Column(
        modifier = Modifier
            .width(TILE_WIDTH)
            .clip(RoundedCornerShape(18.dp))
            .border(border, RoundedCornerShape(18.dp))
            .background(if (selected) colours.primaryContainer.copy(alpha = 0.6f) else colours.surfaceVariant.copy(alpha = 0.65f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TILE_STAGE_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFE2A55A), Color(0xFFF3961E)))),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val w = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
                val h = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
                val settings = ButtonBumpStore.Settings(
                    style = style,
                    left = true,
                    position = 0.5f,
                    lengthDp = maxHeight.value * 0.72f,
                    depthDp = 13f,
                    color = color,
                    accent = accent,
                )
                val placement = ButtonBump.placement(true, 0.5f, 0, w, h, span = h, reach = w)
                AndroidView(
                    factory = { ctx -> ButtonBumpView(ctx) },
                    update = {
                        it.configure(settings, placement)
                        it.setStill()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colours.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colours.primary)
                        .padding(2.dp),
                )
            }
        }
        Text(
            text = stringResource(styleLabel(style)),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = colours.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

// ---- the rows ---------------------------------------------------------------------------------------

/** Rows grouped into one card, a hairline of the page between them. See [segmentShape]. */
@Composable
private fun Segments(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

/** A group's heading, in the Actions screen's style, and the line under it. */
@Composable
private fun Heading(title: String, hint: String) {
    Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp)) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A card of settings in the rows' colour. */
@Composable
private fun Card(shape: Shape, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(16.dp),
        content = content,
    )
}

/** A line of guidance under a row, in the accent. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
    )
}

/** The feature's own switch: its icon, what it does, and the switch, the whole row the switch. */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (checked) colours.primaryContainer else colours.surfaceVariant.copy(alpha = 0.65f))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (checked) colours.primary else colours.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (checked) colours.onPrimary else colours.primary, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colours.onSurface)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colours.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A setting that is on or off, as a card in a group. */
@Composable
private fun SwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colours.surfaceVariant.copy(alpha = 0.65f))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colours.onSurface)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colours.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row of colours to pick from, the chosen one ringed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(label: String, colours: List<Int>, chosen: Int, onPick: (Int) -> Unit) {
    Text(
        text = label,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 8.dp).selectableGroup(),
    ) {
        colours.forEach { argb ->
            val selected = argb == chosen
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(
                        if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        CircleShape,
                    )
                    .padding(if (selected) 5.dp else 2.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(argb) }),
            )
        }
    }
}

// ---- names ---------------------------------------------------------------------------------------------

private fun styleLabel(style: String): Int = when (style) {
    ButtonBump.STYLE_CAPSULE -> R.string.bump_style_capsule
    ButtonBump.STYLE_LINE -> R.string.bump_style_line
    ButtonBump.STYLE_JELLY -> R.string.bump_style_jelly
    ButtonBump.STYLE_NEON -> R.string.bump_style_neon
    ButtonBump.STYLE_AURORA -> R.string.bump_style_aurora
    ButtonBump.STYLE_RIPPLE -> R.string.bump_style_ripple
    ButtonBump.STYLE_SPARKLE -> R.string.bump_style_sparkle
    ButtonBump.STYLE_LAVA -> R.string.bump_style_lava
    ButtonBump.STYLE_GLASS -> R.string.bump_style_glass
    ButtonBump.STYLE_SPECTRUM -> R.string.bump_style_spectrum
    ButtonBump.STYLE_HEARTBEAT -> R.string.bump_style_heartbeat
    ButtonBump.STYLE_ELASTIC -> R.string.bump_style_elastic
    else -> R.string.bump_style_liquid
}

private fun hapticLabel(haptic: String): Int = when (haptic) {
    ButtonBumpStore.HAPTIC_OFF -> R.string.bump_haptic_off
    ButtonBumpStore.HAPTIC_STRONG -> R.string.bump_haptic_strong
    else -> R.string.bump_haptic_light
}

private fun buttonLabel(button: Button): Int = when (button) {
    Button.VOLUME_UP -> R.string.bump_preview_up
    Button.VOLUME_DOWN -> R.string.bump_preview_down
    Button.POWER -> R.string.bump_button_power
}

/** Width over height of the drawn phone, a modern one's. */
private const val PHONE_ASPECT = 0.47f

/** A typical phone's width, which the press's dp are measured against for the preview. */
private const val REAL_PHONE_WIDTH_DP = 411f

/** How far one press of the preview's volume buttons moves its level. */
private const val PREVIEW_STEP = 1f / 15f

/** The least time between two presses on the glass while a place is being dragged. */
private const val GLASS_PREVIEW_GAP_MS = 140L

/** Mirrors the controller's beat between the dent and the slider, and the panel's open and close. */
private const val BUTTON_SLIDER_DELAY_MS = 90L
private const val PANEL_OPEN_MS = 220
private const val PANEL_CLOSE_MS = 260

/** Mirrors `OverlayController.PANEL_LIGHT_INK`. */
private const val PREVIEW_LIGHT_INK = 0xFF15161A.toInt()

private val TILE_WIDTH = 92.dp
private val TILE_STAGE_HEIGHT = 92.dp
