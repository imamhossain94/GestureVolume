package com.newagedevs.gesturevolume.ui.screens.walkthrough

import com.newagedevs.gesturevolume.utils.HandlerPresets
import com.newagedevs.gesturevolume.ui.components.drawPointingHand
import com.newagedevs.gesturevolume.ui.components.setTabOutline
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * The gestures the walkthrough acts out, each on its own loop. The loop length is the story's
 * length: a swipe up and back needs less time than a swipe, a tap and a hold one after another.
 */
internal enum class WalkScene(val cycleMs: Int) {
    Intro(3600),
    Simple(6600),
    QuickSlider(4400),
    Deck(4200),
    LongPress(4400),
    Permission(3600),
    Accessibility(5200),
}

/** Where the permission scene rests once the permission is granted: switch on, bar on screen. */
internal const val PERMISSION_SETTLED = 0.75f

/** Where the accessibility scene rests once the service is on: switched on, the Quick slider open. */
internal const val ACCESSIBILITY_SETTLED = 0.74f

// The scene is drawn in a fixed design space and scaled to fit, so it keeps its proportions on
// any screen: a short phone squeezes the card, and the drawing shrinks with it instead of
// losing the bar off the bottom.
private const val SCENE_W = 280f
private const val SCENE_H = 200f

// The phone's screen. Its bottom runs past the scene: only the top of the phone is shown, which
// is where the bar sits and all a gesture needs.
private const val SCREEN_L = 46f
private const val SCREEN_T = 20f
private const val SCREEN_R = 234f
private const val SCREEN_B = 236f
private const val BAR_CY = 108f

// The Simple button: the real one is 30 x 100 dp, drawn here at six tenths.
private const val PILL_W = 18f
private const val PILL_H = 60f
private const val PILL_CX = SCREEN_R - 4f - PILL_W / 2f

// The Dock tab, flush to the edge.
private const val TAB_W = 9f
private const val TAB_H = 56f
private const val TAB_SWEEP = 10f

/** How wide the index finger is drawn; the rest of the hand is in proportion. */
private const val FINGER_W = 24f

/** How often the touch ring goes out from the fingertip while a finger is down. */
private const val PULSE_MS = 900f

private val Indigo = Color(0xFF4F46E5)
private val SimpleFill = Indigo.copy(alpha = 0.5f)
private val TabColour = Color(0xFF050507)
private val FrameColour = Color(0xFF17171C)
private val WallTop = Color(0xFFD9CCFF)
private val WallBottom = Color(0xFF9DB2FA)
private val DeckPanel = Color(0xE61D1B2B)
private val MenuPanel = Color(0xF7FFFFFF)
private val MenuTile = Color(0xFFEEF0FF)
private val TextLine = Color(0xFFCBC7D8)
private val SwitchOff = Color(0xFFB9B5C6)
private val TileColours = listOf(
    Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFFBBF24),
    Color(0xFFF472B6), Color(0xFFA78BFA), Color(0xFFF87171),
    Color(0xFF22D3EE), Color(0xFF4F46E5), Color(0xFFFB923C),
)

/** Off the phone, bottom right: where a finger comes from and goes back to. */
private val FingerRest = Offset(266f, 206f)

private val Smooth: Easing = FastOutSlowInEasing

/** Out past the end and back: for things that pop open, so they land rather than stop. */
private val Pop: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private val screenPath = Path().apply {
    addRoundRect(RoundRect(SCREEN_L, SCREEN_T, SCREEN_R, SCREEN_B, CornerRadius(24f)))
}

/**
 * The top of a phone with the bar on its right edge, and a finger acting out [scene] on it, on a
 * loop. Everything is drawn: no image assets, so it stays sharp at any size and follows the
 * scene's timings exactly.
 *
 * [settledAt], when set, holds the scene still at that point of its loop.
 */
@Composable
internal fun WalkthroughIllustration(
    scene: WalkScene,
    description: String,
    modifier: Modifier = Modifier,
    settledAt: Float? = null,
) {
    val clock = rememberInfiniteTransition(label = "walkthrough-scene").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(scene.cycleMs, easing = LinearEasing)),
        label = "scene-clock",
    )
    // One path, rewound for each frame's tab, instead of a new one sixty times a second.
    val scratch = remember { Path() }
    Canvas(modifier.clipToBounds().semantics { contentDescription = description }) {
        // The clock is read here, in the draw phase, so a frame of the loop redraws this canvas
        // and nothing else: the page around it never recomposes for the animation.
        val t = settledAt ?: clock.value
        val pulse = (t * scene.cycleMs / PULSE_MS) % 1f
        val s = min(size.width / SCENE_W, size.height / SCENE_H)
        withTransform({
            translate((size.width - SCENE_W * s) / 2f, size.height - SCENE_H * s)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawPhone()
            when (scene) {
                WalkScene.Intro -> drawIntro(t, pulse)
                WalkScene.Simple -> drawSimple(t, pulse)
                WalkScene.QuickSlider -> drawQuickSlider(t, pulse, scratch)
                WalkScene.Deck -> drawDeck(t, pulse, scratch)
                WalkScene.LongPress -> drawLongPress(t, pulse, scratch)
                WalkScene.Permission -> drawPermission(t, pulse)
                WalkScene.Accessibility -> drawAccessibility(t, pulse, scratch)
            }
        }
    }
}

/**
 * The bar as each style draws it, on a sliver of a phone's edge: the Simple button or the Dock
 * tab. The selected one bobs gently, so the choice reads as alive.
 */
@Composable
internal fun StylePreview(advanced: Boolean, selected: Boolean, modifier: Modifier = Modifier) {
    val bob = rememberInfiniteTransition(label = "style-preview").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "style-bob",
    )
    val scratch = remember { Path() }
    Canvas(modifier.clipToBounds()) {
        val d = 1.dp.toPx()
        val edge = size.width - 8f * d
        drawRoundRect(
            FrameColour,
            topLeft = Offset(-40f * d, 6f * d),
            size = Size(edge + 48f * d, size.height + 40f * d),
            cornerRadius = CornerRadius(20f * d),
        )
        drawRoundRect(
            brush = Brush.linearGradient(listOf(WallTop, WallBottom), Offset.Zero, Offset(size.width, size.height)),
            topLeft = Offset(-40f * d, 10f * d),
            size = Size(edge + 40f * d, size.height + 40f * d),
            cornerRadius = CornerRadius(16f * d),
        )
        val cy = size.height / 2f + 6f * d + if (selected) bob.value * 4f * d else 0f
        if (advanced) {
            tabPath(scratch, edge, cy, 5f * d, 34f * d, 6f * d)
            drawPath(scratch, TabColour)
        } else {
            // The real button is 30 x 100 dp; at this size, a little under half.
            val w = 13f * d
            val h = 44f * d
            val topLeft = Offset(edge - 3f * d - w, cy - h / 2f)
            drawRoundRect(SimpleFill, topLeft, Size(w, h), CornerRadius(w / 2f))
            drawRoundRect(Color.White, topLeft, Size(w, h), CornerRadius(w / 2f), style = Stroke(1f * d))
        }
    }
}

// ---- Scenes ----------------------------------------------------------------------------------

/** Swipe up and the level rises; swipe back down and it falls. */
private fun DrawScope.drawIntro(t: Float, pulse: Float) {
    val low = BAR_CY + 16f
    val high = BAR_CY - 16f
    val arrive = span(t, 0.02f, 0.14f)
    val up = span(t, 0.18f, 0.42f)
    val down = span(t, 0.52f, 0.76f)
    val leave = span(t, 0.82f, 0.94f)
    val press = span(t, 0.12f, 0.17f) * (1f - span(t, 0.78f, 0.83f))
    val alpha = span(t, 0f, 0.06f, LinearEasing) * (1f - span(t, 0.86f, 0.95f, LinearEasing))
    val travel = up - down
    val tip = fingerAt(Offset(PILL_CX, mix(low, high, travel)), arrive, leave)
    val levelAlpha = span(t, 0.14f, 0.2f, LinearEasing) * (1f - span(t, 0.84f, 0.92f, LinearEasing))

    clipPath(screenPath) {
        drawLevel(Offset(PILL_CX - 21f, BAR_CY), 7f, 72f, mix(0.3f, 0.85f, travel), levelAlpha)
        drawPill(BAR_CY)
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/** Three short stories in a row: a swipe that raises the volume, a tap, and a hold that moves it. */
private fun DrawScope.drawSimple(t: Float, pulse: Float) {
    val a = (t * 3f).coerceIn(0f, 1f)
    val b = (t * 3f - 1f).coerceIn(0f, 1f)
    val c = (t * 3f - 2f).coerceIn(0f, 1f)

    var tip = FingerRest
    var press = 0f
    var alpha = 0f
    var barCy = BAR_CY
    var lift = 0f

    when {
        t < 1f / 3f -> {
            val up = span(a, 0.28f, 0.66f)
            tip = fingerAt(Offset(PILL_CX, mix(BAR_CY + 16f, BAR_CY - 16f, up)), span(a, 0.02f, 0.2f), span(a, 0.76f, 0.95f))
            press = span(a, 0.2f, 0.28f) * (1f - span(a, 0.7f, 0.78f))
            alpha = span(a, 0f, 0.1f, LinearEasing) * (1f - span(a, 0.82f, 0.96f, LinearEasing))
            val levelAlpha = span(a, 0.22f, 0.3f, LinearEasing) * (1f - span(a, 0.8f, 0.92f, LinearEasing))
            clipPath(screenPath) {
                drawLevel(Offset(PILL_CX - 21f, BAR_CY), 7f, 72f, mix(0.3f, 0.85f, up), levelAlpha)
            }
        }
        t < 2f / 3f -> {
            tip = fingerAt(Offset(PILL_CX, BAR_CY), span(b, 0.02f, 0.2f), span(b, 0.4f, 0.62f))
            press = span(b, 0.22f, 0.27f) * (1f - span(b, 0.3f, 0.36f))
            alpha = span(b, 0f, 0.1f, LinearEasing) * (1f - span(b, 0.5f, 0.64f, LinearEasing))
            // The volume panel pops out beside the button, as the tap opens it.
            val open = span(b, 0.32f, 0.48f, Pop)
            val panelAlpha = span(b, 0.32f, 0.4f, LinearEasing) * (1f - span(b, 0.84f, 0.96f, LinearEasing))
            clipPath(screenPath) {
                drawLevel(Offset(PILL_CX - 36f, BAR_CY), 24f, 96f, 0.6f, panelAlpha, scale = open)
            }
        }
        else -> {
            val move = span(c, 0.4f, 0.66f)
            val home = span(c, 0.86f, 0.99f)
            barCy = BAR_CY + 44f * (move - home)
            lift = span(c, 0.3f, 0.4f, Pop) * (1f - span(c, 0.7f, 0.78f))
            tip = fingerAt(Offset(PILL_CX, BAR_CY + 44f * move), span(c, 0.02f, 0.18f), span(c, 0.74f, 0.9f))
            press = span(c, 0.18f, 0.24f) * (1f - span(c, 0.7f, 0.76f))
            alpha = span(c, 0f, 0.1f, LinearEasing) * (1f - span(c, 0.78f, 0.92f, LinearEasing))
        }
    }

    clipPath(screenPath) {
        drawPill(barCy, lift)
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/** The tab grows into a tall slider under the finger, and its level follows the finger. */
private fun DrawScope.drawQuickSlider(t: Float, pulse: Float, path: Path) {
    val arrive = span(t, 0.02f, 0.12f)
    val press = span(t, 0.12f, 0.16f) * (1f - span(t, 0.72f, 0.77f))
    val grow = span(t, 0.16f, 0.3f, Pop) * (1f - span(t, 0.76f, 0.88f))
    val leave = span(t, 0.78f, 0.92f)
    val alpha = span(t, 0f, 0.06f, LinearEasing) * (1f - span(t, 0.82f, 0.94f, LinearEasing))

    // Up, down past where it started, and back to the middle: the level chases the finger.
    val y = BAR_CY + 8f - 58f * span(t, 0.3f, 0.44f) + 86f * span(t, 0.46f, 0.6f) - 30f * span(t, 0.62f, 0.7f)
    val left = mix(SCREEN_R - TAB_W, 190f, grow)
    // At rest the right side sits past the edge, so its corners are cut off by the screen and
    // the shape reads as flush; grown, it stands free of the edge.
    val right = mix(SCREEN_R + 6f, 226f, grow)
    val top = mix(BAR_CY - TAB_H / 2f, 34f, grow)
    val bottom = mix(BAR_CY + TAB_H / 2f, 194f, grow)
    val tip = fingerAt(Offset(mix(SCREEN_R - TAB_W / 2f - 1f, (190f + 226f) / 2f, grow), y), arrive, leave)

    clipPath(screenPath) {
        // The resting tab, swept ends and all, gives way to the slider as it grows.
        val tabAlpha = (1f - grow * 3f).coerceIn(0f, 1f)
        if (tabAlpha > 0f) drawTab(path, alpha = tabAlpha)
        if (grow > 0.01f) {
            val g = grow.coerceIn(0f, 1f)
            val radius = mix(TAB_W / 2f, 16f, g)
            drawRoundRect(TabColour, Offset(left, top), Size(right - left, bottom - top), CornerRadius(radius))
            val inset = 4f * g
            val fillTop = y.coerceIn(top + inset + 10f, bottom - inset - 10f)
            drawRoundRect(
                Color.White,
                Offset(left + inset, fillTop),
                Size(right - left - inset * 2f, bottom - inset - fillTop),
                CornerRadius((radius - inset).coerceAtLeast(0f)),
                alpha = 0.95f * g,
            )
        }
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/** A swipe inward from the tab, and the Deck's tiles slide in behind the finger. */
private fun DrawScope.drawDeck(t: Float, pulse: Float, path: Path) {
    val arrive = span(t, 0.02f, 0.12f)
    val press = span(t, 0.12f, 0.16f) * (1f - span(t, 0.38f, 0.43f))
    val swipe = span(t, 0.16f, 0.38f)
    val leave = span(t, 0.42f, 0.56f)
    val alpha = span(t, 0f, 0.06f, LinearEasing) * (1f - span(t, 0.46f, 0.58f, LinearEasing))
    val close = span(t, 0.8f, 0.92f)
    val open = swipe * (1f - close)
    val tip = fingerAt(Offset(mix(SCREEN_R - TAB_W / 2f - 1f, 150f, swipe), BAR_CY), arrive, leave)

    clipPath(screenPath) {
        drawTab(path)
        if (open > 0.001f) {
            val panelW = 144f
            val panelH = 100f
            val left = mix(SCREEN_R + 4f, 220f - panelW, open)
            val top = BAR_CY - panelH / 2f
            drawRoundRect(Color.Black, Offset(left + 2f, top + 6f), Size(panelW, panelH), CornerRadius(22f), alpha = 0.18f * open)
            drawRoundRect(DeckPanel, Offset(left, top), Size(panelW, panelH), CornerRadius(22f))
            for (i in 0 until 6) {
                // One after another, not all at once: the Deck fills in as it arrives.
                val appear = span(t, 0.24f + i * 0.035f, 0.36f + i * 0.035f, Pop) * (1f - close)
                if (appear <= 0.01f) continue
                val cx = left + 28f + (i % 3) * 44f
                val cy = top + 28f + (i / 3) * 44f
                val tile = 36f * appear
                drawRoundRect(Color.White, Offset(cx - tile / 2f, cy - tile / 2f), Size(tile, tile), CornerRadius(11f * appear), alpha = 0.14f)
                drawCircle(TileColours[i], radius = 8f * appear, center = Offset(cx, cy))
            }
        }
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/** A hold on the tab, counted by a ring round the fingertip, and the menu pops out beside it. */
private fun DrawScope.drawLongPress(t: Float, pulse: Float, path: Path) {
    val arrive = span(t, 0.02f, 0.12f)
    val press = span(t, 0.12f, 0.16f) * (1f - span(t, 0.5f, 0.55f))
    val hold = span(t, 0.16f, 0.4f, LinearEasing)
    val menu = span(t, 0.4f, 0.52f, Pop) * (1f - span(t, 0.82f, 0.92f))
    val leave = span(t, 0.54f, 0.68f)
    val alpha = span(t, 0f, 0.06f, LinearEasing) * (1f - span(t, 0.58f, 0.7f, LinearEasing))
    val tip = fingerAt(Offset(SCREEN_R - TAB_W / 2f - 1f, BAR_CY), arrive, leave)

    clipPath(screenPath) {
        drawTab(path)
        if (menu > 0.01f) {
            val side = 106f
            val right = 218f
            // Grows out of the bar, from the side the finger is on.
            withTransform({ scale(menu, menu, pivot = Offset(right, BAR_CY)) }) {
                val left = right - side
                val top = BAR_CY - side / 2f
                drawRoundRect(Color.Black, Offset(left + 2f, top + 6f), Size(side, side), CornerRadius(20f), alpha = 0.16f)
                drawRoundRect(MenuPanel, Offset(left, top), Size(side, side), CornerRadius(20f))
                for (i in 0 until 9) {
                    val x = left + 8f + (i % 3) * 32f
                    val y = top + 8f + (i / 3) * 32f
                    drawRoundRect(MenuTile, Offset(x, y), Size(26f, 26f), CornerRadius(8f))
                    drawCircle(TileColours[i], radius = 5f, center = Offset(x + 13f, y + 13f))
                }
            }
        }
        if (press > 0.01f && hold > 0f && hold < 1f) {
            drawArc(
                Color.White,
                startAngle = -90f,
                sweepAngle = 360f * hold,
                useCenter = false,
                topLeft = Offset(tip.x - 21f, tip.y - 21f),
                size = Size(42f, 42f),
                alpha = press,
                style = Stroke(3f),
            )
        }
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/** The system switch flipped on, and the bar arrives over whatever app is open. */
private fun DrawScope.drawPermission(t: Float, pulse: Float) {
    val cardTop = 48f
    val switchLeft = 176f
    val switchTop = cardTop + 14f
    val arrive = span(t, 0.04f, 0.18f)
    val press = span(t, 0.2f, 0.24f) * (1f - span(t, 0.28f, 0.33f))
    val leave = span(t, 0.34f, 0.5f)
    val alpha = span(t, 0f, 0.08f, LinearEasing) * (1f - span(t, 0.4f, 0.52f, LinearEasing))
    // Everything winds back at the end, so the loop starts from a switched-off phone again.
    val reset = 1f - span(t, 0.9f, 0.98f, LinearEasing)
    val on = span(t, 0.24f, 0.34f) * reset
    val bar = span(t, 0.42f, 0.56f, Pop) * reset
    val tip = fingerAt(Offset(switchLeft + 17f, switchTop + 9f), arrive, leave)

    clipPath(screenPath) {
        drawRoundRect(Color.White, Offset(58f, cardTop), Size(164f, 46f), CornerRadius(14f), alpha = 0.95f)
        drawRoundRect(TextLine, Offset(70f, cardTop + 13f), Size(82f, 7f), CornerRadius(3.5f))
        drawRoundRect(TextLine, Offset(70f, cardTop + 26f), Size(56f, 6f), CornerRadius(3f), alpha = 0.6f)
        drawRoundRect(lerp(SwitchOff, Indigo, on), Offset(switchLeft, switchTop), Size(34f, 18f), CornerRadius(9f))
        drawCircle(Color.White, radius = 7f, center = Offset(switchLeft + 9f + 16f * on, switchTop + 9f))
        if (bar > 0.01f) {
            withTransform({ scale(bar, bar, pivot = Offset(PILL_CX, 140f)) }) { drawPill(140f) }
        }
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/**
 * The accessibility switch flipped on, then a press of the volume key on the phone's side, and the
 * Quick slider grows out of the bar at once: what the service is for, in the order it happens.
 */
private fun DrawScope.drawAccessibility(t: Float, pulse: Float, path: Path) {
    // Low on the screen, so the slider has the top of it to open into.
    val cardTop = 146f
    val switchLeft = 176f
    val switchTop = cardTop + 14f
    val arrive = span(t, 0.04f, 0.18f)
    val press = span(t, 0.2f, 0.24f) * (1f - span(t, 0.28f, 0.33f))
    val leave = span(t, 0.34f, 0.5f)
    val alpha = span(t, 0f, 0.08f, LinearEasing) * (1f - span(t, 0.4f, 0.52f, LinearEasing))
    // Everything winds back at the end, so the loop starts from a switched-off phone again.
    val reset = 1f - span(t, 0.9f, 0.98f, LinearEasing)
    val on = span(t, 0.24f, 0.34f) * reset
    // The key goes in and comes back out, and the slider answers the moment it goes in.
    val key = span(t, 0.5f, 0.54f) * (1f - span(t, 0.62f, 0.66f))
    val grow = span(t, 0.52f, 0.64f, Pop) * (1f - span(t, 0.84f, 0.92f))
    val level = mix(0.35f, 0.7f, span(t, 0.58f, 0.72f))
    val tip = fingerAt(Offset(switchLeft + 17f, switchTop + 9f), arrive, leave)

    drawVolumeKeys(key)
    clipPath(screenPath) {
        val tabAlpha = (1f - grow * 3f).coerceIn(0f, 1f)
        if (tabAlpha > 0f) drawTab(path, alpha = tabAlpha)
        if (grow > 0.01f) {
            val g = grow.coerceIn(0f, 1f)
            val left = mix(SCREEN_R - TAB_W, 192f, grow)
            val right = mix(SCREEN_R + 6f, 226f, grow)
            // Below the status bar and above the card, so it covers neither.
            val top = mix(BAR_CY - TAB_H / 2f, 42f, grow)
            val bottom = mix(BAR_CY + TAB_H / 2f, 138f, grow)
            val radius = mix(TAB_W / 2f, 16f, g)
            drawRoundRect(TabColour, Offset(left, top), Size(right - left, bottom - top), CornerRadius(radius))
            val inset = 4f * g
            val fillTop = mix(bottom - inset - 10f, top + inset + 10f, level)
            drawRoundRect(
                Color.White,
                Offset(left + inset, fillTop),
                Size(right - left - inset * 2f, bottom - inset - fillTop),
                CornerRadius((radius - inset).coerceAtLeast(0f)),
                alpha = 0.95f * g,
            )
        }

        drawRoundRect(Color.White, Offset(58f, cardTop), Size(164f, 46f), CornerRadius(14f), alpha = 0.95f)
        drawAccessibilityGlyph(Offset(77f, cardTop + 23f))
        drawRoundRect(TextLine, Offset(94f, cardTop + 13f), Size(66f, 7f), CornerRadius(3.5f))
        drawRoundRect(TextLine, Offset(94f, cardTop + 26f), Size(44f, 6f), CornerRadius(3f), alpha = 0.6f)
        drawRoundRect(lerp(SwitchOff, Indigo, on), Offset(switchLeft, switchTop), Size(34f, 18f), CornerRadius(9f))
        drawCircle(Color.White, radius = 7f, center = Offset(switchLeft + 9f + 16f * on, switchTop + 9f))
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

// ---- Pieces ----------------------------------------------------------------------------------

/**
 * The volume keys on the phone's right side, past the frame. [pressed] pushes the upper one in and
 * lights it, so the eye finds the cause just before the slider shows the effect.
 */
private fun DrawScope.drawVolumeKeys(pressed: Float) {
    // Started inside the frame, in its colour, so each key reads as part of it rather than stuck on.
    drawRoundRect(FrameColour, Offset(237f, 52f), Size(6f - 2f * pressed, 22f), CornerRadius(2f))
    drawRoundRect(FrameColour, Offset(237f, 80f), Size(6f, 22f), CornerRadius(2f))
    if (pressed > 0.01f) {
        drawCircle(Indigo, radius = 8f + 6f * pressed, center = Offset(244f, 63f), alpha = 0.28f * pressed)
    }
}

/** Android's accessibility mark: a figure with open arms in a filled circle. */
private fun DrawScope.drawAccessibilityGlyph(center: Offset) {
    drawCircle(Indigo, radius = 10f, center = center)
    val ink = Color.White
    val stroke = 1.7f
    drawCircle(ink, radius = 1.9f, center = center + Offset(0f, -4.6f))
    drawLine(ink, center + Offset(-5f, -1.4f), center + Offset(5f, -1.4f), stroke, cap = StrokeCap.Round)
    drawLine(ink, center + Offset(0f, -1.4f), center + Offset(0f, 2.2f), stroke, cap = StrokeCap.Round)
    drawLine(ink, center + Offset(0f, 2.2f), center + Offset(-2.8f, 6.2f), stroke, cap = StrokeCap.Round)
    drawLine(ink, center + Offset(0f, 2.2f), center + Offset(2.8f, 6.2f), stroke, cap = StrokeCap.Round)
}


/** The phone: a dark frame, a soft purple-to-blue wallpaper, a status bar and faint app icons. */
private fun DrawScope.drawPhone() {
    drawRoundRect(Color.Black, Offset(37f, 16f), Size(206f, 240f), CornerRadius(33f), alpha = 0.10f)
    drawRoundRect(FrameColour, Offset(40f, 14f), Size(200f, 240f), CornerRadius(30f))
    clipPath(screenPath) {
        drawRect(
            Brush.linearGradient(listOf(WallTop, WallBottom), Offset(SCREEN_L, SCREEN_T), Offset(SCREEN_R, SCREEN_B)),
            topLeft = Offset(SCREEN_L, SCREEN_T),
            size = Size(SCREEN_R - SCREEN_L, SCREEN_B - SCREEN_T),
        )
        // A glow in one corner, so the wallpaper reads as a picture rather than a flat fill.
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), Offset(84f, 56f), 110f),
            radius = 110f,
            center = Offset(84f, 56f),
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
    drawCircle(FrameColour, radius = 4.5f, center = Offset(140f, 32f))
}

/** The Simple button. [lift] raises it off the glass, for when a hold has picked it up. */
private fun DrawScope.drawPill(cy: Float, lift: Float = 0f) {
    val grow = 1f + 0.12f * lift
    val w = PILL_W * grow
    val h = PILL_H * grow
    val topLeft = Offset(PILL_CX - w / 2f, cy - h / 2f)
    if (lift > 0.01f) {
        // A shadow falling away below it says it is in the finger now, not on the screen.
        drawRoundRect(Color.Black, topLeft + Offset(2f, 6f), Size(w, h), CornerRadius(w / 2f), alpha = 0.2f * lift.coerceIn(0f, 1f))
    }
    drawRoundRect(SimpleFill, topLeft, Size(w, h), CornerRadius(w / 2f))
    drawRoundRect(Color.White, topLeft, Size(w, h), CornerRadius(w / 2f), style = Stroke(1f))
}

private fun DrawScope.drawTab(path: Path, cy: Float = BAR_CY, alpha: Float = 1f) {
    tabPath(path, SCREEN_R, cy, TAB_W, TAB_H, TAB_SWEEP)
    drawPath(path, TabColour, alpha = alpha)
}

/**
 * The Dock tab: flush to the edge at [edge], [width] deep, its ends sweeping into the edge
 * rather than meeting it at a corner — [height] of straight side with a [sweep] at each end.
 *
 * Cut from the real bar's outline (see setTabOutline), not drawn freehand: a pair of cubics
 * left the ends as hooks with a crease where they met the straight side, and the walkthrough's
 * handle looked worse than the one it was introducing.
 */
private fun tabPath(path: Path, edge: Float, cy: Float, width: Float, height: Float, sweep: Float) {
    val total = height + 2f * sweep
    path.setTabOutline(
        edgeX = edge,
        top = cy - total / 2f,
        width = width,
        height = total,
        flare = HandlerPresets.DEFAULT.flare,
        edgeOnLeft = false,
    )
}

/** A volume level: a white track with the level filled in indigo from the bottom. */
private fun DrawScope.drawLevel(center: Offset, width: Float, height: Float, level: Float, alpha: Float, scale: Float = 1f) {
    if (alpha <= 0.01f || scale <= 0.01f) return
    val w = width * scale
    val h = height * scale
    val left = center.x - w / 2f
    val top = center.y - h / 2f
    drawRoundRect(Color.White, Offset(left, top), Size(w, h), CornerRadius(w / 2f), alpha = 0.92f * alpha)
    val inset = w * 0.22f
    val innerW = w - inset * 2f
    val innerH = h - inset * 2f
    val fill = (innerH * level).coerceAtLeast(innerW)
    drawRoundRect(Indigo, Offset(left + inset, top + inset + innerH - fill), Size(innerW, fill), CornerRadius(innerW / 2f), alpha = alpha)
}

/** Where a finger touches the glass: a soft spot, and a ring going out from it. */
private fun DrawScope.drawTouch(at: Offset, press: Float, pulse: Float) {
    if (press <= 0.01f) return
    drawCircle(Color.White, radius = 14f, center = at, alpha = 0.5f * press)
    drawCircle(Color.White, radius = 14f + 14f * pulse, center = at, alpha = 0.8f * press * (1f - pulse), style = Stroke(2f))
}

/**
 * The app's right hand (see [drawPointingHand]), reaching in from the bottom right, which is where
 * a right hand comes from to the right edge of a phone held in the left. [press] runs from 0,
 * hovering, to 1, on the glass.
 */
private fun DrawScope.drawFinger(tip: Offset, press: Float, alpha: Float) {
    drawPointingHand(tip, fingerWidth = FINGER_W, tilt = -28f, press = press, alpha = alpha)
}

/** The fingertip on its way in from [FingerRest], on [contact], or on its way back out. */
private fun fingerAt(contact: Offset, arrive: Float, leave: Float): Offset =
    if (leave > 0f) mix(contact, FingerRest, leave) else mix(FingerRest, contact, arrive)

/** How far [t] is through [from]..[to] of the loop: 0 before it, 1 after it, eased between. */
private fun span(t: Float, from: Float, to: Float, easing: Easing = Smooth): Float =
    easing.transform(((t - from) / (to - from)).coerceIn(0f, 1f))

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun mix(a: Offset, b: Offset, f: Float): Offset = Offset(mix(a.x, b.x, f), mix(a.y, b.y, f))
