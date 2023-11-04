package com.newagedevs.gesturevolume.binding

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.newagedevs.gesturevolume.extensions.px
import com.newagedevs.gesturevolume.utils.Constants
import com.skydoves.whatif.whatIfNotNullOrEmpty


object ViewBinding {

    @JvmStatic
    @BindingAdapter("toast")
    fun bindToast(view: FrameLayout, text: String?) {
        text.whatIfNotNullOrEmpty {
            Toast.makeText(view.context, it, Toast.LENGTH_SHORT).show()
        }
    }


    @JvmStatic
    @BindingAdapter(value = ["app:drawableStart"], requireAll = false)
    fun drawableStartCompat(view: TextView, resource: Int?) {

        val image = ResourcesCompat.getDrawable(view.resources, resource!!, null)
        image?.setBounds(0, 0, 24.px, 24.px)

        view.setCompoundDrawables(image, null, null, null)

    }

    @JvmStatic
    @BindingAdapter(value = ["app:textDrawableTint"], requireAll = false)
    fun drawableTint(view: TextView, color: Int?) {
        view.compoundDrawables[0].setTint(color!!)
    }

    @JvmStatic
    @BindingAdapter(value = ["app:imageColorFilter"], requireAll = false)
    fun colorFilter(view: ImageView, color: Int?) {

        val parsedColor = color?: try {
            Color.parseColor("#47000000")
        } catch (e: IllegalArgumentException) {
            Color.BLACK
        }

        DrawableCompat.setTint(
            DrawableCompat.wrap(view.background),
            parsedColor
        )
    }

    @JvmStatic
    @BindingAdapter(value = ["app:colorCode"], requireAll = false)
    fun colorCode(view: TextView, color: Int?) {
        if(color != null) {
            view.text = "#${Integer.toHexString(color).toUpperCase()}"
        }else{
            view.text = "#47000000"
        }
    }

    @JvmStatic
    @BindingAdapter(value = ["app:drawableBackground"], requireAll = false)
    fun drawableBackground(view: ImageView, resource: Drawable?) {
        view.setImageDrawable(resource)
    }


    @JvmStatic
    @BindingAdapter(value = ["app:resource", "app:tint"], requireAll = false)
    fun setImageResource(view: ImageView, resource: Int?, tint: Int?) {
        Glide.with(view.context)
            .load(resource)
            .into(view)
        view.setColorFilter(tint!!)
    }

    @JvmStatic
    @BindingAdapter("touchListener", requireAll = false)
    fun setTouchListener(self: View, vm: com.newagedevs.gesturevolume.view.ui.main.MainViewModel) {
        var initialY = 0f
        var initialTopMargin = 0

        self.setOnTouchListener { view, event ->
            val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams

            layoutParams?.let {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = event.rawY
                        initialTopMargin = it.topMargin
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - initialY).toInt()
                        it.topMargin = initialTopMargin + deltaY
                        view.layoutParams = it
                    }
                    MotionEvent.ACTION_UP -> {
                        vm.topMargin = it.topMargin.toFloat()
                    }
                }
            }

            view.performClick()
            true
        }
    }

    @JvmStatic
    @BindingAdapter("landscapeTouchListener", requireAll = false)
    fun setLandscapeTouchListener(self: View, vm: com.newagedevs.gesturevolume.view.ui.main.MainViewModel) {
        var initialX = 0f
        var initialTopMargin = 0

        self.setOnTouchListener { view, event ->
            val layoutParams = view.layoutParams as? ViewGroup.MarginLayoutParams

            layoutParams?.let {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = event.rawX
                        initialTopMargin = it.leftMargin
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialX).toInt()
                        it.leftMargin = initialTopMargin + deltaX
                        view.layoutParams = it
                    }
                    MotionEvent.ACTION_UP -> {
                        vm.leftMargin = it.leftMargin.toFloat()
                    }
                }
            }

            view.performClick()
            true
        }
    }

    @JvmStatic
    @BindingAdapter("setTopMargin")
    fun setMargin(view: LinearLayout, float: Float) {
        if (view.layoutParams is MarginLayoutParams) {
            val p = view.layoutParams as MarginLayoutParams
            p.topMargin = float.toInt()
            view.requestLayout()
        }
    }

    @JvmStatic
    @BindingAdapter("layoutHeight")
    fun setLayoutHeight(view: View, value: String) {

        val height: Int = when (value) {
            "Small" -> Constants.Small
            "Medium" -> Constants.Medium
            "Large" -> Constants.Large
            else -> Constants.Small
        }

        view.layoutParams = view.layoutParams.apply {
            this.height = height
        }
    }

    @JvmStatic
    @BindingAdapter("layoutWidth")
    fun setLayoutWidth(view: View, value: String) {

        val width: Int = when (value) {
            "Slim" -> Constants.Slim
            "Regular" -> Constants.Regular
            "Bold" -> Constants.Bold
            else -> Constants.Slim
        }

        view.layoutParams = view.layoutParams.apply {
            this.width = width
        }
    }

    @JvmStatic
    @BindingAdapter("setLeftMarginLand")
    fun setMarginLand(view: LinearLayout, float: Float) {
        if (view.layoutParams is MarginLayoutParams) {
            val p = view.layoutParams as MarginLayoutParams
            p.leftMargin = float.toInt()
            view.requestLayout()
        }
    }

    @JvmStatic
    @BindingAdapter("layoutHeightLand")
    fun setLayoutHeightLand(view: View, value: String) {

        val height: Int = when (value) {
            "Small" -> Constants.Small
            "Medium" -> Constants.Medium
            "Large" -> Constants.Large
            else -> Constants.Small
        }

        view.layoutParams = view.layoutParams.apply {
            this.width = height
        }
    }

    @JvmStatic
    @BindingAdapter("layoutWidthLand")
    fun setLayoutWidthLand(view: View, value: String) {

        val width: Int = when (value) {
            "Slim" -> Constants.Slim
            "Regular" -> Constants.Regular
            "Bold" -> Constants.Bold
            else -> Constants.Slim
        }

        view.layoutParams = view.layoutParams.apply {
            this.height = width
        }
    }

}
