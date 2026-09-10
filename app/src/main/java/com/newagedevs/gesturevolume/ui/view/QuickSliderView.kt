package com.newagedevs.gesturevolume.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat

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

    /**
     * Dresses the panel: how much of its colour survives, and whether it has a lit edge.
     *
     * Blur is not set here — it belongs to the window, not the view, and `OverlayController`
     * applies it when the window is added. This is only the part that is painted.
     */
    fun setPanelTheme(surface: Float, litEdge: Boolean) {
        surfaceAlpha = surface.coerceIn(0f, 1f)
        glassEnabled = litEdge
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

        sheenPaint?.shader = android.graphics.LinearGradient(
            0f, top, 0f, top + drawRect.height() * 0.5f,
            intArrayOf(0x2EFFFFFF.toInt(), 0x08FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.55f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        counterLightPaint?.shader = android.graphics.LinearGradient(
            0f, bottom - drawRect.height() * 0.2f, 0f, bottom,
            intArrayOf(0x00FFFFFF, 0x1AFFFFFF),
            null,
            android.graphics.Shader.TileMode.CLAMP,
        )
        edgePaint?.shader = android.graphics.LinearGradient(
            0f, top, 0f, bottom,
            intArrayOf(0xA6FFFFFF.toInt(), 0x3DFFFFFF, 0x14FFFFFF, 0x4DFFFFFF),
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

    fun setValue(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped == value) return
        value = clamped
        invalidate()
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
        expandedRect.set(0f, 0f, w.toFloat(), h.toFloat())
        // Sized from the window rather than the drawn rect, so the number does not grow as the
        // track does — text that scales during an animation reads as a zoom, not a reveal.
        textPaint.textSize = (w * 0.34f).coerceIn(10f * density, 20f * density)
        // A collapsed rect the caller has not set yet would leave the first frame at the window's
        // full size, which is the pop this class exists to remove.
        if (collapsedRect.isEmpty) collapsedRect.set(expandedRect)
        rebuildPath()
    }

    private fun rebuildPath() {
        if (expandedRect.isEmpty) return

        drawRect.set(
            lerp(collapsedRect.left, expandedRect.left),
            lerp(collapsedRect.top, expandedRect.top),
            lerp(collapsedRect.right, expandedRect.right),
            lerp(collapsedRect.bottom, expandedRect.bottom)
        )
        // Never more than half the shorter side, or the corners overlap and the round-rect
        // degenerates into a shape the caller did not ask for. Clamped against the rect being
        // drawn rather than the window, because the collapsed rect is the smaller of the two and
        // is where the clamp actually bites.
        val limit = minOf(drawRect.width(), drawRect.height()) / 2f
        // Each corner travels from its own starting radius to the panel's single one, so a bar
        // that is square on one side rounds off as it grows rather than snapping round on frame
        // one. `addRoundRect` takes them as x/y pairs, clockwise from the top left.
        for (corner in 0..3) {
            val r = lerp(collapsedCornersPx[corner], expandedCornersPx[corner]).coerceIn(0f, limit)
            drawRadii[corner * 2] = r
            drawRadii[corner * 2 + 1] = r
        }

        drawPath.reset()
        if (drawRect.isEmpty) return
        drawPath.addRoundRect(drawRect, drawRadii, Path.Direction.CW)
        rebuildGlass()
    }

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * expansion

    override fun onDraw(canvas: Canvas) {
        if (drawRect.isEmpty) return

        // Everything is inside the pill. Clipping once here is what lets the fill be a plain
        // rectangle and still come out with rounded ends.
        canvas.save()
        canvas.clipPath(drawPath)

        canvas.drawRect(drawRect, trackPaint)

        // Under the contents: lighting on the surface, not over the number.
        sheenPaint?.let { canvas.drawRect(drawRect, it) }
        counterLightPaint?.let { canvas.drawRect(drawRect, it) }

        drawContent(canvas, overFill = false)

        if (fillVisible && contentAlpha > 0.01f) {
            val fillTop = drawRect.bottom - drawRect.height() * value
            // Pass two: the same content, clipped to the filled region, in the inverted colour.
            canvas.save()
            canvas.clipRect(drawRect.left, fillTop, drawRect.right, drawRect.bottom)
            fillPaint.alpha = (contentAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(drawRect.left, fillTop, drawRect.right, drawRect.bottom, fillPaint)
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
        }

        // The rim last and outside the fill pass, so the filled half of the track does not paint
        // over the edge. Still inside the clip, so it follows the corners as they open out.
        edgePaint?.let { canvas.drawPath(drawPath, it) }

        canvas.restore()
    }

    private fun drawContent(canvas: Canvas, overFill: Boolean) {
        if (contentAlpha <= 0.01f) return
        val ink = if (overFill) blendedTrackColor else fillColor
        val alpha = (contentAlpha * 255f).toInt().coerceIn(0, 255)

        if (showValue) {
            textPaint.color = ink
            textPaint.alpha = alpha
            val label = "${(value * 100f).toInt()}"
            // Baseline placed by the font's own metrics rather than a guessed offset, so the
            // number sits the same distance from the top on every device font scale.
            val y = drawRect.top + 14f * density - textPaint.fontMetrics.ascent
            canvas.drawText(label, drawRect.centerX(), y, textPaint)
        }

        icon?.let { drawable ->
            val size = (drawRect.width() * 0.46f).coerceIn(12f * density, 26f * density)
            val cx = drawRect.centerX()
            val cy = drawRect.bottom - 16f * density - size / 2f
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
