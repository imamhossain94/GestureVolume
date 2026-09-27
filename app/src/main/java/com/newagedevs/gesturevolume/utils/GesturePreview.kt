package com.newagedevs.gesturevolume.utils

import kotlin.math.roundToInt

/**
 * What the Appearance preview acts out when its finger taps and swipes the bar: what the action
 * each gesture is set to does, and the level a swipe leaves behind.
 *
 * It asks of an action the questions `OverlayController.resolveAdjustAction` asks of it — is it
 * off, does it steer the volume or the brightness, does it bring up Android's volume panel — so the
 * preview can only show what the live bar would do. And, as there, a swipe's direction comes from
 * the finger, never from the action's name: up raises the level and down lowers it, whatever the
 * slot's identifier says. See [HandlerActions].
 *
 * Plain maths, so it is checked without a device.
 */
object GesturePreview {

    /** What a gesture does, as far as a preview can show it. */
    enum class Effect {
        /** Steers the volume. The level is all there is to see: on the bar, when it shows the number. */
        VOLUME,

        /** Steers the volume and brings up Android's volume panel as it goes. */
        VOLUME_PANEL,

        /** Steers the screen's brightness. */
        BRIGHTNESS,

        /** Opens Android's volume panel and changes nothing: a tap's usual job. */
        OPENS_PANEL,

        /** Anything else, which happens once, at the start of the gesture. The preview names it. */
        ONCE,

        /** Switched off. */
        NOTHING;

        val isVolume: Boolean get() = this == VOLUME || this == VOLUME_PANEL

        /** Whether the gesture moves a level, the number the bar shows while it does. */
        val steers: Boolean get() = isVolume || this == BRIGHTNESS

        val showsPanel: Boolean get() = this == VOLUME_PANEL || this == OPENS_PANEL
    }

    /** What a tap set to [action] does. A tap has no length, so nothing it does is steered. */
    fun tapEffect(action: String): Effect = when {
        HandlerActions.isDisabled(action) -> Effect.NOTHING
        action == HandlerActions.OPEN_VOLUME_UI -> Effect.OPENS_PANEL
        else -> Effect.ONCE
    }

    /** What a swipe up or down set to [action] does. */
    fun swipeEffect(action: String): Effect = when {
        HandlerActions.isDisabled(action) -> Effect.NOTHING
        // Anything from the catalog proper fires once, at the start of the stroke.
        !HandlerActions.isAdjustSwipe(action) -> tapEffect(action)
        HandlerActions.isBrightnessSwipe(action) -> Effect.BRIGHTNESS
        HandlerActions.showsVolumeUi(action) -> Effect.VOLUME_PANEL
        else -> Effect.VOLUME
    }

    /** Where the level stands before the first swipe, in percent: in the middle, with room either way. */
    const val START = 40

    /** The range a swipe by length climbs a step at a time: media volume's fifteen. */
    const val RANGE_STEPS = 15

    /** [START], in steps of [RANGE_STEPS]. */
    private const val START_STEP = 6

    /** How many steps one swipe by length moves in the preview: its travel is a few fingers' widths. */
    const val SWIPE_STEPS = 4

    /**
     * How far into a swipe set to a fixed amount the amount lands: at the start, as the live bar's
     * first step is all of that stroke.
     */
    const val FIXED_AT = 0.12f

    /**
     * The level, in percent, [up] of the way through the swipe up and [down] of the way through the
     * swipe down after it, each counted only when it [upSteers] or [downSteers] this level.
     *
     * By length ([BarBehaviour.SWIPE_STEP_BY_LENGTH]) it climbs a step at a time as the finger
     * travels; set to a fixed [stepPercent], it moves that much once, as soon as the swipe begins.
     */
    fun levelAt(up: Float, down: Float, upSteers: Boolean, downSteers: Boolean, stepPercent: Int): Int {
        if (stepPercent == BarBehaviour.SWIPE_STEP_BY_LENGTH) {
            val step = START_STEP +
                (if (upSteers) stepsAt(up) else 0) -
                (if (downSteers) stepsAt(down) else 0)
            return (step.coerceIn(0, RANGE_STEPS) * 100f / RANGE_STEPS).roundToInt()
        }
        val level = START +
            (if (upSteers && up >= FIXED_AT) stepPercent else 0) -
            (if (downSteers && down >= FIXED_AT) stepPercent else 0)
        return level.coerceIn(0, 100)
    }

    /** The steps a swipe by length has taken [progress] of the way through: the first soon after it starts. */
    fun stepsAt(progress: Float): Int =
        (progress.coerceIn(0f, 1f) * SWIPE_STEPS + 0.4f).toInt().coerceAtMost(SWIPE_STEPS)
}
