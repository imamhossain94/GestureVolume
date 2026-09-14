package com.newagedevs.gesturevolume.ui.screens.quick_slider

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.ui.components.PREVIEW_SUBJECT_MAX_HEIGHT
import com.newagedevs.gesturevolume.ui.components.PanelAnimationSelector
import com.newagedevs.gesturevolume.ui.components.PanelThemeSelector
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.PreviewSettingsLayout
import com.newagedevs.gesturevolume.ui.components.PreviewStage
import com.newagedevs.gesturevolume.ui.components.SliderFillSelector
import com.newagedevs.gesturevolume.ui.components.panelAnimationLabel
import com.newagedevs.gesturevolume.ui.components.panelThemeLabel
import com.newagedevs.gesturevolume.ui.components.sliderFillLabel
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.AppearanceSection
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.ColorPickerControl
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.IconPickerControl
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.IconPickerDialog
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.ShapeSelector
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.HandlerShape
import com.newagedevs.gesturevolume.utils.PanelTheme
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import com.newagedevs.gesturevolume.utils.QuickSliderIcons
import com.newagedevs.gesturevolume.utils.SliderFill

/** The translated name of a slider target. */
fun sliderTargetLabel(id: String): Int = when (id) {
    QuickSliderStore.TARGET_ADAPTIVE -> R.string.slider_target_adaptive
    QuickSliderStore.TARGET_MEDIA -> R.string.slider_target_media
    QuickSliderStore.TARGET_RING -> R.string.slider_target_ring
    QuickSliderStore.TARGET_ALARM -> R.string.slider_target_alarm
    else -> R.string.slider_target_brightness
}

fun sliderOpenerLabel(id: String): Int = when (id) {
    QuickSliderStore.OPEN_OUT -> R.string.slider_open_out
    QuickSliderStore.OPEN_BOTH -> R.string.slider_open_both
    QuickSliderStore.OPEN_OFF -> R.string.slider_open_off
    else -> R.string.slider_open_in
}

fun sliderVolumeKeysLabel(id: String): Int = when (id) {
    QuickSliderStore.VOLUME_KEYS_OFF -> R.string.slider_volume_keys_off
    QuickSliderStore.VOLUME_KEYS_INSTANT -> R.string.slider_volume_keys_instant
    else -> R.string.slider_volume_keys_follow
}

private fun sliderVolumeKeysHint(id: String): Int = when (id) {
    QuickSliderStore.VOLUME_KEYS_OFF -> R.string.slider_volume_keys_off_hint
    QuickSliderStore.VOLUME_KEYS_INSTANT -> R.string.slider_volume_keys_instant_hint
    else -> R.string.slider_volume_keys_follow_hint
}

fun sliderHapticLabel(id: String): Int = when (id) {
    QuickSliderStore.HAPTIC_OFF -> R.string.slider_haptic_off
    QuickSliderStore.HAPTIC_MEDIUM -> R.string.slider_haptic_medium
    QuickSliderStore.HAPTIC_STRONG -> R.string.slider_haptic_strong
    else -> R.string.slider_haptic_light
}

fun sliderSoundLabel(id: String): Int = when (id) {
    QuickSliderStore.SOUND_CLICK -> R.string.slider_sound_click
    QuickSliderStore.SOUND_ADAPTIVE -> R.string.slider_sound_adaptive
    else -> R.string.slider_sound_off
}

/**
 * Everything about the slider the handler expands into.
 *
 * Written straight through to preferences on every change, with no Apply step, because there is
 * nothing here that can be half-applied: each setting is read fresh the next time the slider
 * opens. That is the opposite of the appearance screen, whose Apply/Discard contract exists
 * because it is editing a bar that is on screen while you edit it.
 *
 * The preview is a real [QuickSliderView], not a drawing of one. Every colour, radius and
 * proportion on this screen changes what a user sees during a gesture that lasts under a second
 * and covers the middle of their screen, which is not enough time to evaluate a change by trying
 * it — so the preview has to be the same code that draws the real thing, or it will eventually
 * lie about it.
 *
 * The controls are in five collapsible groups, in the order every panel screen shares — content,
 * size and shape, colours, animation, behaviour — with the first open: what the panel drives and
 * what it shows is why most people are here, and the rest are a tap away rather than a long scroll
 * past.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSliderScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {},
) {
    val store = viewModel.preference.slider

    // One backdrop per visit; see the note in HandlerAppearanceScreen.
    val backdrop = remember { viewModel.getNextBackground() }

    // Read once: which side the bar is on is settled on the Appearance screen, and this one has
    // no way to change it.
    val handlerOnLeft = remember { viewModel.preference.getHandlerPosition() == "Left" }

    var openWith by remember { mutableStateOf(store.getOpenWith()) }
    var target by remember { mutableStateOf(store.getTarget()) }
    var haptic by remember { mutableStateOf(store.getHaptic()) }
    var stepSound by remember { mutableStateOf(store.getStepSound()) }
    var length by remember { mutableFloatStateOf(store.getLengthDp()) }
    var thickness by remember { mutableFloatStateOf(store.getThicknessDp()) }
    var edgeOffset by remember { mutableFloatStateOf(store.getEdgeOffsetDp()) }
    var followBar by remember { mutableStateOf(store.getFollowHandlerShape()) }
    var cornerTL by remember { mutableFloatStateOf(store.getCornerTL()) }
    var cornerTR by remember { mutableFloatStateOf(store.getCornerTR()) }
    var cornerBL by remember { mutableFloatStateOf(store.getCornerBL()) }
    var cornerBR by remember { mutableFloatStateOf(store.getCornerBR()) }
    var panelShape by remember { mutableStateOf(store.getShape()) }
    // Seeded from what the panel would work out for itself, so the slider opens where the shape
    // already is rather than jumping the moment it is touched.
    var panelFlare by remember {
        mutableFloatStateOf(
            if (store.hasShapeFlare()) {
                store.getShapeFlare()
            } else {
                minOf(
                    viewModel.preference.getHandlerShapeFlare(),
                    QUICK_PANEL_MAX_FLARE,
                ).coerceAtLeast(HandlerShape.MIN_FLARE)
            }
        )
    }

    // The bar's own outline, for the preview to show when the panel is set to follow it.
    val barShape = remember { viewModel.preference.getHandlerShape() }
    val barFlare = remember { viewModel.preference.getHandlerShapeFlare() }
    val barCorners = remember {
        listOf(
            viewModel.preference.getHandlerCornerRadiusTL(),
            viewModel.preference.getHandlerCornerRadiusTR(),
            viewModel.preference.getHandlerCornerRadiusBL(),
            viewModel.preference.getHandlerCornerRadiusBR(),
        )
    }
    val barWidth = remember { viewModel.preference.getHandlerWidthDp() }
    var fillStyle by remember { mutableStateOf(store.getFillStyle()) }
    var fillColorsOn by remember { mutableStateOf(store.getFillColorsEnabled()) }
    var fillColors by remember { mutableStateOf(store.getFillColors().toList()) }
    var panelAnimation by remember { mutableStateOf(viewModel.preference.getPanelAnimation()) }
    var animationSpeed by remember { mutableFloatStateOf(viewModel.preference.getPanelAnimationSpeed()) }
    var replay by remember { mutableIntStateOf(0) }
    var trackColor by remember { mutableStateOf(Color(store.getTrackColor())) }
    var fillColor by remember { mutableStateOf(Color(store.getFillColor())) }
    var contentColorsOn by remember { mutableStateOf(store.getContentColorsEnabled()) }
    var valueColor by remember { mutableStateOf(Color(store.getValueColor())) }
    var iconColor by remember { mutableStateOf(Color(store.getIconColor())) }
    var showValue by remember { mutableStateOf(store.getShowValue()) }
    var showIcon by remember { mutableStateOf(store.getShowIcon()) }
    var valueMargin by remember { mutableFloatStateOf(store.getValueMarginDp()) }
    var iconMargin by remember { mutableFloatStateOf(store.getIconMarginDp()) }
    var autoBrightnessOff by remember { mutableStateOf(store.getDisableAutoBrightness()) }
    var volumeKeys by remember { mutableStateOf(store.getVolumeKeyMode()) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var accessibilityOn by remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }
    var canWriteSettings by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var iconName by remember { mutableStateOf(store.getIconName()) }
    var iconOpensPanel by remember { mutableStateOf(store.getIconOpensVolumePanel()) }
    var showIconPicker by remember { mutableStateOf(false) }
    // Resolved against the target, so Automatic shows the sun the moment brightness is picked.
    val iconRes = remember(iconName, target) { QuickSliderIcons.resolve(context, iconName, target) }

    // Back from a system screen: whatever the user granted there is what to show.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = OverlayRuntime.isAccessibilityEnabled(context)
                canWriteSettings = Settings.System.canWrite(context)
                viewModel.preference.setAppOpenAdPaused(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosure = false
                viewModel.preference.setAcceptedAccessibilityDisclosure(true)
                // Paused, so coming back from the system screen is not taken for a fresh launch.
                viewModel.preference.setAppOpenAdPaused(true)
                try {
                    context.startActivity(OverlayRuntime.accessibilitySettingsIntent())
                } catch (_: Exception) {
                    viewModel.preference.setAppOpenAdPaused(false)
                }
            },
            onDismiss = { showDisclosure = false }
        )
    }
    if (showIconPicker) {
        IconPickerDialog(
            // Automatic is the tile reported as 0, whatever it happens to draw right now.
            selectedIconRes = if (iconName == QuickSliderStore.ICON_AUTO) 0 else iconRes,
            options = QuickSliderIcons.CHOICES,
            automatic = QuickSliderIcons.automatic(target) to R.string.slider_icon_auto,
            onIconSelected = { res ->
                iconName = if (res == 0) QuickSliderStore.ICON_AUTO else QuickSliderIcons.nameOf(context, res)
                store.setIconName(iconName)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
    var panelTheme by remember { mutableStateOf(viewModel.preference.getPanelTheme()) }

    val accent = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_slider_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
        // Preview pinned, controls scrolling beside or beneath it — the shape the Appearance screen
        // uses. A preview that scrolls away with the control that changes it is the old two-screen
        // problem with extra steps.
        PreviewSettingsLayout(
            contentPadding = padding,
            header = {
                Text(
                    text = stringResource(R.string.quick_slider_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            preview = { modifier, fillHeight ->
                SliderPreview(
                    modifier = modifier,
                    fillHeight = fillHeight,
                    backdrop = backdrop,
                    handlerOnLeft = handlerOnLeft,
                    lengthDp = length,
                    thicknessDp = thickness,
                    edgeOffsetDp = edgeOffset,
                    trackColor = trackColor,
                    fillColor = fillColor,
                    showValue = showValue,
                    showIcon = showIcon,
                    iconRes = iconRes,
                    valueMargin = valueMargin,
                    iconMargin = iconMargin,
                    panelTheme = panelTheme,
                    fillStyle = fillStyle,
                    fillColors = if (fillColorsOn) fillColors.toIntArray() else null,
                    valueColor = if (contentColorsOn) valueColor else null,
                    iconColor = if (contentColorsOn) iconColor else null,
                    followBar = followBar,
                    barShape = barShape,
                    barFlare = barFlare,
                    barCorners = barCorners,
                    barWidthDp = barWidth,
                    shape = panelShape,
                    flare = panelFlare,
                    corners = listOf(cornerTL, cornerTR, cornerBL, cornerBR),
                    animation = panelAnimation,
                    animationSpeed = animationSpeed,
                    replay = replay
                )
            },
        ) {
            // ---- content: what it drives, and what it shows on it -------------------------------
            AppearanceSection(
                title = stringResource(R.string.group_content),
                summary = stringResource(sliderTargetLabel(target)),
                initiallyExpanded = true,
            ) {
                Label(stringResource(R.string.slider_target))
                ChipRow(
                    ids = QuickSliderStore.ALL_TARGETS,
                    label = { stringResource(sliderTargetLabel(it)) },
                    selected = { it == target },
                    onClick = { target = it; store.setTarget(it) }
                )
                // "Adaptive" alone does not say what it adapts to, and the answer is the reason to
                // pick it: the call's volume during a call, rather than media going up under it.
                if (target == QuickSliderStore.TARGET_ADAPTIVE) {
                    Text(
                        text = stringResource(R.string.slider_target_adaptive_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // A brightness panel with nothing allowed to change the brightness opens, says so in
                // a line in the middle of the screen, and closes. Said here too, with the way out.
                if (target == QuickSliderStore.TARGET_BRIGHTNESS && !canWriteSettings) {
                    PermissionNote(
                        missing = PermissionNeeds.Permission.WRITE_SETTINGS,
                        onOpenPermissions = onOpenPermissions,
                    )
                }
                Sep()
                SettingSwitchItem(
                    title = stringResource(R.string.slider_show_value),
                    description = stringResource(R.string.slider_show_value_desc),
                    checked = showValue,
                    onCheckedChange = { showValue = it; store.setShowValue(it) }
                )
                Sep()
                SettingSwitchItem(
                    title = stringResource(R.string.slider_show_icon),
                    description = stringResource(R.string.slider_show_icon_desc),
                    checked = showIcon,
                    onCheckedChange = { showIcon = it; store.setShowIcon(it) }
                )
                if (showIcon) {
                    Spacer(modifier = Modifier.height(12.dp))
                    IconPickerControl(
                        label = stringResource(R.string.slider_icon),
                        selectedIconRes = iconRes,
                        borderColor = accent,
                        caption = if (iconName == QuickSliderStore.ICON_AUTO) {
                            stringResource(R.string.slider_icon_auto)
                        } else {
                            null
                        },
                        onClick = { showIconPicker = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- size & shape: its proportions, its outline, and the room inside it -------------
            AppearanceSection(
                title = stringResource(R.string.group_size_shape),
                summary = "${length.toInt()} × ${thickness.toInt()}dp",
            ) {
                SliderControl(
                    label = stringResource(R.string.slider_length),
                    value = length,
                    valueRange = 120f..320f,
                    valueDisplay = "${length.toInt()}dp",
                    borderColor = accent,
                    onValueChange = { length = it; store.setLengthDp(it) }
                )
                Hint(stringResource(R.string.slider_length_desc))
                Sep()
                SliderControl(
                    label = stringResource(R.string.slider_thickness),
                    // Starts where the panel's own floor is. Below that the panel is widened back
                    // out when it is built, so a lower range moved the preview and nothing else.
                    valueRange = QuickSliderStore.MIN_THICKNESS..72f,
                    value = thickness,
                    valueDisplay = "${thickness.toInt()}dp",
                    borderColor = accent,
                    onValueChange = { thickness = it; store.setThicknessDp(it) }
                )
                Sep()
                SliderControl(
                    label = stringResource(R.string.edge_offset),
                    value = edgeOffset,
                    valueRange = 0f..QuickSliderStore.MAX_EDGE_OFFSET,
                    valueDisplay = "${edgeOffset.toInt()}dp",
                    borderColor = accent,
                    onValueChange = { edgeOffset = it; store.setEdgeOffsetDp(it) }
                )
                Hint(stringResource(R.string.slider_edge_offset_desc))
                Sep()
                SettingSwitchItem(
                    title = stringResource(R.string.slider_follow_handler),
                    description = stringResource(R.string.slider_follow_handler_desc),
                    checked = followBar,
                    onCheckedChange = { followBar = it; store.setFollowHandlerShape(it) },
                )
                // The sweep stays yours either way. Matching the bar settles which shape the
                // panel is and where its corners sit; how deep the ends cut in is a number about
                // this panel's own proportions, and a panel four times the bar's width does not
                // want the bar's answer to it.
                if (followBar && barShape == HandlerShape.TAB) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SweepSlider(panelFlare) { panelFlare = it; store.setShapeFlare(it) }
                }
                if (!followBar) {
                    Sep()
                    ShapeSelector(
                        shape = panelShape,
                        onShapeChange = { panelShape = it; store.setShape(it) },
                    )
                    if (panelShape == HandlerShape.TAB) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SweepSlider(panelFlare) { panelFlare = it; store.setShapeFlare(it) }
                    } else {
                        val setCorners = {
                            store.setCorners(cornerTL, cornerTR, cornerBL, cornerBR)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        CornerSlider(stringResource(R.string.top_left), cornerTL) { cornerTL = it; setCorners() }
                        CornerSlider(stringResource(R.string.top_right), cornerTR) { cornerTR = it; setCorners() }
                        CornerSlider(stringResource(R.string.bottom_left), cornerBL) { cornerBL = it; setCorners() }
                        CornerSlider(stringResource(R.string.bottom_right), cornerBR) { cornerBR = it; setCorners() }
                    }
                }
                // How far the number and the icon stand in from the ends: spacing, so here with the
                // rest of the proportions, and each only while the switch that shows it is on.
                if (showValue) {
                    Sep()
                    SliderControl(
                        label = stringResource(R.string.slider_value_margin),
                        value = valueMargin,
                        valueRange = 0f..QuickSliderStore.MAX_CONTENT_PADDING,
                        valueDisplay = "${valueMargin.toInt()}dp",
                        borderColor = accent,
                        onValueChange = { valueMargin = it; store.setValueMarginDp(it) }
                    )
                }
                if (showIcon) {
                    Sep()
                    SliderControl(
                        label = stringResource(R.string.slider_icon_margin),
                        value = iconMargin,
                        valueRange = 0f..QuickSliderStore.MAX_CONTENT_PADDING,
                        valueDisplay = "${iconMargin.toInt()}dp",
                        borderColor = accent,
                        onValueChange = { iconMargin = it; store.setIconMarginDp(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- colours ------------------------------------------------------------------------
            AppearanceSection(
                title = stringResource(R.string.group_colours),
                summary = stringResource(panelThemeLabel(panelTheme)),
            ) {
                PanelThemeSelector(
                    theme = panelTheme,
                    onThemeChange = {
                        panelTheme = it
                        viewModel.preference.setPanelTheme(it)
                    },
                )
                Sep()
                ColorPickerControl(
                    label = stringResource(R.string.slider_track_color),
                    color = trackColor,
                    borderColor = accent,
                    onColorChange = { trackColor = it; store.setTrackColor(it.toArgb()) }
                )
                Sep()
                ColorPickerControl(
                    label = stringResource(R.string.slider_fill_color),
                    color = fillColor,
                    borderColor = accent,
                    onColorChange = { fillColor = it; store.setFillColor(it.toArgb()) }
                )
                Sep()
                // The number and the icon, which otherwise swap between the fill and track colours.
                SettingSwitchItem(
                    title = stringResource(R.string.slider_content_colors),
                    description = stringResource(R.string.slider_content_colors_desc),
                    checked = contentColorsOn,
                    onCheckedChange = { contentColorsOn = it; store.setContentColorsEnabled(it) }
                )
                if (contentColorsOn) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ColorPickerControl(
                        label = stringResource(R.string.slider_value_color),
                        color = valueColor,
                        borderColor = accent,
                        onColorChange = { valueColor = it; store.setValueColor(it.toArgb()) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ColorPickerControl(
                        label = stringResource(R.string.slider_icon_color),
                        color = iconColor,
                        borderColor = accent,
                        onColorChange = { iconColor = it; store.setIconColor(it.toArgb()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- animation: how it arrives, and how the fill moves ------------------------------
            // The fill's own colours live here rather than under Colours: they belong to the
            // animation, and do nothing for the styles that draw in the fill colour itself.
            AppearanceSection(
                title = stringResource(R.string.group_animation),
                summary = "${stringResource(panelAnimationLabel(panelAnimation))} · ${stringResource(sliderFillLabel(fillStyle))}",
            ) {
                PanelAnimationSelector(
                    animation = panelAnimation,
                    onAnimationChange = {
                        panelAnimation = it
                        viewModel.preference.setPanelAnimation(it)
                        replay++
                    },
                    speed = animationSpeed,
                    onSpeedChange = {
                        animationSpeed = it
                        viewModel.preference.setPanelAnimationSpeed(it)
                        replay++
                    },
                )
                Sep()
                SliderFillSelector(
                    style = fillStyle,
                    onStyleChange = { fillStyle = it; store.setFillStyle(it) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingSwitchItem(
                    title = stringResource(R.string.slider_fill_colors),
                    description = stringResource(R.string.slider_fill_colors_desc),
                    checked = fillColorsOn,
                    onCheckedChange = { fillColorsOn = it; store.setFillColorsEnabled(it) }
                )
                if (fillColorsOn) {
                    if (SliderFill.supportsCustomColors(fillStyle)) {
                        fillColors.forEachIndexed { index, colour ->
                            Spacer(modifier = Modifier.height(12.dp))
                            ColorPickerControl(
                                label = stringResource(R.string.slider_fill_color_n, index + 1),
                                color = Color(colour),
                                borderColor = accent,
                                onColorChange = { picked ->
                                    fillColors = fillColors.toMutableList().also { it[index] = picked.toArgb() }
                                    store.setFillColor(index, picked.toArgb())
                                }
                            )
                        }
                    } else {
                        // Solid, the tides and the stripes are drawn in the fill colour itself, so
                        // there is nothing here for these to recolour.
                        Hint(stringResource(R.string.slider_fill_colors_unsupported))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- behaviour: what opens it, how it answers, and the keys -------------------------
            AppearanceSection(
                title = stringResource(R.string.group_behaviour),
                summary = "${stringResource(sliderOpenerLabel(openWith))} · ${stringResource(sliderVolumeKeysLabel(volumeKeys))}",
            ) {
                Label(stringResource(R.string.slider_open_with))
                Hint(stringResource(R.string.slider_open_with_desc))
                ChipRow(
                    ids = QuickSliderStore.ALL_OPENERS,
                    label = { stringResource(sliderOpenerLabel(it)) },
                    selected = { it == openWith },
                    onClick = { openWith = it; store.setOpenWith(it) }
                )
                Sep()
                Label(stringResource(R.string.slider_haptics))
                Hint(stringResource(R.string.slider_haptics_desc))
                ChipRow(
                    ids = QuickSliderStore.ALL_HAPTICS,
                    label = { stringResource(sliderHapticLabel(it)) },
                    selected = { it == haptic },
                    onClick = { haptic = it; store.setHaptic(it) }
                )
                Sep()
                // Beside the haptics, because it is the same answer to the same step in another sense.
                Label(stringResource(R.string.slider_sound))
                Hint(stringResource(R.string.slider_sound_desc))
                ChipRow(
                    ids = QuickSliderStore.ALL_SOUNDS,
                    label = { stringResource(sliderSoundLabel(it)) },
                    selected = { it == stepSound },
                    onClick = { stepSound = it; store.setStepSound(it) }
                )
                Sep()
                Label(stringResource(R.string.slider_volume_key))
                Hint(stringResource(R.string.slider_volume_key_desc))
                ChipRow(
                    ids = QuickSliderStore.ALL_VOLUME_KEY_MODES,
                    label = { stringResource(sliderVolumeKeysLabel(it)) },
                    selected = { it == volumeKeys },
                    onClick = { mode ->
                        volumeKeys = mode
                        store.setVolumeKeyMode(mode)
                        // The key filter is asked for only while something needs it.
                        OverlayRuntime.accessibilityService?.applyEventSubscription()
                        if (mode == QuickSliderStore.VOLUME_KEYS_INSTANT && !accessibilityOn) {
                            showDisclosure = true
                        }
                    }
                )
                Text(
                    text = stringResource(sliderVolumeKeysHint(volumeKeys)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (volumeKeys == QuickSliderStore.VOLUME_KEYS_INSTANT && !accessibilityOn) {
                    PermissionNote(
                        text = stringResource(R.string.slider_volume_keys_needs_accessibility),
                        onClick = { onOpenPermissions(PermissionNeeds.Permission.ACCESSIBILITY) },
                    )
                }
                Sep()
                SettingSwitchItem(
                    title = stringResource(R.string.slider_auto_brightness),
                    description = stringResource(R.string.slider_auto_brightness_desc),
                    checked = autoBrightnessOff,
                    onCheckedChange = { autoBrightnessOff = it; store.setDisableAutoBrightness(it) }
                )
                // What a tap on the icon does. Only while there is an icon to tap, and not for
                // brightness, which has no system panel to open.
                if (showIcon && target != QuickSliderStore.TARGET_BRIGHTNESS) {
                    Sep()
                    SettingSwitchItem(
                        title = stringResource(R.string.slider_icon_opens_panel),
                        description = stringResource(R.string.slider_icon_opens_panel_desc),
                        checked = iconOpensPanel,
                        onCheckedChange = { iconOpensPanel = it; store.setIconOpensVolumePanel(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * The real slider view, at a fixed 60%, on one of the preview backdrops.
 *
 * Held at a value rather than animated: the point of the preview is to show what the fill line
 * looks like against the two colours, and a value that moves on its own makes that harder to
 * judge, not easier.
 */
@Composable
private fun SliderPreview(
    modifier: Modifier = Modifier,
    fillHeight: Boolean,
    lengthDp: Float,
    thicknessDp: Float,
    /** How far the panel stands in from the edge it opens against. */
    edgeOffsetDp: Float,
    trackColor: Color,
    fillColor: Color,
    showValue: Boolean,
    showIcon: Boolean,
    /** The icon to draw when [showIcon] is on, already resolved against the target. */
    iconRes: Int,
    valueMargin: Float,
    iconMargin: Float,
    backdrop: Int,
    handlerOnLeft: Boolean,
    /** The panel style, so this shows the material the user is about to get. */
    panelTheme: String,
    /** What the fill does. Runs here exactly as it runs on the real panel. */
    fillStyle: String,
    /** The animation's own colours, or null for its palette. */
    fillColors: IntArray?,
    /** The number's and the icon's own colours, or null to swap with the fill. */
    valueColor: Color?,
    iconColor: Color?,
    /** The entrance, replayed on the preview whenever one is picked. */
    animation: String,
    animationSpeed: Float,
    replay: Int,
    /** The outline, either the bar's scaled up or the panel's own. */
    followBar: Boolean,
    barShape: String,
    barFlare: Float,
    barCorners: List<Float>,
    barWidthDp: Float,
    shape: String,
    flare: Float,
    corners: List<Float>,
) {
    // Against the same edge the handler is on, because that is where the panel actually opens —
    // it grows out of the bar. Centred, it was a picture of a track floating in the middle of the
    // screen, which is the one place it never appears.
    val density = LocalDensity.current.density
    PreviewStage(
        backdrop = backdrop,
        contentAlignment = if (handlerOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
        fillHeight = fillHeight,
        modifier = modifier,
    ) {
        AndroidView(
            factory = { ctx -> QuickSliderView(ctx) },
            update = { view ->
                // Dressed exactly the way the live panel is — see
                // `OverlayController.openQuickSliderWindow`, including which ink a material that
                // brings its own surface is written in: dark on the pale ones, and the chosen fill
                // on the dark ones unless it would vanish into them.
                val forced = PanelTheme.panelSurface(panelTheme)
                if (forced != null) {
                    val light = PanelTheme.isLight(panelTheme)
                    val chosen = fillColor.toArgb()
                    val pale = kotlin.math.abs(
                        ColorUtils.calculateLuminance(chosen) - ColorUtils.calculateLuminance(forced.toInt())
                    ) < 0.25
                    val fill = when {
                        !pale -> chosen
                        light -> PANEL_PREVIEW_LIGHT_INK
                        else -> android.graphics.Color.WHITE
                    }
                    view.setColors(forced.toInt(), fill)
                    view.setPanelTheme(1f, PanelTheme.hasLitEdge(panelTheme), light = light)
                } else {
                    view.setColors(trackColor.toArgb(), fillColor.toArgb())
                    view.setPanelTheme(
                        PanelTheme.surfaceAlpha(panelTheme),
                        PanelTheme.hasLitEdge(panelTheme),
                    )
                }
                // The same resolution the live panel does, for the same reason: following the
                // bar means scaled, not copied — see `OverlayController.openQuickSliderWindow`.
                val ratio = (thicknessDp / barWidthDp.coerceAtLeast(1f)).coerceIn(1f, 4f)
                if (followBar) {
                    view.setExpandedCorners(
                        barCorners[0] * ratio, barCorners[1] * ratio,
                        barCorners[2] * ratio, barCorners[3] * ratio,
                    )
                    view.setShapes(barShape, flare, barShape, barFlare, handlerOnLeft)
                } else {
                    view.setExpandedCorners(corners[0], corners[1], corners[2], corners[3])
                    view.setShapes(shape, flare, shape, flare, handlerOnLeft)
                }
                view.setFillStyle(fillStyle)
                view.setFillColors(fillColors)
                view.setDrawnThickness(thicknessDp * density, handlerOnLeft)
                view.setContentMargins(valueMargin, iconMargin)
                view.setShowValue(showValue)
                view.setContentColors(valueColor?.toArgb(), iconColor?.toArgb())
                view.setIcon(if (showIcon) iconRes else null)
                view.setValue(0.6f)
                // Both, and neither is optional. QuickSliderView is built to grow out of the
                // bar, so it starts collapsed and empty: at expansion 0 it draws no fill, no
                // number and no icon, which in a *static* preview is just a black lozenge.
                view.setExpansion(1f)
                view.setCommitted()
                // Only when asked for. `update` runs on every recomposition — every tick of
                // every slider on this screen — and replaying the entrance each time is what
                // made dragging the sweep look like the panel was stuttering.
                if (view.tag != replay) {
                    view.tag = replay
                    view.playEntrance(animation, handlerOnLeft, animationSpeed)
                }
            },
            modifier = Modifier
                // The side the panel opens against stands in for the screen edge, so at an edge
                // distance of 0 the panel is flush against the stage's wall, as it is flush against
                // the screen; the distance is the gap from it. The far side keeps the stage's inset.
                .padding(
                    start = if (handlerOnLeft) edgeOffsetDp.dp else 18.dp,
                    end = if (handlerOnLeft) 18.dp else edgeOffsetDp.dp,
                )
                // Capped to the stage's clear height, so a 320dp track is shown shortened rather
                // than bleeding off both ends. What the user is judging here is width, colour and
                // the shape of the ends; length is a number they set with a slider and read off it.
                .height(minOf(lengthDp, PREVIEW_SUBJECT_MAX_HEIGHT.value).dp)
                // The window's floor, not the panel's: the preview is the window, and the panel
                // is drawn inside it against the edge, exactly as it is on screen.
                .width(maxOf(thicknessDp, PANEL_PREVIEW_MIN_WINDOW).dp)
        )
    }
}

@Composable
private fun SweepSlider(flare: Float, onChange: (Float) -> Unit) {
    SliderControl(
        label = stringResource(R.string.end_sweep),
        value = flare * 100f,
        valueRange = HandlerShape.MIN_FLARE * 100f..HandlerShape.MAX_FLARE * 100f,
        valueDisplay = "${(flare * 100f).toInt()}%",
        borderColor = MaterialTheme.colorScheme.primary,
        onValueChange = { onChange(it / 100f) },
    )
}

@Composable
private fun CornerSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    SliderControl(
        label = label,
        value = value,
        valueRange = 0f..60f,
        valueDisplay = "${value.toInt()}dp",
        borderColor = MaterialTheme.colorScheme.primary,
        onValueChange = onChange,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/** A wrapping row of choices. Wraps rather than chunking by three, for a narrow landscape column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    ids: List<String>,
    label: @Composable (String) -> String,
    selected: (String) -> Boolean,
    onClick: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        ids.forEach { id ->
            val on = selected(id)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (on) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                onClick = { onClick(id) }
            ) {
                Text(
                    text = label(id),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    color = if (on) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun Sep() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    )
}

/** Mirrors `OverlayController.PANEL_LIGHT_INK`, so the preview and the panel write in one colour. */
private val PANEL_PREVIEW_LIGHT_INK = 0xFF15161A.toInt()

/**
 * Mirrors `OverlayController.PANEL_MAX_FLARE`.
 *
 * Duplicated rather than shared because the controller's copy is private to the service and this
 * one exists only to seed a slider; the number they agree on is a proportion of a panel, not a
 * contract between them.
 */
private const val QUICK_PANEL_MAX_FLARE = 0.22f

/** Mirrors `OverlayController.PANEL_MIN_THICKNESS_DP`: the window's floor, not the panel's. */
private const val PANEL_PREVIEW_MIN_WINDOW = 48f
