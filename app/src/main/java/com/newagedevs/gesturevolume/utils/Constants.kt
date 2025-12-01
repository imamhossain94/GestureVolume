package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.helper.BannerAd

class Constants {

    companion object {

        val feedbackEmails = arrayOf("imamagun94@gmail.com")
        const val PRIVACY_POLICY_URL = "https://newagedevs-privacy-policy.blogspot.com/2023/05/gesture-volume.html"
        const val SOURCE_CODE_URL = "https://github.com/imamhossain94/GestureVolume"
        const val PUBLISHER_URL = "https://play.google.com/store/apps/developer?id=NewAgeDevs"
        const val APP_STORE_ID = "market://details?id=com.newagedevs.gesturevolume"

        val inHouseAdList = listOf(
            BannerAd(
                "https://play-lh.googleusercontent.com/E5emNUh511EaVyznSYbV3UoIvhpSZSAGBrnwHgLIImG2sO-b8TSJ6tel0i8E9C-06A=w240-h480-rw",
                "Story Video Downloader",
                "Download Facebook public and private videos including watch, reels, and stories.",
                "https://play.google.com/store/apps/details?id=com.newagedevs.story_video_downloader"
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
            )
        )
    }

}