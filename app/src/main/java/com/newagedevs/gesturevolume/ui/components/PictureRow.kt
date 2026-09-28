package com.newagedevs.gesturevolume.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.ui.motion.pressBounce

/**
 * One row of choices that scrolls sideways, each a small picture over its name: the way the panels'
 * opening animations, the Quick panel's fills and the bar's keyboard motion are all chosen.
 *
 * A single row rather than a wrapping grid of words, because the words were the problem: "Blinds",
 * "Tide" and "Silk" are not things anybody can picture, and a grid of forty of them was a wall of
 * text above the setting it sets. A picture says what the name cannot; a row keeps the choices to
 * one line of the page however many there are, a swipe away from each other; and the preview above
 * still plays the one picked, full size.
 *
 * Opens on the chosen one, scrolled into view.
 *
 * @param picture draws [item] in its tile, which is [tileWidth] square and a little taller.
 */
@Composable
fun <T> PictureRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    tileWidth: Dp = PICTURE_TILE_WIDTH,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    picture: @Composable BoxScope.(item: T, selected: Boolean) -> Unit,
) {
    val state = rememberLazyListState()
    // Scrolled to what is chosen, one tile of its neighbours before it so it does not sit against
    // the edge. Once: after that the row stays where the finger leaves it.
    LaunchedEffect(Unit) {
        val index = items.indexOf(selected)
        if (index > 0) state.scrollToItem((index - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = contentPadding,
    ) {
        itemsIndexed(items) { _, item ->
            PictureTile(
                label = label(item),
                selected = item == selected,
                width = tileWidth,
                onClick = { onSelect(item) },
            ) { picture(item, item == selected) }
        }
    }
}

@Composable
private fun PictureTile(
    label: String,
    selected: Boolean,
    width: Dp,
    onClick: () -> Unit,
    picture: @Composable BoxScope.() -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    val border by animateColorAsState(
        targetValue = if (selected) colours.primary else colours.outlineVariant.copy(alpha = 0.6f),
        label = "pictureTileBorder",
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, label = "pictureTileBorderWidth")
    val ink by animateColorAsState(
        targetValue = if (selected) colours.primary else colours.onSurfaceVariant,
        label = "pictureTileInk",
    )
    Column(
        modifier = Modifier
            .width(width)
            .pressBounce(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                // Fires even when it is already the chosen one, on purpose: tapping it again is how
                // its animation is watched a second time in the preview above.
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width, width + 12.dp)
                .clip(shape)
                .background(colours.surfaceContainerHigh)
                .border(BorderStroke(borderWidth, border), shape),
        ) {
            picture()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colours.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colours.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp)
                .height(34.dp),
        )
    }
}

/**
 * The inside of a tile: a sliver of the walkthrough's phone — its wallpaper, as far as a tile shows
 * it — for a picture of something on a phone's screen to be drawn over.
 */
@Composable
fun BoxScope.TileWallpaper() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(DeviceArt.WallTop, DeviceArt.WallBottom)),
            ),
    )
}

/**
 * One clock for every picture in a row, in milliseconds through a loop of [loopMs], so the tiles
 * that move keep time with each other rather than each running its own.
 */
@Composable
fun rememberTileClock(loopMs: Int): State<Float> =
    rememberInfiniteTransition(label = "tileClock").animateFloat(
        initialValue = 0f,
        targetValue = loopMs.toFloat(),
        animationSpec = infiniteRepeatable(tween(loopMs, easing = LinearEasing), RepeatMode.Restart),
        label = "tileClockMs",
    )

/** How wide a tile is: four and a bit across a phone, so the row reads as something to swipe. */
val PICTURE_TILE_WIDTH = 76.dp
