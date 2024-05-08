package com.newagedevs.gesturevolume.binding

import android.annotation.SuppressLint
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.BindingAdapter
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.px
import com.skydoves.whatif.whatIfNotNullAs
import com.skydoves.whatif.whatIfNotNullOrEmpty
import java.util.Locale


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

    @SuppressLint("SetTextI18n")
    @JvmStatic
    @BindingAdapter(value = ["app:colorCode"], requireAll = false)
    fun colorCode(view: TextView, color: Int?) {
        if(color != null) {
            view.text = "#${Integer.toHexString(color).uppercase(Locale.getDefault())}"
        }else{
            view.text = "#47000000"
        }
    }

}
