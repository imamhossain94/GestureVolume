package com.newagedevs.gesturevolume.extensions

import android.content.Context
import android.content.res.Resources.getSystem
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

val Int.dp: Int get() = (this / getSystem().displayMetrics.density).toInt()

val Int.px: Int get() = (this * getSystem().displayMetrics.density).toInt()

fun getDisplayMetrics(context: Context): DisplayMetrics {
    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    val defaultDisplayContext = context.createDisplayContext(defaultDisplay)
    return defaultDisplayContext.resources.displayMetrics
}

