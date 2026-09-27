package com.newagedevs.gesturevolume.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/**
 * A right hand pointing with its index finger, seen from above: the back of the hand, the nail on
 * the fingertip, the other three fingers curled into a fist on the right and the thumb on the left.
 *
 * One drawing for every finger the app shows — the walkthrough's scenes and the settings screens'
 * demos — so the hand teaching the bar in the walkthrough is the hand that plays it back later.
 * It used to be a white capsule with a slab for a palm, which read as no hand in particular, and
 * the slab sat on the thumb's side when the bar was on the right, which made it a left hand for
 * most people. This one is always a right hand, turned but never mirrored: most people reach for
 * the bar with their right, from below, whichever edge it is on.
 *
 * Drawn from paths rather than an image, so it is sharp at any size and at any angle, and so its
 * shadow can close in as it presses, which is most of what makes a drawn touch read as a touch.
 */
private object HandArt {

    // Units: the pad of the index finger is at (0, 0), the finger is 20 wide, y runs down the hand.
    // The wrist runs on to y = 250, well past the end of any stage the hand is drawn on.

    private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

    val outline = path(
        // The fingertip, and the finger's right side down to the curled middle finger.
        "M -10 1 C -10 -6.5 -5.6 -11.2 0 -11.2 C 5.6 -11.2 10 -6.5 10 1 L 10.9 51 " +
            // The middle, ring and little fingers, curled: a knuckle each, lower as they go.
            "C 11.6 45.6 15.6 42.6 20.6 42.6 C 25.8 42.6 29.8 46 30.3 51.4 " +
            "C 32.2 48.8 35.2 47.8 38.4 48 C 43.6 48.4 47.2 52.2 47.3 57.6 " +
            "C 48.9 56.2 51.1 55.6 53.3 56 C 57.9 56.8 60.9 60.6 60.9 66 " +
            // The outside of the hand, narrowing into the wrist.
            "C 60.9 80 60.3 96 58.1 112 C 56.1 128 52.7 146 51.1 166 L 50.5 250 L -19 250 L -19.4 170 " +
            // The thumb, up to its tip and back into the crotch below the index finger.
            "C -22 152 -30.6 134 -33.4 116 C -35 104 -34.4 92 -31.8 84 " +
            "C -29.6 77.4 -25.4 74 -21.2 74.8 C -17.2 75.6 -14.6 79.4 -14.2 84.4 " +
            "C -13.8 88 -12.4 90.8 -11.2 91.4 L -10 1 Z"
    )

    val thumb = path(
        "M -19.4 170 C -22 152 -30.6 134 -33.4 116 C -35 104 -34.4 92 -31.8 84 " +
            "C -29.6 77.4 -25.4 74 -21.2 74.8 C -17.2 75.6 -14.6 79.4 -14.2 84.4 " +
            "C -13.4 96 -11.6 110 -7.6 126 C -5.8 140 -8.6 158 -19.4 170 Z"
    )

    /** The three curled fingers, lit from above so each knuckle rounds off into the fist. */
    val knuckles = path(
        "M 10.9 51 C 11.6 45.6 15.6 42.6 20.6 42.6 C 25.8 42.6 29.8 46 30.3 51.4 C 30.5 56 29.8 60 28.6 63 L 12 66 Z " +
            "M 30.3 51.4 C 32.2 48.8 35.2 47.8 38.4 48 C 43.6 48.4 47.2 52.2 47.3 57.6 C 47.3 62 46.6 66 45.4 69 L 29 64 Z " +
            "M 47.3 57.6 C 48.9 56.2 51.1 55.6 53.3 56 C 57.9 56.8 60.9 60.6 60.9 66 C 60.9 70 60.3 73.6 59.4 76.4 L 45.6 70 Z"
    )

    val nail = path(
        "M -6.4 -1.2 C -6.4 -6.6 -3.6 -8.9 0 -8.9 C 3.6 -8.9 6.4 -6.6 6.4 -1.2 L 6.1 10.4 " +
            "C 6 12.6 3.6 14 0 14 C -3.6 14 -6 12.6 -6.1 10.4 Z"
    )
    val nailEdge = path("M -6.3 -1.8 C -6 -6.6 -3.4 -8.9 0 -8.9 C 3.4 -8.9 6 -6.6 6.3 -1.8 C 3.8 -3.6 -3.8 -3.6 -6.3 -1.8 Z")
    val nailShine = path("M -3.2 1 Q -3.4 5 -2.2 9")
    val thumbNail = path("M -30.6 88 C -30.2 82 -27 78.4 -23.4 78.6 C -20.4 78.8 -18.2 81.4 -18.2 85 C -20.2 88.6 -27 90.6 -30.6 88 Z")

    /** Where one part of the hand passes in front of another: the finger over the fist, the thumb over the palm. */
    val seams = path(
        "M 10.9 51 C 11.3 57 11.5 62 11.4 67 " +
            "M 30.3 51.4 C 30.5 55 30.1 58.6 29.4 61.4 " +
            "M 47.3 57.6 C 47.4 61 47 64.4 46.2 67 " +
            "M -14.2 84.4 C -13.4 96 -11.6 110 -8 124"
    )

    /** The skin creases over the finger's joints and the curled knuckles. */
    val creases = path(
        "M -4.4 22.6 Q 0 24.8 4.4 22.6 " +
            "M -6.4 45.4 Q 0 48.6 6.4 45.4 M -5.4 49.6 Q 0 52.2 5.4 49.6 M -3.6 53.4 Q 0 54.8 3.6 53.4 " +
            "M 15.6 52.4 Q 20.4 54.4 25.2 52.4 M 34 56.4 Q 38.4 58.2 42.8 56.4 M 50 63.6 Q 53.6 65 57.2 63.6"
    )

    val skin = Brush.linearGradient(
        0f to Color(0xFFF8D2B0), 1f to Color(0xFFE3A77E),
        start = Offset(-30f, -10f), end = Offset(60f, 170f),
    )
    val thumbSkin = Brush.linearGradient(
        0f to Color(0xFFE9B089), 1f to Color(0xFFF2C29D),
        start = Offset(-40f, 80f), end = Offset(-10f, 120f),
    )
    val knuckleLight = Brush.verticalGradient(
        0f to Color(0xFFF6CDAA), 1f to Color(0x00E6AD84),
        startY = 42f, endY = 72f,
    )

    /** Both sides of the hand a little darker than its middle, so it reads as round rather than cut out. */
    val sideShade = Brush.horizontalGradient(
        0f to Color(0x2E8A4F2E), 0.25f to Color(0x008A4F2E), 0.7f to Color(0x008A4F2E), 1f to Color(0x388A4F2E),
        startX = -40f, endX = 62f,
    )
    val nailColour = Brush.verticalGradient(
        0f to Color(0xFFFCEAE0), 1f to Color(0xFFF0C3B0),
        startY = -9f, endY = 14f,
    )
    val edge = Color(0xFFA5643F)

    /** Everything the hand and its shadow can reach, for the layer a fading hand is drawn through. */
    val bounds = Rect(-46f, -24f, 76f, 262f)

    /** For that layer; the hand is only ever drawn from the main thread. */
    val layerPaint = Paint()
}

/**
 * Draws the hand with the pad of its index finger on [tip].
 *
 * @param fingerWidth how wide the index finger is, in this scope's units; the hand scales with it.
 * @param tilt degrees clockwise about the fingertip. 0 points straight up; positive swings the
 *   wrist to the left, which is how a hand reaches for an edge on the right.
 * @param press 0 hovering, 1 on the glass. Pressing brings the hand down: a little smaller, the
 *   shadow tucked in underneath it.
 * @param alpha the whole hand, faded as one: a translucent hand drawn part by part would show its
 *   thumb through its palm.
 */
fun DrawScope.drawPointingHand(
    tip: Offset,
    fingerWidth: Float,
    tilt: Float,
    press: Float,
    alpha: Float = 1f,
) {
    if (alpha <= 0.01f) return
    val lift = 1.06f + (0.97f - 1.06f) * press
    val scale = fingerWidth / 20f * lift
    withTransform({
        translate(tip.x, tip.y)
        rotate(tilt, pivot = Offset.Zero)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        val canvas = drawContext.canvas
        val layered = alpha < 0.99f
        if (layered) {
            HandArt.layerPaint.alpha = alpha
            canvas.saveLayer(HandArt.bounds, HandArt.layerPaint)
        }
        drawShadow(press)
        drawPath(HandArt.outline, HandArt.skin)
        drawPath(HandArt.thumb, HandArt.thumbSkin)
        drawPath(HandArt.knuckles, HandArt.knuckleLight)
        drawPath(HandArt.outline, HandArt.sideShade)
        drawPath(HandArt.seams, HandArt.edge, alpha = 0.45f, style = Stroke(1.1f, cap = StrokeCap.Round))
        drawPath(HandArt.creases, HandArt.edge, alpha = 0.42f, style = Stroke(1f, cap = StrokeCap.Round))
        // The light catching the knuckle at the root of the finger.
        drawOval(Color.White, topLeft = Offset(-6f, 90f), size = Size(14f, 8f), alpha = 0.22f)
        drawPath(HandArt.nail, HandArt.nailColour)
        drawPath(HandArt.nail, HandArt.edge, alpha = 0.35f, style = Stroke(0.8f))
        drawPath(HandArt.nailEdge, Color.White, alpha = 0.55f)
        drawPath(HandArt.nailShine, Color.White, alpha = 0.7f, style = Stroke(1.3f, cap = StrokeCap.Round))
        drawPath(HandArt.thumbNail, HandArt.nailColour)
        drawPath(HandArt.thumbNail, HandArt.edge, alpha = 0.35f, style = Stroke(0.8f))
        drawPath(HandArt.outline, HandArt.edge, alpha = 0.75f, style = Stroke(1.2f, join = StrokeJoin.Round))
        if (layered) canvas.restore()
    }
}

/**
 * A soft shadow without a blur: the outline filled, then stroked twice wider and fainter, so its
 * edge fades out over a few units. A blur mask would redraw the whole hand into a blurred bitmap
 * every frame of the demo, and before API 28 would not draw at all.
 */
private fun DrawScope.drawShadow(press: Float) {
    val dx = 3f + (1f - 3f) * press
    val dy = 8f + (2.5f - 8f) * press
    translate(dx, dy) {
        drawPath(HandArt.outline, Color.Black, alpha = 0.10f)
        drawPath(HandArt.outline, Color.Black, alpha = 0.06f, style = Stroke(5f, join = StrokeJoin.Round))
        drawPath(HandArt.outline, Color.Black, alpha = 0.035f, style = Stroke(10f, join = StrokeJoin.Round))
    }
}
