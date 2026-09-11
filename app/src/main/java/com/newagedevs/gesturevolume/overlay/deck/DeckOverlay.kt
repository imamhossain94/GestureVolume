package com.newagedevs.gesturevolume.overlay.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import com.newagedevs.gesturevolume.overlay.rememberPanelEntrance
import com.newagedevs.gesturevolume.overlay.panelFrame
import com.newagedevs.gesturevolume.utils.PanelAnimation
import com.newagedevs.gesturevolume.utils.PanelTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickDialEntry
import kotlin.math.max
import kotlin.math.min

/**
 * The colours a Deck surface is drawn with: the user's background and accent, and the text
 * colour that reads on that background.
 */
class DeckPalette(
    surface: Color,
    accent: Color,
    /**
     * How much of the Deck's own background survives, from [com.newagedevs.gesturevolume.utils.PanelTheme].
     *
     * Multiplied into the colour rather than applied to the panel as a whole, for the reason the
     * Quick panel gives: the text and tiles on this surface have to stay legible on a surface that
     * is deliberately see-through, and fading the panel would fade them with it.
     */
    surfaceAlpha: Float = 1f,
) {
    val background: Color = surface.copy(alpha = surface.alpha * surfaceAlpha)

    /**
     * Which way the ink runs, decided from the surface rather than declared.
     *
     * `luminance()` ignores alpha, so this is asking about the *colour* of the pane and not how
     * much of it there is — which is the right question: a pale pane at a third opacity is still
     * a pale pane, because what shows through it has been blurred to a wash of the same
     * brightness.
     */
    val light: Boolean = surface.luminance() > 0.5f

    val onBackground: Color = if (light) Color(0xFF111111) else Color.White
    val subtle: Color = onBackground.copy(alpha = 0.62f)

    /**
     * The accent, forced to something that can be seen.
     *
     * The default accent is white, which is invisible on a pale pane — and it is not just the
     * default: every tile glyph, every quick-dial initial and half the card chrome is drawn in it,
     * so a white accent on frosted glass is most of the Deck disappearing. A *coloured* accent
     * survives untouched; only one too close to the surface's own brightness is pulled to the ink
     * colour, which is the smallest change that keeps the panel readable.
     */
    val accent: Color =
        if (light && accent.luminance() > 0.45f) Color(0xFF111111) else accent

    val onAccent: Color = if (this.accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val chip: Color = this.accent.copy(alpha = if (light) 0.10f else 0.14f)
}

/**
 * The Deck: a strip of shortcuts and tools beside the bar, and a card for whichever tool is open.
 *
 * The strip is placed the way the long-press menu is placed — against the bar's edge, centred on
 * the bar, pulled back inside the frame — and the card opens toward the middle of the screen
 * from the strip's top. Both are positioned in the layout pass from their measured sizes, so a
 * strip shorter than the height budget still sits on the bar rather than where the budget would
 * have put it.
 *
 * A tap outside either dismisses. Every touch anywhere pushes the auto-close back, which the
 * root view reports; nothing here has to remember to.
 */
/**
 * The Deck's placed surfaces, for whoever has to line something up behind them.
 *
 * Corner radii travel with the rectangles because the blur behind each one is rounded off to
 * match, and the two shapes do not share a radius.
 */
data class DeckSurfaces(
    val strip: IntRect,
    val stripCornerPx: Float,
    val card: IntRect?,
    val cardCornerPx: Float,
    /** How strongly to blur behind both, 0 to 1. See [PanelAnimation.glassStrength]. */
    val strength: Float = 1f,
)

@Composable
fun DeckOverlay(
    model: DeckModel,
    actions: DeckActions,
    onDismiss: () -> Unit,
    /**
     * Where the Deck's own surfaces ended up, in window coordinates.
     *
     * Reported out because the blur behind the Deck lives in *other* windows — see
     * `OverlayController.showDeck` and [com.newagedevs.gesturevolume.overlay.PanelBackdrop] — and
     * those have to match these rectangles exactly or the blur shows up where the panel is not.
     * Emitted from the layout pass, so it is the placement that actually happened rather than a
     * second calculation of it that could disagree.
     */
    onSurfaces: (DeckSurfaces) -> Unit = {},
    /** Set when the Deck is on its way out, so its entrance runs backwards. */
    closing: Boolean = false,
    /** How fast the entrance runs, as a multiple of the catalogue's own timing. */
    animationSpeed: Float = 1f,
) {
    val state = actions.env.state
    val palette = remember(model.config, model.panelTheme) {
        // A pale material brings its own surface, alpha included; everything else keeps the colour
        // the user picked and only has it thinned. See PanelTheme.panelSurface.
        val forced = PanelTheme.panelSurface(model.panelTheme)
        DeckPalette(
            surface = forced?.let { Color(it) } ?: model.config.background,
            accent = model.config.accent,
            surfaceAlpha = if (forced != null) 1f else PanelTheme.surfaceAlpha(model.panelTheme),
        )
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val density = LocalDensity.current
    val stripWidthPx = with(density) { model.config.widthDp.dp.roundToPx() }
    val maxStripHeightPx = (model.frame.height * model.config.heightFraction).toInt()
    val edgePx = with(density) { 6.dp.roundToPx() }
    val gapPx = with(density) { 8.dp.roundToPx() }
    val cardWidthPx = with(density) { 320.dp.roundToPx() }
    // Resolved out here rather than inside the measure block: `MeasureScope` has a `density` of
    // its own — a Float — and inside the block it shadows this one.
    val stripCornerPx = with(density) { model.config.cornerDp.dp.toPx() }
    val cardCornerPx = with(density) { DECK_CARD_CORNER.toPx() }

    val expanded = state.expandedTile
    val expandedTile = expanded?.let { DeckTiles.byId(it) }

    // Both grow out of the side they are anchored to, which is where the bar is.
    val stripOrigin = TransformOrigin(if (model.isLeft) 0f else 1f, 0.5f)
    val cardOrigin = TransformOrigin(if (model.isLeft) 0f else 1f, 0.5f)

    val entrance = rememberPanelEntrance(
        animation = model.animation,
        towardLeft = model.isLeft,
        closing = closing,
        speed = animationSpeed,
        settle = true,
    )

    /** What the layout pass placed, before the entrance moves it. Reported on, transformed. */
    var placed by remember { mutableStateOf<DeckSurfaces?>(null) }

    /*
     * The glass stays where the strip comes to rest and fades with it — see
     * [PanelAnimation.glassStrength] for why it no longer follows. Collected from a snapshot flow
     * rather than read in the composition, so an animating value drives the one window it has to
     * and does not recompose the Deck to do it.
     */
    LaunchedEffect(placed) {
        val base = placed ?: return@LaunchedEffect
        snapshotFlow { entrance.value }.collect { f ->
            val tx = with(density) { f.translationX.dp.toPx() }
            val ty = with(density) { f.translationY.dp.toPx() }
            val s = base.strip
            val box = PanelAnimation.bounds(
                s.left.toFloat(), s.top.toFloat(), s.right.toFloat(), s.bottom.toFloat(), f, tx, ty,
            )
            val strength = PanelAnimation.glassStrength(
                box, s.left.toFloat(), s.top.toFloat(), s.right.toFloat(), s.bottom.toFloat(), f.alpha,
            )
            onSurfaces(base.copy(strength = strength))
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
        Layout(
            content = {
                Box(modifier = Modifier.layoutId("strip")) {
                    AnimatedVisibility(
                        visible = shown,
                        // The entrance is played by the strip itself, from the catalogue the user
                        // picked out of — see [PanelAnimation], and the note there on why every
                        // one of them is a draw-layer transform rather than a move. Leaving it to
                        // AnimatedVisibility would mean two animations on the same surface, and
                        // the one that is not the user's choice would win the argument.
                        enter = EnterTransition.None,
                        exit = fadeOut(PANEL_FADE) + scaleOut(PANEL_SCALE, DECK_ENTER_SCALE, stripOrigin)
                    ) {
                        Box(modifier = Modifier.panelFrame { entrance.value }) {
                            DeckStrip(model, actions, palette, stripWidthPx)
                        }
                    }
                }
                Box(modifier = Modifier.layoutId("card")) {
                    AnimatedVisibility(
                        visible = expandedTile != null,
                        enter = fadeIn(PANEL_FADE) + scaleIn(PANEL_SCALE, DECK_ENTER_SCALE, cardOrigin),
                        exit = fadeOut(PANEL_FADE) + scaleOut(PANEL_SCALE, DECK_ENTER_SCALE, cardOrigin),
                    ) {
                        // The last tile is held so the exit animation has something to draw.
                        var lastTile by remember { mutableStateOf(expandedTile) }
                        if (expandedTile != null) lastTile = expandedTile
                        lastTile?.let { tile ->
                            DeckCard(
                                tile = tile,
                                palette = palette,
                                glass = PanelTheme.hasLitEdge(model.panelTheme),
                                onClose = { state.expandedTile = null }
                            ) {
                                DeckCardContent(tile, actions, palette)
                            }
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val frameW = model.frame.width
            val frameH = model.frame.height
            val strip = measurables.first { it.layoutId == "strip" }
                .measure(Constraints(maxWidth = stripWidthPx, maxHeight = max(0, maxStripHeightPx)))
            val cardMaxW = min(cardWidthPx, frameW - stripWidthPx - edgePx - gapPx * 2).coerceAtLeast(0)
            val card = measurables.first { it.layoutId == "card" }
                .measure(Constraints(maxWidth = cardMaxW, maxHeight = max(0, frameH - gapPx * 2)))

            val stripX = if (model.isLeft) edgePx else frameW - edgePx - strip.width
            val stripY = (model.anchor.top + model.anchor.height / 2 - strip.height / 2)
                .coerceIn(gapPx, max(gapPx, frameH - strip.height - gapPx))
            val cardX = if (model.isLeft) stripX + strip.width + gapPx else stripX - gapPx - card.width
            val cardY = stripY.coerceIn(gapPx, max(gapPx, frameH - card.height - gapPx))

            // Reported as two rectangles rather than one enclosing both: the backdrop behind
            // them is a rounded rectangle, and a single one spanning the pair would also blur the
            // gap down the middle. The card is measured even when no tile is expanded — it is what
            // the exit animation draws — so it only counts while there is a tile to show.
            placed = DeckSurfaces(
                strip = IntRect(stripX, stripY, stripX + strip.width, stripY + strip.height),
                stripCornerPx = stripCornerPx,
                card = if (expandedTile != null) {
                    IntRect(cardX, cardY, cardX + card.width, cardY + card.height)
                } else {
                    null
                },
                cardCornerPx = cardCornerPx,
            )

            layout(constraints.maxWidth, constraints.maxHeight) {
                strip.place(stripX, stripY)
                card.place(cardX, cardY)
            }
        }
    }
}

@Composable
private fun DeckStrip(
    model: DeckModel,
    actions: DeckActions,
    palette: DeckPalette,
    stripWidthPx: Int
) {
    val glass = PanelTheme.hasLitEdge(model.panelTheme)
    val state = actions.env.state
    val density = LocalDensity.current
    val stripWidth = with(density) { stripWidthPx.toDp() }
    val shape = RoundedCornerShape(model.config.cornerDp.dp)

    Column(
        modifier = Modifier
            .width(stripWidth)
            .clip(shape)
            .background(palette.background)
            .then(if (glass) Modifier.liquidGlass(model.config.cornerDp.dp, palette.light) else Modifier)
            // Swallows the tap so the root's dismiss does not fire for a press on the strip.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val shortcuts: @Composable () -> Unit = {
            model.quickDial.forEach { entry ->
                QuickDialButton(entry, palette) { actions.dial(entry) }
            }
            model.apps.forEach { app ->
                AppButton(app, palette) { actions.launchApp(app.packageName) }
            }
        }
        val hasShortcuts = model.quickDial.isNotEmpty() || model.apps.isNotEmpty()
        val tiles: @Composable () -> Unit = {
            // Read behind the version so a toggle re-lights its tile without mirroring state.
            @Suppress("UNUSED_VARIABLE") val version = state.toggleVersion
            model.tiles.forEach { tile ->
                val active = when (tile.id) {
                    DeckTiles.FLASHLIGHT -> actions.env.toggles.isFlashlightOn()
                    DeckTiles.DND -> actions.env.toggles.isDndOn()
                    DeckTiles.ROTATION -> actions.env.toggles.isAutoRotateOn()
                    DeckTiles.TIMER -> state.timerRunning
                    else -> false
                } || state.expandedTile == tile.id
                TileButton(
                    icon = tile.icon,
                    label = stringResource(tile.labelRes),
                    active = active,
                    palette = palette
                ) { onTileTap(tile, actions) }
            }
        }

        if (model.config.utilitiesFirst) {
            tiles()
            if (hasShortcuts && model.tiles.isNotEmpty()) StripDivider(palette)
            shortcuts()
        } else {
            shortcuts()
            if (hasShortcuts && model.tiles.isNotEmpty()) StripDivider(palette)
            tiles()
        }
    }
}

/** What a tap on a tile does, by kind. */
private fun onTileTap(tile: DeckTile, actions: DeckActions) {
    val state = actions.env.state
    when (tile.kind) {
        DeckTileKind.TOGGLE -> {
            tile.action?.let { actions.runAction(it) }
            state.toggleVersion++
        }
        DeckTileKind.LAUNCH -> {
            when (tile.id) {
                DeckTiles.WIFI -> {
                    actions.close()
                    actions.launch(actions.env.toggles.wifiPanelIntent())
                }
                DeckTiles.BLUETOOTH -> {
                    actions.close()
                    actions.launch(actions.env.toggles.bluetoothSettingsIntent())
                }
                else -> {
                    // Leaves the Deck: the screenshot must not have it in the picture, the lock
                    // must not leave it open on the lock screen, and the scanner is another app.
                    actions.close()
                    tile.action?.let { actions.runAction(it) }
                }
            }
        }
        DeckTileKind.PANEL -> {
            state.expandedTile = if (state.expandedTile == tile.id) null else tile.id
        }
    }
}

@Composable
private fun StripDivider(palette: DeckPalette) {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .width(22.dp)
            .height(1.dp)
            .background(palette.onBackground.copy(alpha = 0.18f))
    )
}

@Composable
fun TileButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    palette: DeckPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) palette.accent else palette.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (active) palette.onAccent else palette.accent
        )
    }
}

@Composable
private fun AppButton(app: AppShortcut, palette: DeckPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
            )
        } else {
            // Coloured explicitly. This was the one label in the Deck that took whatever the
            // scheme handed it, which on a pale pane is the difference between an initial and a
            // blank tile.
            Text(
                text = app.label.take(1).uppercase(),
                color = palette.onBackground,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickDialButton(entry: QuickDialEntry, palette: DeckPalette, onClick: () -> Unit) {
    val initials = remember(entry.name) {
        entry.name.split(' ').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "#" }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = 0.22f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = palette.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** The card frame: a header with the tile's icon, name and a close button, then the content. */
@Composable
fun DeckCard(
    tile: DeckTile,
    palette: DeckPalette,
    onClose: () -> Unit,
    // Before `content`, so the caller can still pass the card body as a trailing lambda.
    glass: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(DECK_CARD_CORNER))
            .background(palette.background)
            .then(if (glass) Modifier.liquidGlass(DECK_CARD_CORNER, palette.light) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 14.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = palette.accent
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(tile.labelRes),
                color = palette.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.deck_close),
                    tint = palette.subtle
                )
            }
        }
        Box(modifier = Modifier.padding(end = 8.dp)) {
            content()
        }
    }
}

/**
 * The lighting that turns a translucent panel into a piece of glass.
 *
 * The same three cues the long-press menu uses, and deliberately the same numbers: a sheen across
 * the top of the surface, a specular rim that is bright along the top edge and nearly gone by the
 * bottom, and a faint counter-light along the bottom where real glass picks up what it is sitting
 * on. A border of even weight cannot express the second of those, which is why this is drawn.
 *
 * Duplicated rather than shared with `ContextMenuOverlay` because the two live in different
 * packages with different private palettes, and the numbers here are the whole of what is shared —
 * lifting them into a common file would move four colours and leave the drawing behind.
 */
internal fun Modifier.liquidGlass(cornerRadius: Dp, light: Boolean = false): Modifier = drawWithContent {
    // A pale pane is already brighter than what is behind it, so white piled on white flattens it.
    // Its highlights are pulled back to under a half and the rim leans on contrast with the screen
    // behind instead. The menu's copy of this does the same, for the same reason.
    val k = if (light) 0.45f else 1f
    fun w(alpha: Int): Color = Color(((alpha * k).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF)
    val r = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())

    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to w(0x2E),
            0.45f to w(0x08),
            1f to Color.Transparent,
        ),
        cornerRadius = r,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.82f to Color.Transparent,
            1f to w(0x1A),
        ),
        cornerRadius = r,
    )

    drawContent()

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

/** The expanding card's corner. Named so its shape and its lighting cannot drift apart. */
private val DECK_CARD_CORNER = 22.dp

/**
 * How the Deck's surfaces arrive.
 *
 * A tween rather than a spring, and this is the one place in the app where that is not a matter of
 * taste. A spring on a scale overshoots past 1, and the pane of blurred glass behind the panel
 * cannot overshoot with it — its size is a window attribute, not something that can be animated
 * per frame without a relayout on every one. Under a spring the panel would swell a few percent
 * past its glass and settle back; under a tween it never leaves it.
 */
private val PANEL_FADE: FiniteAnimationSpec<Float> = tween(durationMillis = 190)
private val PANEL_SCALE: FiniteAnimationSpec<Float> = tween(durationMillis = 220)

/**
 * How small a surface starts.
 *
 * Close to 1 on purpose. The scale is standing in for a slide, and its whole job is to say "this
 * came from over there" without the panel's painted edge ever straying far from the blurred pane
 * it is lined up against.
 */
private const val DECK_ENTER_SCALE = 0.94f

/**
 * A picture of the Deck's strip, for the settings screen.
 *
 * Not the strip itself. [DeckStrip] needs a [DeckActions], which needs an environment holding a
 * context, the toggles, the volume and brightness controllers and the live Deck state — the whole
 * running overlay, in other words, which a settings screen has no business standing up just to
 * show somebody what a colour looks like.
 *
 * What it does share is everything that decides how the strip *looks*: the same [DeckPalette], the
 * same corner radius, the same glass. Those are the things the screen's controls change, so those
 * are the things that must not be a second implementation. The tiles are drawn from the same
 * [DeckTile] list the real strip walks; they simply do nothing when touched.
 */
@Composable
fun DeckPreviewStrip(
    tiles: List<DeckTile>,
    palette: DeckPalette,
    widthDp: Float,
    cornerDp: Float,
    glass: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(cornerDp.dp)
    Column(
        modifier = modifier
            .width(widthDp.dp)
            .clip(shape)
            .background(palette.background)
            .then(if (glass) Modifier.liquidGlass(cornerDp.dp, palette.light) else Modifier)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // However many fit the stage. The real strip scrolls; a preview that scrolled would be
        // inviting a gesture that tells the user nothing.
        tiles.take(PREVIEW_TILE_COUNT).forEach { tile ->
            TileButton(
                icon = tile.icon,
                label = stringResource(tile.labelRes),
                active = false,
                palette = palette,
                onClick = {},
            )
        }
    }
}

/** As many tiles as the preview stage has room for without the last one being clipped. */
private const val PREVIEW_TILE_COUNT = 4
