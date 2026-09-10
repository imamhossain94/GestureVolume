package com.newagedevs.gesturevolume.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.AppearanceMotion
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.PanelTheme

/**
 * The long-press menu, drawn beside the bar.
 *
 * Placement is decided the way the View-based menu decided it: the card opens on whichever side of
 * the bar has room, preferring the side away from the nearer screen edge, and sits centred on the
 * bar vertically before being pulled back inside the frame. All of it happens in the layout pass,
 * when the card's real size is known, so a tall menu on a bar dragged low is never cut off.
 *
 * The full-screen root is what catches the tap outside the card that dismisses it: the window is
 * not focusable, so there is no back-button route and an outside tap is the only way out.
 *
 * @param anchor the bar's window rectangle, in pixels relative to the usable frame.
 * @param frame the usable frame, in pixels.
 */
@Composable
fun ContextMenuOverlay(
    entries: List<HandlerActionCatalog.Entry>,
    anchor: IntRect,
    frame: IntSize,
    grid: Boolean,
    theme: String,
    onSelect: (HandlerActionCatalog.Entry) -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = AppearanceMotion.Pop,
        label = "menuScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = AppearanceMotion.Fade,
        label = "menuAlpha"
    )

    // Which side the card opened on, for the transform origin. Decided in layout, read in draw.
    var openedRight by remember { mutableStateOf(true) }
    val gapPx = with(LocalDensity.current) { 10.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        ContextMenuCard(
            entries = entries,
            grid = grid,
            theme = theme,
            onSelect = onSelect,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val cardW = placeable.width
                    val cardH = placeable.height
                    val spaceRight = frame.width - anchor.right
                    val spaceLeft = anchor.left
                    val needed = cardW + gapPx * 2
                    val right = when {
                        spaceRight >= needed && spaceLeft >= needed ->
                            (anchor.left + anchor.width / 2) < frame.width / 2
                        spaceRight >= needed -> true
                        spaceLeft >= needed -> false
                        else -> spaceRight >= spaceLeft
                    }
                    openedRight = right
                    val x = (if (right) anchor.right + gapPx else anchor.left - cardW - gapPx)
                        .coerceIn(gapPx, (frame.width - cardW - gapPx).coerceAtLeast(gapPx))
                    val y = (anchor.top + anchor.height / 2 - cardH / 2)
                        .coerceIn(gapPx, (frame.height - cardH - gapPx).coerceAtLeast(gapPx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(x, y)
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(if (openedRight) 0f else 1f, 0.5f)
                }
                // Swallows the tap so the root's dismiss does not fire for a press on the card.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
    }
}

/**
 * The menu itself, without any opinion about where it sits.
 *
 * Separated from the overlay so the settings screen can show the real thing rather than a drawing
 * of it. A preview built from a second implementation is a preview that drifts: the grid gets a
 * new tile size here and the picture over there keeps the old one, and the user is choosing
 * against something that no longer exists. This way there is one menu, rendered twice.
 */
@Composable
fun ContextMenuCard(
    entries: List<HandlerActionCatalog.Entry>,
    grid: Boolean,
    onSelect: (HandlerActionCatalog.Entry) -> Unit,
    modifier: Modifier = Modifier,
    theme: String = PanelTheme.SOLID,
) {
    val shape = RoundedCornerShape(MENU_CORNER)
    Column(
        modifier = modifier
            // The grid is sized by its columns and the list by its longest label, so the two want
            // different widths from the same modifier chain. Fixed for the grid because a
            // three-column tile layout with a flexible width is a layout that reflows depending
            // on which actions you happened to choose.
            .then(
                if (grid) {
                    Modifier.width(GRID_TILE_W * GRID_COLUMNS + MENU_PADDING * 2)
                } else {
                    Modifier.widthIn(min = 180.dp, max = 270.dp)
                }
            )
            .clip(shape)
            .background(MENU_SURFACE.copy(alpha = MENU_SURFACE.alpha * PanelTheme.surfaceAlpha(theme)))
            .then(
                if (PanelTheme.hasLitEdge(theme)) {
                    Modifier.liquidGlass(MENU_CORNER)
                } else {
                    Modifier.border(1.dp, MENU_STROKE, shape)
                }
            )
            .padding(MENU_PADDING)
            .verticalScroll(rememberScrollState())
    ) {
        if (grid) {
            // Chunked into rows rather than drawn with a lazy grid, because the whole menu is on
            // screen at once inside a scrolling column — a lazy grid nested in that has no height
            // of its own to resolve against.
            entries.chunked(GRID_COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    // A short last row is centred under the full ones rather than left-aligned
                    // with a hole beside it. Seven entries in a three-wide grid is the common
                    // case, not an edge case, so what that last row looks like *is* what the menu
                    // looks like.
                    horizontalArrangement = Arrangement.Center,
                ) {
                    row.forEach { entry -> GridTile(entry, onSelect) }
                }
            }
        } else {
            entries.forEach { entry -> ListRow(entry, onSelect) }
        }
    }
}

/**
 * One action in the grid: its icon on a chip, its name underneath.
 *
 * **Why the chip.** Bare glyphs on a dark card read as a list of symbols floating in a void — the
 * eye has nothing to aim at and no sense of where one target ends and the next begins. A filled
 * shape behind each one gives the row a rhythm and makes the tap target visible, which is most of
 * the difference between this and something that looks unfinished.
 *
 * **Why the height is fixed.** The labels are not the same length — "Mute or Unmute" wraps to two
 * lines where "Search" takes one — so a tile sized to its content makes every row a different
 * height and the grid comes out ragged. Reserving both lines on every tile costs a few pixels of
 * card and is the whole reason the rows line up.
 *
 * The label is kept, rather than dropped for a pure icon grid: around forty actions can end up
 * here and a good many share an icon family — three different "open something in the Deck"
 * entries, two volume ones — so an unlabelled grid would be a memory test.
 */
@Composable
private fun GridTile(
    entry: HandlerActionCatalog.Entry,
    onSelect: (HandlerActionCatalog.Entry) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(GRID_TILE_W)
            .height(GRID_TILE_H)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect(entry) }
            .padding(horizontal = 3.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(ICON_CHIP)
                .clip(RoundedCornerShape(14.dp))
                .background(MENU_CHIP),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = entry.icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MENU_ON_SURFACE
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(entry.labelRes),
            color = MENU_ON_SURFACE_DIM,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One labelled row in the list, wearing the same icon chip the grid does. */
@Composable
private fun ListRow(
    entry: HandlerActionCatalog.Entry,
    onSelect: (HandlerActionCatalog.Entry) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect(entry) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ICON_CHIP)
                .clip(RoundedCornerShape(14.dp))
                .background(MENU_CHIP),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = entry.icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MENU_ON_SURFACE
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(entry.labelRes),
            color = MENU_ON_SURFACE,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Three across: as many as fit beside the bar without the card reaching the far edge. */
private const val GRID_COLUMNS = 3

/** Wide enough for two words of label; tall enough for two lines of it on every tile. */
private val GRID_TILE_W = 74.dp
private val GRID_TILE_H = 88.dp

/** The filled square behind each icon. Shared by both layouts, which is what unifies them. */
private val ICON_CHIP = 40.dp

private val MENU_PADDING = 8.dp
private val MENU_CORNER = 24.dp

/** Menu chrome. Fixed dark surface: the overlay has no theme of its own to follow. */
private val MENU_SURFACE = Color(0xF41C1C20)
private val MENU_STROKE = Color(0x1FFFFFFF)
private val MENU_ON_SURFACE = Color(0xF2FFFFFF)

/** The label under a grid tile. Dimmer than the icon, so the icon leads and the word confirms. */
private val MENU_ON_SURFACE_DIM = Color(0xB8FFFFFF)

/** The chip behind each icon. Light enough to separate it from the card, dark enough to recede. */
private val MENU_CHIP = Color(0x1AFFFFFF)

/**
 * The lighting that turns a translucent rectangle into a piece of glass.
 *
 * Three things, and the first version of this had only one of them — a flat gradient border —
 * which is why it read as "the same card, fainter" rather than as a material.
 *
 *  1. **A sheen.** Light falls on the top of an object, so the upper part of the surface is
 *     brighter than the lower. Without this the panel is uniformly grey and looks flat however
 *     transparent it is.
 *  2. **A specular rim.** Not a border of even weight: bright along the top edge where light
 *     catches it, almost gone by the bottom. This is the single strongest cue, and it is what
 *     lets the fill be as weak as it is.
 *  3. **A counter-light along the bottom.** Faint, and the reason it is there is that real glass
 *     picks up light bouncing back from whatever it is sitting on. Leaving it out makes the lower
 *     half of the panel look unfinished.
 *
 * All of it is drawn rather than composed from `border`, because a border cannot vary its own
 * weight around the shape and these two rims are different strengths.
 */
private fun Modifier.liquidGlass(cornerRadius: Dp): Modifier = drawWithContent {
    val r = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())

    // 1. the sheen, over the top of the surface only
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color(0x2EFFFFFF),
            0.45f to Color(0x08FFFFFF),
            1f to Color.Transparent,
        ),
        cornerRadius = r,
    )

    // 3. the counter-light, before the content so it stays behind the labels
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.82f to Color.Transparent,
            1f to Color(0x1AFFFFFF),
        ),
        cornerRadius = r,
    )

    drawContent()

    // 2. the specular rim, last, so nothing draws over the edge
    val stroke = 1.2.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color(0xA6FFFFFF),
            0.35f to Color(0x3DFFFFFF),
            0.75f to Color(0x14FFFFFF),
            1f to Color(0x4DFFFFFF),
        ),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = r,
        style = Stroke(width = stroke),
    )
}
