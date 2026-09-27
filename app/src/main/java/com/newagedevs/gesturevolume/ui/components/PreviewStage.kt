package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout

/**
 * The card every "here is what it will look like" preview sits in: the walkthrough's phone, drawn
 * exactly as the walkthrough draws it, with the subject on its glass at the subject's real size.
 *
 * One composable rather than several nearly-identical ones, because the handler's preview, the
 * Quick panel's, the Deck's and the menu's are answering the same question about things the user
 * sets up on neighbouring screens, and the moment they stop looking alike the app stops looking
 * like one app. The walkthrough's phone, because that is where the user first met the bar: the same
 * frame, the same wallpaper and the same hand say this is that bar, being dressed. It is
 * [DeviceArt]'s scene, fitted to the card by the same rule, so the frame is as wide here as there.
 *
 * **Why a phone.** Every subject here sits against the edge of a screen and is translucent by
 * default. On a phone, "against the edge" is the edge of the glass, with the frame beyond it, and
 * judged against a wallpaper, opacity is how much of the screen shows through rather than a number.
 *
 * Only the top of the phone shows, so everything placed on it is laid out in the part that shows:
 * [content] is the visible glass, clipped to it, flush with it at the sides, so a subject aligned to
 * an edge is on the screen's edge. [overGlass] has the same bounds and no clip, for the demo's hand,
 * which reaches in over the frame from beyond the phone, as it does in the walkthrough.
 *
 * [footer] is the row under the phone, on the card, as the walkthrough's cards have one for their
 * words: what the demo is doing, and the button that plays it. Nothing but the preview and the hand
 * goes on the glass, where a caption or a button would sit on whatever the preview shows there.
 *
 * @param fillHeight fill the height it is given instead of taking [PREVIEW_STAGE_HEIGHT] — for the
 *   landscape arrangement, where the preview has a column of its own. See [PreviewSettingsLayout].
 */
@Composable
fun PreviewStage(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    fillHeight: Boolean = false,
    overGlass: @Composable BoxScope.() -> Unit = {},
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(PREVIEW_STAGE_HEIGHT)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
        // Clipped, as the walkthrough's picture is: the hand reaches in over the frame and is cut off
        // at the bottom of the phone, and never strays into the row of words under it.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            // One of the scene's units, in dp, and where the scene sits: the walkthrough's fit.
            val unit = DeviceArt.scale(maxWidth.value, maxHeight.value)
            val origin = DeviceArt.origin(maxWidth.value, maxHeight.value, unit)
            // Drawn once per size: nothing in it moves.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val px = unit.dp.toPx()
                val left = origin.x.dp.toPx()
                val top = origin.y.dp.toPx()
                withTransform({
                    translate(left, top)
                    scale(px, px, pivot = Offset.Zero)
                }) { drawScenePhone() }
            }
            val glass = Modifier
                .fillMaxSize()
                .padding(
                    start = (origin.x + DeviceArt.SCREEN_L * unit).dp,
                    end = (origin.x + (DeviceArt.SCENE_W - DeviceArt.SCREEN_R) * unit).dp,
                    top = (origin.y + DeviceArt.SCREEN_T * unit).dp,
                )
            val corner = (DeviceArt.SCREEN_CORNER * unit).dp
            Box(
                modifier = glass.clip(RoundedCornerShape(topStart = corner, topEnd = corner)),
                contentAlignment = contentAlignment,
                content = content,
            )
            Box(modifier = glass, content = overGlass)
        }
        if (footer != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_FOOTER_HEIGHT)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = footer,
            )
        }
        }
    }
}

/**
 * Draws what it modifies at up to [maxScale] of its own size, and smaller wherever that would not
 * fit the space it is given, kept [horizontal] from either side, [top] from the top and [bottom]
 * from the bottom — both ways, whatever its contents make it.
 *
 * Measured at its own size and never squeezed: a picture of something that lays itself out from
 * its own size, such as the long-press menu, is drawn smaller rather than drawn wrong. For a
 * subject on the phone's glass, where a menu of many actions is taller than the part of the
 * phone that shows; [top] keeps it off the status bar.
 */
fun Modifier.scaleToFit(maxScale: Float, horizontal: Dp, top: Dp, bottom: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints())
        val sides = horizontal.roundToPx() * 2
        val ends = top.roundToPx() + bottom.roundToPx()
        val roomW = if (constraints.hasBoundedWidth) (constraints.maxWidth - sides).toFloat() else Float.MAX_VALUE
        val roomH = if (constraints.hasBoundedHeight) (constraints.maxHeight - ends).toFloat() else Float.MAX_VALUE
        val scale = if (placeable.width <= 0 || placeable.height <= 0) {
            maxScale
        } else {
            minOf(maxScale, roomW / placeable.width, roomH / placeable.height).coerceAtLeast(0.05f)
        }
        val w = (placeable.width * scale).roundToInt()
        val h = (placeable.height * scale).roundToInt()
        // The margins are part of the size, so whatever centres it keeps them.
        layout(w + sides, h + ends) {
            placeable.placeWithLayer(sides / 2 + (w - placeable.width) / 2, top.roundToPx() + (h - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }

/** The row under the phone, for the demo's caption and its button. */
val PREVIEW_FOOTER_HEIGHT = 52.dp

/**
 * How tall every preview is, the row under the phone included.
 *
 * Fixed, and shared, for the reason the class note gives. The phone is fitted to what is left as the
 * walkthrough's is to its card, so this is also what decides how big the phone is: tall enough for
 * the glass to take the subjects at their real size. It also has to hold the subject: the Quick
 * panel's track runs to 320dp and the bar to 200dp, so neither fits whole — but both are long in the
 * axis where being cropped costs nothing, because a track is the same all the way down and what the
 * user is judging is its width, its colour and its ends.
 */
val PREVIEW_STAGE_HEIGHT = 300.dp

/**
 * The tallest a subject is drawn: tall and thin subjects are capped here, so neither runs off the
 * ends of the screen it is being judged on — a track bleeding off both edges reads as a cropped
 * photograph rather than as an object standing on one.
 */
val PREVIEW_SUBJECT_MAX_HEIGHT = 178.dp
