package com.newagedevs.gesturevolume.overlay.deck

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
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
class DeckPalette(val background: Color, val accent: Color) {
    val onBackground: Color = if (background.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val subtle: Color = onBackground.copy(alpha = 0.62f)
    val onAccent: Color = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val chip: Color = accent.copy(alpha = 0.14f)
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
@Composable
fun DeckOverlay(
    model: DeckModel,
    actions: DeckActions,
    onDismiss: () -> Unit
) {
    val state = actions.env.state
    val palette = remember(model.config) { DeckPalette(model.config.background, model.config.accent) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val density = LocalDensity.current
    val stripWidthPx = with(density) { model.config.widthDp.dp.roundToPx() }
    val maxStripHeightPx = (model.frame.height * model.config.heightFraction).toInt()
    val edgePx = with(density) { 6.dp.roundToPx() }
    val gapPx = with(density) { 8.dp.roundToPx() }
    val cardWidthPx = with(density) { 320.dp.roundToPx() }

    val expanded = state.expandedTile
    val expandedTile = expanded?.let { DeckTiles.byId(it) }

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
                        enter = slideInHorizontally { if (model.isLeft) -it else it } + fadeIn(),
                        exit = slideOutHorizontally { if (model.isLeft) -it else it } + fadeOut()
                    ) {
                        DeckStrip(model, actions, palette, stripWidthPx)
                    }
                }
                Box(modifier = Modifier.layoutId("card")) {
                    AnimatedVisibility(
                        visible = expandedTile != null,
                        enter = slideInHorizontally { if (model.isLeft) -it / 3 else it / 3 } + fadeIn(),
                        exit = slideOutHorizontally { if (model.isLeft) -it / 3 else it / 3 } + fadeOut()
                    ) {
                        // The last tile is held so the exit animation has something to draw.
                        var lastTile by remember { mutableStateOf(expandedTile) }
                        if (expandedTile != null) lastTile = expandedTile
                        lastTile?.let { tile ->
                            DeckCard(
                                tile = tile,
                                palette = palette,
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
    val state = actions.env.state
    val density = LocalDensity.current
    val stripWidth = with(density) { stripWidthPx.toDp() }
    val shape = RoundedCornerShape(model.config.cornerDp.dp)

    Column(
        modifier = Modifier
            .width(stripWidth)
            .clip(shape)
            .background(palette.background)
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
                AppButton(app) { actions.launchApp(app.packageName) }
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
private fun AppButton(app: AppShortcut, onClick: () -> Unit) {
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
            Text(text = app.label.take(1).uppercase(), fontWeight = FontWeight.Bold)
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
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(palette.background)
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
