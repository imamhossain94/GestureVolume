package com.newagedevs.gesturevolume.ui.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
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
 * **Why the Quick panel is not a gesture here, and why a horizontal latch is always recoverable.**
 *
 * The Quick panel used to be the payload of the *same* inward stroke that opens the Deck, told
 * apart from it by distance alone: past 24dp you got the Deck, past a longer threshold you got the
 * panel. That arrangement is unambiguous in code and unusable in the hand. A thumb flicking inward
 * to open the Deck overshoots the second threshold without trying, and nothing on screen says
 * where either boundary is, so the user learns the difference only by getting the wrong one. No
 * arbitration fixes that, because the arbitration was never what was wrong.
 *
 * So the panel left this file entirely. It is `HandlerActions.OPEN_QUICK_SLIDER` now — an action
 * in its own slot, chosen before the finger goes down — and the inward swipe means one thing
 * again. The two gestures cannot conflict because there is only one of them.
 *
 * What is left is the other half of the same complaint: "swipe up and down conflicting". The axis
 * is latched at the slop crossing on 8dp of evidence, and a thumb pivoting at the base of the hand
 * *rolls inward before it travels up* — so on a bar mounted at the screen's edge, the normal shape
 * of a volume swipe starts out looking horizontal. It got worse when the bar became 10dp wide,
 * because the thumb then pivots directly on the edge.
 *
 * Such a stroke used to go straight to [State.DEAD] whenever nothing was bound to that horizontal
 * direction, which is the common case — and DEAD is terminal, so the volume swipe the user was
 * actually making did nothing at all. A horizontal latch now *always* enters [State.EDGE_TRACKING],
 * armed or not, because that is the one state with a way back: the mirror-image test in it
 * promotes the stroke to [State.ADJUSTING] as soon as it has proved itself vertical. The gesture
 * the user meant survives an axis decision made on the worst evidence in it.
 *
 * **Why a long swipe that stops short still ends with the whole panel.**
 *
 * The far threshold is a third of the screen away, and until it the panel is only a stretch that
 * follows the finger. A long swipe that stopped before it — most do, because nothing on screen
 * says where it is — lifted with the panel half grown, watched it shrink back into the bar, and
 * got the Deck instead. The report was "long swipe should show the Quick slider fully". So a lift
 * with the stretch past halfway now finishes the panel ([EdgePullRelease]). A flick is still the
 * Deck: it is the one stroke that carries past halfway without meaning to, and it is told apart
 * by how fast it is still moving when it lifts, not by how far it got.
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
         * Whether a long swipe in this direction opens the Quick panel.
         *
         * Read at the axis decision, alongside [isHorizontalSwipeArmed], and latched for the rest
         * of the gesture. Returning false is what keeps the short swipe's timing untouched: with
         * no panel bound, nothing about this gesture is deferred.
         */
        fun isQuickSliderArmed(inward: Boolean): Boolean

        /**
         * The swipe reached the short threshold with the panel bound to this direction: start
         * showing the bar stretching, but commit to nothing.
         *
         * Both outcomes are still live at this point — lift and the short action takes it, carry
         * on and the panel takes it — and the difference between them is a distance the user
         * cannot see. So the bar begins following the finger immediately, and how far it has
         * stretched is the readout of which one the stroke is currently on course for.
         */
        fun onEdgePullBegin(inward: Boolean)

        /**
         * How far between the two thresholds the finger has travelled, 0..1. Free to go back down:
         * the stretch follows the finger both ways.
         */
        fun onEdgePullUpdate(progress: Float)

        /** The pull ended without reaching the panel. Collapse the stretch. */
        fun onEdgePullCancel()

        /**
         * The long swipe qualified: the stretch becomes the Quick panel.
         *
         * Always preceded by [onEdgePullBegin]. At the far threshold the finger has already grown
         * the shape all the way; on a lift past halfway (see [EdgePullRelease]) it has not, and the
         * host grows it the rest of the way. The panel stays up after this gesture ends and takes
         * its own touches from here, so the detector has nothing further to report — this is the
         * last thing it says about this stroke.
         */
        fun onQuickSliderBegin(inward: Boolean)
    }

    private enum class State { IDLE, DOWN, ADJUSTING, DRAGGING, EDGE_TRACKING, DEAD }

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

        /** See [doubleTapTimeout]. Half again on top of the platform's usual 300ms. */
        const val DOUBLE_TAP_FLOOR_MS = 450L

        /**
         * How far across the screen a horizontal swipe must travel to open the Quick panel.
         *
         * A fraction of the screen's width rather than a fixed distance. The pair this replaced
         * were 24dp and 64dp apart, and 64dp is not a long swipe by any measure a user would
         * recognise: on a 360dp phone it is 18% of the width, which an ordinary flick inward to
         * open the Deck clears without trying — so the Deck swipe kept turning into the panel.
         * A third of the screen is a deliberate drag with the thumb, plainly different in kind
         * from a flick, and it stays that way on a tablet and on a small phone alike.
         */
        const val SLIDER_TRIGGER_FRACTION = 0.32f

        /** The floor under [SLIDER_TRIGGER_FRACTION], for a window too narrow for it to mean much. */
        const val SLIDER_TRIGGER_MIN_DP = 88f

        /** And the ceiling, so the gesture is always completable from where the bar sits. */
        const val SLIDER_TRIGGER_MAX_FRACTION = 0.5f

    }

    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * [EDGE_TRIGGER_DP] in pixels, floored at three times the device's own slop so the lever-arm
     * argument survives an OEM that ships an unusually large [ViewConfiguration.getScaledTouchSlop].
     */
    private val edgeTriggerPx = maxOf(EDGE_TRIGGER_DP * density, touchSlop * 3f)

    /**
     * How far the finger must travel to commit to the panel, in pixels.
     *
     * Read from the display once, when the detector is built. The bar is rebuilt on a
     * configuration change, so a rotation gets a detector measured against the width it rotated
     * into. Always clear of [edgeTriggerPx] by a factor of two, whatever the arithmetic produces,
     * so the two thresholds cannot collapse into each other on any display.
     */
    private val sliderTriggerPx = run {
        val widthPx = context.resources.displayMetrics.widthPixels.toFloat()
        (widthPx * SLIDER_TRIGGER_FRACTION)
            .coerceAtLeast(SLIDER_TRIGGER_MIN_DP * density)
            .coerceAtMost(widthPx * SLIDER_TRIGGER_MAX_FRACTION)
            .coerceAtLeast(edgeTriggerPx * 2f)
    }

    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    /**
     * How long the second tap of a double tap may take to arrive.
     *
     * The platform's own figure is 300ms, chosen for targets the size of a button. This one is a
     * bar 10 to 40dp wide at the very edge of the screen, usually tapped with the side of a thumb
     * while the hand is holding the phone — the finger has to leave and come back to a target it
     * cannot comfortably see, and 300ms is not enough to do that reliably. The failure is silent
     * and total: two taps 350ms apart are two single taps, and a single tap is bound to nothing
     * by default, so the user gets no action and no clue why.
     *
     * Only ever *longer* than the platform value, never shorter, so a device that has already
     * decided its users need more time keeps it.
     */
    private val doubleTapTimeout =
        maxOf(ViewConfiguration.getDoubleTapTimeout().toLong(), DOUBLE_TAP_FLOOR_MS)

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

    /** Whether the Quick panel is bound to this direction. Latched with [edgeInwardSign]. */
    private var edgeSliderArmed = false

    /**
     * Whether the short swipe has passed its distance and ratio test but has not fired yet.
     *
     * Only ever set when [edgeSliderArmed] is true — the one case where the action has to wait for
     * the finger to lift before it can be sure the stroke was not on its way to the panel. Without
     * a panel bound the action fires at the threshold and this stays false.
     */
    private var edgeQualified = false

    /**
     * Whether the bar is currently stretched out under the finger, short of committing.
     *
     * Its one job is to make [Host.onEdgePullCancel] idempotent, because the paths that end a pull
     * overlap: a lift runs through both `onUp` and the direction re-test, and a cancel arrives on
     * top of whatever the pull was already doing.
     */
    private var pullActive = false

    /** How far out the stretch is, 0..1: the figure last sent to [Host.onEdgePullUpdate]. */
    private var pullProgress = 0f

    /** The finger's velocity, for telling a flick from a drag when it lifts. See [EdgePullRelease]. */
    private var velocityTracker: VelocityTracker? = null

    private val flickPxPerS = EdgePullRelease.FLICK_DP_PER_S * density

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
        trackVelocity(event)
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

    /**
     * Feeds the velocity tracker in screen coordinates, like every other measurement here: a
     * window-relative velocity reads zero for a finger that is carrying its window along.
     */
    private fun trackVelocity(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
            tracker.clear()
        }
        val tracker = velocityTracker ?: return
        val screen = MotionEvent.obtain(event)
        screen.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        tracker.addMovement(screen)
        screen.recycle()
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
        // For the same reason as the menu above: a stretch is a window of its own, so nothing else
        // would take it down once the bar it grew out of has gone.
        endPull()
        finishInFlight(commitDrag = false)
        velocityTracker?.recycle()
        velocityTracker = null
        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    // ---- pointer handling -------------------------------------------------------------------

    private fun onDown(event: MotionEvent) {
        handler.removeCallbacks(longPressRunnable)
        // Collapses a stretch left over from a previous gesture rather than orphaning its window.
        endPull()
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
        pullProgress = 0f
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
                        // EDGE_TRACKING unconditionally, even with nothing bound to this
                        // direction. It used to go DEAD in that case, which is terminal — and a
                        // volume swipe that rolled sideways off a thumb pivot latches horizontal
                        // on 8dp of evidence, so the common configuration silently ate the
                        // commonest gesture. EDGE_TRACKING is the only state with a way back to
                        // ADJUSTING; an unarmed direction simply never fires anything from it.
                        state = State.EDGE_TRACKING
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
                    // The stroke was a volume swipe that rolled sideways on the way up. The short
                    // action it may have qualified for is abandoned, and the stretch it was
                    // showing snaps back.
                    edgeQualified = false
                    endPull()
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
                    // The far threshold wins outright: the stroke went far enough that the short
                    // swipe was never what the user meant, so its deferred action is dropped
                    // rather than also firing on lift.
                    edgeQualified = false
                    // Deliberately not endPull(): the stretch is handed over, not collapsed. It is
                    // already at full extension because the finger dragged it there, so committing
                    // is a change of meaning with no change of shape.
                    pullActive = false
                    // DEAD, not a state of its own. The panel stays up after this and takes its
                    // own touches from here, so there is nothing left for this stroke to drive.
                    state = State.DEAD
                    host.onQuickSliderBegin(edgeInward)
                } else if (straight && edx >= edgeTriggerPx) {
                    if (edgeSliderArmed) {
                        // Both outcomes are still live, so neither fires and the bar starts
                        // stretching instead. The short action is held until lift — but only if
                        // one is bound; the stretch is shown either way, because it answers "how
                        // much further to the panel?" and that does not depend on what the shorter
                        // swipe happens to do.
                        if (nowInward == edgeInward) {
                            if (edgeShortArmed) edgeQualified = true
                            if (!pullActive) {
                                pullActive = true
                                host.onEdgePullBegin(edgeInward)
                            }
                        }
                    } else if (edgeShortArmed) {
                        // Nothing further along this direction to wait for, so the Deck opens
                        // while the finger is still moving.
                        state = State.DEAD
                        if (nowInward == edgeInward) host.onHorizontalSwipe(edgeInward)
                    }
                }

                // Every frame the pull is live. Measured along the direction the gesture armed
                // rather than from `edx`, which is absolute and would read a finger that has
                // doubled back past its own start as travelling *forward* — stretching the bar
                // hardest exactly when the user is undoing the gesture.
                if (pullActive) {
                    val along = (rawX - downRawX) * edgeInwardSign * (if (edgeInward) 1f else -1f)
                    pullProgress = ((along - edgeTriggerPx) / (sliderTriggerPx - edgeTriggerPx))
                        .coerceIn(0f, 1f)
                    host.onEdgePullUpdate(pullProgress)
                }

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
            // palm and the original touch — on a curved edge, easily past the threshold, firing
            // the action from a palm. A drag re-anchors because the bar is already visibly
            // following the finger and must not leap; an edge swipe has emitted nothing yet, so
            // abandoning it costs the user only a re-swipe.
            state = State.DEAD
            edgeQualified = false
            endPull()
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
            State.EDGE_TRACKING -> {
                if (settlesIntoPanel()) {
                    // Handed over exactly as the far threshold hands it over: the stretch is not
                    // collapsed, and the short action it was holding for this lift is dropped.
                    edgeQualified = false
                    pullActive = false
                    host.onQuickSliderBegin(edgeInward)
                } else {
                    // Collapse first, fire second. The short action is usually a window — the
                    // Deck — and letting it go up before the stretch has been told to retract
                    // leaves the stretch frozen at full extension underneath it.
                    endPull()
                    fireDeferredHorizontalSwipe()
                }
            }
            else -> Unit
        }

        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    private fun onCancel() {
        handler.removeCallbacks(longPressRunnable)
        // A short swipe held back for the panel still fires here. Before the panel shared this
        // direction the action had already gone out by the time a cancel could arrive, so dropping
        // it now would turn "the system stole the gesture" into "the gesture did nothing".
        if (state == State.EDGE_TRACKING) {
            endPull()
            fireDeferredHorizontalSwipe()
        }
        // Commit an in-flight drag rather than snapping back: the bar is already visibly where the
        // user put it, and the cancel usually came from the system stealing the gesture, not from
        // the user changing their mind.
        finishInFlight(commitDrag = true)
        state = State.IDLE
        pointerId = MotionEvent.INVALID_POINTER_ID
    }

    /**
     * Fires a short swipe that qualified while the panel was armed and has been waiting for lift.
     *
     * The direction is re-tested against where the finger actually ended up: a stroke that went out
     * past the threshold and came back has reversed, and the direction it armed no longer describes
     * it. That is the same test the immediate path runs at the threshold — deferring the action
     * defers the test with it.
     */
    private fun fireDeferredHorizontalSwipe() {
        if (!edgeQualified) return
        edgeQualified = false
        val endedInward = (lastRawX - downRawX) * edgeInwardSign > 0f
        if (endedInward == edgeInward) host.onHorizontalSwipe(edgeInward)
    }

    /**
     * Whether the stretch on screen as the finger lifts finishes opening into the panel.
     *
     * Read in `onUp` before the pointer is let go, while the tracker still holds the lift itself.
     */
    private fun settlesIntoPanel(): Boolean {
        if (!pullActive) return false
        var inward = 0f
        velocityTracker?.let { tracker ->
            tracker.computeCurrentVelocity(1000)
            inward = tracker.getXVelocity(pointerId) * edgeInwardSign * (if (edgeInward) 1f else -1f)
        }
        return EdgePullRelease.settles(pullProgress, inward, flickPxPerS, edgeShortArmed)
    }

    /**
     * Collapses a stretch that never became a panel, at most once.
     *
     * Every way out of [State.EDGE_TRACKING] that is not the commit runs through here, and several
     * of them run through each other. The flag is what makes that safe, so callers do not each have
     * to know which of the others has already been.
     */
    private fun endPull() {
        if (!pullActive) return
        pullActive = false
        host.onEdgePullCancel()
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

/**
 * What a long swipe's stretch becomes when the finger lifts short of the far threshold. Kept apart
 * from the detector, and free of Android, so the rule can be tested on its own.
 */
internal object EdgePullRelease {

    /**
     * How far out the stretch must be, 0..1, for a lift to finish the panel rather than take it
     * away. Half: past it the stroke has gone further than a flick for the Deck needs to.
     */
    const val SETTLE_PROGRESS = 0.5f

    /**
     * A lift still moving faster than this along the swipe is a flick, in dp per second.
     *
     * A user watching the panel grow lifts at a few hundred; a flick leaves at well over a
     * thousand. A flick is how the Deck is opened, and a hard one carries past halfway without
     * meaning to, so it keeps the short action.
     */
    const val FLICK_DP_PER_S = 1000f

    /**
     * @param progress how far out the stretch is, 0..1.
     * @param inwardVelocity along the swipe's own direction, in px/s. Negative is back toward
     *   where it started.
     * @param flickVelocity [FLICK_DP_PER_S] in px/s.
     * @param shortActionBound whether the short swipe has an action a flick could have been for.
     */
    fun settles(
        progress: Float,
        inwardVelocity: Float,
        flickVelocity: Float,
        shortActionBound: Boolean,
    ): Boolean {
        if (progress < SETTLE_PROGRESS) return false
        // Thrown back toward the edge: the user is putting it away.
        if (inwardVelocity <= -flickVelocity) return false
        // Thrown on inward: a flick, and it was for the Deck. With nothing on the short swipe there
        // is nothing to protect, and the panel is the only thing this stroke can mean.
        if (shortActionBound && inwardVelocity >= flickVelocity) return false
        return true
    }
}
