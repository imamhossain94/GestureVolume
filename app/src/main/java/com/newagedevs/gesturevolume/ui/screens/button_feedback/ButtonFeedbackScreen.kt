package com.newagedevs.gesturevolume.ui.screens.button_feedback

import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.ButtonBumpStore
import com.newagedevs.gesturevolume.service.ButtonBumpOverlay
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.ChoiceChip
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.PreviewSettingsLayout
import com.newagedevs.gesturevolume.ui.motion.Button
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import com.newagedevs.gesturevolume.ui.motion.TextButton
import com.newagedevs.gesturevolume.ui.screens.handler_action.segmentShape
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.view.ButtonBumpView
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ButtonBump
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import kotlinx.coroutines.delay
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The button animation: the edge of the screen bulging out beside the volume buttons as one is
 * pressed, with the volume inside the bulge, in place of the system's own slider.
 *
 * At the top, a phone with the bump on it, its buttons and the two below it to press; under it, the
 * styles — four that come with the app and the Pro ones, every one of which plays in the preview
 * when tapped, so a Pro style is seen before it is bought — then where the buttons are, how it
 * looks and how it feels. Every change is written as it is made: the overlay reads the settings at
 * each press, so the next press of a real button already shows it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ButtonFeedbackScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val store = viewModel.preference.bump
    val pro = state.isProActivated

    var enabled by remember { mutableStateOf(store.isEnabled()) }
    var style by remember { mutableStateOf(store.getStyle()) }
    var side by remember { mutableStateOf(store.getSide()) }
    var position by remember { mutableFloatStateOf(store.getPosition()) }
    var length by remember { mutableFloatStateOf(store.getLengthDp()) }
    var depth by remember { mutableFloatStateOf(store.getDepthDp()) }
    var color by remember { mutableIntStateOf(store.getColor()) }
    var accent by remember { mutableIntStateOf(store.getAccent()) }
    var showLevel by remember { mutableStateOf(store.getShowLevel()) }
    var showPercent by remember { mutableStateOf(store.getShowPercent()) }
    var haptic by remember { mutableStateOf(store.getHaptic()) }
    var holdMs by remember { mutableIntStateOf(store.getHoldMs()) }
    var speed by remember { mutableFloatStateOf(store.getSpeed()) }
    var accessibilityOn by remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }

    /** The style the preview plays: the one chosen, or a Pro one being tried. */
    var previewStyle by remember { mutableStateOf(ButtonBump.effectiveStyle(style, pro)) }
    /** A Pro style tapped without Pro: offered, not chosen. */
    var offered by remember { mutableStateOf<String?>(null) }

    // Back from the accessibility settings, or the upgrade: look again.
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

    val previewSettings = ButtonBumpStore.Settings(
        style = previewStyle,
        left = side == ButtonBumpStore.SIDE_LEFT,
        position = position,
        lengthDp = length,
        depthDp = depth,
        color = color,
        accent = accent,
        showLevel = showLevel,
        showPercent = showPercent,
        haptic = haptic,
        holdMs = holdMs,
        speed = speed,
    )

    // The bump on the glass for the "Show on my phone" button, when the bar's own controller is
    // not running to put it there. Taken away with the screen.
    val localOverlay = remember { mutableStateOf<ButtonBumpOverlay?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            localOverlay.value?.destroy()
            localOverlay.value = null
        }
    }
    fun showOnPhone() {
        val controller = OverlayRuntime.activeController
        if (controller != null) {
            controller.previewBump(1)
            return
        }
        val a11y = OverlayRuntime.accessibilityService
        val overlay = localOverlay.value ?: when {
            a11y != null -> ButtonBumpOverlay(a11y, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            Settings.canDrawOverlays(context) -> ButtonBumpOverlay(context, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            else -> null
        }?.also { localOverlay.value = it } ?: return
        overlay.press(store.settings(pro), 1, 0.6f, atLimit = false)
    }

    // Presses for the preview: a counter so the same direction twice still plays twice.
    var pressCount by remember { mutableIntStateOf(0) }
    var pressDirection by remember { mutableIntStateOf(1) }
    var previewLevel by remember { mutableFloatStateOf(0.45f) }
    fun pressPreview(direction: Int) {
        pressDirection = direction
        previewLevel = (previewLevel + direction / 15f).coerceIn(0f, 1f)
        pressCount++
    }

    offered?.let { id ->
        AlertDialog(
            onDismissRequest = { offered = null },
            title = { Text(stringResource(R.string.bump_pro_title)) },
            text = { Text(stringResource(R.string.bump_pro_message, stringResource(styleLabel(id)))) },
            confirmButton = {
                Button(onClick = {
                    offered = null
                    onOpenUpgrade()
                }) { Text(stringResource(R.string.bump_pro_upgrade)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    offered = null
                    previewStyle = ButtonBump.effectiveStyle(style, pro)
                }) { Text(stringResource(R.string.bump_pro_not_now)) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
        )
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
                    settings = previewSettings,
                    pressCount = pressCount,
                    pressDirection = pressDirection,
                    level = previewLevel,
                    onPress = ::pressPreview,
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
                        // The keys are asked for only while something wants them.
                        OverlayRuntime.accessibilityService?.applyEventSubscription()
                        if (it) pressPreview(1)
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

            // ---- the styles ---------------------------------------------------------------------
            Heading(stringResource(R.string.bump_style_title), stringResource(R.string.bump_style_desc))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                items(ButtonBump.STYLES, key = { it }) { id ->
                    StyleTile(
                        style = id,
                        selected = id == ButtonBump.effectiveStyle(style, pro),
                        trying = id == previewStyle && id != ButtonBump.effectiveStyle(style, pro),
                        locked = ButtonBump.isPremium(id) && !pro,
                        color = color,
                        accent = accent,
                        onClick = {
                            previewStyle = id
                            pressPreview(1)
                            if (ButtonBump.isPremium(id) && !pro) {
                                offered = id
                            } else {
                                style = id
                                store.setStyle(id)
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- where the buttons are ------------------------------------------------------------
            Heading(stringResource(R.string.bump_place_title), stringResource(R.string.bump_place_desc))
            Card(shape = segmentShape(0, 1)) {
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
                    ChoiceChip(
                        label = stringResource(R.string.bump_side_left),
                        selected = side == ButtonBumpStore.SIDE_LEFT,
                        onClick = {
                            side = ButtonBumpStore.SIDE_LEFT
                            store.setSide(side)
                            pressPreview(1)
                        },
                    )
                    ChoiceChip(
                        label = stringResource(R.string.bump_side_right),
                        selected = side == ButtonBumpStore.SIDE_RIGHT,
                        onClick = {
                            side = ButtonBumpStore.SIDE_RIGHT
                            store.setSide(side)
                            pressPreview(1)
                        },
                    )
                }
                SliderControl(
                    label = stringResource(R.string.bump_position),
                    value = position * 100f,
                    valueRange = 0f..100f,
                    valueDisplay = stringResource(R.string.bump_value_percent, (position * 100f).roundToInt()),
                    borderColor = MaterialTheme.colorScheme.primary,
                    onValueChange = {
                        position = it / 100f
                        pressPreview(pressDirection)
                    },
                    onValueChangeFinished = { store.setPosition(position) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                SliderControl(
                    label = stringResource(R.string.bump_length),
                    value = length,
                    valueRange = ButtonBumpStore.MIN_LENGTH..ButtonBumpStore.MAX_LENGTH,
                    valueDisplay = stringResource(R.string.bump_value_dp, length.roundToInt()),
                    borderColor = MaterialTheme.colorScheme.primary,
                    step = 5f,
                    onValueChange = {
                        length = it
                        pressPreview(pressDirection)
                    },
                    onValueChangeFinished = { store.setLengthDp(length) },
                )
                OutlinedButton(
                    onClick = { showOnPhone() },
                    modifier = Modifier.padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.bump_try))
                }
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
                            pressPreview(pressDirection)
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
                            pressPreview(pressDirection)
                        },
                    )
                    Swatches(
                        label = stringResource(R.string.bump_accent),
                        colours = ButtonBumpStore.ACCENT_COLORS,
                        chosen = accent,
                        onPick = {
                            accent = it
                            store.setAccent(it)
                            pressPreview(pressDirection)
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
                        pressPreview(pressDirection)
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
                        pressPreview(pressDirection)
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
                    onValueChangeFinished = { store.setHoldMs(holdMs) },
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
                        pressPreview(pressDirection)
                    },
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ---- the preview ---------------------------------------------------------------------------------

/**
 * A phone, upright, with the bump on it where the buttons are, and the buttons to press: the two on
 * its side and the two under it. Played from [pressCount], which goes up once for every press.
 *
 * The real view, the one on the glass, scaled to the drawing: a bump [ButtonBumpStore.Settings.lengthDp]
 * long on a phone 411dp wide is drawn as long, in proportion, on this one.
 */
@Composable
private fun BumpPreview(
    settings: ButtonBumpStore.Settings,
    pressCount: Int,
    pressDirection: Int,
    level: Float,
    onPress: (Int) -> Unit,
    modifier: Modifier,
    fillHeight: Boolean,
) {
    val colours = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(260.dp)),
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
                    settings = settings,
                    pressCount = pressCount,
                    pressDirection = pressDirection,
                    level = level,
                    onPress = onPress,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                PressButton(Icons.Filled.Remove, stringResource(R.string.bump_preview_down)) { onPress(-1) }
                PressButton(Icons.Filled.Add, stringResource(R.string.bump_preview_up)) { onPress(1) }
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
    settings: ButtonBumpStore.Settings,
    pressCount: Int,
    pressDirection: Int,
    level: Float,
    onPress: (Int) -> Unit,
) {
    val buttonColour = Color(0xFF3A3A40)
    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        // The phone: a frame, and the screen inside it with a wallpaper the bump can be seen on.
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
                // The real bump on a real phone, in proportion: 411dp is a typical phone's width.
                val scale = (maxWidth.value / REAL_PHONE_WIDTH_DP).coerceIn(0.1f, 1f)
                val scaled = settings.copy(
                    lengthDp = settings.lengthDp * scale,
                    depthDp = (settings.depthDp * scale).coerceAtLeast(4f),
                    showPercent = false,
                )
                val placement = ButtonBump.placement(
                    left = scaled.left,
                    along = scaled.position,
                    turns = 0,
                    screenWidth = screenW,
                    screenHeight = screenH,
                    span = ButtonBump.windowSpan(scaled.style, scaled.lengthDp * density.density, density.density),
                    reach = ButtonBump.windowReach(scaled.style, scaled.depthDp * density.density, density.density, false),
                )
                val view = remember { mutableStateOf<ButtonBumpView?>(null) }
                AndroidView(
                    factory = { ctx -> ButtonBumpView(ctx).also { view.value = it } },
                    update = { it.configure(scaled, placement) },
                    modifier = Modifier
                        .offset { IntOffset(placement.x, placement.y) }
                        .size(
                            width = with(density) { placement.width.toDp() },
                            height = with(density) { placement.height.toDp() },
                        ),
                )
                LaunchedEffect(pressCount) {
                    if (pressCount == 0) {
                        // Shown once on arrival, so the screen opens on the animation, not a blank phone.
                        delay(350)
                        view.value?.press(1, level)
                    } else {
                        view.value?.press(pressDirection, level, atLimit = level <= 0f || level >= 1f)
                    }
                }
            }
        }
        // The two buttons on the phone's side, where the bump says they are: presses too.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(PHONE_ASPECT),
        ) {
            val buttonH = maxHeight * 0.075f
            val gap = 3.dp
            val centre = maxHeight * settings.position.coerceIn(0.1f, 0.9f)
            val x: Dp = if (settings.left) (-3).dp else maxWidth - 1.dp
            SideButton(x, centre - buttonH - gap / 2, buttonH, buttonColour) { onPress(1) }
            SideButton(x, centre + gap / 2, buttonH, buttonColour) { onPress(-1) }
        }
    }
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

/** One style in the gallery: a still of it on a sliver of screen, its name, and Pro where it is. */
@Composable
private fun StyleTile(
    style: String,
    selected: Boolean,
    trying: Boolean,
    locked: Boolean,
    color: Int,
    accent: Int,
    onClick: () -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val border = when {
        selected -> BorderStroke(2.dp, colours.primary)
        trying -> BorderStroke(2.dp, colours.tertiary)
        else -> BorderStroke(1.dp, colours.outlineVariant)
    }
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
            if (ButtonBump.isPremium(style)) {
                Text(
                    text = stringResource(R.string.bump_pro_badge),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (locked) Color(0xFFFFD60A) else Color(0xFFFFFFFF))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
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

/** Width over height of the drawn phone, a modern one's. */
private const val PHONE_ASPECT = 0.47f

/** A typical phone's width, which the bump's dp are measured against for the preview. */
private const val REAL_PHONE_WIDTH_DP = 411f

private val TILE_WIDTH = 92.dp
private val TILE_STAGE_HEIGHT = 92.dp
