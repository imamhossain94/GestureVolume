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
/**
 * A small screen, and the preset's bar against its right edge.
 *
 * The bar is the swatch's own dimensions scaled down to fit, so the presets keep their proportions
 * relative to one another: the Edge still reads as the thinnest and the bubble as the widest.
 */
@Composable
internal fun MiniScreen(
    barWidth: Dp,
    barCorner: Dp,
    barOuterCorner: Dp?,
    shape: String,
    flare: Float,
    round: Boolean,
    color: Color,
    /** The screen's size: a row's 46dp tile on the home screen. */
    screenWidth: Dp = SCREEN_SIZE,
    screenHeight: Dp = SCREEN_SIZE,
) {
    val width = (barWidth * SWATCH_SCALE).coerceIn(4.dp, 22.dp)
    val height = if (round) width else screenHeight * 0.66f
    Box(
        modifier = Modifier
            .size(screenWidth, screenHeight)
            .clip(RoundedCornerShape(14.dp))
            // The tile every other row's icon sits on, so the presets' pictures read as icons.
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
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

/** A row's icon tile: the size every row on the home screen gives its picture. */
private val SCREEN_SIZE = 46.dp
