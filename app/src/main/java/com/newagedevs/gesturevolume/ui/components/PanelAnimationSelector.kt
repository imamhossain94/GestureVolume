package com.newagedevs.gesturevolume.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.utils.PanelAnimation

/**
 * Picks how the panels arrive, from any screen that shows one.
 *
 * A wrapping row of chips rather than a dropdown, because there are sixteen of them and the point
 * is to try them: a list you scroll through one at a time makes comparing two of them a chore,
 * where a grid of chips lets the finger walk along and watch the preview above replay each one.
 * That replay is the whole design — "Blinds" and "Tide" are not words anybody can picture.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelAnimationSelector(
    animation: String,
    onAnimationChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** How fast they run, as a multiple of the catalogue's own timing. */
    speed: Float = 1f,
    onSpeedChange: ((Float) -> Unit)? = null,
    /** The heading and the line under it, for a screen that says more about its own panel. */
    title: String = stringResource(R.string.panel_animation),
    description: String = stringResource(R.string.panel_animation_desc),
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelAnimation.ALL.forEach { id ->
                AnimationChip(
                    label = stringResource(labelFor(id)),
                    selected = animation == id,
                    // Fires even when it is already the selected one, on purpose: tapping the
                    // chip that is already on is how you watch the animation a second time.
                    onClick = { onAnimationChange(id) },
                )
            }
        }

        if (onSpeedChange != null) {
            Spacer(modifier = Modifier.height(14.dp))
            SliderControl(
                label = stringResource(R.string.panel_animation_speed),
                // Shown as a multiple rather than in milliseconds: the catalogue's entrances do
                // not all take the same time — a wipe has further to travel than a fade — so one
                // number of milliseconds would be a lie about fifteen of the sixteen.
                value = speed,
                valueRange = PanelAnimation.MIN_SPEED..PanelAnimation.MAX_SPEED,
                valueDisplay = String.format(java.util.Locale.US, "%.1f×", speed),
                borderColor = MaterialTheme.colorScheme.primary,
                onValueChange = onSpeedChange,
            )
        }
    }
}

@Composable
private fun AnimationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        },
        label = "animationChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "animationChipContent",
    )
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
    )
}

private fun labelFor(id: String): Int = when (id) {
    PanelAnimation.FADE -> R.string.anim_fade
    PanelAnimation.POP -> R.string.anim_pop
    PanelAnimation.SPRING -> R.string.anim_spring
    PanelAnimation.ZOOM -> R.string.anim_zoom
    PanelAnimation.UNFOLD -> R.string.anim_unfold
    PanelAnimation.EXPAND -> R.string.anim_expand
    PanelAnimation.RISE -> R.string.anim_rise
    PanelAnimation.DROP -> R.string.anim_drop
    PanelAnimation.SLIDE -> R.string.anim_slide
    PanelAnimation.SWING -> R.string.anim_swing
    PanelAnimation.FLIP -> R.string.anim_flip
    PanelAnimation.TILT -> R.string.anim_tilt
    PanelAnimation.BLINDS -> R.string.anim_blinds
    PanelAnimation.TIDE -> R.string.anim_tide
    PanelAnimation.IRIS -> R.string.anim_iris
    else -> R.string.anim_settle
}

/** Unused here, but it keeps the "every id has a label" promise checkable. */
internal val PANEL_ANIMATION_LABELS: List<Int> = PanelAnimation.ALL.map(::labelFor)
