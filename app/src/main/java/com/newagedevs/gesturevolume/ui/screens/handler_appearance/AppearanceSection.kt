package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R

/**
 * One collapsible group on the appearance screen.
 *
 * The screen's problem was never that it lacked controls — it was that it showed all of them at
 * once. Seven headings and roughly thirty controls in one scroll means the two things most people
 * came to change are somewhere in the middle of a wall. Collapsing each group behind a header, with
 * the current value summarised on the header itself, turns the same screen into a list of seven
 * lines that can be read at a glance and opened one at a time.
 *
 * Every section toggles **independently**. There is deliberately no exclusive-accordion behaviour
 * and no programmatic scroll: with independent toggles, opening a section only ever pushes content
 * *down*, so the header the user just tapped never moves out from under their finger. An exclusive
 * accordion would close a section above the tapped one and yank the whole list upward.
 *
 * Expansion state is `rememberSaveable` with no explicit key, so it is keyed by position in the
 * composition. That survives a locale change, which a title-derived key would not.
 */
@Composable
fun AppearanceSection(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = AppearanceMotion.Chevron,
        label = "sectionChevron",
    )

    val expandLabel = stringResource(R.string.expand_section)
    val collapseLabel = stringResource(R.string.collapse_section)

    CustomizationCard(borderColor = MaterialTheme.colorScheme.primary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics {
                    this.role = Role.Button
                    heading()
                    stateDescription = if (expanded) collapseLabel else expandLabel
                },
        ) {
            SectionTitle(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                // Passing a modifier replaces SectionTitle's default, so the padding it normally
                // applies is restated here rather than lost.
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 16.dp, start = 4.dp),
            )

            if (summary != null && !expanded) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp, bottom = 16.dp),
                )
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                // The row already announces itself and its state; naming the chevron too would
                // make a screen reader say the same thing twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = AppearanceMotion.ExpandSize) +
                fadeIn(animationSpec = AppearanceMotion.Fade),
            exit = shrinkVertically(animationSpec = AppearanceMotion.ExpandSize) +
                fadeOut(animationSpec = AppearanceMotion.Fade),
        ) {
            // The inner Column is load-bearing: AnimatedVisibility's slot is an
            // AnimatedVisibilityScope, and at least one section body uses Modifier.align, which
            // needs a ColumnScope to resolve against.
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                content()
            }
        }
    }
}
