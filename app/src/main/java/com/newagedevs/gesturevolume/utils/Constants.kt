package com.newagedevs.gesturevolume.utils

import android.app.Activity
import android.content.Context
import android.graphics.Color
import com.maxkeppeler.sheets.option.Option
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.toast

class Constants {

    companion object {

        val feedbackMail = arrayOf("imamagun94@gmail.com")
        val contactMail = arrayOf("imamagun94@gmail.com")
        const val privacyPolicyUrl = "https://newagedevs-privacy-policy.blogspot.com/2023/05/gesture-volume.html"
        const val sourceCodeUrl = "https://github.com/imamhossain94/GestureVolume"
        const val publisherName = "https://play.google.com/store/apps/developer?id=NewAgeDevs"
        const val appStoreId = "https://play.google.com/store/apps/details?id=com.newagedevs.gesturevolume"

        val gravityDrawables = listOf(R.drawable.ic_align_left, R.drawable.ic_align_right)
        val gravityTitles = listOf("Left", "Right")

        val sizeDrawables = listOf(R.drawable.ic_small, R.drawable.ic_medium, R.drawable.ic_large)
        val sizeTitles = listOf("Small", "Medium", "Large")

        val widthDrawables = listOf(R.drawable.ic_small, R.drawable.ic_regular, R.drawable.ic_bold)
        val widthTitles = listOf("Slim", "Regular", "Bold")

        val tapActionDrawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_increase, R.drawable.ic_mute, R.drawable.ic_music_ui, R.drawable.ic_lock, R.drawable.ic_visibility_hide, R.drawable.ic_app_open)
        val tapActionTitles = listOf("None", "Open volume UI", "Mute", "Mute or Unmute", "Active Music Overlay", "Lock", "Hide Handler", "Open App")

        val swipeUpDrawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_increase, R.drawable.ic_vol_plus)
        val swipeUpTitles = listOf("None", "Increase volume and show UI", "Increase volume")

        val swipeDownDrawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_decrease, R.drawable.ic_vol_minus)
        val swipeDownTitles = listOf("None", "Decrease volume and show UI", "Decrease volume")

        // Default values for handler
        val defaultHandlerColor: Int = Color.parseColor("#80808080")

        fun handlerSizeValue(title: String?): Int {
            return when(title) {
                sizeTitles.first() -> 250
                sizeTitles[1] -> 350
                sizeTitles.last() -> 450
                else -> 250
            }
        }

        fun handlerWidthValue(title: String?): Int {
            return when(title) {
                widthTitles.first() -> 40
                widthTitles[1] -> 50
                widthTitles.last() -> 60
                else -> 40
            }
        }

        fun tapActionLists(context: Context): MutableList<Option> {
            val options = mutableListOf<Option>()
            for (i in tapActionDrawables.indices) {
                val drawable = tapActionDrawables[i]
                val title = tapActionTitles[i]
                val action = { context.toast(title) }
                options.add(Option(drawable, title, "", action))
            }
            return options
        }

    }

}