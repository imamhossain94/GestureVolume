package com.newagedevs.gesturevolume.ui.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * The one and only gesture engine for the floating handler, shared by the live overlay and the
 * in-app preview so the two cannot disagree.
 *
 * The gesture vocabulary:
 *
 *  - **Vertical swipe** — always adjusts volume or brightness, never moves the bar.
 *  - **Long press** — either arms drag-to-reposition (the default, see [Host.isLongPressReposition])
 *    or fires the configured long-press action. One or the other, decided by that one setting, so
 *    repositioning can never collide with another action. Once armed, the drag is free on both
 *    axes: the bar follows the finger anywhere, and the host settles it against an edge on release.
 *  - **Tap / double tap** — the configured tap actions.
 *
 * Four invariants, each replacing a specific defect in the code this supersedes:
 *
 *  1. **Every vertical measurement is in screen space.** Window-relative `event.y` is useless here:
 *     while dragging, the window is moved out from under the finger, so `y` stops changing even
 *     though the finger is still moving.
 *  2. **The gesture is never bounded by the bar's rectangle.** Android routes an entire gesture to
 *     whichever view accepted `ACTION_DOWN`, so coordinates outside the bar are normal and expected.
 *     The old code bailed out (`return false`) as soon as the finger reached the bar's edge, which
 *     is why the volume swipe died at the top and bottom of the bar.
 *  3. **The tracked finger is latched by pointer *id*, never index.** On a curved screen the palm
 *     resting on the edge arrives as a second pointer and would otherwise hijack the gesture.
 *  4. **`ACTION_CANCEL` is terminal and is handled.** Gesture navigation and palm rejection steal
 *     the pointer stream by cancelling it. The old code ignored `ACTION_CANCEL`, leaving its
 *     long-press flag stuck on, which swallowed the *next* tap.
 */
class HandlerGestureDetector(
    context: Context,
    private val host: Host
) {

    interface Host {
        /**
         * True when the long press should arm drag-to-reposition instead of firing an action —
         * i.e. the user's long-press action is `HandlerActions.REPOSITION`. Read live, every
         * gesture, so a change in settings takes effect without recreating the bar.
         */
        fun isLongPressReposition(): Boolean

        /**
         * True when a double-tap action is actually configured. When it is not — which is the
         * default — a single tap fires immediately instead of paying the double-tap timeout.
         */
        fun isDoubleTapArmed(): Boolean

        fun onTap()
        fun onDoubleTap()

        /** The configured long-press action. Never called when [isLongPressReposition] is true. */
        fun onLongPress()

        /**
         * Fired once, when the swipe crosses the touch slop. The host latches the domain
         * (volume or brightness) and the show-UI flag here, then calls [setStepCount].
         */
        fun onAdjustBegin(initialDirection: Int)

        /** One discrete step. `+1` up, `-1` down. Return false when already pinned at an end. */
        fun onAdjustStep(direction: Int): Boolean

        fun onAdjustEnd()

        /** Drag mode armed or disarmed — for haptics and the visual cue. */
        fun onDragCue(active: Boolean)

        fun onDragBegin()

        /**
         * Cumulative offset in pixels from where the drag started, on both axes.
         *
         * Horizontal is reported so the bar can be carried across to the other edge; the host
         * decides which edge it settles on when the drag ends.
         */
        fun onDragUpdate(offsetXPx: Float, offsetYPx: Float)

        fun onDragEnd(moved: Boolean)
    }

    private enum class State { IDLE, DOWN, ADJUSTING, DRAGGING, DEAD }

    private companion object {
        /**
         * Finger travel that sweeps the whole range once. The single sensitivity knob.
         *
         * The code this replaces stepped every 10 *raw pixels*, which is 3.3dp on a 3x device —
         * below the system touch slop, so ordinary finger jitter ratcheted the volume up and down.
         */
        const val SWEEP_DP = 150f

        /** Never let a step get so small that jitter can trigger it. */
        const val MIN_STEP_DP = 4f
    }

    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private val handler = Handler(Looper.getMainLooper())

    private var state = State.IDLE
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downTime = 0L

    // Swipe-to-adjust
    private var stepPx = MIN_STEP_DP * density
    private var accumPx = 0f
    private var pinnedUp = false
    private var pinnedDown = false

    // Drag-to-reposition
    private var dragAnchorRawX = 0f
    private var dragAnchorRawY = 0f
    private var dragOffsetXPx = 0f
    private var dragOffsetPx = 0f
    private var dragMoved = false

    // Tap / double-tap
    private var lastTapTime = 0L
    private var pendingTap: Runnable? = null

    private val longPressRunnable = Runnable {
        if (state != State.DOWN) return@Runnable

        // Long press is the *only* way into drag mode. Previously an unlocked bar entered it on any
        // vertical swipe, which meant reposition and volume were competing for the same gesture and
        // the user could only ever have one of them.
        if (host.isLongPressReposition()) {
            state = State.DRAGGING
            // Anchor where the finger is right now, so the bar does not jump when it starts to
            // follow. The finger is still within the touch slop of the down point at this stage.
            dragAnchorRawX = lastRawX
            dragAnchorRawY = lastRawY
            dragOffsetXPx = 0f
            dragOffsetPx = 0f
            dragMoved = false
            // Buzz first, then drag — the cue is what tells the user the bar is now theirs to move.
            host.onDragCue(true)
            host.onDragBegin()
        } else {
            state = State.DEAD
            host.onLongPress()
        }
    }

    /**
     * How many discrete steps span [SWEEP_DP] of travel — the volume stream's step count, or the
     * brightness step count. Call from [Host.onAdjustBegin].
     */
    fun setStepCount(steps: Int) {
        val safeSteps = steps.coerceAtLeast(1)
        stepPx = (SWEEP_DP * density / safeSteps).coerceAtLeast(MIN_STEP_DP * density)
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> Unit // A second finger (or a palm) never takes over.
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(event)
            MotionEvent.ACTION_UP -> onUp()
            MotionEvent.ACTION_CANCEL -> onCancel()
            else -> return false
        }
        return true
    }

    /** Call from `hideHandlerView()`, `onDestroy()` and `View.onDetachedFromWindow()`. */
    fun cancel() {
        handler.removeCallbacks(longPressRunnable)
        cancelPendingTap()
        finishInFlight(commitDrag = false)
        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    // ---- pointer handling -------------------------------------------------------------------

    private fun onDown(event: MotionEvent) {
        handler.removeCallbacks(longPressRunnable)
        state = State.DOWN
        pointerId = event.getPointerId(0)
        downRawX = event.rawX
        downRawY = event.rawY
        lastRawX = event.rawX
        lastRawY = event.rawY
        downTime = now()
        accumPx = 0f
        pinnedUp = false
        pinnedDown = false
        dragMoved = false
        handler.postDelayed(longPressRunnable, longPressTimeout)
    }

    private fun onMove(event: MotionEvent) {
        val index = event.findPointerIndex(pointerId)
        // findPointerIndex returns -1 once the id is gone; getY(-1) throws.
        if (index < 0) return

        val offsetX = event.rawX - event.getX(0)
        val offsetY = event.rawY - event.getY(0)
        val rawX = event.getX(index) + offsetX
        val rawY = event.getY(index) + offsetY

        when (state) {
            State.DOWN -> {
                val dx = abs(rawX - downRawX)
                val dy = abs(rawY - downRawY)
                if (dx > touchSlop || dy > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                    // The axis is decided once, here. Re-deciding on every event (as the old code
                    // did) made diagonal swipes flicker between adjusting and doing nothing.
                    if (dy < dx) {
                        state = State.DEAD
                    } else {
                        // A plain vertical swipe is always the volume/brightness gesture. Moving
                        // the bar takes a long press first, which is handled in longPressRunnable.
                        state = State.ADJUSTING
                        lastRawY = rawY
                        accumPx = 0f
                        host.onAdjustBegin(if (rawY < downRawY) 1 else -1)
                    }
                }
            }

            State.ADJUSTING -> accumulate(event, index, offsetY, rawY)

            State.DRAGGING -> {
                dragOffsetXPx = rawX - dragAnchorRawX
                dragOffsetPx = rawY - dragAnchorRawY
                // Sub-pixel jitter from a finger resting still after the long press is not a move,
                // and must not be persisted as a new position.
                if (abs(dragOffsetPx) >= 1f || abs(dragOffsetXPx) >= 1f) dragMoved = true
                lastRawX = rawX
                lastRawY = rawY
                host.onDragUpdate(dragOffsetXPx, dragOffsetPx)
            }

            else -> {
                lastRawX = rawX
                lastRawY = rawY
            }
        }

        if (state == State.DOWN) {
            lastRawX = rawX
            lastRawY = rawY
        }
    }

    /**
     * Walks the batched historical samples before the current one. MotionEvents are coalesced to
     * the display frame, so a fast flick arrives as one event carrying several samples; ignoring
     * the history quantises the whole flick to a single step.
     */
    private fun accumulate(event: MotionEvent, index: Int, offsetY: Float, currentRawY: Float) {
        for (h in 0 until event.historySize) {
            step(event.getHistoricalY(index, h) + offsetY)
        }
        step(currentRawY)
    }

    private fun step(rawY: Float) {
        accumPx += rawY - lastRawY
        lastRawY = rawY

        // Screen coordinates grow downward, so a negative delta is an upward swipe.
        while (accumPx <= -stepPx) {
            accumPx += stepPx
            if (!emit(1)) break
        }
        while (accumPx >= stepPx) {
            accumPx -= stepPx
            if (!emit(-1)) break
        }

        // Once pinned at an end, stop banking travel — otherwise the user has to un-swipe all the
        // over-travel they accumulated before the control responds again.
        if (pinnedUp) accumPx = accumPx.coerceAtLeast(-stepPx)
        if (pinnedDown) accumPx = accumPx.coerceAtMost(stepPx)
    }

    private fun emit(direction: Int): Boolean {
        val accepted = host.onAdjustStep(direction)
        if (direction > 0) {
            pinnedUp = !accepted
            if (accepted) pinnedDown = false
        } else {
            pinnedDown = !accepted
            if (accepted) pinnedUp = false
        }
        return accepted
    }

    private fun onPointerUp(event: MotionEvent) {
        val upIndex = event.actionIndex
        if (event.getPointerId(upIndex) != pointerId) return

        // The tracked finger lifted but others remain — re-anchor onto one of them so the gesture
        // continues smoothly rather than jumping.
        var newIndex = -1
        for (i in 0 until event.pointerCount) {
            if (i != upIndex) {
                newIndex = i
                break
            }
        }
        if (newIndex < 0) {
            onUp()
            return
        }

        pointerId = event.getPointerId(newIndex)
        val offsetY = event.rawY - event.getY(0)
        val newRawY = event.getY(newIndex) + offsetY
        lastRawY = newRawY
        if (state == State.DRAGGING) {
            dragAnchorRawY = newRawY - dragOffsetPx
        }
    }

    private fun onUp() {
        handler.removeCallbacks(longPressRunnable)

        when (state) {
            State.DOWN -> handleTap()
            State.ADJUSTING -> host.onAdjustEnd()
            State.DRAGGING -> {
                host.onDragCue(false)
                host.onDragEnd(dragMoved)
            }
            else -> Unit
        }

        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun onCancel() {
        handler.removeCallbacks(longPressRunnable)
        // Commit an in-flight drag rather than snapping back: the bar is already visibly where the
        // user put it, and the cancel usually came from the system stealing the gesture, not from
        // the user changing their mind.
        finishInFlight(commitDrag = true)
        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun finishInFlight(commitDrag: Boolean) {
        when (state) {
            State.ADJUSTING -> host.onAdjustEnd()
            State.DRAGGING -> {
                host.onDragCue(false)
                host.onDragEnd(commitDrag && dragMoved)
            }
            else -> Unit
        }
    }

    private fun handleTap() {
        val elapsed = now() - downTime
        if (elapsed >= longPressTimeout) return
        if (abs(lastRawY - downRawY) > touchSlop) return

        if (!host.isDoubleTapArmed()) {
            // Nothing is bound to double tap, so there is no reason to make the user wait.
            host.onTap()
            return
        }

        val time = now()
        if (time - lastTapTime < doubleTapTimeout) {
            cancelPendingTap()
            lastTapTime = 0L
            host.onDoubleTap()
        } else {
            lastTapTime = time
            val runnable = Runnable {
                pendingTap = null
                host.onTap()
            }
            pendingTap = runnable
            handler.postDelayed(runnable, doubleTapTimeout)
        }
    }

    private fun cancelPendingTap() {
        pendingTap?.let { handler.removeCallbacks(it) }
        pendingTap = null
    }

    private fun now(): Long = SystemClock.elapsedRealtime()
}
