package com.newagedevs.gesturevolume.utils

import java.lang.ref.WeakReference

class SharedData {
    companion object {
        var shouldShowAppOpenAds = true
        var refActivity: WeakReference<() -> Unit>? = null

        fun onAdsComplete() {
            refActivity?.get()?.invoke()
        }
    }
}