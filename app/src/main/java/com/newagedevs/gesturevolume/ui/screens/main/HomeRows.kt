package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The home screen's rows, in the design the Actions and Visibility screens share: a heading in the
 * accent with a line under it, and under that the rows of a group joined into one card — an icon on
 * a tile, a title and what it is for, and a chevron or a switch at the end. See segmentShape for how
 * the joined rows are cut.
 */

/** A group's heading, and the line under it saying what the group is for. */
@Composable
fun HomeHeading(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Rows grouped into one card, a hairline of the page between them. */
@Composable
fun HomeSegments(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

/** A row's icon on its tile: filled in the accent while what the row sets is on. */
@Composable
fun HomeIconTile(icon: Painter, on: Boolean = false) {
    val colours = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) colours.primary else colours.primaryContainer.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = if (on) colours.onPrimary else colours.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun HomeIconTile(icon: ImageVector, on: Boolean = false) = HomeIconTile(rememberVectorPainter(icon), on)

/** The row's surface and padding, the same for every kind of row in a group. */
private fun Modifier.rowSurface(shape: Shape, fill: androidx.compose.ui.graphics.Color): Modifier =
    fillMaxWidth().clip(shape).background(fill)

/** A title and what it is for, filling what the row leaves. */
@Composable
private fun RowScope.RowText(title: String, summary: String, summaryColour: androidx.compose.ui.graphics.Color? = null) {
    Column(modifier = Modifier.weight(1f)) {
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = summaryColour ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A way to another screen: its icon, its name and what it holds, and a chevron. [trailing] goes
 * before the chevron, for a count; [footer] under the row, inside the same card.
 */
@Composable
fun HomeLinkRow(
    icon: @Composable () -> Unit,
    title: String,
    summary: String,
    shape: Shape,
    onClick: () -> Unit,
    summaryColour: androidx.compose.ui.graphics.Color? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colours = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .rowSurface(shape, colours.surfaceVariant.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(14.dp))
            RowText(title, summary, summaryColour)
            trailing()
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colours.outline,
                modifier = Modifier.size(22.dp),
            )
        }
        footer?.let { content ->
            Column(modifier = Modifier.padding(start = 60.dp, top = 10.dp), content = content)
        }
    }
}

/**
 * Something on or off: its icon, its name and what it means, and a switch. The whole row is the
 * switch; the switch itself only shows where it stands. [highlight] fills the row in the accent's
 * container while on, for the one switch the screen is about.
 */
@Composable
fun HomeToggleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
    highlight: Boolean = false,
) {
    val colours = MaterialTheme.colorScheme
    val lit = highlight && checked
    Row(
        modifier = Modifier
            .rowSurface(shape, if (lit) colours.primaryContainer else colours.surfaceVariant.copy(alpha = 0.65f))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeIconTile(icon, on = checked)
        Spacer(modifier = Modifier.width(14.dp))
        RowText(title, summary)
        Spacer(modifier = Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
