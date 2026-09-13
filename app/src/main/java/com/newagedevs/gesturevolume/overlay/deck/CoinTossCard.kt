package com.newagedevs.gesturevolume.overlay.deck

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.PanelAnimation
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Heads, tails and the run the coin is on, for as long as the Deck's host lives.
 *
 * Immutable, and replaced whole on every toss, so the card can hold back the new tally until the
 * coin has landed without a second copy of the counting rules.
 */
@Immutable
data class CoinTally(
    val heads: Int = 0,
    val tails: Int = 0,
    /** The face the current run is on; null before the first toss. */
    val streakHeads: Boolean? = null,
    /** How many tosses in a row have landed on [streakHeads]. */
    val streak: Int = 0,
) {
    val total: Int get() = heads + tails

    fun record(resultHeads: Boolean): CoinTally = CoinTally(
        heads = heads + if (resultHeads) 1 else 0,
        tails = tails + if (resultHeads) 0 else 1,
        streakHeads = resultHeads,
        streak = if (streakHeads == resultHeads) streak + 1 else 1,
    )
}

/**
 * The arithmetic of a toss, kept free of Compose so it can be checked without a device.
 *
 * Angles are the coin's rotation about its horizontal axis, in degrees: 0 is heads facing the
 * viewer, 180 is tails.
 */
internal object CoinToss {

    private const val BASE_FLIP_MS = 1100
    const val MIN_FLIP_MS = 900
    const val MAX_FLIP_MS = 1300

    /** Three full turns. One more half-turn when the coin has to come down on its other face. */
    const val SAME_FACE_HALF_TURNS = 6

    fun restAngle(heads: Boolean): Float = if (heads) 0f else 180f

    /** Which face is towards the viewer at [angle]. Edge-on at exactly 90° counts as tails. */
    fun showsHeads(angle: Float): Boolean {
        val a = ((angle % 360f) + 360f) % 360f
        return a < 90f || a >= 270f
    }

    /** Half-turns for a toss from one face to the other: even keeps the face, odd turns it over. */
    fun halfTurns(fromHeads: Boolean, toHeads: Boolean): Int =
        if (fromHeads == toHeads) SAME_FACE_HALF_TURNS else SAME_FACE_HALF_TURNS + 1

    /** How much of the spin is done at [progress]: fast off the thumb, slowing into the catch. */
    fun spin(progress: Float): Float = 1f - (1f - progress.coerceIn(0f, 1f)).pow(2.5f)

    /** Height of the toss at [progress], 0 on the ground and 1 at the top: a plain parabola. */
    fun lift(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return 4f * t * (1f - t)
    }

    /** How squarely the coin faces the viewer, 1 flat-on and 0 edge-on. Drives the shading. */
    fun facing(angle: Float): Float = abs(cos(angle / 180f * PI.toFloat()))

    /** The toss's length under the user's panel animation speed, kept to a range that reads as a toss. */
    fun flipDurationMs(speed: Float): Int =
        (BASE_FLIP_MS / PanelAnimation.sanitizeSpeed(speed)).roundToInt().coerceIn(MIN_FLIP_MS, MAX_FLIP_MS)
}

private val COIN_SIZE = 100.dp
private val TOSS_HEIGHT = 22.dp

/**
 * The coin toss card: a drawn coin that flips end over end, the result, and a tally for the session.
 *
 * The result is decided, and written to [DeckState], the moment the toss starts; the card only
 * holds back *showing* it until the coin lands. So closing the Deck mid-air loses nothing — the
 * next opening shows the coin on the face it was always going to land on, and counts it.
 */
@Composable
fun CoinTossCard(actions: DeckActions, palette: DeckPalette) {
    val state = actions.env.state
    val context = actions.env.context
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val headsLabel = stringResource(R.string.deck_coin_heads)
    val tailsLabel = stringResource(R.string.deck_coin_tails)
    val tossLabel = stringResource(R.string.deck_coin_toss)

    val restAngle = CoinToss.restAngle(state.coinHeads != false)
    // The flight runs from one angle to another as progress goes 0 → 1; at rest both are the face.
    var fromAngle by remember { mutableFloatStateOf(restAngle) }
    var toAngle by remember { mutableFloatStateOf(restAngle) }
    val progress = remember { Animatable(1f) }
    val landing = remember { Animatable(1f) }
    val reveal = remember { Animatable(if (state.coinHeads != null) 1f else 0f) }
    var flipping by remember { mutableStateOf(false) }
    var shownHeads by remember { mutableStateOf(state.coinHeads) }
    var shownTally by remember { mutableStateOf(state.coinTally) }
    val speed = remember { actions.env.preference.getPanelAnimationSpeed() }

    fun angleNow(): Float = fromAngle + (toAngle - fromAngle) * CoinToss.spin(progress.value)

    fun toss() {
        if (flipping) return
        actions.onInteraction()
        val fromHeads = CoinToss.showsHeads(toAngle)
        val result = Random.nextBoolean()
        state.coinHeads = result
        state.coinTally = state.coinTally.record(result)
        fromAngle = CoinToss.restAngle(fromHeads)
        toAngle = fromAngle + CoinToss.halfTurns(fromHeads, result) * 180f
        flipping = true
        scope.launch {
            try {
                if (animationsDisabled(context)) {
                    progress.snapTo(1f)
                } else {
                    launch { reveal.animateTo(0f, tween(durationMillis = 140)) }
                    progress.snapTo(0f)
                    progress.animateTo(
                        1f,
                        tween(durationMillis = CoinToss.flipDurationMs(speed), easing = LinearEasing)
                    )
                }
                shownHeads = result
                shownTally = state.coinTally
                flipping = false
                if (actions.env.preference.getHandlerVibrateOnClick()) landingTick(context)
                launch {
                    landing.animateTo(0.93f, tween(durationMillis = 70))
                    landing.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 900f))
                }
                reveal.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
            } finally {
                flipping = false
            }
        }
    }

    val colors = remember(palette) { CoinColors(palette) }
    val measurer = rememberTextMeasurer()
    val numeralSize = with(density) { (COIN_SIZE * 0.42f).toSp() }
    val numeral = remember(measurer, numeralSize) {
        measurer.measure("1", TextStyle(fontSize = numeralSize, fontWeight = FontWeight.Black))
    }
    val numeralPx = with(density) { numeralSize.toPx() }
    val tossHeightPx = with(density) { TOSS_HEIGHT.toPx() }

    val coinDescription = when (shownHeads) {
        true -> stringResource(R.string.deck_coin_showing, headsLabel)
        false -> stringResource(R.string.deck_coin_showing, tailsLabel)
        null -> stringResource(R.string.deck_coin_untossed)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // Room above the coin for the toss, so it never rises into the card's header.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(COIN_SIZE + TOSS_HEIGHT + 14.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.width(COIN_SIZE * 0.86f).height(14.dp)) {
                val up = CoinToss.lift(progress.value)
                val w = size.width * (1f - 0.4f * up)
                withTransform({ scale(1f, size.height / size.width, pivot = center) }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to colors.shadow.copy(alpha = colors.shadow.alpha * (1f - 0.6f * up)),
                            1f to Color.Transparent,
                            center = center,
                            radius = w / 2f
                        ),
                        radius = w / 2f,
                        center = center
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 7.dp)
                    .size(COIN_SIZE)
                    .graphicsLayer {
                        val p = progress.value
                        val up = CoinToss.lift(p)
                        rotationX = angleNow()
                        // The layer's own density, not the composable's LocalDensity of the same name.
                        cameraDistance = 14f * this.density
                        translationY = -tossHeightPx * up
                        val s = (1f + 0.12f * up) * landing.value
                        scaleX = s
                        scaleY = s
                    }
                    .semantics { contentDescription = coinDescription }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !flipping,
                        onClickLabel = tossLabel,
                        role = Role.Button,
                        onClick = ::toss
                    )
            ) {
                Canvas(modifier = Modifier.size(COIN_SIZE)) {
                    val angle = angleNow()
                    val heads = CoinToss.showsHeads(angle)
                    // Past a quarter-turn the layer shows the coin's back mirrored top to bottom;
                    // mirroring the drawing too puts the face, and its highlight, back upright.
                    withTransform({ if (!heads) scale(1f, -1f, pivot = center) }) {
                        drawCoin(heads, CoinToss.facing(angle), colors, numeral, numeralPx)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (shownHeads) {
                true -> headsLabel
                false -> tailsLabel
                null -> ""
            },
            color = palette.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .height(30.dp)
                .graphicsLayer {
                    alpha = reveal.value
                    translationY = (1f - reveal.value) * 10.dp.toPx()
                }
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        val streakText = when {
            shownTally.streak >= 2 -> stringResource(
                R.string.deck_coin_streak,
                shownTally.streak,
                if (shownTally.streakHeads == true) headsLabel else tailsLabel
            )
            shownTally.total == 0 && !flipping -> stringResource(R.string.deck_coin_hint)
            else -> ""
        }
        Text(
            text = streakText,
            color = if (shownTally.streak >= 2) palette.accent else palette.subtle,
            fontSize = 12.sp,
            fontWeight = if (shownTally.streak >= 2) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .height(18.dp)
                .graphicsLayer { alpha = if (shownTally.total == 0) 1f else reveal.value }
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TallyPill(
                label = headsLabel,
                count = shownTally.heads,
                highlighted = !flipping && shownTally.total > 0 && shownHeads == true,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            TallyPill(
                label = tailsLabel,
                count = shownTally.tails,
                highlighted = !flipping && shownTally.total > 0 && shownHeads == false,
                palette = palette,
                modifier = Modifier.weight(1f)
            )
            val canReset = shownTally.total > 0 && !flipping
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette.chip)
                    .clickable(enabled = canReset, role = Role.Button) {
                        state.coinTally = CoinTally()
                        shownTally = CoinTally()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.deck_coin_reset),
                    tint = palette.accent.copy(alpha = if (canReset) 1f else 0.35f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        val headsShare by animateFloatAsState(
            targetValue = if (shownTally.total == 0) 0.5f else shownTally.heads / shownTally.total.toFloat(),
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            label = "coinShare"
        )
        Canvas(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = palette.accent.copy(alpha = 0.18f), cornerRadius = radius)
            if (shownTally.total > 0 && headsShare > 0f) {
                drawRoundRect(
                    color = palette.accent,
                    size = Size(size.width * headsShare, size.height),
                    cornerRadius = radius
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        val buttonAlpha by animateFloatAsState(if (flipping) 0.55f else 1f, label = "coinButton")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = buttonAlpha }
                .clip(RoundedCornerShape(14.dp))
                .background(palette.accent)
                .clickable(enabled = !flipping, role = Role.Button, onClick = ::toss)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tossLabel,
                color = palette.onAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TallyPill(
    label: String,
    count: Int,
    highlighted: Boolean,
    palette: DeckPalette,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) palette.accent.copy(alpha = if (palette.light) 0.20f else 0.26f) else palette.chip)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (highlighted) palette.onBackground else palette.subtle,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            color = palette.accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** The coin's metal, worked out from the accent so it reads on pale and dark panels alike. */
private class CoinColors(palette: DeckPalette) {
    val base: Color = palette.accent
    private val bright = base.luminance() > 0.5f
    /** A bright coin gets a darker edge; a dark one a lighter, polished-looking one. */
    val rim: Color = lerp(base, if (bright) Color.Black else Color.White, if (bright) 0.28f else 0.24f)
    val rimShade: Color = lerp(base, Color.Black, if (bright) 0.45f else 0.55f)
    val faceLight: Color = lerp(base, Color.White, if (bright) 0.55f else 0.32f)
    val faceShade: Color = lerp(base, Color.Black, if (bright) 0.16f else 0.32f)
    val ink: Color = palette.onAccent.copy(alpha = 0.9f)
    val inkSoft: Color = palette.onAccent.copy(alpha = 0.32f)
    val emboss: Color = rimShade.copy(alpha = 0.45f)
    val shadow: Color = Color.Black.copy(alpha = if (palette.light) 0.22f else 0.5f)
}

/**
 * One face of the coin: a bevelled rim, a domed face lit from the top left, a ring of beads, and
 * the emblem — a star for heads, a "1" for tails, so the faces differ in every language.
 */
private fun DrawScope.drawCoin(
    heads: Boolean,
    facing: Float,
    colors: CoinColors,
    numeral: TextLayoutResult,
    numeralPx: Float
) {
    val r = size.minDimension / 2f
    val c = center

    // Rim, lit across its diagonal.
    drawCircle(
        brush = Brush.linearGradient(
            0f to colors.faceLight,
            0.45f to colors.rim,
            1f to colors.rimShade,
            start = Offset(c.x - r, c.y - r),
            end = Offset(c.x + r, c.y + r)
        ),
        radius = r,
        center = c
    )
    // The groove between rim and face.
    drawCircle(color = colors.rimShade.copy(alpha = 0.6f), radius = r * 0.86f, center = c, style = Stroke(width = r * 0.035f))
    // Face.
    drawCircle(
        brush = Brush.radialGradient(
            0f to colors.faceLight,
            0.55f to colors.base,
            1f to colors.faceShade,
            center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
            radius = r * 1.3f
        ),
        radius = r * 0.84f,
        center = c
    )
    // Beads.
    val beads = 32
    for (i in 0 until beads) {
        val t = 2.0 * PI * i / beads
        drawCircle(
            color = colors.inkSoft,
            radius = r * 0.026f,
            center = Offset(c.x + (cos(t) * r * 0.73f).toFloat(), c.y + (sin(t) * r * 0.73f).toFloat())
        )
    }

    val lift = r * 0.03f
    if (heads) {
        val star = starPath(c, outer = r * 0.40f, inner = r * 0.17f)
        withTransform({ translate(0f, lift) }) { drawPath(star, colors.emboss) }
        drawPath(star, colors.ink)
    } else {
        drawCircle(color = colors.inkSoft, radius = r * 0.56f, center = c, style = Stroke(width = r * 0.025f))
        // Centred on the digit's own height rather than the line box, which carries ascent and descent.
        val baseline = c.y + numeralPx * 0.36f
        val topLeft = Offset(c.x - numeral.size.width / 2f, baseline - numeral.firstBaseline)
        drawText(numeral, color = colors.emboss, topLeft = topLeft + Offset(0f, lift))
        drawText(numeral, color = colors.ink, topLeft = topLeft)
    }

    // Specular highlight, strongest face-on.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.42f * facing),
            1f to Color.Transparent,
            center = Offset(c.x - r * 0.38f, c.y - r * 0.45f),
            radius = r * 0.62f
        ),
        radius = r * 0.84f,
        center = c
    )
    // A coin turning edge-on catches less light.
    if (facing < 1f) drawCircle(color = Color.Black.copy(alpha = (1f - facing) * 0.38f), radius = r, center = c)
}

private fun starPath(c: Offset, outer: Float, inner: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = c.x + (cos(a) * radius).toFloat()
        val y = c.y + (sin(a) * radius).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** True when the system's animator duration scale is off, where the toss simply lands. */
private fun animationsDisabled(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

/**
 * One short tick as the coin lands. The vibrator rather than view haptics, for the reason the
 * overlay controller gives: view haptics are routinely dropped in overlay windows.
 */
private fun landingTick(context: Context) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    val effect = if (vibrator.hasAmplitudeControl()) {
        VibrationEffect.createOneShot(18L, 140)
    } else {
        VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    runCatching { vibrator.vibrate(effect) }
}
