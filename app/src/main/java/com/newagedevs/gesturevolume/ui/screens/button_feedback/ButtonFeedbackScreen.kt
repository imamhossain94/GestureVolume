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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore.Button
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.service.ButtonBumpOverlay
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.ChoiceChip
import com.newagedevs.gesturevolume.ui.components.DeviceArt
import com.newagedevs.gesturevolume.ui.components.InfoNote
import com.newagedevs.gesturevolume.ui.screens.quick_slider.sliderWithBumpLabel
import com.newagedevs.gesturevolume.ui.screens.quick_slider.sliderWithBumpHint
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.PreviewSettingsLayout
import com.newagedevs.gesturevolume.ui.components.PreviewStage
import com.newagedevs.gesturevolume.utils.StepSound
import android.media.AudioAttributes
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
 * The button animation: the glass answers at the very button that was pressed — volume up where
 * volume up is, volume down where it is, the power button where that is.
 *
 * It is its own thing, apart from the Quick slider, with its own level. Whether the Quick slider
 * also comes up for a press, or pauses while the animation is on, is the user's choice, offered
 * here as well as on the slider's own screen.
 *
 * At the top, the phone the Quick slider's screen uses, playing the animation exactly as the glass
 * will; under it, what the Quick slider does, the animations — every one of them free — then where
 * each button is (lined up on the real glass while it is being set), the power button, the look and
 * the feel. Every change is written as it is made: the overlay reads the settings at each press, so
 * the next press of a real button already shows it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ButtonFeedbackScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onOpenQuickSlider: () -> Unit = {},
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val store = viewModel.preference.bump

    var enabled by remember { mutableStateOf(store.isEnabled()) }
    var style by remember { mutableStateOf(store.getStyle()) }
    var sweep by remember { mutableFloatStateOf(store.getSweep()) }
    var sound by remember { mutableStateOf(store.getSound()) }
    // What the Quick slider does about a press meanwhile: the slider's own setting, chosen here too.
    val sliderStore = viewModel.preference.slider
    var withBump by remember { mutableStateOf(sliderStore.getWithButtonAnimation()) }
    var sliderKeysOff by remember { mutableStateOf(sliderStore.getVolumeKeyMode() == QuickSliderStore.VOLUME_KEYS_OFF) }
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
                withBump = sliderStore.getWithButtonAnimation()
                sliderKeysOff = sliderStore.getVolumeKeyMode() == QuickSliderStore.VOLUME_KEYS_OFF
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
        return ButtonBumpStore.Settings(
            style = style,
            left = place.left,
            position = place.position,
            lengthDp = place.lengthDp,
            depthDp = depth,
            color = color,
            accent = accent,
            showLevel = showLevel && !power,
            showPercent = showPercent && !power,
            haptic = haptic,
            holdMs = holdMs,
            speed = speed,
            sweep = sweep,
        )
    }

    // Each animation's own sound, played as its tile is picked.
    val tapSound = remember { StepSound(context) }
    DisposableEffect(Unit) { onDispose { tapSound.release() } }
    fun playSound(id: String) {
        if (!sound) return
        tapSound.prepare(AudioAttributes.USAGE_MEDIA, ButtonBump.sound(id))
        tapSound.playOnce()
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
    fun pressPreview(button: Button) {
        pressed = button
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
                BumpStage(
                    settingsFor = ::settingsFor,
                    pressed = pressed,
                    replay = pressCount,
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

            // ---- the Quick slider ---------------------------------------------------------------------
            // A separate thing, so the user says how the two share a press: both, or the animation alone.
            Heading(stringResource(R.string.quick_slider_title), stringResource(R.string.bump_slider_desc))
            Card(shape = segmentShape(0, 1)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.selectableGroup(),
                ) {
                    QuickSliderStore.ALL_WITH_BUMP.forEach { option ->
                        ChoiceChip(
                            label = stringResource(sliderWithBumpLabel(option)),
                            selected = withBump == option,
                            onClick = {
                                withBump = option
                                sliderStore.setWithButtonAnimation(option)
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(sliderWithBumpHint(withBump)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Both, but the slider's own setting keeps it closed: say so, and where to change it.
            if (withBump == QuickSliderStore.WITH_BUMP_BOTH && sliderKeysOff) {
                InfoNote(
                    text = stringResource(R.string.bump_slider_keys_off),
                    onClick = onOpenQuickSlider,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the animations -------------------------------------------------------------------
            Heading(stringResource(R.string.bump_style_title), stringResource(R.string.bump_style_desc))
            // Two rows, so more of the animations are in sight at once.
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.height(GALLERY_HEIGHT).selectableGroup(),
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
                            playSound(id)
                            pressPreview(if (pressed == Button.POWER) Button.VOLUME_UP else pressed)
                        },
                    )
                }
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
                                pressPreview(button)
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
                                pressPreview(placing)
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
                        pressPreview(placing)
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
                        pressPreview(placing)
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
                        pressPreview(placing)
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
                            pressPreview(pressed)
                        },
                        onValueChangeFinished = { store.setDepthDp(depth) },
                    )
                    // How the ends curve into the edge: the style's own until it is moved.
                    val shownSweep = if (sweep >= 0f) sweep else ButtonBump.spec(style).shoulder
                    SliderControl(
                        label = stringResource(R.string.end_sweep),
                        value = shownSweep * 100f,
                        valueRange = 5f..100f,
                        valueDisplay = stringResource(R.string.bump_value_percent, (shownSweep * 100f).roundToInt()),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = {
                            sweep = it / 100f
                            pressPreview(pressed)
                        },
                        onValueChangeFinished = { store.setSweep(sweep) },
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

            Spacer(modifier = Modifier.height(24.dp))

            // ---- the sound ----------------------------------------------------------------------------
            Heading(stringResource(R.string.bump_sound_title), stringResource(R.string.bump_sound_desc))
            Segments {
                SwitchCard(
                    title = stringResource(R.string.bump_sound),
                    description = stringResource(R.string.bump_sound_detail),
                    checked = sound,
                    shape = segmentShape(0, 1),
                    onCheckedChange = {
                        sound = it
                        store.setSound(it)
                        playSound(style)
                    },
                )
            }

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
 * The animation on the phone the Quick slider's screen shows its panel on, playing by itself: a press
 * now and then — sometimes two or three in a row, as a thumb plays the buttons, which is what the
 * characters' moods answer to — so what is chosen is seen as it will be, with nothing to press. It
 * is drawn over the whole glass, nothing cut off at the edge of a box. A change replays it at once.
 */
@Composable
private fun BumpStage(
    settingsFor: (Button) -> ButtonBumpStore.Settings,
    pressed: Button,
    replay: Int,
    modifier: Modifier,
    fillHeight: Boolean,
) {
    val latest by rememberUpdatedState(settingsFor)
    PreviewStage(modifier = modifier, fillHeight = fillHeight) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val w = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
            val h = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
            val view = remember { mutableStateOf<ButtonBumpView?>(null) }

            // In the middle of the glass that shows, on the side the buttons are on.
            fun place(button: Button): Pair<ButtonBumpStore.Settings, ButtonBump.Placement> {
                val s = latest(button).copy(position = 0.5f, showPercent = false)
                val d = density.density
                val p = ButtonBump.placement(
                    left = s.left,
                    along = 0.5f,
                    turns = 0,
                    screenWidth = w,
                    screenHeight = h,
                    span = ButtonBump.windowSpan(s.style, s.lengthDp * d, d, s.depthDp * d),
                    reach = ButtonBump.windowReach(s.style, s.depthDp * d, d, false),
                )
                return s to p
            }

            AndroidView(
                factory = { ctx -> ButtonBumpView(ctx).also { view.value = it } },
                update = { v ->
                    val (s, p) = place(Button.VOLUME_UP)
                    v.configure(s, p)
                    v.setOrigin(p.x, p.y)
                },
                modifier = Modifier.fillMaxSize(),
            )
            LaunchedEffect(replay, w, h) {
                val v = view.value ?: return@LaunchedEffect
                var level = 0.45f
                var n = 0
                delay(180)
                while (true) {
                    val run = when (n % 6) {
                        2 -> 2
                        4 -> 3
                        else -> 1
                    }
                    repeat(run) {
                        val button = when {
                            n == 0 && pressed != Button.POWER -> pressed
                            n % 5 == 3 -> Button.VOLUME_DOWN
                            else -> Button.VOLUME_UP
                        }
                        val (s, p) = place(button)
                        v.configure(s, p)
                        v.setOrigin(p.x, p.y)
                        val down = button == Button.VOLUME_DOWN
                        level = (level + if (down) -0.07f else 0.07f).coerceIn(0.05f, 0.95f)
                        v.press(if (down) -1 else 1, level)
                        delay(PRESS_GAP_MS)
                    }
                    n++
                    delay(LOOP_GAP_MS)
                }
            }
        }
    }
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
                // The wallpaper the Quick slider's tiles are painted on, so the two galleries match.
                .background(Brush.linearGradient(listOf(DeviceArt.WallTop, DeviceArt.WallBottom))),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val w = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
                val h = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
                val settings = ButtonBumpStore.Settings(
                    style = style,
                    left = true,
                    position = 0.5f,
                    // Water's drop shorter, so the ring it sends out has room to be round.
                    lengthDp = maxHeight.value * if (style == ButtonBump.STYLE_WATER) 0.4f else 0.72f,
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
    ButtonBump.STYLE_WATER -> R.string.bump_style_water
    ButtonBump.STYLE_BLOSSOM -> R.string.bump_style_blossom
    ButtonBump.STYLE_FIREWORKS -> R.string.bump_style_fireworks
    ButtonBump.STYLE_SOLAR -> R.string.bump_style_solar
    ButtonBump.STYLE_BUBBLES -> R.string.bump_style_bubbles
    ButtonBump.STYLE_VOLCANO -> R.string.bump_style_volcano
    ButtonBump.STYLE_WIND -> R.string.bump_style_wind
    ButtonBump.STYLE_NINJA -> R.string.bump_style_ninja
    ButtonBump.STYLE_BEES -> R.string.bump_style_bees
    ButtonBump.STYLE_ROCKET -> R.string.bump_style_rocket
    ButtonBump.STYLE_SPRAY -> R.string.bump_style_spray
    ButtonBump.STYLE_CRACK -> R.string.bump_style_crack
    ButtonBump.STYLE_SMOKE -> R.string.bump_style_smoke
    ButtonBump.STYLE_CAT -> R.string.bump_style_cat
    ButtonBump.STYLE_FISH -> R.string.bump_style_fish
    ButtonBump.STYLE_MONKEY -> R.string.bump_style_monkey
    ButtonBump.STYLE_FROG -> R.string.bump_style_frog
    ButtonBump.STYLE_SPIDER -> R.string.bump_style_spider
    ButtonBump.STYLE_WOODPECKER -> R.string.bump_style_woodpecker
    ButtonBump.STYLE_UFO -> R.string.bump_style_ufo
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



/** How far one press of the preview's volume buttons moves its level. */
private const val PREVIEW_STEP = 1f / 15f

/** The preview's rhythm: between presses in a run, and between runs. */
private const val PRESS_GAP_MS = 260L
private const val LOOP_GAP_MS = 1900L

/** The least time between two presses on the glass while a place is being dragged. */
private const val GLASS_PREVIEW_GAP_MS = 140L



private val TILE_WIDTH = 92.dp
private val TILE_STAGE_HEIGHT = 92.dp

/** Two rows of tiles: each a stage, its name and its padding, and the gap between the rows. */
private val GALLERY_HEIGHT = 274.dp
