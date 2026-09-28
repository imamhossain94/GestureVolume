package com.newagedevs.gesturevolume.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.min

/**
 * The phone the app draws things on: the walkthrough's, and every settings preview's since, so a
 * bar met in the walkthrough is seen on the same phone when it is dressed, with the same hand
 * reaching for it.
 *
 * One definition, because two copies drift. The phone is drawn in a scene of its own units,
 * [SCENE_W] by [SCENE_H], scaled to fit whatever holds it by [scale] — the walkthrough's card and a
 * preview's stage alike — so the frame, the glass, the status bar and the hand come out in the same
 * proportions in both. Only the top of the phone is in the scene; the rest runs off its bottom.
 */
object DeviceArt {
    /** The frame round the glass. */
    val Frame = Color(0xFF17171C)

    /** The wallpaper, purple at the top left into blue at the bottom right. */
    val WallTop = Color(0xFFD9CCFF)
    val WallBottom = Color(0xFF9DB2FA)

    /** The scene, in its own units. */
    const val SCENE_W = 280f
    const val SCENE_H = 200f

    /** The glass, in the scene's units. Its bottom is past the scene's: only its top shows. */
    const val SCREEN_L = 46f
    const val SCREEN_T = 20f
    const val SCREEN_R = 234f
    const val SCREEN_B = 236f
    const val SCREEN_CORNER = 24f

    /** How wide the glass is, in the scene's units: what a unit is worth, measured off the glass. */
    const val SCREEN_W = SCREEN_R - SCREEN_L

    /** How wide the hand's index finger is, in the scene's units. The rest of the hand is in proportion. */
    const val FINGER_W = 24f

    /**
     * The hand's lean for a bar on the right: the wrist out to the right, as a right hand reaches
     * the right edge of a phone held in the other. See [drawPointingHand]; the left hand, for a bar
     * on the left, is its mirror at the opposite lean.
     */
    const val HAND_TILT = -28f

    /** Where the fingertip comes in from and goes back to: off the phone, bottom right. */
    val FINGER_REST = Offset(266f, 206f)

    /** How many pixels a scene unit is, fitted into [width] by [height] without cropping either. */
    fun scale(width: Float, height: Float): Float = min(width / SCENE_W, height / SCENE_H)

    /** Where the scene's top left lands in [width] by [height] at [scale]: centred across, on the bottom. */
    fun origin(width: Float, height: Float, scale: Float): Offset =
        Offset((width - SCENE_W * scale) / 2f, height - SCENE_H * scale)

    /** The glass's outline, for clipping what is on it. Drawn from, never changed. */
    internal val screenPath = Path().apply {
        addRoundRect(RoundRect(SCREEN_L, SCREEN_T, SCREEN_R, SCREEN_B, CornerRadius(SCREEN_CORNER)))
    }
}

/**
 * The phone, in the scene's units: a shadow, the frame, the wallpaper, a status bar, a home screen's
 * first icons, faint, and the camera.
 */
fun DrawScope.drawScenePhone() {
    drawRoundRect(Color.Black, Offset(37f, 16f), Size(206f, 240f), CornerRadius(33f), alpha = 0.10f)
    drawRoundRect(DeviceArt.Frame, Offset(40f, 14f), Size(200f, 240f), CornerRadius(30f))
    clipPath(DeviceArt.screenPath) {
        drawDeviceWallpaper(
            Offset(DeviceArt.SCREEN_L, DeviceArt.SCREEN_T),
            Size(DeviceArt.SCREEN_R - DeviceArt.SCREEN_L, DeviceArt.SCREEN_B - DeviceArt.SCREEN_T),
        )
        val ink = Color.White.copy(alpha = 0.85f)
        drawRoundRect(ink, Offset(62f, 29f), Size(18f, 6f), CornerRadius(3f))
        drawRoundRect(ink, Offset(198f, 29f), Size(9f, 6f), CornerRadius(2f))
        drawRoundRect(ink, Offset(210f, 29f), Size(14f, 6f), CornerRadius(2f))
        for (i in 0 until 6) {
            drawRoundRect(
                Color.White,
                Offset(66f + (i % 3) * 36f, 58f + (i / 3) * 40f),
                Size(24f, 24f),
                CornerRadius(8f),
                alpha = 0.22f,
            )
        }
    }
    drawCircle(DeviceArt.Frame, radius = 4.5f, center = Offset(140f, 32f))
}

/**
 * The wallpaper over the rectangle at [topLeft] of [size], its corners rounded by [corner]: the
 * gradient on the diagonal, and a glow in the top left so it reads as a picture rather than a fill.
 */
fun DrawScope.drawDeviceWallpaper(topLeft: Offset, size: Size, corner: Float = 0f) {
    val radius = CornerRadius(corner)
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(DeviceArt.WallTop, DeviceArt.WallBottom),
            topLeft,
            topLeft + Offset(size.width, size.height),
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
    )
    val glowAt = topLeft + Offset(size.width * GLOW_X, size.height * GLOW_Y)
    val glow = maxOf(size.width, size.height) * GLOW_RADIUS
    drawRoundRect(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), glowAt, glow),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
    )
}

// Where the glow sits and how far it reaches, as the walkthrough's scene has always drawn it.
private const val GLOW_X = 0.2f
private const val GLOW_Y = 0.17f
private const val GLOW_RADIUS = 0.51f
