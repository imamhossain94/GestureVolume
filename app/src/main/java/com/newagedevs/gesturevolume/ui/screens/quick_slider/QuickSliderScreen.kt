package com.newagedevs.gesturevolume.ui.screens.quick_slider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
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

fun sliderHapticLabel(id: String): Int = when (id) {
    QuickSliderStore.HAPTIC_OFF -> R.string.slider_haptic_off
    QuickSliderStore.HAPTIC_MEDIUM -> R.string.slider_haptic_medium
    QuickSliderStore.HAPTIC_STRONG -> R.string.slider_haptic_strong
    else -> R.string.slider_haptic_light
}

fun sliderOpenerLabel(id: String): Int = when (id) {
    QuickSliderStore.OPEN_OUT -> R.string.slider_open_out
    QuickSliderStore.OPEN_BOTH -> R.string.slider_open_both
    QuickSliderStore.OPEN_OFF -> R.string.slider_open_off
    else -> R.string.slider_open_in
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

    var openWith by remember { mutableStateOf(store.getOpenWith()) }
    var target by remember { mutableStateOf(store.getTarget()) }
    var haptic by remember { mutableStateOf(store.getHaptic()) }
    var length by remember { mutableFloatStateOf(store.getLengthDp()) }
    var thickness by remember { mutableFloatStateOf(store.getThicknessDp()) }
    var corner by remember { mutableFloatStateOf(store.getCornerDp()) }
    var trackColor by remember { mutableStateOf(Color(store.getTrackColor())) }
    var fillColor by remember { mutableStateOf(Color(store.getFillColor())) }
    var showValue by remember { mutableStateOf(store.getShowValue()) }
    var showIcon by remember { mutableStateOf(store.getShowIcon()) }
    var autoBrightnessOff by remember { mutableStateOf(store.getDisableAutoBrightness()) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_slider_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))
            SliderPreview(
                lengthDp = length,
                thicknessDp = thickness,
                cornerDp = corner,
                trackColor = trackColor,
                fillColor = fillColor,
                showValue = showValue,
                showIcon = showIcon,
                target = target
            )

            Spacer(modifier = Modifier.height(24.dp))
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
                    value = thickness,
                    valueRange = 24f..72f,
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
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.quick_slider_look_title), accent)
            Card {
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

/**
 * The real slider view, at a fixed 60%, on a chequered-neutral panel.
 *
 * Held at a value rather than animated: the point of the preview is to show what the fill line
 * looks like against the two colours, and a value that moves on its own makes that harder to
 * judge, not easier.
 */
@Composable
private fun SliderPreview(
    lengthDp: Float,
    thicknessDp: Float,
    cornerDp: Float,
    trackColor: Color,
    fillColor: Color,
    showValue: Boolean,
    showIcon: Boolean,
    target: String
) {
    val iconRes = if (target == QuickSliderStore.TARGET_BRIGHTNESS) {
        R.drawable.ic_brightness_up
    } else {
        R.drawable.ic_vol_increase
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((lengthDp + 48f).dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx -> QuickSliderView(ctx) },
                update = { view ->
                    view.setColors(trackColor.toArgb(), fillColor.toArgb())
                    view.setCornerRadiusDp(cornerDp)
                    view.setShowValue(showValue)
                    view.setIcon(if (showIcon) iconRes else null)
                    view.setValue(0.6f)
                },
                modifier = Modifier
                    .height(lengthDp.dp)
                    .width(thicknessDp.dp)
            )
        }
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
