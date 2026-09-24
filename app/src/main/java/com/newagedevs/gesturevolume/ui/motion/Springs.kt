package com.newagedevs.gesturevolume.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset

/**
 * The springs the app's screens move on: one place, so a press, a list meeting its end and a
 * screen arriving all feel like the same material.
 *
 * Springs rather than curves because the things they move get interrupted — a finger comes back
 * mid-bounce, a second tap lands before the first has settled — and a spring picks up from
 * wherever it is, at whatever speed it had, where a curve restarts from its beginning.
 *
 * All of it runs on the system's animation scale: with animations switched off in the phone's
 * settings, every one of these arrives at rest on its first frame.
 */
object Springs {

    /** A press going in: quick and firm, with no bounce. A finger pushing, not a spring letting go. */
    val PressIn: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /**
     * A press let go: back out past rest by about a third of the squeeze, and settling. The
     * bounce everything else is measured against.
     */
    val Release: SpringSpec<Float> = spring(dampingRatio = 0.32f, stiffness = 420f)

    /** Content held out past its end and let go: straight back, the least overshoot that still reads as a spring. */
    val Settle: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Content that ran into its end mid-fling: carried on a little past it, and back. */
    val Rebound: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 300f)

    /**
     * A screen sliding in or out. A slight overshoot — a few pixels on a fifth of the screen's
     * width — so it lands rather than stops.
     */
    val ScreenOffset: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    /**
     * The fade that goes with a screen's slide. Not bouncy: alpha stops at 0 and 1, and a spring
     * that overshoots spends its bounce against that stop and shows as a flicker.
     */
    val ScreenFade: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 1600f)
}
