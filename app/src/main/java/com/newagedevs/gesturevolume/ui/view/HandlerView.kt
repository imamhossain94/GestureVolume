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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs

class HandlerView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    companion object {
        private const val DEFAULT_INSET: Float = 0f
    }

    private val touchMoveFactor: Long = (20 * resources.displayMetrics.density).toLong()
    private val touchTimeFactor: Long = 300L
    private val doubleClickTimeDelta: Long = 300L

    // View properties with default values
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
    private var centerIconColor: Int = Color.WHITE // NEW: Icon color property
    private var centerIconVisible: Boolean = true
    private val centerIconView: ImageView

    // Behavior properties
    private var positionLocked: Boolean = false
    private var vibrateOnClick: Boolean = false

    // Touch tracking
    private var lastY = 0f
    private var actionDownPoint = PointF(0f, 0f)
    private var touchDownTime = 0L
    private var lastClickTime = 0L

    init {
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

    // Data class for option items
    data class OptionItem(
        val id: Int,
        val icon: Drawable? = null,
        val title: String,
        val subtitle: String? = null
    )

    // Listeners
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

    // NEW: Set all corner radii at once
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

    // UPDATED: Added color parameter
    fun setCenterIcon(drawable: Drawable?, sizeDp: Float = 24f, color: Int = centerIconColor) {
        centerIcon = drawable
        centerIconSize = sizeDp
        centerIconColor = color
        updateCenterIcon()
    }

    // UPDATED: Added color parameter
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

    // NEW: Set icon color
    fun setCenterIconColor(color: Int) {
        centerIconColor = color
        updateCenterIcon()
    }

    // UPDATED: Apply color tint to icon
    private fun updateCenterIcon() {
        if (centerIconVisible && centerIcon != null) {
            centerIconView.visibility = VISIBLE

            // Apply color tint to the icon
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.rawY
                actionDownPoint = PointF(event.x, event.y)
                touchDownTime = now()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!positionLocked) {
                    val deltaY = event.rawY - lastY
                    translationY += deltaY
                    lastY = event.rawY
                    handlerPositionChangeListener?.onVertical(translationY)
                } else {
                    val deltaY = event.rawY - lastY
                    lastY = event.rawY
                    handlerPositionChangeListener?.onVertical(deltaY.toInt())
                }
            }
            MotionEvent.ACTION_UP -> {
                val isTouchDuration = now() - touchDownTime < touchTimeFactor
                val isTouchLength = abs(event.x - actionDownPoint.x) + abs(event.y - actionDownPoint.y) < touchMoveFactor
                val shouldClick = isTouchLength && isTouchDuration

                if (shouldClick) {
                    if (now() - lastClickTime < doubleClickTimeDelta) {
                        // Double click
                        handlerClickListener?.onDoubleClick()
                        lastClickTime = 0
                    } else {
                        lastClickTime = now()

                        if (vibrateOnClick) {
                            performHapticFeedback()
                        }

                        performClick()
                        handlerClickListener?.onSingleClick()

                    }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun performHapticFeedback() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

}