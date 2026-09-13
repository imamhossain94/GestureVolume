package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.utils.HandlerShape

/**
 * One handler preset on the home screen: what it is called, a line about it, and a picture of the
 * bar it puts on screen.
 *
 * Built like the navigation cards above it — the same surface, corner and icon chip, the name and
 * a short description under it — so the presets read as part of one home screen rather than as a
 * second, older design below the first. The description used to be cut off by a fixed height and
 * never seen at all.
 *
 * **The picture.** A small screen with the bar against its right edge, the way the preset sits
 * against the side of the phone. The screen is what makes the swatch read as a *bar*: a black
 * sliver alone on a card is a stripe, the same sliver at the edge of a screen is a handle. Drawn in
 * the preset's own proportions, corners and outline — a tab as a tab, the bubble as a circle held
 * off the edge — because telling the presets apart at a glance is the one thing this row is for.
 */
@Composable
fun PresetCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>, // kept for API compatibility
    previewWidth: Dp = 20.dp,
    previewCorner: Dp = 10.dp,
    /**
     * The radius on the side that faces the screen edge, when it differs from [previewCorner].
     *
     * The Edge bar is rounded on the inside and all but square where it meets the edge, and a
     * swatch that showed it as a symmetric pill would be advertising a shape the preset does not
     * apply. Null keeps both sides the same, which is every other preset.
     */
    previewOuterCorner: Dp? = null,
    /**
     * The outline the swatch is cut to, when the preset is not a rounded rectangle.
     *
     * Drawn from [HandlerShape]'s own geometry rather than approximated with a corner radius, so
     * the card advertises the shape the preset actually applies.
     */
    previewShape: String = HandlerShape.ROUNDED,
    previewFlare: Float = HandlerShape.DEFAULT_FLARE,
    /** A circle held a little off the edge rather than a bar against it: the floating bubble. */
    previewRound: Boolean = false,
    previewColor: Color = MaterialTheme.colorScheme.primary,
    previewAlpha: Float = 0.8f,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            MiniScreen(
                barWidth = previewWidth,
                barCorner = previewCorner,
                barOuterCorner = previewOuterCorner,
                shape = previewShape,
                flare = previewFlare,
                round = previewRound,
                color = previewColor.copy(alpha = previewAlpha),
            )
        }
    }
}

/**
 * A small screen, and the preset's bar against its right edge.
 *
 * The bar is the swatch's own dimensions scaled down to fit, so the presets keep their proportions
 * relative to one another: the Edge still reads as the thinnest and the bubble as the widest.
 */
@Composable
private fun MiniScreen(
    barWidth: Dp,
    barCorner: Dp,
    barOuterCorner: Dp?,
    shape: String,
    flare: Float,
    round: Boolean,
    color: Color,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val width = (barWidth * SWATCH_SCALE).coerceIn(4.dp, 22.dp)
    val height = if (round) width else SCREEN_HEIGHT * 0.6f
    Box(
        modifier = Modifier
            .size(SCREEN_WIDTH, SCREEN_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(onSurface.copy(alpha = 0.05f), onSurface.copy(alpha = 0.12f))
                )
            ),
        contentAlignment = Alignment.CenterEnd
    ) {
        val barModifier = Modifier
            // The bubble floats a little off the edge, the way its preset holds it; every bar
            // is flush against it.
            .padding(end = if (round) 5.dp else 0.dp)
            .size(width, height)
        if (shape == HandlerShape.TAB) {
            Canvas(modifier = barModifier) {
                // The screen's right edge stands in for the phone's, which is the side a tab's
                // sweeps run to.
                val outline = HandlerShape.tabOutline(size.width, size.height, flare, edgeOnLeft = false)
                val path = Path().apply {
                    moveTo(outline[0], outline[1])
                    var i = 2
                    while (i < outline.size) {
                        lineTo(outline[i], outline[i + 1])
                        i += 2
                    }
                    close()
                }
                drawPath(path, color)
            }
        } else {
            val inner = if (round) width / 2 else (barCorner * SWATCH_SCALE).coerceAtMost(width / 2)
            val outer = if (round) width / 2 else ((barOuterCorner ?: barCorner) * SWATCH_SCALE).coerceAtMost(width / 2)
            Box(
                modifier = barModifier
                    .clip(
                        RoundedCornerShape(
                            topStart = inner,
                            bottomStart = inner,
                            topEnd = outer,
                            bottomEnd = outer,
                        )
                    )
                    .background(color)
            )
        }
    }
}

/** How much smaller than the preset's own swatch numbers the bar is drawn. */
private const val SWATCH_SCALE = 0.6f

private val SCREEN_WIDTH = 46.dp
private val SCREEN_HEIGHT = 76.dp
