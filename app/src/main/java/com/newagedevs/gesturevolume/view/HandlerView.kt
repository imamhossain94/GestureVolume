package com.newagedevs.gesturevolume.view

import android.R.attr
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.*
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.abs


class HandlerView(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {

    companion object {
        private const val TOUCH_MOVE_FACTOR: Long = 20
        private const val TOUCH_TIME_FACTOR: Long = 300
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 300
        private const val HANDLER_INSET: Int = 30
    }

    private var positionLocked:Boolean = false
    private var vibrateOnClick:Boolean = false

    private var lastY = 0f
    private var actionDownPoint = PointF(0f, 0f)
    private var touchDownTime = 0L
    private var lastClickTime = 0L

    init {
        updateViewProperties()
        compoundDrawablePadding = 15
    }

    interface HandlerPositionChangeListener {
        fun onVertical(rawY: Float)
        fun onVertical(rawY: Int)
    }

    private var handlerPositionChangeListener: HandlerPositionChangeListener? = null

    fun setHandlerPositionChangeListener(listener: HandlerPositionChangeListener) {
        handlerPositionChangeListener = listener
    }

    private fun createViewDrawable(
        color: Int,
        cornerRadiusTopLeft: Float,
        cornerRadiusTopRight: Float,
        cornerRadiusBottomLeft: Float,
        cornerRadiusBottomRight: Float,
        strokeColor: Int,
        strokeWidth: Float
    ): GradientDrawable {
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.cornerRadii = floatArrayOf(
            cornerRadiusTopLeft,
            cornerRadiusTopLeft,
            cornerRadiusTopRight,
            cornerRadiusTopRight,
            cornerRadiusBottomRight,
            cornerRadiusBottomRight,
            cornerRadiusBottomLeft,
            cornerRadiusBottomLeft
        )
        shape.setColor(color)
        shape.setStroke(strokeWidth.toInt(), strokeColor)
        return shape
    }

    private fun updateViewProperties(
        width: Int = 50,
        height: Int = 300,
        gravity: Int = Gravity.END,
        color: Int = Color.BLUE,
        cornerRadiusTopLeft: Float = 20f,
        cornerRadiusTopRight: Float = 0f,
        cornerRadiusBottomLeft: Float = 20f,
        cornerRadiusBottomRight: Float = 0f,
        strokeColor: Int = Color.GRAY,
        strokeWidth: Float = 1f
    ) {
        val layoutParams = FrameLayout.LayoutParams(width, height)
        layoutParams.gravity = gravity
        this.layoutParams = layoutParams

        background = InsetDrawable(createViewDrawable(
            color,
            cornerRadiusTopLeft,
            cornerRadiusTopRight,
            cornerRadiusBottomLeft,
            cornerRadiusBottomRight,
            strokeColor,
            strokeWidth
        ), HANDLER_INSET, 0, 0, 0)

    }

    fun setViewWidth(width: Int) {
        val layoutParams = this.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = width
        layoutParams.height = height
        requestLayout()
    }

    fun setViewSize(height: Int) {
        val layoutParams = this.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = width
        layoutParams.height = height
        requestLayout()
    }

    fun setViewDimension(width: Int, height: Int) {
        val layoutParams = this.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = width
        layoutParams.height = height
        requestLayout()
    }

    fun setViewGravity(gravity: Int) {
        val layoutParams = this.layoutParams as FrameLayout.LayoutParams
        layoutParams.gravity = gravity
        requestLayout()

        when (gravity) {
            Gravity.START -> {
                val drawable = setCornerRadius(cornerRadiusBottomRight = 20f, cornerRadiusTopRight = 20f)
                background = InsetDrawable(drawable, 0, 0, HANDLER_INSET, 0)
            }
            Gravity.END -> {
                val drawable = setCornerRadius(cornerRadiusBottomLeft = 20f, cornerRadiusTopLeft = 20f)
                background = InsetDrawable(drawable, HANDLER_INSET, 0, 0, 0)
            }
        }

    }

//    fun setViewColor(color: Int, alpha: Int = 255) {
//        val adjustedColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
//        val insetDrawable = background as InsetDrawable
//        val gradientDrawable = insetDrawable.drawable as GradientDrawable
//        gradientDrawable.setColor(adjustedColor)
//    }

    fun setViewColor(color: Int, alpha: Int = 255) {
        val adjustedAlpha = when {
            alpha > 0 -> alpha + 25
            else -> 0
        }

        val adjustedColor = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        val strokeAlpha = Color.argb(adjustedAlpha, Color.red(color), Color.green(color), Color.blue(color))

        val insetDrawable = background as InsetDrawable
        val gradientDrawable = insetDrawable.drawable as GradientDrawable
        gradientDrawable.setColor(adjustedColor)
        gradientDrawable.setStroke(1, strokeAlpha)
    }


    fun setCornerRadius(
        cornerRadiusTopLeft: Float = 0f,
        cornerRadiusTopRight: Float = 0f,
        cornerRadiusBottomLeft: Float = 0f,
        cornerRadiusBottomRight: Float = 0f
    ) : GradientDrawable{
        val insetDrawable = background as InsetDrawable
        val gradientDrawable = insetDrawable.drawable as GradientDrawable
        gradientDrawable.cornerRadii = floatArrayOf(
            cornerRadiusTopLeft,
            cornerRadiusTopLeft,
            cornerRadiusTopRight,
            cornerRadiusTopRight,
            cornerRadiusBottomRight,
            cornerRadiusBottomRight,
            cornerRadiusBottomLeft,
            cornerRadiusBottomLeft
        )
        return gradientDrawable
    }

    fun setStrokeColor(strokeColor: Int) {
        (background.mutate() as GradientDrawable).setStroke(attr.width, strokeColor)
    }

    fun setStrokeWidth(strokeWidth: Int) {
        (background.mutate() as GradientDrawable).setStroke(strokeWidth, attr.color)
    }

    fun setTranslationYPosition(translationY: Float) {
        this.translationY = translationY
    }

    fun getTranslationYPosition(): Float {
        return translationY
    }

    fun setHandlerPositionIsLocked(isLocked: Boolean) {
        positionLocked = isLocked
    }

    fun getHandlerPositionIsLocked(): Boolean {
        return positionLocked
    }

    fun setVibrateOnClick(isLocked: Boolean) {
        vibrateOnClick = isLocked
    }

    fun getVibrateOnClick(): Boolean {
        return vibrateOnClick
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
                val isTouchDuration = now() - touchDownTime < TOUCH_TIME_FACTOR
                val isTouchLength = abs(event.x - actionDownPoint.x) + abs(event.y - actionDownPoint.y) < TOUCH_MOVE_FACTOR
                val shouldClick = isTouchLength && isTouchDuration

                if (shouldClick) {
                    if (now() - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                        // Double click
                        lastClickTime = 0
                    } else {
                        lastClickTime = now()

                        if(vibrateOnClick) {
                            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                vibratorManager.defaultVibrator
                            } else {
                                @Suppress("DEPRECATION")
                                context.getSystemService(AppCompatActivity.VIBRATOR_SERVICE) as Vibrator
                            }
                            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        }

                        performClick()
                    }
                }
            }
        }
        return true
    }

    private fun now(): Long {
        return SystemClock.elapsedRealtime()
    }
}

