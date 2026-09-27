package com.newagedevs.gesturevolume.ui.components

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.newagedevs.gesturevolume.R
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
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
 * Dressed as the Actions screen's Try it card, so every screen that shows its subject shows it the
 * same way: a card with its name at the top — and, when there is a [demo], the button that plays it
 * across from the name — the phone on a stage inside it, and under the stage a few lines on what
 * the preview is for ([LocalPreviewHint]). While the demo plays, what the finger is doing takes the
 * place of those lines, centred, in the same space: the card never grows or shrinks around it.
 * Nothing but the preview and the hand goes on the glass, where a caption or a button would sit on
 * whatever the preview shows there.
 *
 * @param demo the "How it works" demo this preview plays, if it has one.
 * @param caption words the gesture the demo is making: by default, its name.
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
    demo: GestureDemoState? = null,
    caption: @Composable (DemoGesture) -> Unit = { DemoGestureText(it) },
    content: @Composable BoxScope.() -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(colours.surfaceVariant.copy(alpha = 0.65f))
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = colours.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.preview_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = colours.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (demo != null) HowItWorksButton(onClick = demo::replay)
        }
        // Clipped, as the walkthrough's picture is: the hand reaches in over the frame and is cut off
        // at the bottom of the phone, and never strays into the row of words under it.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier.height(PREVIEW_PHONE_HEIGHT))
                .clip(RoundedCornerShape(18.dp))
                .background(colours.surface)
                .clipToBounds()
        ) {
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
            // The screen as this phone would show it: its contents laid out at the width of the
            // phone's own screen and drawn smaller by as much as the frame is, so a 30dp bar is as
            // thin against this glass as it is against the real one, and a Deck as narrow. Drawn at
            // their size in dp on a phone a fraction of the size, they were several times too big.
            val glassWidth = maxWidth.value - 2f * origin.x - (DeviceArt.SCREEN_L + DeviceArt.SCENE_W - DeviceArt.SCREEN_R) * unit
            val screenWidth = LocalConfiguration.current.let { minOf(it.screenWidthDp, it.screenHeightDp) }
            val scale = (glassWidth / screenWidth.coerceAtLeast(1)).coerceIn(0.05f, 1f)
            CompositionLocalProvider(LocalGlassScale provides scale) {
                Box(modifier = glass.clip(RoundedCornerShape(topStart = corner, topEnd = corner))) {
                    Box(
                        modifier = Modifier.miniature(scale),
                        contentAlignment = contentAlignment,
                        content = content,
                    )
                }
                Box(modifier = glass, content = overGlass)
            }
        }
        // The lines under the stage: what the preview is for, and while the demo plays, what the
        // finger is doing, centred, in the same three lines' space.
        val hint = LocalPreviewHint.current
        if (hint != null || demo != null) {
            val lines = with(LocalDensity.current) { (MaterialTheme.typography.bodySmall.lineHeight * 3).toDp() }
            val step = demo?.let { d -> remember(d) { derivedStateOf { d.currentStep } }.value }
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 10.dp)
                    .height(lines),
                label = "previewLines",
            ) { gesture ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (gesture != null) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (gesture != null) {
                        caption(gesture)
                    } else if (hint != null) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = colours.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * How much smaller than on the phone itself things are drawn on a preview's glass: see
 * [PreviewStage]. For what is drawn over the glass rather than on it, such as the demo's hand,
 * to find what is on it.
 */
val LocalGlassScale = compositionLocalOf { 1f }

/**
 * Lays out what it modifies at the size it is given divided by [scale], and draws it [scale] times
 * as big, from the top left: the phone's screen, in the space of a picture of it.
 */
private fun Modifier.miniature(scale: Float): Modifier = layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed((w / scale).roundToInt(), (h / scale).roundToInt()))
    layout(w, h) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/**
 * What the preview is for, said under it while its demo is not playing — the line each screen
 * used to put above its preview, now on the card, under the phone. See [PreviewStage].
 */
val LocalPreviewHint = compositionLocalOf<String?> { null }

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

/** The row under the phone, for the demo's caption and its button: at least this tall. */
val PREVIEW_FOOTER_HEIGHT = 44.dp

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

/** The phone's part of that, above the row: where the card fits the phone the way it always has. */
val PREVIEW_PHONE_HEIGHT = 212.dp

/**
 * The tallest a subject is drawn: tall and thin subjects are capped here, so neither runs off the
 * ends of the screen it is being judged on — a track bleeding off both edges reads as a cropped
 * photograph rather than as an object standing on one.
 */
val PREVIEW_SUBJECT_MAX_HEIGHT = 260.dp
