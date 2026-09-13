package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R

/**
 * One way into a part of the app, from the home screen.
 *
 * @param stacked the icon above the words rather than beside them, for a card sharing its row with
 *   another. Side by side at half a phone's width, an icon, a title and an arrow leave the subtitle
 *   about a dozen characters, which is not a description, it is an ellipsis.
 * @param compact a stacked card cut down for a phone held upright: tighter padding, a smaller icon
 *   chip and a subtitle that takes the lines it needs rather than always two. Upright, six of these
 *   stack three rows deep above the fold, and at full size they pushed everything under them off
 *   the screen. On its side there is width to spare and the full size reads better.
 * @param needsPermission a setting on this card's screen is waiting on a permission. The card gets
 *   an error badge beside its arrow and a one-line hint that takes the subtitle's first line, so
 *   the card keeps its height and the grid stays even whichever cards are warning.
 */
@Composable
fun NavigationCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: Int,
    gradientColors: List<Color>,
    stacked: Boolean = false,
    compact: Boolean = false,
    needsPermission: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (stacked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (compact) 12.dp else 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (compact) 32.dp else 38.dp)
                            .clip(RoundedCornerShape(if (compact) 10.dp else 12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = icon),
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (needsPermission) {
                        PermissionBadge(size = if (compact) 18.dp else 20.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 14.sp else 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (needsPermission) PermissionHint()
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    // Two lines reserved at full size, so cards sharing a row stay the same height
                    // whichever has the shorter description. Compact, the row evens the heights
                    // out itself, so a one-line subtitle no longer carries an empty second line.
                    // With the permission hint above it, the hint is the first of those two.
                    minLines = if (compact || needsPermission) 1 else 2,
                    maxLines = if (needsPermission) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            return@Card
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (needsPermission) PermissionHint()
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = if (needsPermission) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (needsPermission) {
                Spacer(modifier = Modifier.width(8.dp))
                PermissionBadge(size = 20.dp)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/** The error mark beside the arrow. Decorative: [PermissionHint] says it in words. */
@Composable
private fun PermissionBadge(size: Dp) {
    Icon(
        imageVector = Icons.Filled.Error,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.error
    )
}

/** "Needs a permission", in the error colour, one line. Read out with the card's title. */
@Composable
private fun PermissionHint() {
    Text(
        text = stringResource(R.string.nav_card_needs_permission),
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.error,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
