package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.utils.HandlerShape


@Composable
fun PresetCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>, // kept for API compatibility
    // Visual preview properties to make the card more logical
    previewWidth: Dp = 20.dp,
    previewCorner: Dp = 10.dp,
    /**
     * The radius on the side that faces the screen edge, when it differs from [previewCorner].
     *
     * The Default bar is rounded on the inside and all but square where it meets the edge, and a
     * swatch that showed it as a symmetric pill would be advertising a shape the preset does not
     * apply. Null keeps both sides the same, which is every other preset.
     */
    previewOuterCorner: Dp? = null,
    /**
     * The outline the swatch is cut to, when the preset is not a rounded rectangle.
     *
     * Drawn from [HandlerShape]'s own geometry rather than approximated with a corner radius, so
     * the card advertises the shape the preset actually applies. A tab drawn as a pill here is
     * the one thing this swatch exists to prevent.
     */
    previewShape: String = HandlerShape.ROUNDED,
    previewFlare: Float = HandlerShape.DEFAULT_FLARE,
    previewColor: Color = MaterialTheme.colorScheme.primary,
    previewAlpha: Float = 0.8f,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp

    Surface(
        modifier = modifier
            .height(90.dp)
            .clickable(onClick = onClick)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Icon + Text
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .height(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
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
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right: Visual handler preview strip
            if (previewShape == HandlerShape.TAB) {
                Canvas(
                    modifier = Modifier
                        .width(previewWidth)
                        .fillMaxHeight()
                ) {
                    // The swatch sits at the right of the card, so its right edge stands in for
                    // the screen edge — which is the side a tab's sweeps run to.
                    val outline = HandlerShape.tabOutline(
                        size.width,
                        size.height,
                        previewFlare,
                        edgeOnLeft = false,
                    )
                    val path = Path().apply {
                        moveTo(outline[0], outline[1])
                        var i = 2
                        while (i < outline.size) {
                            lineTo(outline[i], outline[i + 1])
                            i += 2
                        }
                        close()
                    }
                    drawPath(path, previewColor.copy(alpha = previewAlpha))
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(previewWidth)
                        .fillMaxHeight()
                        // The swatch sits at the right of the card, so its right edge is the one
                        // standing in for the screen edge.
                        .clip(
                            RoundedCornerShape(
                                topStart = previewCorner,
                                bottomStart = previewCorner,
                                topEnd = previewOuterCorner ?: previewCorner,
                                bottomEnd = previewOuterCorner ?: previewCorner,
                            )
                        )
                        .background(previewColor.copy(alpha = previewAlpha))
                )
            }
        }
    }
}