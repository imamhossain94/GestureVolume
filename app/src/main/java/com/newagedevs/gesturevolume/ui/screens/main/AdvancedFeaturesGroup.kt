package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import com.newagedevs.gesturevolume.ui.screens.handler_action.segmentShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.motion.TextButton
import com.newagedevs.gesturevolume.utils.UserMode

/**
 * The home screen's advanced features — the Deck, the Quick slider, the long-press menu and
 * Visibility — as a group in the Actions and Visibility screens' design: its heading, then the
 * switch between a regular and an advanced user as the group's first row, and the four under it.
 *
 * For a regular user the four are folded behind a last row that shows them, so the home screen is
 * what the app used to be; for an advanced one they are always there. Nothing the others use has
 * gone anywhere.
 *
 * Switching mode replaces the bar's look and gestures with that mode's preset, so it asks first:
 * a switch that quietly rewrote someone's tuned bar would be worse than no switch.
 *
 * @param rows the four rows, each told its shape in the group.
 * @param ad a native ad, as a row of its own under the mode switch; null for Pro, and for a build
 *   without ads. Always composed, so it can load, but it only counts as a row — a gap above it and
 *   a place in the group's corners — while [adShown].
 */
@Composable
fun AdvancedFeaturesGroup(
    userMode: String,
    onChangeMode: (String) -> Unit,
    modifier: Modifier = Modifier,
    rows: List<@Composable (Shape) -> Unit>,
    ad: (@Composable (Shape) -> Unit)? = null,
    adShown: Boolean = false,
) {
    val advanced = UserMode.sanitize(userMode) == UserMode.ADVANCED
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = advanced || expanded
    val adRows = if (ad != null && adShown) 1 else 0
    val count = 1 + adRows + (if (shown) rows.size else 0) + (if (advanced) 0 else 1)

    Column(modifier = modifier) {
        HomeHeading(
            title = stringResource(R.string.home_advanced_features),
            hint = stringResource(R.string.home_advanced_features_desc),
        )
        HomeSegments(modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
            // The switch and the ad as one child of the group, so an ad not yet loaded adds no gap.
            Column {
                HomeToggleRow(
                    icon = Icons.Outlined.AutoAwesome,
                    title = stringResource(R.string.mode_advanced_switch),
                    summary = stringResource(if (advanced) R.string.mode_advanced_on_desc else R.string.mode_advanced_off_desc),
                    checked = advanced,
                    shape = segmentShape(0, count),
                    onCheckedChange = { confirming = if (advanced) UserMode.REGULAR else UserMode.ADVANCED },
                )
                if (ad != null) {
                    Box(modifier = Modifier.padding(top = if (adRows == 1) 3.dp else 0.dp)) {
                        ad(segmentShape(1, count))
                    }
                }
            }
            if (shown) rows.forEachIndexed { i, row -> row(segmentShape(i + 1 + adRows, count)) }
            if (!advanced) {
                ExpanderRow(expanded = expanded, shape = segmentShape(count - 1, count)) { expanded = !expanded }
            }
        }
    }

    confirming?.let { target ->
        val toAdvanced = target == UserMode.ADVANCED
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = {
                Text(stringResource(if (toAdvanced) R.string.mode_to_advanced_title else R.string.mode_to_simple_title))
            },
            text = {
                Text(stringResource(if (toAdvanced) R.string.mode_to_advanced_body else R.string.mode_to_simple_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onChangeMode(target)
                }) { Text(stringResource(R.string.mode_switch_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.mode_switch_cancel)) }
            },
        )
    }
}

/** Shows or folds the four, for a regular user: the group's last row. */
@Composable
private fun ExpanderRow(expanded: Boolean, shape: Shape, onClick: () -> Unit) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "advancedChevron",
    )
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colours.surfaceVariant.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(if (expanded) R.string.collapse_advanced_features else R.string.expand_advanced_features),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colours.primary,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = colours.primary,
            modifier = Modifier.rotate(chevron),
        )
    }
}
