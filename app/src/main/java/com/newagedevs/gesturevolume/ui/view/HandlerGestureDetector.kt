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
 *  - **Tap / double tap / triple tap** — the configured tap actions.
 *  - **Horizontal swipe** — inward (toward the middle of the screen) and outward are two separate
 *    action slots; each is inert until something is bound to it. See the note on the deferred
 *    qualification below.
 *  - **Long horizontal swipe** — the same stroke carried much further. The bar expands into a
 *    slider and the finger that opened it keeps setting the value, vertically, until it lifts.
 *    See the note on the two thresholds below.
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
 *
 * **Why the inward swipe qualifies twice, and why that is not a second axis decision.**
 *
 * The axis is still latched once, on one event, at one line, by the same `dy < dx`. What happens
 * afterwards inside `EDGE_TRACKING` is not "which axis is this?" but "has this travelled far enough,
 * and straight enough, to have meant anything?" — a magnitude question inside an axis that is
 * already decided, and a monotonic one: it arms once and there is no path back out.
 *
 * The defect invariant 1's comment records was diagonal swipes *flickering* between adjusting and
 * doing nothing. Flicker needs a cycle. A one-way latch has none, so that defect is structurally
 * unreachable here rather than merely unlikely.
 *
 * The second test exists because the first one is decided on the worst evidence in the gesture. The
 * gate at the slop crossing is an OR, so the losing axis may be anywhere below slop when the axis is
 * chosen: `dx = 9`, `dy = 7` is 38 degrees off horizontal — an ordinary volume swipe — and it
 * latches horizontal. On a bar mounted at the side that is not a rare case, because a thumb pivoting
 * at the base of the hand rolls inward before it travels up. Before this gesture existed such a
 * swipe cost the user nothing: it went `DEAD`, silently, and they swiped again. Binding an action to
 * the horizontal branch is what would turn that forgiving miss into a wrong action, so the action is
 * withheld until 24dp of travel (three times the slop, three times the lever arm on the angle) at a
 * ratio of 2:1 — 26.565 degrees off horizontal, leaving an 18.4-degree band on each side where
 * nothing happens at all.
 *
 * Folding that test back into the axis branch would put it back on 8dp of evidence. It would look
 * like a simplification and it would be a regression.
 *
 * **Why a long swipe delays the short one, and only when it has to.**
 *
 * The short swipe and the long swipe are the same stroke measured at two distances, so at the
 * moment the short one qualifies there is no way to know whether the finger is finished. Firing
 * immediately and then opening a slider as well would run both actions off one gesture; waiting to
 * see would put a delay on a gesture that never had one.
 *
 * So the wait is charged only where it is owed. With nothing bound to the long swipe in this
 * direction — which is every direction the user has not deliberately set up — the short swipe fires
 * the instant it qualifies, exactly as before, and [State.DEAD] follows. With a slider bound, the
 * short action is held until the finger lifts, because until then the stroke may still be going
 * somewhere. The cost is bounded by how long the user keeps their finger down, which for a flick is
 * nothing, and it is the only arrangement in which both gestures can share one direction without
 * either firing when the user meant the other.
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

        /**
         * True when a triple-tap action is configured. Armed, a double tap has to wait out one
         * more timeout to be sure it is not the first two thirds of a triple; unarmed, it costs
         * nothing extra, which is why this is read rather than assumed.
         */
        fun isTripleTapArmed(): Boolean

        fun onTap()
        fun onDoubleTap()
        fun onTripleTap()

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

        /**
         * The long press landed and the bar is being held still: offer the context menu.
         *
         * Fired alongside [onDragBegin], not instead of it. Holding arms both outcomes at once and
         * the finger decides between them — stay put and the menu is the gesture, move and
         * [onContextMenuDismiss] retracts it and the drag takes over. Waiting to see which one the
         * user meant before showing anything would put a second delay on top of the long-press
         * timeout, and the menu would arrive after the user had already given up on it.
         */
        fun onContextMenuOpen()

        /** The held finger travelled far enough to mean a drag. Retract the menu. */
        fun onContextMenuDismiss()

        fun onDragBegin()

        /**
         * Cumulative offset in pixels from where the drag started, on both axes.
         *
         * Horizontal is reported so the bar can be carried across to the other edge; the host
         * decides which edge it settles on when the drag ends.
         */
        fun onDragUpdate(offsetXPx: Float, offsetYPx: Float)

        fun onDragEnd(moved: Boolean)

        /**
         * Which horizontal direction counts as "inward" right now: `+1` rightward, `-1` leftward.
         *
         * Read live, at the moment a horizontal swipe is classified, exactly as
         * [isLongPressReposition] and [isDoubleTapArmed] are — so dragging the bar to the other
         * edge takes effect without recreating the bar.
         */
        fun edgeSwipeInwardSign(): Int

        /**
         * Whether anything is bound to a horizontal swipe in this direction.
         *
         * Returning false is what makes the gesture inert: the detector never enters its tracking
         * state, and the horizontal branch behaves exactly as it did before this gesture existed —
         * a silent miss the user simply retries.
         */
        fun isHorizontalSwipeArmed(inward: Boolean): Boolean

        /**
         * A horizontal swipe qualified.
         *
         * Fired once, the instant the swipe passes its distance and ratio test, never on lift — so
         * the user sees the result while the finger is still moving, which is what makes it feel
         * like a pull rather than a delayed tap.
         *
         * The host owns getting anything that touches windows off the input stack.
         */
        fun onHorizontalSwipe(inward: Boolean)

        /**
         * Whether the expanding slider is bound to a long horizontal swipe in this direction.
         *
         * Read at the axis decision, alongside [isHorizontalSwipeArmed], and latched for the rest
         * of the gesture. Returning false is what keeps the short swipe's timing untouched: with
         * no slider bound, nothing about this gesture is deferred.
         */
        fun isQuickSliderArmed(inward: Boolean): Boolean

        /**
         * How much vertical finger travel, in dp, sweeps the slider's whole range.
         *
         * This is the slider's drawn length, so the track on screen *is* the scale: the fill keeps
         * pace with the finger one pixel for one pixel, and running the finger from one end of the
         * track to the other covers exactly the full range. Read once, when the slider opens.
         */
        fun quickSliderSweepDp(): Float

        /**
         * The long swipe qualified: expand the bar into the slider.
         *
         * The slider opens showing the value the control already has. It does not jump to where
         * the finger happens to be — the finger is at the edge of the screen because that is where
         * the stroke started, which says nothing about what the user wants the brightness to be.
         */
        fun onQuickSliderBegin(inward: Boolean)

        /**
         * Cumulative movement since the slider opened, as a fraction of the full range.
         *
         * Positive is up, which is more. Not clamped and not quantised here: the host owns the
         * value, so it owns both the ends of the range and the step boundaries the haptics fire on.
         * Reported cumulatively rather than as a delta so that a dropped or coalesced event costs
         * precision on one frame instead of being lost from the total.
         */
        fun onQuickSliderUpdate(fractionFromOpen: Float)

        /** The finger lifted or the gesture was taken away. Collapse the slider. */
        fun onQuickSliderEnd()
    }

    private enum class State { IDLE, DOWN, ADJUSTING, DRAGGING, EDGE_TRACKING, SLIDING, DEAD }

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

        /**
         * How far an inward swipe must travel before it is allowed to fire.
         *
         * Three times the usual 8dp touch slop, and that multiple is the whole point. The axis is
         * latched at the slop crossing, on the shortest and noisiest travel in the gesture: at 8dp
         * the losing axis may sit anywhere below slop, so dx=9/dy=7 — a stroke 38 degrees off
         * horizontal, which is an ordinary volume swipe — already latches horizontal. Re-testing at
         * 24dp gives the angle three times the lever arm, so the same sampling jitter is about a
         * degree of error instead of nearly four.
         *
         * It is also 16% of [SWEEP_DP], so it reads as a flick rather than a stroke, and just under
         * the 30dp default bar width, so on a default bar the whole qualifying travel is still over
         * the bar.
         */
        const val EDGE_TRIGGER_DP = 24f

        /**
         * How much more horizontal than vertical an inward swipe must be.
         *
         * 2:1 is atan(0.5) = 26.565 degrees off horizontal, so the gesture claims a 53-degree cone
         * on each side and leaves an 18.4-degree band between that cone and the vertical boundary
         * where nothing at all happens. That band is deliberate: it is the margin a curving thumb
         * arc lands in, and every degree of it was already silent before this gesture existed.
         */
        const val EDGE_RATIO = 2f

        /**
         * How far a horizontal swipe must travel to stop being a flick and become a slider pull.
         *
         * Just under three times [EDGE_TRIGGER_DP]. The gap between the two has to be wide enough
         * that a user aiming for the short swipe cannot overshoot into the long one by accident:
         * 24dp is a flick of the thumb tip, 64dp is most of a thumb's reach across the screen and
         * has to be meant. It is also comfortably inside the narrowest phone in portrait, so the
         * slider can always be reached without the finger running out of screen.
         */
        const val SLIDER_TRIGGER_DP = 64f
    }

    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * [EDGE_TRIGGER_DP] in pixels, floored at three times the device's own slop so the lever-arm
     * argument survives an OEM that ships an unusually large [ViewConfiguration.getScaledTouchSlop].
     */
    private val edgeTriggerPx = maxOf(EDGE_TRIGGER_DP * density, touchSlop * 3f)

    /** [SLIDER_TRIGGER_DP] in pixels, always clear of [edgeTriggerPx] so the two cannot collide. */
    private val sliderTriggerPx = maxOf(SLIDER_TRIGGER_DP * density, edgeTriggerPx * 2f)
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

    /**
     * Whether the menu offered at the start of this drag is still on screen.
     *
     * Separate from [dragMoved], which trips at a single pixel so that sub-pixel jitter is not
     * persisted as a new position. Retracting the menu needs the much larger touch slop: a menu
     * that vanished on a pixel of tremor would be unusable one-handed.
     */
    private var menuOpen = false

    /**
     * Which horizontal direction counts as "inward" for this gesture: `+1` rightward, `-1`
     * leftward, `0` while no horizontal swipe is being tracked.
     *
     * Latched once, at the axis decision, and never re-read while the finger is down — the bar can
     * be carried across the screen by a drag, and a sign that changed mid-gesture would reverse the
     * meaning of a swipe already in flight.
     */
    private var edgeInwardSign = 0

    /** Whether the tracked horizontal swipe set off inward. Latched with [edgeInwardSign]. */
    private var edgeInward = false

    /** Whether a short-swipe action is bound to this direction. Latched with [edgeInwardSign]. */
    private var edgeShortArmed = false

    /** Whether the slider is bound to this direction. Latched with [edgeInwardSign]. */
    private var edgeSliderArmed = false

    /**
     * Whether the short swipe has passed its distance and ratio test but has not fired yet.
     *
     * Only ever set when [edgeSliderArmed] is true — that is the one case where the action has to
     * wait for the finger to lift before it can be sure the stroke was not on its way to the
     * slider. Without a slider bound the action fires at the threshold and this stays false.
     */
    private var edgeQualified = false

    // The expanding slider
    private var sliderAnchorRawY = 0f
    private var sliderSweepPx = 1f

    // Tap / double-tap / triple-tap
    private var lastTapTime = 0L
    private var tapCount = 0
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
            menuOpen = true
            host.onContextMenuOpen()
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
        // Before finishInFlight, which clears the drag: an open menu outlives the gesture by
        // design — it is still on screen after the finger lifts — so nothing else would take it
        // down, and it would sit there pointing at a bar that no longer exists.
        if (menuOpen) {
            menuOpen = false
            host.onContextMenuDismiss()
        }
        edgeQualified = false
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
        menuOpen = false
        edgeInwardSign = 0
        edgeInward = false
        edgeShortArmed = false
        edgeSliderArmed = false
        edgeQualified = false
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
                        // Horizontal. Which way it set off, and whether anything is bound to that
                        // direction, is decided here, once, from the host's live settings — but
                        // whether it has travelled far enough to MEAN anything is deferred to
                        // EDGE_TRACKING. The predicate above is untouched, so the vertical branch
                        // below is unchanged and not one degree is taken from the volume swipe.
                        edgeInwardSign = host.edgeSwipeInwardSign()
                        edgeInward = (rawX - downRawX) * edgeInwardSign > 0f
                        edgeShortArmed = edgeInwardSign != 0 && host.isHorizontalSwipeArmed(edgeInward)
                        edgeSliderArmed = edgeInwardSign != 0 && host.isQuickSliderArmed(edgeInward)
                        state = if (edgeShortArmed || edgeSliderArmed) {
                            State.EDGE_TRACKING
                        } else {
                            State.DEAD
                        }
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

            State.EDGE_TRACKING -> {
                // The deferred qualification, and the reason this state exists. Measured
                // cumulatively from the DOWN point, like the axis gate, on the same reconstructed
                // screen coordinates. Monotonic: it arms once and never un-arms.
                val edx = abs(rawX - downRawX)
                val edy = abs(rawY - downRawY)
                // A finger that set off one way and came back past the down point has reversed,
                // and the direction it was armed for no longer describes it.
                val nowInward = (rawX - downRawX) * edgeInwardSign > 0f
                val straight = edx >= EDGE_RATIO * edy

                // The stroke turned out to be vertical after all: hand it to the adjust gesture.
                //
                // This is the deferred qualification's other half, and without it the deferral was
                // only ever half honest. The axis is latched at the slop crossing on 8dp of
                // evidence, where dx=9/dy=7 already picks horizontal — and a thumb pivoting at the
                // base of the hand *rolls inward before it travels up*, so on a bar mounted at the
                // edge that is the normal shape of a volume swipe, not an unusual one. It got much
                // worse when the bar became 10dp wide: the thumb now pivots directly on the screen
                // edge, so almost every swipe starts with a sideways roll.
                //
                // Before this, such a stroke sat in EDGE_TRACKING until the finger lifted and did
                // nothing at all. The user's report was the plain one: "swipe up and down not
                // working."
                //
                // The test is the mirror image of the horizontal one — same 24dp of travel, same
                // 2:1 ratio — so it takes nothing from the horizontal gesture: every stroke it
                // claims is one the horizontal branch had already declined to act on, and the
                // 18.4-degree dead band between the two cones is untouched. And it is one-way,
                // like every other transition here: EDGE_TRACKING to ADJUSTING and never back, so
                // there is no cycle for a diagonal swipe to flicker around.
                if (edy >= edgeTriggerPx && edy >= EDGE_RATIO * edx) {
                    state = State.ADJUSTING
                    // A short swipe that qualified earlier in this same stroke is abandoned rather
                    // than left pending. Nothing would fire it now — that only happens from
                    // EDGE_TRACKING — but leaving it set makes that a fact about the other states
                    // rather than about this line.
                    edgeQualified = false
                    // Measured from here, not from the down point: the travel that went into
                    // proving the stroke was vertical is evidence, not input, and banking it would
                    // jump the volume several steps the instant the gesture was recognised.
                    lastRawY = rawY
                    accumPx = 0f
                    pinnedUp = false
                    pinnedDown = false
                    host.onAdjustBegin(if (rawY < downRawY) 1 else -1)
                    return
                }

                if (edgeSliderArmed && straight && nowInward == edgeInward && edx >= sliderTriggerPx) {
                    // The long threshold wins outright: the stroke went far enough that the short
                    // swipe was never what the user meant, so its deferred action is dropped
                    // rather than also firing on lift.
                    edgeQualified = false
                    // Set before the host call so a re-entrant host cannot open the slider twice.
                    state = State.SLIDING
                    sliderAnchorRawY = rawY
                    sliderSweepPx = (host.quickSliderSweepDp() * density).coerceAtLeast(1f)
                    host.onQuickSliderBegin(edgeInward)
                    host.onQuickSliderUpdate(0f)
                } else if (edgeShortArmed && straight && edx >= edgeTriggerPx) {
                    if (edgeSliderArmed) {
                        // Held until lift. See "Why a long swipe delays the short one" above.
                        edgeQualified = true
                    } else {
                        state = State.DEAD
                        if (nowInward == edgeInward) host.onHorizontalSwipe(edgeInward)
                    }
                }
                lastRawX = rawX
                lastRawY = rawY
            }

            State.SLIDING -> {
                // Vertical, because the bar expands into a vertical track along the edge it is
                // mounted on and the fill has to follow the finger. Screen coordinates grow
                // downward, hence the negation: up is more.
                host.onQuickSliderUpdate(-(rawY - sliderAnchorRawY) / sliderSweepPx)
                lastRawX = rawX
                lastRawY = rawY
            }

            State.ADJUSTING -> accumulate(event, index, offsetY, rawY)

            State.DRAGGING -> {
                dragOffsetXPx = rawX - dragAnchorRawX
                dragOffsetPx = rawY - dragAnchorRawY
                // Sub-pixel jitter from a finger resting still after the long press is not a move,
                // and must not be persisted as a new position.
                if (abs(dragOffsetPx) >= 1f || abs(dragOffsetXPx) >= 1f) dragMoved = true
                if (menuOpen && (abs(dragOffsetPx) > touchSlop || abs(dragOffsetXPx) > touchSlop)) {
                    menuOpen = false
                    host.onContextMenuDismiss()
                }
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
        val offsetX = event.rawX - event.getX(0)
        val offsetY = event.rawY - event.getY(0)
        val newRawX = event.getX(newIndex) + offsetX
        val newRawY = event.getY(newIndex) + offsetY
        lastRawX = newRawX
        lastRawY = newRawY
        if (state == State.DRAGGING) {
            // Both axes, or the bar jumps horizontally the moment a resting palm becomes the
            // tracked pointer — the same defect this re-anchoring exists to prevent vertically.
            dragAnchorRawX = newRawX - dragOffsetXPx
            dragAnchorRawY = newRawY - dragOffsetPx
        }
        if (state == State.EDGE_TRACKING) {
            // The opposite remedy to the drag's, for the same hazard, and for a stated reason.
            // downRawX is deliberately never re-anchored here, so a resting palm becoming the
            // tracked pointer makes the measured travel jump instantly to the distance between the
            // palm and the original touch — on a curved edge, easily past both thresholds, firing
            // the action from a palm. A drag re-anchors because the bar is already visibly
            // following the finger and must not leap; an edge swipe has emitted nothing yet, so
            // abandoning it costs the user only a re-swipe.
            state = State.DEAD
            edgeQualified = false
        }
        if (state == State.SLIDING) {
            // Same hazard, same answer, and here the stakes are higher: re-anchoring onto a palm
            // would hand it a live brightness control. The value set so far stands — it was
            // applied as the finger moved and the user watched it happen — but nothing further is
            // read from a pointer the user did not choose.
            state = State.DEAD
            host.onQuickSliderEnd()
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
            State.EDGE_TRACKING -> fireDeferredHorizontalSwipe()
            State.SLIDING -> host.onQuickSliderEnd()
            else -> Unit
        }

        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun onCancel() {
        handler.removeCallbacks(longPressRunnable)
        // A short swipe held back for the slider still fires here. Before the slider existed this
        // action had already gone out by the time a cancel could arrive — it fired at the
        // threshold — so dropping it now would turn "the system stole the gesture" into "the
        // gesture did nothing", which is a regression the user would read as the swipe being
        // unreliable. Deliberately not done from [cancel], where the bar is going away.
        if (state == State.EDGE_TRACKING) fireDeferredHorizontalSwipe()
        // Commit an in-flight drag rather than snapping back: the bar is already visibly where the
        // user put it, and the cancel usually came from the system stealing the gesture, not from
        // the user changing their mind.
        finishInFlight(commitDrag = true)
        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    /**
     * Fires a short swipe that qualified while the slider was armed and has been waiting for the
     * finger to lift.
     *
     * The direction is re-tested against where the finger actually ended up, not where it was when
     * it qualified: a stroke that went out past the threshold and came back has reversed, and the
     * direction it armed no longer describes it. That test is the same one the immediate path runs
     * at the threshold — deferring the action defers the test with it.
     */
    private fun fireDeferredHorizontalSwipe() {
        if (!edgeQualified) return
        edgeQualified = false
        val endedInward = (lastRawX - downRawX) * edgeInwardSign > 0f
        if (endedInward == edgeInward) host.onHorizontalSwipe(edgeInward)
    }

    private fun finishInFlight(commitDrag: Boolean) {
        when (state) {
            State.ADJUSTING -> host.onAdjustEnd()
            State.DRAGGING -> {
                host.onDragCue(false)
                host.onDragEnd(commitDrag && dragMoved)
            }
            // Always collapsed, never "committed or not": the value the slider set was applied
            // live as the finger moved, so there is nothing left to commit and a slider left on
            // screen after the bar went away would be unreachable.
            State.SLIDING -> host.onQuickSliderEnd()
            else -> Unit
        }
    }

    /**
     * Counts taps within the double-tap timeout and fires the biggest multiple that is bound.
     *
     * The rule that decides latency: a tap waits only while a *longer* bound sequence is still
     * possible. Nothing bound above single fires it at once; double bound alone fires a double on
     * the second tap at once; triple bound makes the second tap wait, because it may be two thirds
     * of a triple. Unbound multiples in between still swallow their taps rather than firing the
     * single — a user who taps twice with only a triple bound meant something, and it was not two
     * volume panels.
     */
    private fun handleTap() {
        val elapsed = now() - downTime
        if (elapsed >= longPressTimeout) return
        if (abs(lastRawY - downRawY) > touchSlop) return

        val doubleArmed = host.isDoubleTapArmed()
        val tripleArmed = host.isTripleTapArmed()
        if (!doubleArmed && !tripleArmed) {
            // Nothing is bound above a single tap, so there is no reason to make the user wait.
            host.onTap()
            return
        }

        val time = now()
        tapCount = if (time - lastTapTime < doubleTapTimeout) tapCount + 1 else 1
        lastTapTime = time
        cancelPendingTap()

        when {
            tapCount >= 3 -> {
                resetTaps()
                if (tripleArmed) host.onTripleTap()
            }
            tapCount == 2 && !tripleArmed -> {
                resetTaps()
                host.onDoubleTap()
            }
            else -> {
                val count = tapCount
                val runnable = Runnable {
                    pendingTap = null
                    resetTaps()
                    when (count) {
                        1 -> host.onTap()
                        2 -> if (doubleArmed) host.onDoubleTap()
                    }
                }
                pendingTap = runnable
                handler.postDelayed(runnable, doubleTapTimeout)
            }
        }
    }

    private fun resetTaps() {
        tapCount = 0
        lastTapTime = 0L
    }

    private fun cancelPendingTap() {
        pendingTap?.let { handler.removeCallbacks(it) }
        pendingTap = null
    }

    private fun now(): Long = SystemClock.elapsedRealtime()
}
