package com.newagedevs.gesturevolume.ui.screens.main

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
 * Visibility — behind one header, with the switch between a regular and an advanced user at the
 * top of them.
 *
 * Folded for a regular user and open for an advanced one, on first showing; after that the header
 * is the user's. The home screen of a regular user is then what the app used to be — the switch,
 * the look and the gestures — which is the look the users who asked for it missed, and nothing
 * the others use has gone anywhere.
 *
 * Switching mode replaces the bar's look and gestures with that mode's preset, so it asks first:
 * a switch that quietly rewrote someone's tuned bar would be worse than no switch.
 *
 * @param content the four cards, laid out by the caller to match its grid.
 */
@Composable
fun AdvancedFeaturesGroup(
    userMode: String,
    onChangeMode: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val advanced = UserMode.sanitize(userMode) == UserMode.ADVANCED
    var expanded by rememberSaveable { mutableStateOf(advanced) }
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }

    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "advancedChevron",
    )
    val expandLabel = stringResource(R.string.expand_advanced_features)
    val collapseLabel = stringResource(R.string.collapse_advanced_features)

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics {
                    role = Role.Button
                    stateDescription = if (expanded) collapseLabel else expandLabel
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_advanced_features),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.home_advanced_features_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevron),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                )
            ) + fadeIn(spring(stiffness = Spring.StiffnessMedium)),
            exit = shrinkVertically(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMedium,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                )
            ) + fadeOut(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(modifier = Modifier.height(0.dp))
                ModeSwitchRow(
                    advanced = advanced,
                    onToggle = { confirming = if (advanced) UserMode.REGULAR else UserMode.ADVANCED },
                )
                content()
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

/** Regular or advanced, as a switch, with what each one means under it. */
@Composable
private fun ModeSwitchRow(advanced: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mode_advanced_switch),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(if (advanced) R.string.mode_advanced_on_desc else R.string.mode_advanced_off_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // The row is the control; the switch only shows its state, so a tap anywhere asks.
            Switch(checked = advanced, onCheckedChange = { onToggle() })
        }
    }
}
