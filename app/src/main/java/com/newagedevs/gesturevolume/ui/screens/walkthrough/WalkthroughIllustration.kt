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
    /** Hold, drag, let go: the Simple button moved to the other side. */
    MoveButton(6000),
    /** The same, with the Dock tab. */
    MoveTab(6000),
    Permission(3600),
    Notifications(5200),
    Accessibility(5200),
}

/** Where the permission scene rests once the permission is granted: switch on, bar on screen. */
internal const val PERMISSION_SETTLED = 0.75f

/** Where the notifications scene rests once they are allowed: the shade down, the controls in it. */
internal const val NOTIFICATIONS_SETTLED = 0.75f

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

// The Deck's strip: the real one's 64 dp against a phone's width, and as tall as its six items,
// centred on the bar and short of the tab, which stays in sight beside it.
private const val DECK_W = 32f
private const val DECK_PAD = 5f
private const val DECK_GAP = 4f
private const val DECK_H = 141f
private const val DECK_TOP = BAR_CY - DECK_H / 2f
private const val DECK_RIGHT = 219f

// Where a moved bar comes to rest: the left edge, as the right one's mirror.
private const val PILL_CX_LEFT = SCREEN_L + 4f + PILL_W / 2f

/** How far the real bar fades while a hold has it following the finger. See HandlerView.setDragCue. */
private const val DRAG_CUE_ALPHA = 0.65f

/** How far down the edge the moved bar is carried, and where across the screen it is let go. */
private const val MOVE_DOWN = 44f
private const val MOVE_DROP_X = 88f

/** The middle of the notification request's Allow button. */
private const val NOTIF_ALLOW_Y = 116f

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
        drawWalkScene(scene, settledAt ?: clock.value, scratch)
    }
}

/**
 * [scene] at [t] of its loop, fitted to this scope's size. The whole drawing, apart from the clock
 * that moves it, so a test can put any moment of any scene on a bitmap.
 *
 * @param path scratch for the tab's outline, rewound for each use rather than made per frame.
 */
internal fun DrawScope.drawWalkScene(scene: WalkScene, t: Float, path: Path) {
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
            WalkScene.QuickSlider -> drawQuickSlider(t, pulse, path)
            WalkScene.Deck -> drawDeck(t, pulse, path)
            WalkScene.LongPress -> drawLongPress(t, pulse, path)
            WalkScene.MoveButton -> drawMove(t, pulse, path, tab = false)
            WalkScene.MoveTab -> drawMove(t, pulse, path, tab = true)
            WalkScene.Permission -> drawPermission(t, pulse)
            WalkScene.Notifications -> drawNotifications(t, pulse)
            WalkScene.Accessibility -> drawAccessibility(t, pulse, path)
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

/**
 * A swipe inward from the tab, and the Deck slides in beside it: one column down the edge, as it is
 * on the phone — a contact to dial, two apps, a divider, and two toggles, one of them on.
 */
private fun DrawScope.drawDeck(t: Float, pulse: Float, path: Path) {
    val arrive = span(t, 0.02f, 0.12f)
    val press = span(t, 0.12f, 0.16f) * (1f - span(t, 0.38f, 0.43f))
    val swipe = span(t, 0.16f, 0.38f)
    val leave = span(t, 0.42f, 0.56f)
    val alpha = span(t, 0f, 0.06f, LinearEasing) * (1f - span(t, 0.46f, 0.58f, LinearEasing))
    val close = span(t, 0.8f, 0.92f)
    val open = swipe * (1f - close)
    val tip = fingerAt(Offset(mix(SCREEN_R - TAB_W / 2f - 1f, 160f, swipe), BAR_CY), arrive, leave)

    clipPath(screenPath) {
        drawTab(path)
        if (open > 0.001f) {
            val left = mix(SCREEN_R + 4f, DECK_RIGHT - DECK_W, open)
            val corner = CornerRadius(DECK_W / 2f)
            drawRoundRect(Color.Black, Offset(left + 2f, DECK_TOP + 6f), Size(DECK_W, DECK_H), corner, alpha = 0.18f * open)
            drawRoundRect(DeckPanel, Offset(left, DECK_TOP), Size(DECK_W, DECK_H), corner)
            val cx = left + DECK_W / 2f
            var y = DECK_TOP + DECK_PAD
            DeckItem.entries.forEachIndexed { i, item ->
                // One after another down the column: the Deck fills in as it arrives.
                val appear = span(t, 0.24f + i * 0.03f, 0.34f + i * 0.03f, Pop) * (1f - close)
                val cy = y + item.height / 2f
                if (appear > 0.01f) drawDeckItem(item, Offset(cx, cy), appear)
                y += item.height + DECK_GAP
            }
        }
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/** What the Deck's column holds in the scene, top to bottom, in the real strip's order. */
private enum class DeckItem(val height: Float) { Dial(22f), App(22f), SecondApp(22f), Divider(1f), ToggleOn(22f), Toggle(22f) }

private fun DrawScope.drawDeckItem(item: DeckItem, center: Offset, appear: Float) {
    val r = 11f * appear
    when (item) {
        // A contact: a round chip with an initial.
        DeckItem.Dial -> {
            drawCircle(Color.White, radius = r, center = center, alpha = 0.14f)
            drawCircle(TileColours[3], radius = 4.5f * appear, center = center)
        }
        // Apps are their own icons, rounded squares in their own colours.
        DeckItem.App, DeckItem.SecondApp -> {
            val side = 20f * appear
            val colour = if (item == DeckItem.App) TileColours[0] else TileColours[1]
            drawRoundRect(colour, center - Offset(side / 2f, side / 2f), Size(side, side), CornerRadius(6f * appear))
            drawCircle(Color.White, radius = 3.5f * appear, center = center, alpha = 0.85f)
        }
        DeckItem.Divider -> drawRoundRect(
            Color.White, center - Offset(7f, 0.5f), Size(14f, 1f), CornerRadius(0.5f), alpha = 0.18f * appear,
        )
        // A toggle that is on wears the accent; one that is off is a chip with its glyph in colour.
        DeckItem.ToggleOn -> {
            drawCircle(Indigo, radius = r, center = center)
            drawCircle(Color.White, radius = 4f * appear, center = center)
        }
        DeckItem.Toggle -> {
            drawCircle(Color.White, radius = r, center = center, alpha = 0.14f)
            drawCircle(TileColours[2], radius = 4f * appear, center = center)
        }
    }
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

/**
 * A hold until the bar comes away from the edge, a drag down and across the screen, and a let-go
 * short of the far side: it flies the rest of the way and stays there. While it is held it fades
 * and wears the move arrows, as the real one does. Then it is put back, for the next loop.
 *
 * [tab] draws the Dock tab, whose flat side turns to face whichever edge it is nearer, as the
 * real one's does; otherwise the Simple button.
 */
private fun DrawScope.drawMove(t: Float, pulse: Float, path: Path, tab: Boolean) {
    val arrive = span(t, 0.02f, 0.12f)
    val press = span(t, 0.12f, 0.16f) * (1f - span(t, 0.7f, 0.74f))
    val hold = span(t, 0.16f, 0.34f, LinearEasing)
    // Picked up the moment the hold completes; put down the moment the finger lifts.
    val held = span(t, 0.33f, 0.4f, Pop) * (1f - span(t, 0.7f, 0.76f))
    val down = span(t, 0.4f, 0.52f)
    val across = span(t, 0.54f, 0.68f)
    // Let go short of the left edge, it flies the rest of the way on its own.
    val snap = span(t, 0.72f, 0.82f, Pop)
    val leave = span(t, 0.74f, 0.88f)
    val alpha = span(t, 0f, 0.06f, LinearEasing) * (1f - span(t, 0.78f, 0.9f, LinearEasing))
    // Faded out where it landed and back in where it began, so the next loop starts the same way.
    val gone = span(t, 0.9f, 0.94f, LinearEasing) * (1f - span(t, 0.95f, 0.99f, LinearEasing))
    val home = t >= 0.945f

    val startX = if (tab) SCREEN_R - TAB_W / 2f else PILL_CX
    val restX = if (tab) SCREEN_L + TAB_W / 2f else PILL_CX_LEFT
    val heldX = mix(startX, MOVE_DROP_X, across)
    val x = if (home) startX else mix(heldX, restX, snap)
    val y = if (home) BAR_CY else BAR_CY + MOVE_DOWN * down
    val tip = fingerAt(Offset(heldX, BAR_CY + MOVE_DOWN * down), arrive, leave)
    val lift = held.coerceIn(0f, 1f)
    val barAlpha = (1f - gone) * (1f - (1f - DRAG_CUE_ALPHA) * lift)

    clipPath(screenPath) {
        if (tab) {
            val onLeft = x < (SCREEN_L + SCREEN_R) / 2f
            val edge = if (onLeft) x - TAB_W / 2f else x + TAB_W / 2f
            withTransform({ scale(1f + 0.12f * held, 1f + 0.12f * held, pivot = Offset(x, y)) }) {
                if (lift > 0.01f) {
                    // A shadow falling away below it: it is in the finger now, not on the glass.
                    tabPath(path, edge + 2f, y + 6f, TAB_W, TAB_H, TAB_SWEEP, onLeft)
                    drawPath(path, Color.Black, alpha = 0.2f * lift * (1f - gone))
                }
                tabPath(path, edge, y, TAB_W, TAB_H, TAB_SWEEP, onLeft)
                drawPath(path, TabColour, alpha = barAlpha)
            }
            drawMoveGlyph(Offset(x, y), 7f, lift * (1f - gone))
        } else {
            drawPill(y, held, cx = x, alpha = barAlpha)
            drawMoveGlyph(Offset(x, y), 10f, lift * (1f - gone))
        }
        if (press > 0.01f && hold > 0f && hold < 1f) {
            // The hold, counted round the fingertip, as on the long-press page.
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
 * Allow tapped on Android's notification request, and the notification shade comes down with the
 * bar's own notification in it: its Hide, Settings and Stop buttons, there with the app closed.
 */
private fun DrawScope.drawNotifications(t: Float, pulse: Float) {
    val arrive = span(t, 0.04f, 0.18f)
    val press = span(t, 0.2f, 0.24f) * (1f - span(t, 0.28f, 0.33f))
    val leave = span(t, 0.34f, 0.5f)
    val alpha = span(t, 0f, 0.08f, LinearEasing) * (1f - span(t, 0.4f, 0.52f, LinearEasing))
    // Answered, the request goes; at the end it comes back, so the loop starts from the question.
    val asking = (1f - span(t, 0.3f, 0.4f)) + span(t, 0.93f, 0.99f, LinearEasing)
    val shade = span(t, 0.42f, 0.58f) * (1f - span(t, 0.86f, 0.93f))
    val tip = fingerAt(Offset(140f, NOTIF_ALLOW_Y), arrive, leave)

    clipPath(screenPath) {
        if (shade > 0.001f) drawShade(shade)
        if (asking > 0.01f) {
            drawRect(Color.Black, Offset(SCREEN_L, SCREEN_T), Size(SCREEN_R - SCREEN_L, SCREEN_B - SCREEN_T), alpha = 0.22f * asking)
            val grow = 0.92f + 0.08f * asking
            withTransform({ scale(grow, grow, pivot = Offset(140f, 100f)) }) {
                drawRoundRect(Color.White, Offset(72f, 50f), Size(136f, 100f), CornerRadius(16f), alpha = 0.97f * asking)
                drawBell(Offset(140f, 67f), asking)
                drawRoundRect(TextLine, Offset(100f, 82f), Size(80f, 6f), CornerRadius(3f), alpha = asking)
                drawRoundRect(TextLine, Offset(112f, 93f), Size(56f, 5f), CornerRadius(2.5f), alpha = 0.6f * asking)
                // Allow, lit as the finger lands on it, and Don't allow under it.
                drawRoundRect(Indigo, Offset(84f, NOTIF_ALLOW_Y - 7.5f), Size(112f, 15f), CornerRadius(7.5f), alpha = (0.14f + 0.3f * press) * asking)
                drawRoundRect(Indigo, Offset(122f, NOTIF_ALLOW_Y - 2f), Size(36f, 4f), CornerRadius(2f), alpha = asking)
                drawRoundRect(TextLine, Offset(116f, NOTIF_ALLOW_Y + 17f), Size(48f, 4f), CornerRadius(2f), alpha = asking)
            }
        }
        drawTouch(tip, press, pulse)
    }
    drawFinger(tip, press, alpha)
}

/**
 * The notification shade, [open] of the way down, with the bar's notification in it: the app's
 * mark, its name and state, and its three buttons.
 */
private fun DrawScope.drawShade(open: Float) {
    // Slid down from above the screen rather than grown, so everything in it moves together.
    val dy = -(1f - open) * 118f
    drawRoundRect(DeckPanel, Offset(SCREEN_L, SCREEN_T - 30f + dy), Size(SCREEN_R - SCREEN_L, 140f), CornerRadius(20f))
    for (i in 0 until 4) {
        drawCircle(Color.White, radius = 7f, center = Offset(82f + i * 38f, 46f + dy), alpha = if (i == 1) 0.9f else 0.18f)
    }
    val top = 62f + dy
    drawRoundRect(Color.White, Offset(58f, top), Size(164f, 56f), CornerRadius(14f), alpha = 0.97f)
    // The app's mark: the bar, in a circle of the accent.
    drawCircle(Indigo, radius = 7f, center = Offset(72f, top + 14f))
    drawRoundRect(Color.White, Offset(70.8f, top + 10f), Size(2.4f, 8f), CornerRadius(1.2f))
    drawRoundRect(TextLine, Offset(84f, top + 9f), Size(60f, 6f), CornerRadius(3f))
    drawRoundRect(TextLine, Offset(84f, top + 19f), Size(84f, 5f), CornerRadius(2.5f), alpha = 0.6f)
    // Hide, Settings, Stop.
    for (i in 0 until 3) {
        val left = 70f + i * 48f
        drawRoundRect(Indigo, Offset(left, top + 35f), Size(42f, 12f), CornerRadius(6f), alpha = 0.12f)
        drawRoundRect(Indigo, Offset(left + 10f, top + 39.5f), Size(22f, 3f), CornerRadius(1.5f), alpha = 0.75f)
    }
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

/** A bell in a filled circle: what Android's notification request leads with. */
private fun DrawScope.drawBell(center: Offset, alpha: Float) {
    drawCircle(Indigo, radius = 9f, center = center, alpha = alpha)
    val ink = Color.White
    // A dome, the body under it, the flared lip, and the clapper below.
    drawArc(ink, 180f, 180f, useCenter = true, topLeft = center + Offset(-4f, -5.5f), size = Size(8f, 8f), alpha = alpha)
    drawRect(ink, center + Offset(-4f, -1.6f), Size(8f, 3.6f), alpha = alpha)
    drawRoundRect(ink, center + Offset(-5.6f, 1.8f), Size(11.2f, 1.8f), CornerRadius(0.9f), alpha = alpha)
    drawCircle(ink, radius = 1.3f, center = center + Offset(0f, 4.8f), alpha = alpha)
}

/** The move mark the real bar wears while it follows a finger: arrows out to all four sides. */
private fun DrawScope.drawMoveGlyph(center: Offset, size: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val r = size / 2f
    val head = r * 0.42f
    val stroke = size * 0.13f
    val ink = Color.White
    drawLine(ink, center + Offset(-r, 0f), center + Offset(r, 0f), stroke, StrokeCap.Round, alpha = alpha)
    drawLine(ink, center + Offset(0f, -r), center + Offset(0f, r), stroke, StrokeCap.Round, alpha = alpha)
    // Each head: two short strokes back from the tip, either side of the shaft.
    for (i in 0 until 4) {
        val ux = if (i == 0) 1f else if (i == 1) -1f else 0f
        val uy = if (i == 2) 1f else if (i == 3) -1f else 0f
        val tip = center + Offset(ux * r, uy * r)
        drawLine(ink, tip, tip + Offset(-ux * head - uy * head, -uy * head + ux * head), stroke, StrokeCap.Round, alpha = alpha)
        drawLine(ink, tip, tip + Offset(-ux * head + uy * head, -uy * head - ux * head), stroke, StrokeCap.Round, alpha = alpha)
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

/**
 * The Simple button, centred on ([cx], [cy]). [lift] raises it off the glass, for when a hold has
 * picked it up.
 */
private fun DrawScope.drawPill(cy: Float, lift: Float = 0f, cx: Float = PILL_CX, alpha: Float = 1f) {
    val grow = 1f + 0.12f * lift
    val w = PILL_W * grow
    val h = PILL_H * grow
    val topLeft = Offset(cx - w / 2f, cy - h / 2f)
    if (lift > 0.01f) {
        // A shadow falling away below it says it is in the finger now, not on the screen.
        drawRoundRect(Color.Black, topLeft + Offset(2f, 6f), Size(w, h), CornerRadius(w / 2f), alpha = 0.2f * lift.coerceIn(0f, 1f) * alpha)
    }
    drawRoundRect(SimpleFill, topLeft, Size(w, h), CornerRadius(w / 2f), alpha = alpha)
    drawRoundRect(Color.White, topLeft, Size(w, h), CornerRadius(w / 2f), alpha = alpha, style = Stroke(1f))
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
 *
 * [edgeOnLeft] turns it to face a left edge: its flat side on the left, reaching right.
 */
private fun tabPath(path: Path, edge: Float, cy: Float, width: Float, height: Float, sweep: Float, edgeOnLeft: Boolean = false) {
    val total = height + 2f * sweep
    path.setTabOutline(
        edgeX = edge,
        top = cy - total / 2f,
        width = width,
        height = total,
        flare = HandlerPresets.DEFAULT.flare,
        edgeOnLeft = edgeOnLeft,
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
