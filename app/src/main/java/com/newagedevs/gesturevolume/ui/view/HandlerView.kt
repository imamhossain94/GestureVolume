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
import kotlin.math.abs
import com.newagedevs.gesturevolume.R

class HandlerView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    companion object {
        private const val DEFAULT_INSET: Float = 0f
    }

    // ========== Touch Configuration (device-calibrated) ==========
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val doubleClickTimeDelta: Long = 300L
    private val pressAnimDuration: Long = 100L
    private val pressAlpha: Float = 0.7f

    // Gesture state machine
    private enum class GestureState { IDLE, PRESSED, DRAGGING }
    private var gestureState = GestureState.IDLE

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

    // Touch tracking
    private var lastRawY = 0f
    private var actionDownPoint = PointF(0f, 0f)
    private var touchDownTime = 0L
    private var lastClickTime = 0L

    // Delayed single-click handler
    private val clickHandler = Handler(Looper.getMainLooper())
    private var pendingSingleClick: Runnable? = null

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

    interface HandlerPositionChangeListener {
        fun onVertical(rawY: Float)
        fun onVertical(rawY: Int)
    }

    interface HandlerClickListener {
        fun onSingleClick()
        fun onDoubleClick()
    }

    private var handlerPositionChangeListener: HandlerPositionChangeListener? = null
    private var handlerClickListener: HandlerClickListener? = null

    fun setHandlerPositionChangeListener(listener: HandlerPositionChangeListener) {
        handlerPositionChangeListener = listener
    }

    fun setHandlerClickListener(listener: HandlerClickListener) {
        handlerClickListener = listener
    }

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

    // ========== Touch Handling (State Machine) ==========

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                gestureState = GestureState.PRESSED
                lastRawY = event.rawY
                actionDownPoint = PointF(event.x, event.y)
                touchDownTime = now()

                // Visual press feedback
                animate().alpha(pressAlpha).setDuration(pressAnimDuration).start()

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - actionDownPoint.x)
                val dy = abs(event.y - actionDownPoint.y)

                when (gestureState) {
                    GestureState.PRESSED -> {
                        // Transition to DRAGGING if finger moved beyond touch slop
                        if (dx > touchSlop || dy > touchSlop) {
                            gestureState = GestureState.DRAGGING
                            // Release press animation since we're now dragging
                            animate().alpha(1f).setDuration(pressAnimDuration).start()
                        }
                    }
                    GestureState.DRAGGING -> {
                        if (!positionLocked) {
                            val deltaY = event.rawY - lastRawY
                            translationY += deltaY
                            lastRawY = event.rawY
                            handlerPositionChangeListener?.onVertical(translationY)
                        } else {
                            val deltaY = event.rawY - lastRawY
                            lastRawY = event.rawY
                            handlerPositionChangeListener?.onVertical(deltaY.toInt())
                        }
                    }
                    else -> { /* IDLE — shouldn't happen during MOVE */ }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Release press animation
                animate().alpha(1f).setDuration(pressAnimDuration).start()

                if (gestureState == GestureState.PRESSED) {
                    // Finger didn't move beyond touch slop — this is a click
                    val isTouchDuration = now() - touchDownTime < 500L
                    if (isTouchDuration) {
                        handleClick()
                    }
                }

                gestureState = GestureState.IDLE
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // Clean up: restore visual state, cancel pending clicks
                animate().alpha(1f).setDuration(pressAnimDuration).start()
                cancelPendingSingleClick()
                gestureState = GestureState.IDLE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleClick() {
        val currentTime = now()

        if (currentTime - lastClickTime < doubleClickTimeDelta) {
            // Double click detected — cancel pending single click
            cancelPendingSingleClick()
            lastClickTime = 0L

            if (vibrateOnClick) {
                triggerHapticFeedback()
            }

            handlerClickListener?.onDoubleClick()
        } else {
            // Possible single click — defer to allow double-click window
            lastClickTime = currentTime

            pendingSingleClick = Runnable {
                if (vibrateOnClick) {
                    triggerHapticFeedback()
                }

                performClick()
                handlerClickListener?.onSingleClick()
                pendingSingleClick = null
            }

            clickHandler.postDelayed(pendingSingleClick!!, doubleClickTimeDelta)
        }
    }

    private fun cancelPendingSingleClick() {
        pendingSingleClick?.let {
            clickHandler.removeCallbacks(it)
            pendingSingleClick = null
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun triggerHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            @Suppress("DEPRECATION")
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    // ========== Utility ==========

    fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelPendingSingleClick()
        animate().cancel()
    }
}