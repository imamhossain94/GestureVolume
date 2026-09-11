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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.newagedevs.gesturevolume.utils.PanelAnimation
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
    /** Which of [PanelAnimation]'s entrances to play. */
    animation: String,
    /** How fast to play it, as a multiple of the catalogue's own timing. */
    animationSpeed: Float,
    /** Set when the menu is on its way out, so the entrance runs backwards. */
    closing: Boolean,
    /** A surface colour of the user's own, or null to take the material's. */
    surfaceOverride: Long?,
    onSelect: (HandlerActionCatalog.Entry) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Where the card ended up, in window pixels, with the radius its corners are cut to.
     *
     * Reported out for the same reason the Deck reports its surfaces: the blur behind this card
     * lives in a window of its own — a `WindowManager` overlay has no `Window`, and only a
     * `Window` can blur *within its own bounds* rather than across the whole screen. That window
     * has to be told where the card is, and this is the placement that actually happened rather
     * than a second calculation of it that could disagree.
     */
    onCardBounds: (IntRect, Float) -> Unit = { _, _ -> },
) {
    /*
     * The entrance, from the catalogue the user picked out of.
     *
     * Every one of them transforms the card's *contents*, never the rectangle the card occupies —
     * there is a blurred window sitting exactly underneath it whose bounds are window attributes,
     * and pushing new ones at it every frame is a relayout per frame. A card that grew or slid
     * would spend its entrance travelling across a stationary pane of glass. See [PanelAnimation].
     */
    // Which edge the card grows from, taken from the bar rather than from the layout pass: the
    // entrance has to start on the first frame, and the layout that finally settles which side the
    // card opens on has not run yet. The bar's own side is the same answer in every case that
    // matters — the card opens away from it — and where the frame is too narrow for that, the
    // difference is a hinge on the wrong edge of a card that filled the screen anyway.
    val barOnLeft = anchor.left + anchor.width / 2 < frame.width / 2
    val entrance = rememberPanelEntrance(
        animation = animation,
        towardLeft = barOnLeft,
        closing = closing,
        speed = animationSpeed,
    )

    val gapPx = with(LocalDensity.current) { 10.dp.roundToPx() }
    val cornerPx = with(LocalDensity.current) { MENU_CORNER.toPx() }

    /** Where the layout pass put the card, before the entrance moves it. */
    var placedRect by remember { mutableStateOf(IntRect.Zero) }
    val density = LocalDensity.current

    /*
     * The glass follows the card, frame by frame.
     *
     * Collected from a snapshot flow rather than read in the composition: reading an animating
     * state in the body would recompose the whole menu sixty times a second to move a window that
     * is not part of it. This way the animation drives exactly one thing — the rectangle the blur
     * is drawn in — and the menu itself is composed once.
     */
    LaunchedEffect(placedRect, cornerPx) {
        if (placedRect.width <= 0) return@LaunchedEffect
        snapshotFlow { entrance.value }.collect { f ->
            val box = PanelAnimation.bounds(
                placedRect.left.toFloat(),
                placedRect.top.toFloat(),
                placedRect.right.toFloat(),
                placedRect.bottom.toFloat(),
                f,
                with(density) { f.translationX.dp.toPx() },
                with(density) { f.translationY.dp.toPx() },
            )
            // Nothing to blur until the card is actually visible: the glass is opaque from its
            // first frame, so putting it up under a card that has not faded in yet is a blurred
            // rectangle arriving on its own.
            val visible = f.alpha > 0.12f
            onCardBounds(
                IntRect(
                    box[0].toInt(),
                    box[1].toInt(),
                    if (visible) box[2].toInt() else box[0].toInt(),
                    if (visible) box[3].toInt() else box[1].toInt(),
                ),
                cornerPx * minOf(f.scaleX, f.scaleY).coerceIn(0.2f, 1f),
            )
        }
    }

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
            surfaceOverride = surfaceOverride,
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
                    val x = (if (right) anchor.right + gapPx else anchor.left - cardW - gapPx)
                        .coerceIn(gapPx, (frame.width - cardW - gapPx).coerceAtLeast(gapPx))
                    val y = (anchor.top + anchor.height / 2 - cardH / 2)
                        .coerceIn(gapPx, (frame.height - cardH - gapPx).coerceAtLeast(gapPx))
                    placedRect = IntRect(x, y, x + cardW, y + cardH)
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(x, y)
                    }
                }
                // The whole card, surface and all. It used to be the contents only, because the
                // pane of glass behind the card could not follow it; the pane is moved per frame
                // now — see [PanelBackdrop] — so there is no longer any reason for the background
                // to sit still while the things on it move, which is what made the entrances look
                // like they were happening to the wrong object.
                .panelFrame(entrance.value)
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
    /** A surface colour of the user's own, or null to take the material's. */
    surfaceOverride: Long? = null,
) {
    val shape = RoundedCornerShape(MENU_CORNER)
    val palette = PanelTheme.menuPalette(theme, surfaceOverride)
    val surface = Color(palette.surface)
    val onSurface = Color(palette.onSurface)
    val onSurfaceDim = Color(palette.onSurfaceDim)
    val chip = Color(palette.chip)
    val divider = Color(palette.divider)

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
            .background(surface)
            .then(
                if (PanelTheme.hasLitEdge(theme)) {
                    Modifier.liquidGlass(MENU_CORNER, PanelTheme.luminanceOf(palette.surface) > 0.5)
                } else {
                    Modifier.border(1.dp, Color(palette.border), shape)
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
                    row.forEach { entry -> GridTile(entry, onSurface, onSurfaceDim, chip, onSelect) }
                }
            }
        } else {
            // Hairlines between the rows, not around them. On a material this transparent the
            // rows would otherwise float in a wash of whatever is behind the panel with nothing
            // saying where one target ends and the next begins — which is exactly what a real
            // frosted menu uses a divider for.
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = ICON_CHIP + 20.dp, end = 8.dp),
                        thickness = Dp.Hairline,
                        color = divider,
                    )
                }
                ListRow(entry, onSurface, chip, onSelect)
            }
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
    onSurface: Color,
    onSurfaceDim: Color,
    chip: Color,
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
                .background(chip),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = entry.icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(entry.labelRes),
            color = onSurfaceDim,
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
    onSurface: Color,
    chip: Color,
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
                .background(chip),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = entry.icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = onSurface
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(entry.labelRes),
            color = onSurface,
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
private fun Modifier.liquidGlass(cornerRadius: Dp, light: Boolean): Modifier = drawWithContent {
    // A pale pane is already brighter than what is behind it, so white piled on white flattens it.
    // Its highlights are pulled back to roughly a third and the rim leans on contrast with the
    // screen behind instead.
    val k = if (light) 0.45f else 1f
    fun w(alpha: Int): Color = Color(((alpha * k).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF)
    val r = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())

    // 1. the sheen, over the top of the surface only
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to w(0x2E),
            0.45f to w(0x08),
            1f to Color.Transparent,
        ),
        cornerRadius = r,
    )

    // 3. the counter-light, before the content so it stays behind the labels
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.82f to Color.Transparent,
            1f to w(0x1A),
        ),
        cornerRadius = r,
    )

    drawContent()

    // 2. the specular rim, last, so nothing draws over the edge
    val stroke = 1.2.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to w(0xA6),
            0.35f to w(0x3D),
            0.75f to w(0x14),
            1f to w(0x4D),
        ),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = r,
        style = Stroke(width = stroke),
    )
}
