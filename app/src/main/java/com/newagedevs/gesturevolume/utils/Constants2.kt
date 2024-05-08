package com.newagedevs.gesturevolume.utils

import android.graphics.Color
import com.newagedevs.gesturevolume.model.UnlockCondition

class Constants2 {

    companion object {

        val feedbackMail = arrayOf("imamagun94@gmail.com")
        val contactMail = arrayOf("imamagun94@gmail.com")
        const val privacyPolicyUrl = "https://newagedevs-privacy-policy.blogspot.com/2023/12/music-overlay.html"
        const val sourceCodeUrl = "https://github.com/imamhossain94/BlackScreenMusicOverlay"
        const val publisherName = "https://play.google.com/store/apps/developer?id=NewAgeDevs"
        const val appStoreId = "market://details?id=com.newagedevs.musicoverlay"

        const val showAdsOnEveryClick: Int = 5
        const val showAdsOnEveryOpen: Int = 3

        // Default values for clock and overlay
        const val defaultClockColor: Int = Color.WHITE
        const val defaultTextClockTransparency: Float = 100f
        const val defaultFrameClockTransparency: Int = 255
        const val defaultOverlayColor: Int = Color.BLACK
        const val defaultOverlayTransparency: Int = 255

        // Default unlock condition
        var defaultUnlockCondition: String = UnlockCondition.TAP.displayText

        // Default values for handler
        const val defaultHandlerPosition: String = "Right"
        const val defaultHandlerColor: Int = Color.WHITE
        const val defaultHandlerTransparency: Int = 255
        const val defaultHandlerSize: Int = 100
        const val defaultHandlerWidth: Int = 0

        val handlerWidthList = arrayOf(40, 50, 60)

    }

}