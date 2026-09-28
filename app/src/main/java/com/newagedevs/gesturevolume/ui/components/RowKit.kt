package com.newagedevs.gesturevolume.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The pieces the Actions and Visibility screens are made of, for every screen that follows them:
 * a heading with a line under it, rows grouped into one card with a hairline of the page between
 * them, a tile with a row's picture, and the rows themselves — the whole row the thing to tap.
 */

/** A group's heading, in the accent, and the line under it when there is one. */
@Composable
fun GroupHeading(title: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier = modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Rows grouped into one card: see [cardShape]. */
@Composable
fun RowGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

/**
 * The shape of the [index]th of [count] rows grouped into one card: round where the group starts
 * and ends, nearly square where two rows meet, so the rows read as one piece and each as its own.
 */
fun cardShape(index: Int, count: Int): Shape {
    val top = if (index == 0) CARD_OUTER else CARD_SEAM
    val bottom = if (index == count - 1) CARD_OUTER else CARD_SEAM
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val CARD_OUTER = 20.dp
private val CARD_SEAM = 6.dp

/** The colour a row sits on. */
@Composable
fun rowColor(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)

/**
 * A row's picture on its tile: faint in the accent, or [lit] — filled — for the one that is on,
 * chosen or done. [container] and [tint] for a tile that says something else, such as a warning.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
    size: Dp = 46.dp,
    container: Color? = null,
    tint: Color? = null,
) {
    val colours = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(container ?: if (lit) colours.primary else colours.primaryContainer.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: if (lit) colours.onPrimary else colours.primary,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/**
 * One row: its [leading] picture, its [title], a [summary] under it in the accent, a [description]
 * under that, and [trailing] at the end — a chevron when it leads somewhere. The whole row is the
 * thing to tap when there is an [onClick]; [footer] is for what belongs to the row below it.
 */
@Composable
fun KitRow(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    summary: String? = null,
    description: String? = null,
    background: Color = rowColor(),
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = if (onClick != null) {
        { Chevron() }
    } else {
        null
    },
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colours = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
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
                        fontWeight = FontWeight.Medium,
                        color = colours.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colours.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
        footer?.invoke(this)
    }
}

/** The end of a row that leads somewhere. */
@Composable
fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(22.dp),
    )
}

/**
 * The card at the head of a screen, in the accent's container, as the action picker opens with:
 * a [leading] picture, a [title], and a line or two under it.
 */
@Composable
fun HeroCard(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    leading: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colours.primaryContainer.copy(alpha = 0.55f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colours.onSurface,
            )
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colours.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            content?.invoke(this)
        }
    }
}
