package com.newagedevs.gesturevolume.ui.screens.quick_slider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.graphics.ColorUtils
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.PREVIEW_SUBJECT_MAX_HEIGHT
import com.newagedevs.gesturevolume.ui.components.PanelThemeSelector
import com.newagedevs.gesturevolume.ui.components.SliderFillSelector
import com.newagedevs.gesturevolume.ui.components.PreviewStage
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.utils.PanelTheme
import com.newagedevs.gesturevolume.ui.screens.handler_action.SectionTitle
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.ColorPickerControl
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel

/** The translated name of a slider target. */
fun sliderTargetLabel(id: String): Int = when (id) {
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

fun sliderHapticLabel(id: String): Int = when (id) {
    QuickSliderStore.HAPTIC_OFF -> R.string.slider_haptic_off
    QuickSliderStore.HAPTIC_MEDIUM -> R.string.slider_haptic_medium
    QuickSliderStore.HAPTIC_STRONG -> R.string.slider_haptic_strong
    else -> R.string.slider_haptic_light
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSliderScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val store = viewModel.preference.slider

    // One wallpaper per visit; see the note in HandlerAppearanceScreen.
    val bgImage = remember { viewModel.getNextBackground() }

    // Read once: which side the bar is on is settled on the Appearance screen, and this one has
    // no way to change it.
    val handlerOnLeft = remember { viewModel.preference.getHandlerPosition() == "Left" }

    var openWith by remember { mutableStateOf(store.getOpenWith()) }
    var target by remember { mutableStateOf(store.getTarget()) }
    var haptic by remember { mutableStateOf(store.getHaptic()) }
    var length by remember { mutableFloatStateOf(store.getLengthDp()) }
    var thickness by remember { mutableFloatStateOf(store.getThicknessDp()) }
    // The bar's colour and corners, read once. Not settings any more — the panel has its own —
    // but still the shape the morph *starts* from, which is what the preview has to show as the
    // collapsed end so that the preview and the real opening agree about frame zero.
    val handlerTrackColor = remember {
        Color(
            ColorUtils.setAlphaComponent(
                viewModel.preference.getHandlerColor(),
                viewModel.preference.getHandlerBackgroundAlpha().coerceIn(0, 255)
            )
        )
    }
    var corner by remember { mutableFloatStateOf(store.getCornerDp()) }
    var fillStyle by remember { mutableStateOf(store.getFillStyle()) }
    var trackColor by remember { mutableStateOf(Color(store.getTrackColor())) }
    var fillColor by remember { mutableStateOf(Color(store.getFillColor())) }
    var showValue by remember { mutableStateOf(store.getShowValue()) }
    var showIcon by remember { mutableStateOf(store.getShowIcon()) }
    var autoBrightnessOff by remember { mutableStateOf(store.getDisableAutoBrightness()) }
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
        // Preview pinned, controls scrolling underneath — the shape the Appearance screen uses.
        // A preview that scrolls away with the control that changes it is the old two-screen
        // problem with extra steps: you set a number, lose sight of the thing it applies to, and
        // have to scroll back to find out what you did.
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Top inset only. The bottom one belongs to the scroller below, or the fixed
                // block gets padded away from the gesture pill it is nowhere near.
                .padding(top = padding.calculateTopPadding())
        ) {
            Text(
                text = stringResource(R.string.quick_slider_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            SliderPreview(
                modifier = Modifier.padding(horizontal = 16.dp),
                backgroundImageURL = bgImage,
                handlerOnLeft = handlerOnLeft,
                lengthDp = length,
                thicknessDp = thickness,
                trackColor = trackColor,
                corner = corner,
                fillColor = fillColor,
                showValue = showValue,
                showIcon = showIcon,
                target = target,
                panelTheme = panelTheme,
                fillStyle = fillStyle
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
            SectionTitle(stringResource(R.string.quick_slider_gesture_title), accent)
            Card {
                Text(
                    text = stringResource(R.string.slider_open_with),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.slider_open_with_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                ChipRow(
                    ids = QuickSliderStore.ALL_OPENERS,
                    label = { stringResource(sliderOpenerLabel(it)) },
                    selected = { it == openWith },
                    onClick = { openWith = it; store.setOpenWith(it) }
                )
                Sep()
                Text(
                    text = stringResource(R.string.slider_target),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                ChipRow(
                    ids = QuickSliderStore.ALL_TARGETS,
                    label = { stringResource(sliderTargetLabel(it)) },
                    selected = { it == target },
                    onClick = { target = it; store.setTarget(it) }
                )
                Sep()
                Text(
                    text = stringResource(R.string.slider_haptics),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.slider_haptics_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                ChipRow(
                    ids = QuickSliderStore.ALL_HAPTICS,
                    label = { stringResource(sliderHapticLabel(it)) },
                    selected = { it == haptic },
                    onClick = { haptic = it; store.setHaptic(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.quick_slider_shape_title), accent)
            Card {
                SliderControl(
                    label = stringResource(R.string.slider_length),
                    value = length,
                    valueRange = 120f..320f,
                    valueDisplay = "${length.toInt()}dp",
                    borderColor = accent,
                    onValueChange = { length = it; store.setLengthDp(it) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.slider_length_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Sep()
                SliderControl(
                    label = stringResource(R.string.slider_thickness),
                    // Starts where the panel's own floor is rather than at 24dp. Below that the
                    // panel is widened back out when it is built, so the lower half of this slider
                    // used to move the preview and nothing else.
                    valueRange = QuickSliderStore.MIN_THICKNESS..72f,
                    value = thickness,
                    valueDisplay = "${thickness.toInt()}dp",
                    borderColor = accent,
                    onValueChange = { thickness = it; store.setThicknessDp(it) }
                )
                Sep()
                SliderControl(
                    label = stringResource(R.string.slider_corner),
                    value = corner,
                    valueRange = 0f..40f,
                    valueDisplay = "${corner.toInt()}dp",
                    borderColor = accent,
                    onValueChange = { corner = it; store.setCornerDp(it) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.slider_corner_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.quick_slider_look_title), accent)
            Card {
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
                SliderFillSelector(
                    style = fillStyle,
                    onStyleChange = { fillStyle = it; store.setFillStyle(it) },
                )
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
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.quick_slider_behaviour_title), accent)
            Card {
                SettingSwitchItem(
                    title = stringResource(R.string.slider_auto_brightness),
                    description = stringResource(R.string.slider_auto_brightness_desc),
                    checked = autoBrightnessOff,
                    onCheckedChange = { autoBrightnessOff = it; store.setDisableAutoBrightness(it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The real slider view, at a fixed 60%, on a chequered-neutral panel.
 *
 * Held at a value rather than animated: the point of the preview is to show what the fill line
 * looks like against the two colours, and a value that moves on its own makes that harder to
 * judge, not easier.
 */
@Composable
private fun SliderPreview(
    modifier: Modifier = Modifier,
    lengthDp: Float,
    thicknessDp: Float,
    corner: Float,
    trackColor: Color,
    fillColor: Color,
    showValue: Boolean,
    showIcon: Boolean,
    target: String,
    backgroundImageURL: String,
    handlerOnLeft: Boolean,
    /** The panel style, so this shows the material the user is about to get. */
    panelTheme: String,
    /** What the fill does. Runs here exactly as it runs on the real panel. */
    fillStyle: String,
) {
    val iconRes = if (target == QuickSliderStore.TARGET_BRIGHTNESS) {
        R.drawable.ic_brightness_up
    } else {
        R.drawable.ic_vol_increase
    }
    // Against the same edge the handler is on, because that is where the panel actually opens —
    // it grows out of the bar. Centred, it was a picture of a track floating in the middle of the
    // screen, which is the one place it never appears.
    PreviewStage(
        backgroundImageURL = backgroundImageURL,
        contentAlignment = if (handlerOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
        modifier = modifier,
    ) {
        AndroidView(
                factory = { ctx -> QuickSliderView(ctx) },
                update = { view ->
                    // Dressed exactly the way the live panel is — see
                    // `OverlayController.openQuickSliderWindow`. It used to skip the theme
                    // entirely, so the preview showed the Solid look whatever was selected, which
                    // on a picker whose whole job is choosing a look is worse than no preview.
                    val paleSurface = PanelTheme.panelSurface(panelTheme)
                    if (paleSurface != null) {
                        view.setColors(paleSurface.toInt(), PANEL_PREVIEW_LIGHT_INK)
                        view.setPanelTheme(1f, PanelTheme.hasLitEdge(panelTheme), light = true)
                    } else {
                        view.setColors(trackColor.toArgb(), fillColor.toArgb())
                        view.setPanelTheme(
                            PanelTheme.surfaceAlpha(panelTheme),
                            PanelTheme.hasLitEdge(panelTheme),
                        )
                    }
                    view.setCornerRadiusDp(corner)
                    view.setFillStyle(fillStyle)
                    view.setShowValue(showValue)
                    view.setIcon(if (showIcon) iconRes else null)
                    view.setValue(0.6f)
                    // Both, and neither is optional. QuickSliderView is built to grow out of the
                    // bar, so it starts collapsed and empty: at expansion 0 it draws no fill, no
                    // number and no icon, which in a *static* preview is just a black lozenge.
                    // These two say "this one is already open" — the state every other caller
                    // reaches by animating, and this one has no reason to animate to.
                    view.setExpansion(1f)
                    view.setCommitted()
                },
            modifier = Modifier
                .padding(horizontal = 18.dp)
                // Capped to the stage's clear height, so a 320dp track is shown shortened rather
                // than bleeding off both ends. What the user is judging here is width, colour and
                // the shape of the ends; length is a number they set with a slider and read off it.
                .height(minOf(lengthDp, PREVIEW_SUBJECT_MAX_HEIGHT.value).dp)
                .width(thicknessDp.dp)
        )
    }
}

@Composable
private fun ChipRow(
    ids: List<String>,
    label: @Composable (String) -> String,
    selected: (String) -> Boolean,
    onClick: (String) -> Unit
) {
    ids.chunked(3).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            row.forEach { id ->
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
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
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
