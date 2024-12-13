package com.newagedevs.gesturevolume.utils

import android.content.Context
import android.graphics.Color
import com.maxkeppeler.sheets.option.Option
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.toast
import com.newagedevs.gesturevolume.inhouseads.BannerAd

class Constants {

    companion object {

        val feedbackMail = arrayOf("imamagun94@gmail.com")
//        val contactMail = arrayOf("imamagun94@gmail.com")
        const val privacyPolicyUrl = "https://newagedevs-privacy-policy.blogspot.com/2023/05/gesture-volume.html"
        const val sourceCodeUrl = "https://github.com/imamhossain94/GestureVolume"
        const val publisherName = "https://play.google.com/store/apps/developer?id=NewAgeDevs"
        const val appStoreId = "market://details?id=com.newagedevs.gesturevolume"

        val gravityDrawables = listOf(R.drawable.ic_align_left, R.drawable.ic_align_right)
        val gravityTitles = listOf("Left", "Right")

        val sizeDrawables = listOf(R.drawable.ic_small, R.drawable.ic_medium, R.drawable.ic_large)
        val sizeTitles = listOf("Small", "Medium", "Large")

        val widthDrawables = listOf(R.drawable.ic_small, R.drawable.ic_regular, R.drawable.ic_bold)
        val widthTitles = listOf("Slim", "Regular", "Bold")

        val tapActionDrawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_increase, R.drawable.ic_mute, R.drawable.ic_mute, R.drawable.ic_music_ui, R.drawable.ic_lock, R.drawable.ic_visibility_hide, R.drawable.ic_app_open)
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

        val inHouseAds = listOf(
            BannerAd(
                "https://play-lh.googleusercontent.com/E5emNUh511EaVyznSYbV3UoIvhpSZSAGBrnwHgLIImG2sO-b8TSJ6tel0i8E9C-06A=w240-h480-rw",
                "Story Video Downloader",
                "Download Facebook public and private videos including watch, reels, and stories.",
                "https://play.google.com/store/apps/details?id=com.newagedevs.story_video_downloader"
            ),
            BannerAd(
                "https://play-lh.googleusercontent.com/23iXWyFSHQT6DUN2QdkBFxEGZPUpNJfvICGN_FbU3elbhUAe-4ClKE92cE_sTiz0Hoc=w240-h480-rw",
                "5G LTE Enabled",
                "5G LTE Enabler will help you to set the preferred network type to 5G/4G/3G etc.",
                "https://play.google.com/store/apps/details?id=com.newagedevs.enable_network"
            ),
            BannerAd(
                "https://play-lh.googleusercontent.com/7pVgsRrLl5jbAhJsifnZTQyRKvKglO4no9ROFIiJfZavQpFhzCsfXgQvMZoT16Csq_s=w240-h480-rw",
                "Reels Video Downloader",
                "Download Instagram reels, stories, and public or private videos while scrolling",
                "https://play.google.com/store/apps/details?id=com.newagedevs.reels_video_downloader"
            ),
            BannerAd(
                "https://play-lh.googleusercontent.com/JF9gruedbqwmYGlJZevwnlg1uD-dt2oFoiMagnf7HLcAMlbuE9ifMsxxLqNHg85VlQ=w240-h480-rw",
                "Fb Video Downloader",
                "Download Facebook public and private videos including watch, reels, and stories.",
                "https://play.google.com/store/apps/details?id=com.newagedevs.facebook_video_downloader"
            ),
            BannerAd(
                "https://play-lh.googleusercontent.com/gbSKulARlfe1RPHzR9D0yzR1zaluks5-GpoCNNk6z4RaTrfRv3G9gYQmMMptlF789Js=w240-h480-rw",
                "Temp Mail",
                "Temporary email - create and receive email in only 10 seconds!",
                "https://play.google.com/store/apps/details?id=com.newagedevs.temp_mail"
            ),
            BannerAd(
                "https://play-lh.googleusercontent.com/DUF7uUl2C48zt3b6x9wXPVZRZpPV-HiT9ZHObdhLW9KiPLen62cK51eDoDBlOdXeeXo=w240-h480-rw",
                "Shortly - URL Shortener",
                "Shorten or Expand links that are too long or already shorten using 9 providers.",
                "https://play.google.com/store/apps/details?id=com.newagedevs.url_shortener"
            )
        )


    }

}