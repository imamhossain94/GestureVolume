package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.PanelTheme

/**
 * Picks how the floating panels are dressed, from any of the three screens that own one.
 *
 * The same composable on the Deck, the Quick panel and the long-press menu, writing the same
 * preference — so wherever the user goes looking, the setting is there and says the same thing.
 * Repeating one control across three screens is usually a smell; here it is the point, because
 * the thing being set is not a property of any one of them.
 *
 * One row of tiles, like the opening animations beside it: each a small panel in its material over
 * two bright shapes, so what the material does to what is behind it — lets it through, blurs it to
 * a wash, hides it — is there to see. "Acrylic" and "Vibrant" are not words that say that. See
 * [PictureRow].
 */
@Composable
fun PanelThemeSelector(
    theme: String,
    onThemeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.panel_theme),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.panel_theme_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        PictureRow(
            items = PanelTheme.ALL,
            selected = PanelTheme.sanitize(theme),
            onSelect = onThemeChange,
            label = { stringResource(panelThemeLabel(it)) },
        ) { id, _ -> ThemePicture(id) }
    }
}

/** The translated name of a panel style, for the summaries that name the one chosen. */
fun panelThemeLabel(id: String): Int = when (id) {
    PanelTheme.FROSTED -> R.string.panel_theme_frosted
    PanelTheme.GLASS -> R.string.panel_theme_glass
    PanelTheme.AERO -> R.string.panel_theme_aero
    PanelTheme.VIBRANT -> R.string.panel_theme_vibrant
    PanelTheme.PAPER -> R.string.panel_theme_paper
    PanelTheme.MIDNIGHT -> R.string.panel_theme_midnight
    PanelTheme.AMOLED -> R.string.panel_theme_amoled
    PanelTheme.ACRYLIC -> R.string.panel_theme_acrylic
    PanelTheme.AMETHYST -> R.string.panel_theme_amethyst
    else -> R.string.panel_theme_solid
}

/**
 * A panel dressed as [id] over a sliver of the phone, in the long-press menu's palette for it: the
 * one panel whose whole look comes from the material, with no colour of the user's in it.
 *
 * The blur is drawn rather than applied — behind a blurring material the shapes are redrawn as soft
 * washes of their colour, softer the stronger the blur — so it looks the same on every phone and
 * costs nothing to scroll.
 */
@Composable
private fun BoxScope.ThemePicture(id: String) {
    TileWallpaper()
    val palette = PanelTheme.menuPalette(id)
    val blurDp = PanelTheme.blurRadiusDp(id)
    val litEdge = PanelTheme.hasLitEdge(id)
    val light = PanelTheme.isLight(id)
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(5.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                val w = size.width
                val h = size.height
                // What the panel sits over: two bright shapes, half behind it and half not.
                Behind.forEach { drawCircle(it.color, it.radius * w, Offset(it.x * w, it.y * h)) }

                val panelWidth = w * 0.66f
                val panelHeight = h * 0.7f
                val left = (w - panelWidth) / 2f
                val top = (h - panelHeight) / 2f
                val corner = CornerRadius(10.dp.toPx())
                val outline = Path().apply {
                    addRoundRect(RoundRect(left, top, left + panelWidth, top + panelHeight, corner))
                }
                clipPath(outline) {
                    if (blurDp > 0) {
                        // The wallpaper again, over the sharp shapes, and the shapes as washes on it.
                        drawRect(Brush.linearGradient(listOf(DeviceArt.WallTop, DeviceArt.WallBottom)))
                        val soft = blurDp.dp.toPx() * BLUR_SPREAD
                        Behind.forEach { washOf(it, soft) }
                    }
                    drawRect(Color(palette.surface))
                    if (litEdge) {
                        // Light from above on the upper half, and a little caught at the bottom.
                        val k = if (light) 0.45f else 1f
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.18f * k),
                                0.55f to Color.White.copy(alpha = 0.03f * k),
                                1f to Color.Transparent,
                                startY = top,
                                endY = top + panelHeight * 0.5f,
                            ),
                        )
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.1f * k)),
                                startY = top + panelHeight * 0.8f,
                                endY = top + panelHeight,
                            ),
                        )
                    }
                    // Three rows of something, in the material's own ink: a chip and a label.
                    val chip = 4.5.dp.toPx()
                    val bar = 3.5.dp.toPx()
                    for (row in 0 until 3) {
                        val cy = top + panelHeight * (0.26f + row * 0.24f)
                        val cx = left + 7.dp.toPx() + chip
                        drawCircle(Color(palette.chip), chip, Offset(cx, cy))
                        drawCircle(Color(palette.onSurface).copy(alpha = 0.9f), chip * 0.42f, Offset(cx, cy))
                        val start = cx + chip + 4.dp.toPx()
                        val length = (left + panelWidth - 6.dp.toPx() - start) * (if (row == 1) 0.65f else 1f)
                        drawRoundRect(
                            color = Color(if (row == 0) palette.onSurface else palette.onSurfaceDim),
                            topLeft = Offset(start, cy - bar / 2f),
                            size = Size(length, bar),
                            cornerRadius = CornerRadius(bar / 2f),
                        )
                    }
                }
                // The edge: a lit rim on the glass ones, a hairline on the rest.
                val stroke = Stroke(width = if (litEdge) 1.2.dp.toPx() else 1.dp.toPx())
                val edgeTopLeft = Offset(left, top)
                val edgeSize = Size(panelWidth, panelHeight)
                if (litEdge) {
                    val k = if (light) 0.45f else 1f
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.65f * k),
                            0.35f to Color.White.copy(alpha = 0.24f * k),
                            0.75f to Color.White.copy(alpha = 0.08f * k),
                            1f to Color.White.copy(alpha = 0.3f * k),
                            startY = top,
                            endY = top + panelHeight,
                        ),
                        topLeft = edgeTopLeft,
                        size = edgeSize,
                        cornerRadius = corner,
                        style = stroke,
                    )
                } else {
                    drawRoundRect(Color(palette.border), edgeTopLeft, edgeSize, corner, style = stroke)
                }
            },
    )
}

/** One of the shapes behind the panel, as fractions of the tile: where it is, how big, what colour. */
private class Blob(val x: Float, val y: Float, val radius: Float, val color: Color)

private val Behind = listOf(
    Blob(0.2f, 0.22f, 0.3f, Color(0xFFFF8A5B)),
    Blob(0.84f, 0.8f, 0.34f, Color(0xFF26C6B0)),
)

/** [shape] seen through a blur of [soft] pixels: its colour, fading out past where its edge was. */
private fun DrawScope.washOf(shape: Blob, soft: Float) {
    val center = Offset(shape.x * size.width, shape.y * size.height)
    val radius = shape.radius * size.width + soft
    drawCircle(
        brush = Brush.radialGradient(
            0f to shape.color.copy(alpha = 0.95f),
            0.45f to shape.color.copy(alpha = 0.6f),
            1f to shape.color.copy(alpha = 0f),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** How much of a material's blur, in dp, softens the shapes in a tile, which is far smaller than a panel. */
private const val BLUR_SPREAD = 0.45f
