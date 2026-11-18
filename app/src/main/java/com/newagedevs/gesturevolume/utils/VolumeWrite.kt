package com.newagedevs.gesturevolume.utils

import kotlin.math.abs

/**
 * How the bar moves the volume, and how it tells a write that landed from one the system refused
 * or quietly put back.
 *
 * Pure, like [AudioStreamResolver], so the decisions can be checked on the host JVM; the Android
 * half is [VolumeController].
 *
 * **Why this exists.** Every write used to be an absolute `setStreamVolume` that was reported as a
 * success the moment the call returned. Two kinds of report came of that:
 *
 *  - *"Volume not increasing after 78%."* Above the hearing-safety level, with headphones in,
 *    Android holds an absolute write until the user accepts a warning — and on some builds the
 *    warning is only shown to a caller that asks for the system's UI, which a swipe with the system
 *    panel switched off does not. The bar showed the level it had asked for, the audio stayed where
 *    it was, and nothing on screen said why.
 *  - *"Swiping changes the volume but it quickly falls back to a fixed level, which only the system
 *    panel can change."* Some routes take a relative step and put an absolute one back: headsets
 *    and speakers that keep their own volume and answer a phone that sets it with the level they
 *    already had, devices whose volume is adjust-only, and apps that pin the level they last saw
 *    the system set. The volume keys are relative, so they always worked; the bar's absolute writes
 *    snapped back.
 *
 * So a write is read back, and what was read decides what happens next — see [outcome] — and a
 * swipe's result is looked at again a moment after it ends, to catch a route that puts levels back
 * ([isRevert]). A route caught doing that is remembered by [routeKey] and driven the way the keys
 * drive it from then on.
 */
object VolumeWrite {

    // ---- the user's choice: a persistence format, never renamed --------------------------------

    /**
     * Exact writes, read back; a refused step is handed once to the system's own relative step,
     * and a route that puts levels back is driven by relative steps from then on. The default.
     */
    const val METHOD_AUTO = "auto"

    /**
     * Exactly what the volume buttons do, every step: the system chooses the stream and the route,
     * casting sessions and Bluetooth absolute volume included. The level shown is predicted and
     * corrected a moment later, because the system applies these in its own time.
     */
    const val METHOD_KEYS = "keys"

    /** Exact writes only, as every build before 1.5.5 did, but read back so the level shown is true. */
    const val METHOD_PRECISE = "precise"

    val METHODS: List<String> = listOf(METHOD_AUTO, METHOD_KEYS, METHOD_PRECISE)

    fun sanitizeMethod(id: String?): String = if (id in METHODS) id!! else METHOD_AUTO

    // ---- reading a write back ---------------------------------------------------------------------

    enum class Outcome {
        /** The level is where it was asked to go. */
        LANDED,

        /**
         * The level moved the right way, but not all the way: a limiter clamped it short. Treated
         * as landed, at where it actually is.
         */
        PARTIAL,

        /** Nothing moved, or it moved the wrong way: the system refused the write. */
        REFUSED,

        /** The level could not be read back, so nothing can be said either way. */
        UNKNOWN,
    }

    /**
     * What a write from [before] to [target] did, judged by the level read straight after it.
     *
     * Read straight after, because `AudioService` records an absolute write's index before the
     * call returns: an index that has not moved by then was not moved at all.
     */
    fun outcome(before: Int, target: Int, after: Int?): Outcome {
        if (after == null) return Outcome.UNKNOWN
        if (after == target) return Outcome.LANDED
        val wanted = target - before
        val moved = after - before
        if (wanted == 0) return if (moved == 0) Outcome.LANDED else Outcome.REFUSED
        return if (moved != 0 && (moved > 0) == (wanted > 0) && abs(moved) < abs(wanted)) {
            Outcome.PARTIAL
        } else {
            Outcome.REFUSED
        }
    }

    // ---- a route that puts levels back --------------------------------------------------------------

    /**
     * How long after a swipe ends its result is looked at again.
     *
     * Long enough for a headset to answer the phone, and for an app that pins the level to hear
     * about the change and put it back — both measured in a few hundred milliseconds — and short
     * enough that the correction lands while the user is still looking at the bar.
     */
    const val REVERT_CHECK_MS = 900L

    /**
     * Whether a level that a swipe moved from [start] to [end] has been put back, now that it reads
     * [now].
     *
     * Put back means nearer where it started than where it was left. A press of the volume keys
     * just after a swipe moves it on from [end], away from [start], and is not mistaken for this; a
     * press that happened to undo the whole swipe in under a second is the one case that would be,
     * and the cost of that is a level put where the swipe left it, which is what was asked for.
     */
    fun isRevert(start: Int, end: Int, now: Int?): Boolean {
        if (now == null || end == start || now == end) return false
        return abs(now - start) < abs(now - end)
    }

    /**
     * Which output a volume is going to, as a key the routes that put levels back are remembered
     * by: the stream, the kind of device, and its product name, so one pair of earbuds that answers
     * with its own level does not change how the phone's speaker is driven.
     */
    fun routeKey(stream: Int, deviceType: Int, productName: CharSequence?): String {
        val name = productName?.toString()?.trim().orEmpty().take(MAX_NAME)
        return "s$stream:t$deviceType:$name"
    }

    private const val MAX_NAME = 48

    /** The most routes remembered, so the set cannot grow without end across years of headsets. */
    const val MAX_ROUTES = 24

    /** [routes] with [key] added, the oldest dropped once there are more than [MAX_ROUTES]. */
    fun remember(routes: List<String>, key: String): List<String> {
        val next = routes.filter { it != key } + key
        return if (next.size > MAX_ROUTES) next.takeLast(MAX_ROUTES) else next
    }

    /**
     * Where the level will be after [presses] relative steps from [current], within the range: the
     * honest guess for a step the system applies in its own time ([METHOD_KEYS]).
     */
    fun predict(current: Int, direction: Int, presses: Int, minIndex: Int, maxIndex: Int): Int =
        (current + direction.coerceIn(-1, 1) * presses.coerceAtLeast(0)).coerceIn(minIndex, maxIndex)

    /** [index] as 0..100 of the range, the arithmetic every readout uses. */
    fun percentOf(index: Int, minIndex: Int, maxIndex: Int): Int {
        val span = (maxIndex - minIndex).coerceAtLeast(1)
        return Math.round((index - minIndex) * 100f / span).coerceIn(0, 100)
    }
}
