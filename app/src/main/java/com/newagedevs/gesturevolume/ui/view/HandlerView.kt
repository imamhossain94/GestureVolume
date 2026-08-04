package com.newagedevs.gesturevolume.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.newagedevs.gesturevolume.R

class HandlerView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    companion object {
        private const val DEFAULT_INSET: Float = 0f
    }

    // ========== Touch Configuration (device-calibrated) ==========
    private val pressAnimDuration: Long = 100L
    private val dragCueAlpha: Float = 0.65f

    // ========== View properties with default values ==========
    private var viewWidth: Float = 50f
    private var viewHeight: Float = 300f
    private var viewGravityPosition: Int = Gravity.END
    private var backgroundColor: Int = Color.BLUE
    private var backgroundAlpha: Int = 255

    // Corner radius properties
    private var cornerRadiusTopLeft: Float = 20f
    private var cornerRadiusTopRight: Float = 0f
    private var cornerRadiusBottomLeft: Float = 20f
    private var cornerRadiusBottomRight: Float = 0f

    // Stroke properties
    private var strokeColor: Int = Color.GRAY
    private var strokeWidth: Float = 1f
    private var strokeAlpha: Int = 255

    // Inset properties
    private var insetLeft: Float = DEFAULT_INSET
    private var insetTop: Float = 0f
    private var insetRight: Float = 0f
    private var insetBottom: Float = 0f

    // Icon properties
    private var centerIcon: Drawable? = null
    private var centerIconSize: Float = 24f
    private var centerIconColor: Int = Color.WHITE
    private var centerIconVisible: Boolean = true
    private val centerIconView: ImageView

    // Behavior properties
    private var positionLocked: Boolean = false
    private var vibrateOnClick: Boolean = false

    /**
     * The gesture engine. When set, it receives every touch. Callers that only need a static
     * rendering of the bar (the small appearance preview) simply leave it null.
     */
    private var gestureDetector: HandlerGestureDetector? = null

    /** Saved so the drag cue can restore the icon it temporarily replaced. */
    private var iconBeforeDragCue: Drawable? = null
    private var iconVisibleBeforeDragCue: Boolean = true
    private var dragCueActive: Boolean = false

    init {
        // Accessibility defaults
        contentDescription = context.getString(R.string.volume_gesture_handler)
        isFocusable = true

        // Create and add center icon ImageView
        centerIconView = ImageView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        addView(centerIconView)

        updateViewAppearance()
    }

    // ========== Data class and Listeners ==========

    data class OptionItem(
        val id: Int,
        val icon: Drawable? = null,
        val title: String,
        val subtitle: String? = null
    )

    // Tap and drag handling now lives in HandlerGestureDetector.Host, installed via
    // setGestureDetector(). This view is responsible only for how the bar looks.

    // ========== Dimension Setters ==========

    fun setViewWidthDp(widthDp: Float) {
        viewWidth = widthDp
        updateLayoutParams()
    }

    fun setViewHeightDp(heightDp: Float) {
        viewHeight = heightDp
        updateLayoutParams()
    }

    fun setViewDimensionsDp(widthDp: Float, heightDp: Float) {
        viewWidth = widthDp
        viewHeight = heightDp
        updateLayoutParams()
    }

    // ========== Corner Radius Setters ==========

    fun setCornerRadiusDp(radius: Float) {
        cornerRadiusTopLeft = radius
        cornerRadiusTopRight = radius
        cornerRadiusBottomLeft = radius
        cornerRadiusBottomRight = radius
        updateViewAppearance()
    }

    fun setCornerRadiiDp(
        topLeft: Float = cornerRadiusTopLeft,
        topRight: Float = cornerRadiusTopRight,
        bottomLeft: Float = cornerRadiusBottomLeft,
        bottomRight: Float = cornerRadiusBottomRight
    ) {
        cornerRadiusTopLeft = topLeft
        cornerRadiusTopRight = topRight
        cornerRadiusBottomLeft = bottomLeft
        cornerRadiusBottomRight = bottomRight
        updateViewAppearance()
    }

    fun setAllCornerRadiiDp(radius: Float) {
        cornerRadiusTopLeft = radius
        cornerRadiusTopRight = radius
        cornerRadiusBottomLeft = radius
        cornerRadiusBottomRight = radius
        updateViewAppearance()
    }

    // ========== Background Color Setters ==========

    fun setViewBackgroundColor(color: Int, alpha: Int = 255) {
        backgroundColor = color
        backgroundAlpha = alpha.coerceIn(0, 255)
        updateViewAppearance()
    }

    fun setViewBackgroundAlpha(alpha: Int) {
        backgroundAlpha = alpha.coerceIn(0, 255)
        updateViewAppearance()
    }

    // ========== Stroke Setters ==========

    fun setStrokeProperties(color: Int, widthDp: Float = strokeWidth, alpha: Int = 255) {
        strokeColor = color
        strokeWidth = widthDp
        strokeAlpha = alpha.coerceIn(0, 255)
        updateViewAppearance()
    }

    fun setStrokeColorWithAlpha(color: Int, alpha: Int) {
        strokeColor = color
        strokeAlpha = alpha.coerceIn(0, 255)
        updateViewAppearance()
    }

    fun setStrokeWidthDp(widthDp: Float) {
        strokeWidth = widthDp
        updateViewAppearance()
    }

    // ========== Icon Setters ==========

    fun setCenterIcon(drawable: Drawable?, sizeDp: Float = 24f, color: Int = centerIconColor) {
        centerIcon = drawable
        centerIconSize = sizeDp
        centerIconColor = color
        updateCenterIcon()
    }

    fun setCenterIcon(drawableRes: Int, sizeDp: Float = 24f, color: Int = centerIconColor) {
        centerIcon = ContextCompat.getDrawable(context, drawableRes)
        centerIconSize = sizeDp
        centerIconColor = color
        updateCenterIcon()
    }

    fun removeCenterIcon() {
        centerIcon = null
        updateCenterIcon()
    }

    fun setCenterIconVisible(visible: Boolean) {
        centerIconVisible = visible
        updateCenterIcon()
    }

    fun isCenterIconVisible(): Boolean = centerIconVisible

    fun setCenterIconColor(color: Int) {
        centerIconColor = color
        updateCenterIcon()
    }

    private fun updateCenterIcon() {
        if (centerIconVisible && centerIcon != null) {
            centerIconView.visibility = VISIBLE

            val tintedDrawable = centerIcon?.mutate()?.let { drawable ->
                DrawableCompat.wrap(drawable).apply {
                    DrawableCompat.setTint(this, centerIconColor)
                }
            }

            centerIconView.setImageDrawable(tintedDrawable)

            val sizePx = dpToPx(centerIconSize).toInt()
            centerIconView.layoutParams = (centerIconView.layoutParams as LayoutParams).apply {
                width = sizePx
                height = sizePx
                gravity = Gravity.CENTER
            }
        } else {
            centerIconView.visibility = GONE
        }
    }

    // ========== Gravity Setter ==========

    fun setViewGravity(gravity: Int) {
        viewGravityPosition = gravity
        updateLayoutParams()
        updateInsetsForGravity(gravity)
    }

    private fun updateInsetsForGravity(gravity: Int) {
        when (gravity) {
            Gravity.START -> {
                insetLeft = 0f
                insetRight = DEFAULT_INSET
            }
            Gravity.END -> {
                insetLeft = DEFAULT_INSET
                insetRight = 0f
            }
        }
        updateViewAppearance()
    }

    // ========== Behavior Setters ==========

    fun setHandlerPositionLocked(locked: Boolean) {
        positionLocked = locked
    }

    fun getHandlerPositionLocked(): Boolean = positionLocked

    fun setVibrateOnClick(vibrate: Boolean) {
        vibrateOnClick = vibrate
    }

    fun getVibrateOnClick(): Boolean = vibrateOnClick

    fun setTranslationYPosition(translationY: Float) {
        this.translationY = translationY
    }

    fun getTranslationYPosition(): Float = translationY

    // ========== Internal Methods ==========

    private fun updateLayoutParams() {
        val layoutParams = LayoutParams(
            dpToPx(viewWidth).toInt(),
            dpToPx(viewHeight).toInt()
        ).apply {
            gravity = viewGravityPosition
        }
        this.layoutParams = layoutParams
        requestLayout()
    }

    private fun updateViewAppearance() {
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            this.cornerRadii = floatArrayOf(
                dpToPx(cornerRadiusTopLeft), dpToPx(cornerRadiusTopLeft),
                dpToPx(cornerRadiusTopRight), dpToPx(cornerRadiusTopRight),
                dpToPx(cornerRadiusBottomRight), dpToPx(cornerRadiusBottomRight),
                dpToPx(cornerRadiusBottomLeft), dpToPx(cornerRadiusBottomLeft)
            )

            val bgColor = Color.argb(
                backgroundAlpha,
                Color.red(backgroundColor),
                Color.green(backgroundColor),
                Color.blue(backgroundColor)
            )
            setColor(bgColor)

            val stColor = Color.argb(
                strokeAlpha,
                Color.red(strokeColor),
                Color.green(strokeColor),
                Color.blue(strokeColor)
            )
            setStroke(dpToPx(strokeWidth).toInt(), stColor)
        }

        background = InsetDrawable(
            shape,
            dpToPx(insetLeft).toInt(),
            dpToPx(insetTop).toInt(),
            dpToPx(insetRight).toInt(),
            dpToPx(insetBottom).toInt()
        )

        updateCenterIcon()
    }

    // ========== Touch Handling ==========

    /**
     * Installs the gesture engine. Everything about tap / swipe / drag semantics lives in
     * [HandlerGestureDetector], so the live overlay and the in-app preview behave identically.
     */
    fun setGestureDetector(detector: HandlerGestureDetector?) {
        gestureDetector = detector
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val detector = gestureDetector ?: return super.onTouchEvent(event)
        return detector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun triggerHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            @Suppress("DEPRECATION")
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Shows that drag mode has armed.
     *
     * Deliberately expressed with alpha and an icon swap rather than a scale. In the live overlay
     * this view *is* the root of a window sized exactly to the bar, so anything drawn outside those
     * bounds — a scaled-up view, for instance — is clipped by the window surface and never seen.
     */
    fun setDragCue(active: Boolean) {
        if (active) {
            if (dragCueActive) return
            dragCueActive = true
            iconBeforeDragCue = centerIcon
            iconVisibleBeforeDragCue = centerIconVisible
            animate().alpha(dragCueAlpha).setDuration(pressAnimDuration).start()
            ContextCompat.getDrawable(context, R.drawable.ic_move)?.let {
                centerIcon = it
                // Force the icon on for the duration of the cue: a user who hides the icon would
                // otherwise get no visual confirmation that drag mode armed.
                centerIconVisible = true
                updateCenterIcon()
            }
        } else {
            if (!dragCueActive) return
            dragCueActive = false
            animate().alpha(1f).setDuration(pressAnimDuration).start()
            centerIcon = iconBeforeDragCue
            centerIconVisible = iconVisibleBeforeDragCue
            iconBeforeDragCue = null
            updateCenterIcon()
        }
    }

    /**
     * Asks the system not to treat this strip as the back-gesture zone.
     *
     * This is the fix for the bar being unusable when gesture navigation is on: the left and right
     * screen edges are exactly where the system watches for the back swipe, so without an exclusion
     * the bar's own gestures are stolen. The platform caps exclusions at 200dp per edge, which the
     * bar (100dp tall by default) sits comfortably within.
     *
     * Whether the window manager honours this for an overlay window is not guaranteed on every OEM
     * skin, so it is a best-effort improvement layered on top of the edge-offset setting, which
     * moves the bar clear of the gesture strip outright.
     */
    private fun refreshGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (width <= 0 || height <= 0) {
            systemGestureExclusionRects = emptyList()
            return
        }
        systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        refreshGestureExclusion()
    }

    // ========== Utility ==========

    fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gestureDetector?.cancel()
        animate().cancel()
    }
}