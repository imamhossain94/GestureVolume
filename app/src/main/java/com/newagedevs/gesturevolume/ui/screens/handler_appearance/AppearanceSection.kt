package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R

/**
 * One collapsible group on the screens with a preview — Appearance, the Deck, the Quick panel, the
 * long-press menu.
 *
 * The screen's problem was never that it lacked controls — it was that it showed all of them at
 * once. Seven headings and roughly thirty controls in one scroll means the two things most people
 * came to change are somewhere in the middle of a wall. Collapsing each group behind a header, with
 * the current value summarised on the header itself, turns the same screen into a list of seven
 * lines that can be read at a glance and opened one at a time.
 *
 * Dressed as the Actions and Visibility screens' rows: a tile with the group's picture, its name
 * and what it is set to, the whole row the thing to tap; and the settings, once opened, the second
 * half of the same card, a hairline of the page below it. The tile lights up while it is open.
 *
 * Every section toggles **independently**. There is deliberately no exclusive-accordion behaviour
 * and no programmatic scroll: with independent toggles, opening a section only ever pushes content
 * *down*, so the header the user just tapped never moves out from under their finger. An exclusive
 * accordion would close a section above the tapped one and yank the whole list upward.
 *
 * Expansion state is `rememberSaveable` with no explicit key, so it is keyed by position in the
 * composition. That survives a locale change, which a title-derived key would not.
 *
 * @param icon the picture on the header's tile; none leaves the title to stand on its own.
 */
@Composable
fun AppearanceSection(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = AppearanceMotion.Chevron,
        label = "sectionChevron",
    )
    // Where the header meets the settings under it: round while it stands alone, nearly square
    // while the two are one card.
    val join by animateDpAsState(
        targetValue = if (expanded) SEAM else OUTER,
        label = "sectionJoin",
    )

    val expandLabel = stringResource(R.string.expand_section)
    val collapseLabel = stringResource(R.string.collapse_section)
    val colours = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = OUTER, topEnd = OUTER, bottomStart = join, bottomEnd = join))
                .background(colours.surfaceVariant.copy(alpha = 0.65f))
                .clickable { expanded = !expanded }
                .semantics {
                    this.role = Role.Button
                    heading()
                    stateDescription = if (expanded) collapseLabel else expandLabel
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (icon != null) {
                val tile by animateColorAsState(
                    if (expanded) colours.primary else colours.primaryContainer.copy(alpha = 0.6f),
                    label = "sectionTile",
                )
                val glyph by animateColorAsState(
                    if (expanded) colours.onPrimary else colours.primary,
                    label = "sectionGlyph",
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tile),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = glyph,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colours.onSurface,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        fontSize = 14.sp,
                        color = colours.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                // The row already announces itself and its state; naming the chevron too would
                // make a screen reader say the same thing twice.
                contentDescription = null,
                tint = colours.outline,
                modifier = Modifier.rotate(chevronRotation),
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
            Column(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = SEAM, topEnd = SEAM, bottomStart = OUTER, bottomEnd = OUTER))
                    .background(colours.surfaceVariant.copy(alpha = 0.65f))
                    .padding(16.dp),
                content = content,
            )
        }
    }
}

/** The card's outer corners, and where its two halves meet: as the Actions screen's groups. */
private val OUTER = 20.dp
private val SEAM = 6.dp
