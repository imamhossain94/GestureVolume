package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntSize

/**
 * The motion vocabulary for the appearance screen.
 *
 * One rule organises all of it, and it is the difference between motion that feels physical and
 * motion that feels broken:
 *
 * > **A spring may overshoot a transform. It must never overshoot a layout dimension, and it must
 * > never be applied to a colour.**
 *
 * A bouncy spring on a *layout* size makes every row below the animating one lurch past its resting
 * place and settle back — the whole screen wobbles because one section opened. A spring on a
 * *colour* or an alpha overshoots out of the legal 0..1 range, clamps there, and reads as a dropped
 * frame rather than as bounce. So layout springs here are damped, colours use a tween, and the
 * bounce is spent where it is free: rotation and scale, which are draw-layer properties that cost
 * no layout pass and cannot push anything else around.
 *
 * Kept in one object rather than inlined at each call site so the screen reads as one system —
 * which is the actual complaint behind "it should feel smoother", since mismatched durations are
 * what make a screen feel assembled rather than designed.
 *
 * Reduced-motion needs no code here: Compose scales every one of these through the platform's
 * animator duration setting, so a user who has turned animations off gets the end state directly.
 */
object AppearanceMotion {

    /**
     * Expanding and collapsing a section body.
     *
     * `DampingRatioLowBouncy` (0.75) overshoots by under 3%, and `expandVertically` *clips* rather
     * than stretches, so that overshoot is a few pixels of empty space at the bottom for one frame
     * rather than a stretched control. Raise this toward 0.9 if that transient gap ever reads as a
     * glitch on a slower device.
     *
     * The visibility threshold is not load-bearing — the default resolves to a hundredth of a pixel
     * and the animation terminates either way — but stating it in integer pixels is honest about
     * what "finished" means for a size.
     */
    val ExpandSize: FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize.VisibilityThreshold,
    )

    /**
     * The fade that rides along with an expand.
     *
     * Deliberately not bouncy: alpha is clamped to 0..1, so an overshooting spring would spend its
     * bounce against the clamp and show as a stutter.
     */
    val Fade: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /**
     * The section chevron's half-turn.
     *
     * The bounciest thing on the screen, and the only place a real overshoot is visible. It is safe
     * precisely because rotation is a draw-layer property: overshooting 180° by a fifth of the
     * travel is about 37° past the target, and a chevron is symmetric under a half-turn, so the
     * overshoot reads as spring rather than as the wrong glyph.
     */
    val Chevron: FiniteAnimationSpec<Float> = spring(
        dampingRatio = 0.45f,
        stiffness = 320f,
    )

    /**
     * A press or a state change on something small — the Apply tick, a preset chip.
     *
     * Applied to `scaleX`/`scaleY` through `graphicsLayer`, never to a size, so it cannot reflow
     * anything around it however far it overshoots.
     */
    val Pop: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Colour changes. A tween, for the clamping reason in this file's header. */
    val Tint: FiniteAnimationSpec<androidx.compose.ui.graphics.Color> = tween(durationMillis = 180)
}
