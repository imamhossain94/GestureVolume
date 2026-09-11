package com.newagedevs.gesturevolume.ui.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.newagedevs.gesturevolume.utils.SliderFill
import com.newagedevs.gesturevolume.utils.PanelAnimation
import com.newagedevs.gesturevolume.utils.HandlerShape

/**
 * The bar, mid-way through becoming a track, at any point on that journey.
 *
 * Drawn rather than composed. It is created and destroyed inside a single touch gesture, in a
 * Service-owned window, and it has to redraw every frame the finger moves — a Compose window with
 * its own recomposer and lifecycle owners would be several milliseconds of setup at the exact
 * moment the user is expecting the bar to snap open. The Deck can afford that because it opens
 * once and stays; this cannot.
 *
 * **Why this draws a rect instead of being scaled.**
 *
 * The version this replaces was a finished slider that `View.scaleX`/`scaleY` squashed down to the
 * bar's proportions and then let go of. Two things are wrong with that and both are visible. A
 * rounded rectangle under non-uniform scale has elliptical corners, so through the whole animation
 * the pill was a lozenge that un-squashed at the end — the "ugly" part of it. And the shape it
 * squashed to was a *slider*, in slider colours, so it never actually resembled the bar it was
 * supposed to be growing out of; the bar was simply blanked on the same frame and the eye read a
 * swap rather than a movement.
 *
 * So the geometry is interpolated instead of transformed. [setExpansion] moves the drawn rect
 * between [setCollapsedRect] — the bar's own bounds, in this window's coordinates — and the full
 * window, and the radius, the colour and the contents all travel with it. Corners stay circular at
 * every value because the radius is a number being animated rather than a curve being stretched,
 * and at expansion 0 what is on screen is the bar: same rectangle, same colour, same corner. That
 * is what lets the handoff be invisible.
 *
 * **Why the expansion is not animated here.** It is a property, set from outside, and for most of
 * its life the thing setting it is the user's finger. The gesture reports how far it has pulled
 * between the two thresholds and that number goes straight in, so the bar tracks the finger one
 * pixel for one pixel and there is no animator between the two to add lag or overshoot. Only the
 * collapse is animated, because only the collapse has no finger driving it.
 *
 * **Why everything is drawn twice.** The value label and the icon sit inside the track, which is
 * one colour below the fill line and another above it. Picking a single colour for them means
 * choosing which half of the range they disappear in. So each is drawn once in the colour that
 * contrasts the track, then again clipped to the filled region in the colour that contrasts the
 * fill. The result inverts exactly at the fill line and is legible at every value, which is the
 * only property that matters for something the user reads while their finger is moving.
 */
class QuickSliderView(context: Context) : View(context) {

    private companion object {
        /**
         * The expansion at which the contents have finished fading in.
         *
         * Well before the end, so the icon and the number are solid for the whole second half of
         * the pull rather than arriving with the commit. The user is deciding whether to keep
         * pulling during exactly that stretch, and what the slider is going to control is the
         * information that decision needs.
         */
        const val CONTENT_FADE_END = 0.55f

        /**
         * The expansion at which the contents start to appear at all.
         *
         * Not zero: at low expansion the shape is still bar-sized and an icon drawn into it would
         * be a smudge that pops. Starting a third of the way in means the first thing that
         * happens is the bar moving, which is the part that has to read as continuous.
         */
        const val CONTENT_FADE_START = 0.18f

        /** Thickness of the bright lip drawn on the fill line while a finger is on the track. */
        const val GRAB_LIP_DP = 3f
    }

    private val density = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    /** The bar's bounds in this window's coordinates — where a fully collapsed slider is drawn. */
    private val collapsedRect = RectF()

    /** The window's own bounds — where a fully expanded slider is drawn. */
    private val expandedRect = RectF()

    /**
     * How much of the window the open panel actually fills, and which side it sits against.
     *
     * The two used to be the same thing, which put a floor under how thin the panel could be: the
     * window has to be at least as wide as the bar, because the bar is what the morph starts from
     * and a collapsed rect wider than its own window is a first frame with its edge cut off. So
     * the window keeps that floor and the panel is drawn inside it, pushed against the screen edge
     * — the same arrangement the handler has always used, and for the same reason.
     */
    private var drawnThicknessPx = 0f
    private var drawnOnLeft = false

    fun setDrawnThickness(px: Float, onLeft: Boolean) {
        drawnThicknessPx = px.coerceAtLeast(0f)
        drawnOnLeft = onLeft
        applyExpandedRect()
        rebuildPath()
        invalidate()
    }

    /** [collapsedRect] and [expandedRect] interpolated by [expansion]. What actually gets drawn. */
    private val drawRect = RectF()
    private val drawPath = Path()

    /**
     * The four corners the shape ends on, clockwise from the top left, in pixels.
     *
     * Four, and usually the same four as [collapsedCornersPx]. The panel takes the handler's own
     * corners, so the morph is a *growth* and nothing else: the radii hold still while the rect
     * expands, which is what makes the panel read as the bar getting bigger rather than as a
     * different object fading in over it.
     */
    private val expandedCornersPx = FloatArray(4) { 22f * density }

    /**
     * The bar's four corners, clockwise from the top left, in pixels.
     *
     * Four rather than one because the bar's are not required to agree: the default shape is
     * square where it meets the screen edge and rounded where it faces the app, and collapsing
     * that to an average would make the first frame of every morph a shape the bar has never had.
     */
    private val collapsedCornersPx = FloatArray(4) { 22f * density }

    /** The eight radii `Path.addRoundRect` wants: an x and a y for each corner. */
    private val drawRadii = FloatArray(8)

    /**
     * Which outline the open panel is cut to, and how far a tab's ends sweep.
     *
     * The bar's shape is the *collapsed* end and the panel's is the expanded one, exactly as the
     * corner radii are. Where the two agree the morph interpolates the flare and the change is
     * seamless; where they disagree there is nothing to interpolate — a rectangle and a tab are
     * not the same outline with different numbers — so the panel's shape is used throughout and
     * the first frame is a close approximation of the bar rather than a copy of it.
     */
    private var expandedShape = HandlerShape.ROUNDED
    private var expandedFlare = HandlerShape.DEFAULT_FLARE
    private var collapsedShape = HandlerShape.ROUNDED
    private var collapsedFlare = HandlerShape.DEFAULT_FLARE

    /** Which side of the panel the screen edge is on, for a tab's sweeps. */
    private var edgeOnLeft = false

    /**
     * How far the number sits from the top of the shape, and the icon from the bottom, in pixels.
     *
     * Settings rather than constants because the panel is now anything from a 10dp sliver to a
     * 72dp slab, and a margin that centres the number on one of those crowds it on another. They
     * are measured from where the shape is still full width — see [drawContent] — so a tab's sweep
     * is already accounted for and this is the gap on top of it.
     */
    private var valueMarginPx = 14f * density
    private var iconMarginPx = 16f * density

    fun setContentMargins(valueTopDp: Float, iconBottomDp: Float) {
        valueMarginPx = valueTopDp.coerceAtLeast(0f) * density
        iconMarginPx = iconBottomDp.coerceAtLeast(0f) * density
        invalidate()
    }

    /** Reused by the stripe pass, so a repeating animation allocates nothing per frame. */
    private val stripePath = Path()

    private var trackColor = Color.BLACK
    private var fillColor = Color.WHITE

    /** The bar's background colour, so a collapsed slider is the bar rather than a small slider. */
    private var collapsedColor = Color.BLACK

    /** The colour actually painted: [collapsedColor] to [trackColor], travelling with the shape. */
    private var blendedTrackColor = Color.BLACK

    private var icon: Drawable? = null
    private var showValue = true

    /** 0 = the bar's shape and colour, 1 = the full track. */
    private var expansion = 0f

    /**
     * How much of the contents to draw, 0..1. Purely a function of [expansion].
     *
     * It governs the fill as well as the number and the icon, so a track that is retracting
     * dissolves as one thing. Left solid to the last frame instead, the shape's final state is a
     * bar-sized pill with a fill line and a two-digit number crammed into it — briefly, but it is
     * the frame the eye lands on as the motion stops.
     */
    private var contentAlpha = 0f

    /** 0..1. The value the fill is drawn to. */
    private var value = 0f

    /**
     * How much of the track's own colour survives, 0..1. See PanelTheme.
     *
     * Applied to the painted colour rather than to the whole view's alpha, because the contents —
     * the number, the icon, the fill — have to stay legible on a surface that is deliberately
     * see-through. Fading the view would fade them with it.
     */
    private var surfaceAlpha = 1f

    /**
     * The glass lighting: a rim, a sheen and a counter-light, or null for the other themes.
     *
     * Three paints rather than one border, for the reason the menu's version gives: a border of
     * even weight all the way round reads as a fainter card, not as a material. The rim is bright
     * along the top and nearly gone by the bottom, the sheen falls across the upper surface, and
     * the counter-light is what real glass picks up from whatever it is sitting on.
     *
     * Their shaders depend on the drawn rectangle, which moves every frame of the morph, so they
     * are rebuilt in [rebuildGlass] rather than once here.
     */
    private var edgePaint: Paint? = null
    private var sheenPaint: Paint? = null
    private var counterLightPaint: Paint? = null
    private var glassEnabled = false

    /**
     * Whether the fill is drawn at all.
     *
     * False for the whole pre-commit stretch. A pull that has not yet reached the slider controls
     * nothing, and a track already showing a fill level would say it does — the user would read
     * the current brightness as something their finger had just set, and lifting early would look
     * like the value had been applied and then thrown away.
     */
    private var fillVisible = false

    /**
     * How the panel reports the user's finger back to whoever opened it.
     *
     * The panel drives itself now. It used to be a read-only picture of a value the gesture engine
     * was computing from a finger held somewhere else on the screen, which is why it could be
     * opened and then not adjusted: the stroke that opened it was the only thing that could move
     * it, and once that stroke ended there was no way in. Taking its own touches is the whole
     * difference between a readout and a control.
     */
    interface Listener {
        /**
         * The finger is on the track at this fraction of its height, 0 at the bottom.
         *
         * Absolute, not a delta. A panel the user has *deliberately opened* and is now touching is
         * a slider like any other, and every slider on the platform jumps to where it is touched;
         * a relative grab would mean the first touch does nothing visible, which on a control that
         * closes itself shortly afterwards reads as broken.
         */
        fun onValuePicked(fraction: Float)

        /** The finger lifted. The panel has been used, so it can now show its result and go. */
        fun onAdjustFinished()
    }

    var listener: Listener? = null

    /**
     * What to do when a touch lands anywhere but on this panel.
     *
     * Delivered as `ACTION_OUTSIDE` because the window carries `FLAG_WATCH_OUTSIDE_TOUCH`, and it
     * is a notification rather than an interception: the touch still reaches whatever is under it,
     * so dismissing the panel never costs the user the tap they were actually making.
     */
    private var onTouchOutside: (() -> Unit)? = null

    fun setOnTouchOutside(block: (() -> Unit)?) {
        onTouchOutside = block
    }

    /**
     * Whether this panel answers touches.
     *
     * False until the opening animation has grown it enough to be worth aiming at — a track two
     * pixels tall that already accepts touches turns the first frame of the animation into a
     * value the user did not choose.
     */
    private var interactive = false

    /** Whether a finger is on the track right now. Widens the fill's leading edge as a grab cue. */
    private var grabbed = false

    /** Whether the surface is a pale one, which halves the strength of the glass lighting. */
    private var glassLight = false

    /** The entrance, when one is playing. Held so a second opening can take it over. */
    private var entranceAnimator: ValueAnimator? = null

    /** Which of [SliderFill]'s behaviours the filled portion has. */
    private var fillStyle = SliderFill.SOLID

    /** 0..1 through the current style's cycle. Driven by [fillClock] while the panel is open. */
    private var fillPhase = 0f
    private var fillClock: ValueAnimator? = null

    /** The fill's outline. A rectangle for most styles, a wave or a stack of blocks for the rest. */
    private val fillShapePath = Path()
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Held so a second glide, or a finger arriving mid-glide, can take it over. */
    private var valueAnimator: ValueAnimator? = null

    /**
     * Dresses the panel: how much of its colour survives, and whether it has a lit edge.
     *
     * Blur is not set here — it belongs to the window, not the view, and `OverlayController`
     * applies it when the window is added. This is only the part that is painted.
     */
    fun setPanelTheme(surface: Float, litEdge: Boolean, light: Boolean = false) {
        surfaceAlpha = surface.coerceIn(0f, 1f)
        glassEnabled = litEdge
        glassLight = light
        if (litEdge) {
            edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * density
            }
            sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            counterLightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        } else {
            edgePaint = null
            sheenPaint = null
            counterLightPaint = null
        }
        refreshBlend()
        rebuildGlass()
        invalidate()
    }

    /**
     * Re-aims the glass shaders at the rectangle currently being drawn.
     *
     * Called from [rebuildPath], so it follows the panel as it grows out of the bar — a shader
     * fixed to the panel's final size would put the highlight somewhere off the shape for the
     * whole of the morph.
     */
    private fun rebuildGlass() {
        if (!glassEnabled || drawRect.isEmpty) return
        val top = drawRect.top
        val bottom = drawRect.bottom
        // On a pale pane, white lighting piled on a white surface flattens it. Same correction the
        // menu and the Deck make, and for the same reason.
        val k = if (glassLight) 0.45f else 1f
        fun w(alpha: Int): Int = ((alpha * k).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF

        sheenPaint?.shader = android.graphics.LinearGradient(
            0f, top, 0f, top + drawRect.height() * 0.5f,
            intArrayOf(w(0x2E), w(0x08), 0x00FFFFFF),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        counterLightPaint?.shader = android.graphics.LinearGradient(
            0f, bottom - drawRect.height() * 0.2f, 0f, bottom,
            intArrayOf(0x00FFFFFF, w(0x1A)),
            null,
            android.graphics.Shader.TileMode.CLAMP,
        )
        edgePaint?.shader = android.graphics.LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(w(0xA6), w(0x3D), w(0x14), w(0x4D)),
            floatArrayOf(0f, 0.35f, 0.75f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }

    fun setColors(track: Int, fill: Int) {
        trackColor = track
        fillColor = fill
        // Assigning `color` resets the paint's alpha to the colour's own, so the per-frame alpha
        // set in onDraw is re-applied there rather than being expected to survive this.
        fillPaint.color = fill
        refreshBlend()
    }

    /**
     * The bar's own colour and corner, so expansion 0 is a pixel-accurate stand-in for it.
     *
     * Taken from the live handler rather than assumed, because the bar is fully themeable: a user
     * with a translucent red bar and a dark slider would otherwise see the red flick to dark on
     * the first frame of every pull.
     */
    fun setCollapsedAppearance(
        color: Int,
        topLeftDp: Float,
        topRightDp: Float,
        bottomLeftDp: Float,
        bottomRightDp: Float,
    ) {
        collapsedColor = color
        collapsedCornersPx[0] = topLeftDp * density
        collapsedCornersPx[1] = topRightDp * density
        collapsedCornersPx[2] = bottomRightDp * density
        collapsedCornersPx[3] = bottomLeftDp * density
        refreshBlend()
        rebuildPath()
        invalidate()
    }

    /** Where the bar sits inside this window. See the class note on why the window is bigger. */
    fun setCollapsedRect(left: Float, top: Float, right: Float, bottom: Float) {
        collapsedRect.set(left, top, right, bottom)
        rebuildPath()
        invalidate()
    }

    /** One radius for all four. For callers with a uniform shape — the settings preview. */
    fun setCornerRadiusDp(dp: Float) {
        expandedCornersPx.fill(dp * density)
        rebuildPath()
        invalidate()
    }

    /**
     * The outline the panel grows into, and the one it grows out of.
     *
     * @param edgeLeft which screen edge the bar is on, so a tab's sweeps run the right way.
     */
    fun setShapes(
        expanded: String,
        expandedFlare: Float,
        collapsed: String,
        collapsedFlare: Float,
        edgeLeft: Boolean,
    ) {
        this.expandedShape = HandlerShape.sanitize(expanded)
        this.expandedFlare = HandlerShape.sanitizeFlare(expandedFlare)
        this.collapsedShape = HandlerShape.sanitize(collapsed)
        this.collapsedFlare = HandlerShape.sanitizeFlare(collapsedFlare)
        this.edgeOnLeft = edgeLeft
        rebuildPath()
        invalidate()
    }

    /** The four the shape grows into. Clockwise from the top left, like the collapsed set. */
    fun setExpandedCorners(
        topLeftDp: Float,
        topRightDp: Float,
        bottomLeftDp: Float,
        bottomRightDp: Float,
    ) {
        expandedCornersPx[0] = topLeftDp * density
        expandedCornersPx[1] = topRightDp * density
        expandedCornersPx[2] = bottomRightDp * density
        expandedCornersPx[3] = bottomLeftDp * density
        rebuildPath()
        invalidate()
    }

    fun setIcon(@DrawableRes res: Int?) {
        icon = res?.let { ContextCompat.getDrawable(context, it)?.mutate() }
        invalidate()
    }

    fun setShowValue(show: Boolean) {
        showValue = show
        invalidate()
    }

    /**
     * Moves the shape between the bar and the track.
     *
     * The one property the finger drives, and everything else here is downstream of it: the size,
     * the corner, the colour and how much of the contents show are all read off this single
     * number, so there is no way for them to get out of step with each other or with the gesture.
     */
    fun setExpansion(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped == expansion) return
        expansion = clamped
        contentAlpha = contentFadeFor(clamped)
        refreshBlend()
        rebuildPath()
        invalidate()
    }

    fun expansion(): Float = expansion

    /**
     * The pull became a live slider: start drawing the fill.
     *
     * One-way. Nothing un-commits — the window is torn down and rebuilt for the next gesture —
     * so there is no path back to a track that has a value and stops showing it.
     */
    fun setCommitted() {
        if (fillVisible) return
        fillVisible = true
        invalidate()
    }

    /**
     * Puts the fill at [fraction] this frame.
     *
     * For a finger on the track, and nothing else. A drag must be exactly where the finger is —
     * a control that eases toward the touch instead of sitting under it feels like it is lagging,
     * however short the ease is. Use [animateValue] when there is no finger to explain the move.
     */
    fun setValue(fraction: Float) {
        valueAnimator?.cancel()
        setValueNow(fraction)
    }

    private fun setValueNow(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped == value) return
        value = clamped
        invalidate()
    }

    /**
     * Glides the fill to [fraction].
     *
     * For the moves nothing on screen accounts for: the nudge buttons, and a level changed from
     * somewhere else while the panel happens to be open. A jump there reads as a glitch, because
     * the user's eye has nothing to attribute it to.
     */
    fun animateValue(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped == value) return
        valueAnimator?.cancel()
        valueAnimator = ValueAnimator.ofFloat(value, clamped).apply {
            duration = VALUE_GLIDE_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { setValueNow(it.animatedValue as Float) }
            start()
        }
    }

    private fun contentFadeFor(e: Float): Float =
        ((e - CONTENT_FADE_START) / (CONTENT_FADE_END - CONTENT_FADE_START)).coerceIn(0f, 1f)

    private fun refreshBlend() {
        blendedTrackColor = ColorUtils.blendARGB(collapsedColor, trackColor, expansion)
        trackPaint.color = blendedTrackColor
        // Scales whatever alpha the colour already carried — the bar can itself be translucent,
        // and a theme that replaced that alpha rather than multiplying it would make a see-through
        // bar grow into a solid panel.
        trackPaint.alpha =
            (Color.alpha(blendedTrackColor) * surfaceAlpha).toInt().coerceIn(0, 255)
    }

    /**
     * Opens the panel to touches. Called once the grow animation has finished.
     */
    fun setInteractive(value: Boolean) {
        interactive = value
        if (!value && grabbed) {
            grabbed = false
            invalidate()
        }
    }

    /**
     * Which behaviour the fill has, and starts or stops the clock that drives it.
     *
     * The clock only runs while there is something to animate and the panel is on screen — this
     * view's whole life is a few seconds, but a repeating animator left running after the window
     * is gone is a leak that outlives the thing that leaked it.
     */
    fun setFillStyle(style: String) {
        val next = SliderFill.sanitize(style)
        if (next == fillStyle) return
        fillStyle = next
        fillPhase = 0f
        restartFillClock()
        invalidate()
    }

    /**
     * Plays one of [PanelAnimation]'s entrances on this view.
     *
     * On top of the morph, not instead of it: the panel grows out of the bar because that is what
     * it *is*, and the entrance is what it does while it grows. They compose because the morph
     * animates the shape that gets drawn and this animates the layer it is drawn into.
     *
     * Not for the pull gesture. There a finger is driving the expansion, and a second animation
     * moving the panel while the user is trying to place it is the panel arguing with them.
     */
    fun playEntrance(animation: String, towardLeft: Boolean, speed: Float) {
        entranceAnimator?.cancel()
        val id = PanelAnimation.sanitize(animation)
        entranceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PanelAnimation.scaledDurationMs(id, speed).toLong()
            interpolator = null
            addUpdateListener {
                applyFrame(PanelAnimation.frameAt(id, it.animatedValue as Float, towardLeft))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Exactly at rest, whatever the clock did on its last frame. A panel left a
                    // hundredth of a degree off is a panel that never settles.
                    applyFrame(PanelAnimation.Frame())
                    if (entranceAnimator === animation) entranceAnimator = null
                }
            })
            start()
        }
    }

    private fun applyFrame(f: PanelAnimation.Frame) {
        pivotX = width * f.originX
        pivotY = height * f.originY
        alpha = f.alpha
        scaleX = f.scaleX
        scaleY = f.scaleY
        translationX = f.translationX * density
        translationY = f.translationY * density
        rotation = f.rotationZ
        rotationX = f.rotationX
        rotationY = f.rotationY
    }

    private fun restartFillClock() {
        fillClock?.cancel()
        fillClock = null
        if (!SliderFill.isAnimated(fillStyle) || !isAttachedToWindow) return
        fillClock = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SliderFill.cycleMs(fillStyle).toLong()
            repeatCount = ValueAnimator.INFINITE
            interpolator = null
            addUpdateListener {
                fillPhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartFillClock()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fillClock?.cancel()
        fillClock = null
        valueAnimator?.cancel()
        valueAnimator = null
        entranceAnimator?.cancel()
        entranceAnimator = null
    }

    /**
     * The outline of the filled portion.
     *
     * A plain rectangle unless the style says otherwise. The wave styles replace its top edge with
     * a surface; the block styles cut the whole thing into a stack. Everything downstream — the
     * fill itself, the effects, the clip that inverts the content colour — uses this one path, so
     * a style cannot end up with its fill and its ink disagreeing about where the fill is.
     */
    private fun buildFillPath(fillTop: Float) {
        fillShapePath.reset()
        val l = drawRect.left
        val r = drawRect.right
        val b = drawRect.bottom

        when {
            SliderFill.hasWave(fillStyle) && fillTop > drawRect.top -> {
                val amp = SliderFill.WAVE_AMPLITUDE_DP * density
                fillShapePath.moveTo(l, b)
                fillShapePath.lineTo(l, fillTop)
                var x = l
                while (x <= r) {
                    val at = (x - l) / (r - l).coerceAtLeast(1f)
                    fillShapePath.lineTo(x, fillTop - SliderFill.waveAt(fillStyle, fillPhase, at) * amp)
                    x += WAVE_STEP_PX
                }
                fillShapePath.lineTo(r, fillTop - SliderFill.waveAt(fillStyle, fillPhase, 1f) * amp)
                fillShapePath.lineTo(r, b)
                fillShapePath.close()
            }

            else -> fillShapePath.addRect(l, fillTop, r, b, Path.Direction.CW)
        }

        // Cut to the panel's own outline rather than left to a clip to do it. Same reason as the
        // surface: a clipped edge is a stepped edge, and on a tab the fill's edge *is* the tab's.
        fillShapePath.op(drawPath, Path.Op.INTERSECT)
    }

    /** The part of a style that is drawn *over* the fill rather than being its shape. */
    private fun drawFillEffects(canvas: Canvas, fillTop: Float, alpha: Int) {
        if (fillStyle == SliderFill.SOLID) return

        canvas.save()
        canvas.clipPath(fillShapePath)
        if (SliderFill.isPictorial(fillStyle)) {
            drawPictorialFill(canvas, fillTop, alpha)
        } else {
            drawTintedFill(canvas, fillTop, alpha)
        }
        canvas.restore()
    }

    /**
     * The styles that tint the fill rather than painting over it.
     *
     * Drawn in the *track's* colour, not in white. White was the obvious choice and it was wrong:
     * the fill's own default is white, so a white charging band on it was invisible. The track
     * colour is the one colour in this view guaranteed to contrast with the fill — it is already
     * what the number and the icon are drawn in over the filled half, for the same reason.
     */
    private fun drawTintedFill(canvas: Canvas, fillTop: Float, alpha: Int) {
        if (fillStyle != SliderFill.STRIPES) return
        val ink = blendedTrackColor or (0xFF shl 24)
        effectPaint.shader = null
        effectPaint.color = ink
        effectPaint.alpha = (0.20f * alpha).toInt().coerceIn(0, 255)
        val pitch = STRIPE_PITCH_DP * density
        val offset = fillPhase * pitch * 2f
        val width = drawRect.width()
        var y = fillTop - width - pitch * 2f + offset
        while (y < drawRect.bottom + pitch) {
            stripePath.reset()
            stripePath.moveTo(drawRect.left, y)
            stripePath.lineTo(drawRect.left + width, y - width)
            stripePath.lineTo(drawRect.left + width, y - width + pitch)
            stripePath.lineTo(drawRect.left, y + pitch)
            stripePath.close()
            canvas.drawPath(stripePath, effectPaint)
            y += pitch * 2f
        }
    }

    /**
     * The styles that paint their own picture on the fill.
     *
     * These carry a palette rather than borrowing the track's colour, because the palette is the
     * idea: a nebula in one colour is a cloud, and a rain that is not green is just rain. They are
     * laid over a darkened fill so their own light has something to be light against — a neon line
     * on a white bar is a grey line.
     */
    private fun drawPictorialFill(canvas: Canvas, fillTop: Float, alpha: Int) {
        val palette = SliderFill.palette(fillStyle)
        val height = (drawRect.bottom - fillTop).coerceAtLeast(1f)
        val width = drawRect.width().coerceAtLeast(1f)

        // The ground. Without it every one of these is washed out by whatever colour the fill is.
        effectPaint.shader = null
        effectPaint.color = Color.BLACK
        effectPaint.alpha = (0.82f * alpha).toInt().coerceIn(0, 255)
        canvas.drawRect(drawRect.left, fillTop, drawRect.right, drawRect.bottom, effectPaint)

        when (fillStyle) {
            SliderFill.NEBULA -> {
                // Four soft clouds on their own slow orbits. Radial gradients rather than circles:
                // a cloud with an edge is a balloon.
                for (i in palette.indices) {
                    val seed = SliderFill.pseudoRandom(i * 17 + 3)
                    val drift = fillPhase * 2f * Math.PI.toFloat() * (0.6f + seed * 0.8f) +
                        seed * 6.28f
                    val cx = drawRect.left + width * (0.5f + 0.42f * kotlin.math.cos(drift))
                    val cy = fillTop + height * (0.5f + 0.42f * kotlin.math.sin(drift * 0.7f))
                    val radius = width * (1.1f + seed * 0.6f)
                    effectPaint.shader = android.graphics.RadialGradient(
                        cx, cy, radius,
                        intArrayOf(palette[i].toInt(), palette[i].toInt() and 0x00FFFFFF),
                        null,
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                    effectPaint.alpha = alpha
                    canvas.drawRect(drawRect.left, fillTop, drawRect.right, drawRect.bottom, effectPaint)
                }
                effectPaint.shader = null
            }

            SliderFill.CYBERPUNK -> {
                // Scan lines, sliding.
                effectPaint.shader = null
                val pitch = 5f * density
                val offset = (fillPhase * pitch * 2f) % (pitch * 2f)
                effectPaint.color = palette[0].toInt()
                effectPaint.alpha = (0.5f * alpha).toInt().coerceIn(0, 255)
                var y = fillTop + offset - pitch * 2f
                while (y < drawRect.bottom) {
                    canvas.drawRect(drawRect.left, y, drawRect.right, y + pitch * 0.45f, effectPaint)
                    y += pitch * 2f
                }
                // A magenta bloom at the fill's edge, which is where the eye goes.
                effectPaint.shader = android.graphics.LinearGradient(
                    0f, fillTop, 0f, fillTop + height * 0.3f,
                    intArrayOf(palette[1].toInt(), palette[1].toInt() and 0x00FFFFFF), null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
                effectPaint.alpha = alpha
                canvas.drawRect(drawRect.left, fillTop, drawRect.right, fillTop + height * 0.3f, effectPaint)
                effectPaint.shader = null
                // And a glitch band, now and then.
                val glitch = SliderFill.glitchAt(fillStyle, fillPhase)
                if (!glitch.isNaN()) {
                    val gy = fillTop + height * glitch
                    effectPaint.color = palette[2].toInt()
                    effectPaint.alpha = (0.85f * alpha).toInt().coerceIn(0, 255)
                    canvas.drawRect(drawRect.left, gy, drawRect.right, gy + 3f * density, effectPaint)
                }
            }

            SliderFill.PLASMA -> {
                // Three sine fields folded together, sampled on a coarse grid. The classic, and
                // it is classic because two fields read as stripes and four as noise.
                // Fine enough to read as a field rather than as tiles. Coarser was cheaper and
                // looked like a spreadsheet.
                val cell = 2f * density
                val cols = (width / cell).toInt().coerceIn(1, 64)
                val rows = (height / cell).toInt().coerceIn(1, 320)
                val cw = width / cols
                val ch = height / rows
                val t = fillPhase * 6.28318f
                for (c in 0 until cols) {
                    for (r in 0 until rows) {
                        val x = c.toFloat() / cols
                        val y = r.toFloat() / rows
                        val v = (
                            kotlin.math.sin(x * 5f + t) +
                                kotlin.math.sin(y * 7f - t * 0.8f) +
                                kotlin.math.sin((x + y) * 6f + t * 1.3f)
                            ) / 3f
                        val idx = ((v + 1f) / 2f * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)
                        effectPaint.color = palette[idx].toInt()
                        effectPaint.alpha = alpha
                        canvas.drawRect(
                            drawRect.left + cw * c, drawRect.bottom - ch * (r + 1),
                            drawRect.left + cw * (c + 1), drawRect.bottom - ch * r, effectPaint
                        )
                    }
                }
            }

            SliderFill.AURORA -> {
                // Curtains: soft vertical gradients that lean as they drift. Leaning is what
                // separates an aurora from a set of coloured bars.
                for (i in palette.indices) {
                    val seed = SliderFill.pseudoRandom(i * 53 + 11)
                    val drift = fillPhase * 6.28318f * (0.5f + seed * 0.7f) + seed * 6.28f
                    val cx = drawRect.left + width * (0.5f + 0.45f * kotlin.math.sin(drift))
                    val bandWidth = width * (0.32f + seed * 0.3f)
                    val lean = width * 0.18f * kotlin.math.cos(drift * 0.8f)
                    effectPaint.shader = android.graphics.LinearGradient(
                        cx, drawRect.bottom, cx + lean, fillTop,
                        intArrayOf(
                            palette[i].toInt() and 0x00FFFFFF,
                            palette[i].toInt(),
                            palette[i].toInt() and 0x00FFFFFF,
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                    effectPaint.alpha = alpha
                    // Drawn twice, the second pass narrower and offset: a single band is a stripe,
                    // and what makes a curtain is folds overlapping at different depths.
                    stripePath.reset()
                    stripePath.moveTo(cx - bandWidth / 2f, drawRect.bottom)
                    stripePath.lineTo(cx + bandWidth / 2f, drawRect.bottom)
                    stripePath.lineTo(cx + bandWidth / 2f + lean, fillTop)
                    stripePath.lineTo(cx - bandWidth / 2f + lean, fillTop)
                    stripePath.close()
                    canvas.drawPath(stripePath, effectPaint)
                    canvas.drawPath(stripePath, effectPaint)
                }
                effectPaint.shader = null
            }

            SliderFill.HOLOGRAM -> {
                // A projected image: scan bands, the colour split either side of them, and a
                // flicker. The split is the part that sells it — a clean band is a blind.
                val pitch = 7f * density
                val offset = (fillPhase * pitch * 3f) % (pitch * 2f)
                val flicker = if (SliderFill.pseudoRandom((fillPhase * 24f).toInt()) > 0.88f) 0.45f else 1f
                var y = fillTop + offset - pitch * 2f
                while (y < drawRect.bottom) {
                    effectPaint.shader = null
                    effectPaint.color = palette[1].toInt()
                    effectPaint.alpha = (0.55f * flicker * alpha).toInt().coerceIn(0, 255)
                    canvas.drawRect(drawRect.left, y - 1.2f * density, drawRect.right, y, effectPaint)
                    effectPaint.color = palette[0].toInt()
                    effectPaint.alpha = (0.9f * flicker * alpha).toInt().coerceIn(0, 255)
                    canvas.drawRect(drawRect.left, y, drawRect.right, y + pitch * 0.5f, effectPaint)
                    y += pitch * 2f
                }
                // The sheet the bands are printed on.
                effectPaint.shader = android.graphics.LinearGradient(
                    0f, fillTop, 0f, drawRect.bottom,
                    intArrayOf(palette[2].toInt(), palette[2].toInt() and 0x00FFFFFF), null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
                effectPaint.alpha = (0.5f * alpha).toInt().coerceIn(0, 255)
                canvas.drawRect(drawRect.left, fillTop, drawRect.right, drawRect.bottom, effectPaint)
                effectPaint.shader = null
            }

            SliderFill.EMBER -> {
                // Sparks, each on its own seeded path, fading as it climbs. Deterministic, so the
                // same panel opened twice shows the same fire continuing rather than restarting.
                effectPaint.shader = null
                for (i in 0 until EMBER_COUNT) {
                    val seed = SliderFill.pseudoRandom(i * 97 + 5)
                    val seed2 = SliderFill.pseudoRandom(i * 131 + 17)
                    val life = ((fillPhase * (0.7f + seed * 0.8f) + seed2) % 1f)
                    val y = drawRect.bottom - height * life
                    if (y < fillTop) continue
                    val sway = kotlin.math.sin(life * 9f + seed * 6.28f) * width * 0.16f
                    val x = drawRect.left + width * (0.2f + seed * 0.6f) + sway
                    val fade = (1f - life).coerceIn(0f, 1f)
                    val idx = ((1f - fade) * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)
                    effectPaint.color = palette[idx].toInt()
                    effectPaint.alpha = (fade * fade * alpha).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x, y, (1.1f + seed2 * 1.6f) * density, effectPaint)
                }
            }

            SliderFill.SONAR -> {
                // Rings leaving the fill line. Three at a time, evenly spaced through the cycle,
                // so there is always one arriving and one on its way out.
                effectPaint.style = Paint.Style.STROKE
                effectPaint.shader = null
                for (i in 0 until 3) {
                    val life = ((fillPhase + i / 3f) % 1f)
                    val radius = width * 0.2f + life * height * 0.9f
                    val fade = (1f - life)
                    effectPaint.strokeWidth = (1f + fade * 1.6f) * density
                    effectPaint.color = palette[0].toInt()
                    effectPaint.alpha = (fade * fade * alpha).toInt().coerceIn(0, 255)
                    canvas.drawCircle(drawRect.centerX(), fillTop, radius, effectPaint)
                }
                effectPaint.style = Paint.Style.FILL
                // The source, so the rings have something to come from.
                effectPaint.color = palette[0].toInt()
                effectPaint.alpha = (0.8f * alpha).toInt().coerceIn(0, 255)
                canvas.drawCircle(drawRect.centerX(), fillTop, 2.2f * density, effectPaint)
            }

            SliderFill.CIRCUIT -> {
                // Traces on a board. Each segment is seeded to run one way or the other, and a
                // travelling wave lights them — a board where everything lit at once would be a
                // grid, and a board where nothing travelled would be wallpaper.
                val cell = 11f * density
                val cols = (width / cell).toInt().coerceIn(1, 8)
                val rows = (height / cell).toInt().coerceIn(1, 50)
                val cw = width / cols
                val ch = height / rows
                effectPaint.style = Paint.Style.STROKE
                effectPaint.strokeWidth = 1.5f * density
                effectPaint.shader = null
                for (c in 0 until cols) {
                    for (r in 0 until rows) {
                        val seed = SliderFill.pseudoRandom(c * 733 + r * 191)
                        if (seed < 0.38f) continue
                        val here = 1f - r.toFloat() / rows
                        val d = kotlin.math.abs(((here - fillPhase) % 1f + 1f) % 1f)
                        val glow = (1f - minOf(d, 1f - d) / 0.3f).coerceAtLeast(0f)
                        val idx = ((1f - glow) * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)
                        effectPaint.color = palette[idx].toInt()
                        effectPaint.alpha = ((0.25f + glow * 0.75f) * alpha).toInt().coerceIn(0, 255)
                        val x = drawRect.left + cw * (c + 0.5f)
                        val y = drawRect.bottom - ch * (r + 0.5f)
                        if (seed > 0.72f) {
                            canvas.drawLine(x, y, x, y - ch, effectPaint)
                            canvas.drawLine(x, y - ch, x + cw * 0.5f, y - ch, effectPaint)
                        } else {
                            canvas.drawLine(x - cw * 0.5f, y, x + cw * 0.5f, y, effectPaint)
                        }
                        if (glow > 0.6f) {
                            effectPaint.style = Paint.Style.FILL
                            canvas.drawCircle(x, y, 1.7f * density, effectPaint)
                            effectPaint.style = Paint.Style.STROKE
                        }
                    }
                }
                effectPaint.style = Paint.Style.FILL
            }

            else -> {
                // The three grid styles. One loop, because what separates them is what goes in a
                // cell and which way the wave runs, not how the grid is built.
                val cell = SliderFill.CELL_DP * density
                val columns = (width / cell).toInt().coerceIn(1, 12)
                val rows = (height / cell).toInt().coerceIn(1, 80)
                val cw = width / columns
                val ch = height / rows
                effectPaint.shader = null
                for (col in 0 until columns) {
                    for (row in 0 until rows) {
                        val glow = SliderFill.cellGlow(fillStyle, fillPhase, col, row, columns, rows)
                        if (glow <= 0.02f) continue
                        val cx = drawRect.left + cw * (col + 0.5f)
                        val cy = drawRect.bottom - ch * (row + 0.5f)
                        val shade = palette[((1f - glow) * (palette.size - 1)).toInt().coerceIn(0, palette.size - 1)]
                        effectPaint.color = shade.toInt()
                        effectPaint.alpha = (glow * alpha).toInt().coerceIn(0, 255)
                        when (fillStyle) {
                            SliderFill.DOT_MATRIX ->
                                canvas.drawCircle(cx, cy, cw * 0.26f, effectPaint)

                            SliderFill.MATRIX_RAIN ->
                                canvas.drawRect(
                                    cx - cw * 0.22f, cy - ch * 0.34f,
                                    cx + cw * 0.22f, cy + ch * 0.34f, effectPaint
                                )

                            // A mark rather than a blob: two strokes crossing, which at this size
                            // reads as carved without needing a font.
                            else -> {
                                effectPaint.style = Paint.Style.STROKE
                                effectPaint.strokeWidth = 1.6f * density
                                canvas.drawLine(cx, cy - ch * 0.3f, cx, cy + ch * 0.3f, effectPaint)
                                canvas.drawLine(
                                    cx - cw * 0.2f, cy - ch * 0.08f,
                                    cx + cw * 0.2f, cy + ch * 0.16f, effectPaint
                                )
                                effectPaint.style = Paint.Style.FILL
                            }
                        }
                    }
                }
            }
        }
        effectPaint.shader = null
        effectPaint.style = Paint.Style.FILL
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Ahead of the interactive gate, and deliberately. A panel still growing is exactly the
        // one a user is most likely to dismiss by tapping past it, and refusing to notice would
        // leave it on screen until its idle timeout.
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onTouchOutside?.invoke()
            return true
        }
        if (!interactive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                grabbed = true
                // Measured against the drawn rect rather than the view, because the two are only
                // the same once the panel is fully open — and a touch landing during the last few
                // frames of the animation must still mean what it looks like it means.
                val h = drawRect.height()
                if (h <= 0f) return true
                val fraction = (1f - (event.y - drawRect.top) / h).coerceIn(0f, 1f)
                listener?.onValuePicked(fraction)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                grabbed = false
                invalidate()
                listener?.onAdjustFinished()
            }
        }
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyExpandedRect()
        // Sized from the *drawn* panel rather than the window, so a panel deliberately made
        // narrower than the bar gets a number that fits it. Still not from the rect being drawn,
        // because text that scales during the morph reads as a zoom rather than as a reveal.
        textPaint.textSize = (expandedRect.width() * 0.34f).coerceIn(9f * density, 20f * density)
        // A collapsed rect the caller has not set yet would leave the first frame at the window's
        // full size, which is the pop this class exists to remove.
        if (collapsedRect.isEmpty) collapsedRect.set(expandedRect)
        rebuildPath()
    }

    private fun applyExpandedRect() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val drawn = if (drawnThicknessPx <= 0f) w else drawnThicknessPx.coerceAtMost(w)
        if (drawnOnLeft) {
            expandedRect.set(0f, 0f, drawn, h)
        } else {
            expandedRect.set(w - drawn, 0f, w, h)
        }
    }

    private fun rebuildPath() {
        if (expandedRect.isEmpty) return

        drawRect.set(
            lerp(collapsedRect.left, expandedRect.left),
            lerp(collapsedRect.top, expandedRect.top),
            lerp(collapsedRect.right, expandedRect.right),
            lerp(collapsedRect.bottom, expandedRect.bottom)
        )

        drawPath.reset()
        if (drawRect.isEmpty) {
            rebuildGlass()
            return
        }

        // A tab at either end means a tab: the outline has no corners to travel, only a sweep, and
        // the sweep's length is what interpolates.
        val tab = expandedShape == HandlerShape.TAB ||
            (collapsedShape == HandlerShape.TAB && expansion < 0.5f)

        if (tab) {
            val flare = when {
                expandedShape == collapsedShape -> lerp(collapsedFlare, expandedFlare)
                expandedShape == HandlerShape.TAB -> expandedFlare
                else -> collapsedFlare
            }
            val outline = HandlerShape.tabOutline(drawRect.width(), drawRect.height(), flare, edgeOnLeft)
            val l = drawRect.left
            val t = drawRect.top
            drawPath.moveTo(l + outline[0], t + outline[1])
            var i = 2
            while (i < outline.size) {
                drawPath.lineTo(l + outline[i], t + outline[i + 1])
                i += 2
            }
            drawPath.close()
        } else {
            // Never more than half the shorter side, or the corners overlap and the round-rect
            // degenerates into a shape the caller did not ask for. Clamped against the rect being
            // drawn rather than the window, because the collapsed rect is the smaller of the two
            // and is where the clamp actually bites.
            val limit = minOf(drawRect.width(), drawRect.height()) / 2f
            // Each corner travels from its own starting radius to its own finishing one, so a bar
            // that is square on one side rounds off as it grows rather than snapping round on
            // frame one. `addRoundRect` takes them as x/y pairs, clockwise from the top left.
            for (corner in 0..3) {
                val r = lerp(collapsedCornersPx[corner], expandedCornersPx[corner]).coerceIn(0f, limit)
                drawRadii[corner * 2] = r
                drawRadii[corner * 2 + 1] = r
            }
            drawPath.addRoundRect(drawRect, drawRadii, Path.Direction.CW)
        }
        rebuildGlass()
    }

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * expansion

    override fun onDraw(canvas: Canvas) {
        if (drawRect.isEmpty) return

        /*
         * The surface is *drawn* as a path, not a rectangle inside a clip.
         *
         * It used to be the second, and a clip is not antialiased. On a rounded rectangle that
         * shows as a slightly hard corner and nobody notices; on a tab, whose sides are one long
         * sweep, it shows as stair-steps down the whole edge. Everything that has to stay inside
         * the shape is still clipped — but the shape's own outline, the part the eye follows, is
         * now a path with antialiasing on.
         */
        canvas.drawPath(drawPath, trackPaint)

        canvas.save()
        canvas.clipPath(drawPath)

        // Under the contents: lighting on the surface, not over the number.
        sheenPaint?.let { canvas.drawRect(drawRect, it) }
        counterLightPaint?.let { canvas.drawRect(drawRect, it) }

        drawContent(canvas, overFill = false)
        canvas.restore()

        if (fillVisible && contentAlpha > 0.01f) {
            val fillTop = drawRect.bottom - drawRect.height() * value
            buildFillPath(fillTop)

            val alpha = (contentAlpha * 255f).toInt().coerceIn(0, 255)
            fillPaint.alpha = alpha
            canvas.drawPath(fillShapePath, fillPaint)

            // Whatever the style adds on top of a plain fill. All of it clipped to the fill, so
            // none of it strays onto the empty half of the track — and the fill's own edge is
            // already drawn antialiased underneath, so a clip here lands colour-on-colour rather
            // than colour-on-wallpaper.
            drawFillEffects(canvas, fillTop, alpha)

            // Pass two: the same content, clipped to the filled region, in the inverted colour.
            canvas.save()
            canvas.clipPath(fillShapePath)
            drawContent(canvas, overFill = true)
            canvas.restore()

            // A bright lip on the fill's leading edge while a finger is on it. The panel has no
            // thumb — the fill line *is* the value — so without this there is nothing to confirm
            // that the touch was received, and on a control the user only touches for a moment
            // that confirmation is most of the feedback there is.
            if (grabbed) {
                canvas.save()
                canvas.clipPath(drawPath)
                fillPaint.alpha = 255
                canvas.drawRect(
                    drawRect.left,
                    fillTop - GRAB_LIP_DP * density / 2f,
                    drawRect.right,
                    fillTop + GRAB_LIP_DP * density / 2f,
                    fillPaint
                )
                canvas.restore()
            }
            fillPaint.alpha = 255
        }

        // The rim last and outside the fill pass, so the filled half of the track does not paint
        // over the edge.
        edgePaint?.let { canvas.drawPath(drawPath, it) }
    }

    private fun drawContent(canvas: Canvas, overFill: Boolean) {
        if (contentAlpha <= 0.01f) return
        val ink = if (overFill) blendedTrackColor else fillColor
        val alpha = (contentAlpha * 255f).toInt().coerceIn(0, 255)

        /*
         * How far in from each end the shape is still full width.
         *
         * Zero for a rounded panel, which is full width from top to bottom. A tab is not: its ends
         * sweep away to nothing, and a number placed a fixed distance from the top of the *window*
         * lands in the part that has been swept away and comes out with its top sliced off. The
         * icon at the other end had the same problem. Measured from the shape rather than guessed,
         * so it follows the sweep as the panel's flare is changed.
         */
        val endInset = if (expandedShape == HandlerShape.TAB) {
            HandlerShape.tabSweepDepth(drawRect.height(), expandedFlare)
        } else {
            0f
        }

        if (showValue) {
            textPaint.color = ink
            textPaint.alpha = alpha
            val label = "${(value * 100f).toInt()}"
            // Baseline placed by the font's own metrics rather than a guessed offset, so the
            // number sits the same distance from the top on every device font scale.
            val y = drawRect.top + endInset + valueMarginPx - textPaint.fontMetrics.ascent
            canvas.drawText(label, drawRect.centerX(), y, textPaint)
        }

        icon?.let { drawable ->
            val size = (drawRect.width() * 0.46f).coerceIn(12f * density, 26f * density)
            val cx = drawRect.centerX()
            val cy = drawRect.bottom - endInset - iconMarginPx - size / 2f
            val wrapped = DrawableCompat.wrap(drawable)
            DrawableCompat.setTint(wrapped, ink)
            wrapped.alpha = alpha
            wrapped.setBounds(
                (cx - size / 2f).toInt(),
                (cy - size / 2f).toInt(),
                (cx + size / 2f).toInt(),
                (cy + size / 2f).toInt()
            )
            wrapped.draw(canvas)
        }
    }
}

/**
 * How long the fill takes to travel to a value nobody's finger is on.
 *
 * Short. This is a confirmation that something moved, not a journey; anything longer and a second
 * press of a nudge button arrives while the first is still travelling.
 */
private const val VALUE_GLIDE_MS = 130L

/** How often the wave's outline is sampled across the track. Finer than the eye can resolve. */
private const val WAVE_STEP_PX = 6f

/** The width of one diagonal band, and of the gap after it. */
private const val STRIPE_PITCH_DP = 9f

/** How many sparks an ember fill carries. Enough to read as fire, few enough to stay sparks. */
private const val EMBER_COUNT = 22
