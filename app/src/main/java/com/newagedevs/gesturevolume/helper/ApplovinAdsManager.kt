package com.newagedevs.gesturevolume.helper

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import java.util.concurrent.TimeUnit
import kotlin.math.pow

interface ApplovinAdsManagerListener {
    fun onUserRewarded(maxReward: MaxReward)
}

class ApplovinAdsManager(private val context: Activity, private val listener: ApplovinAdsManagerListener? = null) {
    private var retryAttempt = 0.0
    private var rewardRetryAttempt = 0.0

    private var interstitialAd: MaxInterstitialAd? = null
    private var rewardedAd: MaxRewardedAd? = null
    private var nativeAdLoader: MaxNativeAdLoader? = null
    private var nativeAd: MaxAd? = null

    private val bannerId = BuildConfig.banner_AdUnit
    private val interstitialId: String = BuildConfig.interstitial_AdUnit
    private val rewardId: String = BuildConfig.reward_AdUnit
    private val nativeAdUnitId: String = BuildConfig.native_AdUnit

    init {
        // Preload interstitial and rewarded ads
        preloadInterstitialAd()
        preloadRewardedAd()
    }

    // Function to create banner ads
    fun createBannerAd(view: LinearLayout) {
        val bannerAdsListener = BannerAdsListener(view)
        val adView = MaxAdView(bannerId, context).apply {
            setListener(bannerAdsListener)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.banner_height)
            )
        }
        view.addView(adView)
        adView.loadAd()
    }

    // Function to preload interstitial ads
    private fun preloadInterstitialAd() {
        interstitialAd = MaxInterstitialAd(interstitialId, context).apply {
            setListener(InterstitialAdsListener())
            loadAd()
        }
    }

    // Function to preload rewarded ads
    private fun preloadRewardedAd() {
        rewardedAd = MaxRewardedAd.getInstance(rewardId, context).apply {
            setListener(RewardAdsListener())
            loadAd()
        }
    }

    // Function to load native ads
    fun createNativeAds(view: FrameLayout) {
        nativeAdLoader = MaxNativeAdLoader(nativeAdUnitId, context).apply {
            setNativeAdListener(object : MaxNativeAdListener() {
                override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, ad: MaxAd) {
                    // Release the previous native ad if any
                    nativeAd?.let { nativeAdLoader?.destroy(it) }
                    nativeAd = ad

                    // Clear previous ad views and add the new one
                    view.removeAllViews()
                    if (nativeAdView != null) {
                        view.addView(nativeAdView)
                        view.visibility = View.VISIBLE
                    }
                }

                override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                    println("Native ad failed to load: ${error.message}")
                    view.visibility = View.GONE
                }

                override fun onNativeAdClicked(ad: MaxAd) {
                    println("Native ad clicked!")
                }

            })
        }
        nativeAdLoader?.loadAd()
    }

    // Function to show preloaded interstitial ads
    fun showInterstitialAd(loaded: () -> Unit = { }, failed: () -> Unit = { }) {
        if (interstitialAd?.isReady == true) {
            interstitialAd?.showAd(context)
            loaded.invoke()
        } else {
            failed.invoke()
        }
    }

    // Function to show preloaded reward ads
    fun showRewardAd(
        failed: () -> Unit
    ) {
        if (rewardedAd?.isReady == true) {
            rewardedAd?.showAd(context)
        } else {
            failed.invoke()
        }
    }

    // Destroy all ads to free resources
    fun destroyAds() {
        interstitialAd?.destroy()
        rewardedAd?.destroy()
        nativeAdLoader?.destroy(nativeAd)
    }

    // Listener for banner ads
    class BannerAdsListener(private val view: LinearLayout) : MaxAdViewAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            view.visibility = View.VISIBLE
        }

        override fun onAdDisplayed(maxAd: MaxAd) {
            view.visibility = View.VISIBLE
        }

        override fun onAdHidden(maxAd: MaxAd) {
            view.visibility = View.GONE
        }

        override fun onAdLoadFailed(maxAdUnitId: String, error: MaxError) {
            view.visibility = View.GONE
        }

        override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
            view.visibility = View.GONE
        }

        override fun onAdClicked(maxAd: MaxAd) {}
        override fun onAdExpanded(maxAd: MaxAd) {}
        override fun onAdCollapsed(maxAd: MaxAd) {}
    }

    // Listener for interstitial ads
    inner class InterstitialAdsListener : MaxAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            retryAttempt = 0.0
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            retryAttempt++
            val delayMillis = TimeUnit.SECONDS.toMillis(
                2.0.pow(6.0.coerceAtMost(retryAttempt)).toLong()
            )
            Handler(Looper.getMainLooper()).postDelayed({
                preloadInterstitialAd()
            }, delayMillis)
        }

        override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {}
        override fun onAdDisplayed(maxAd: MaxAd) {}
        override fun onAdClicked(maxAd: MaxAd) {}
        override fun onAdHidden(maxAd: MaxAd) {
            preloadInterstitialAd() // Preload the next ad
        }
    }

    // Listener for reward ads
    inner class RewardAdsListener : MaxRewardedAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            rewardRetryAttempt = 0.0
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            rewardRetryAttempt++
            val delayMillis = TimeUnit.SECONDS.toMillis(
                2.0.pow(6.0.coerceAtMost(rewardRetryAttempt)).toLong()
            )
            Handler(Looper.getMainLooper()).postDelayed({
                preloadRewardedAd()
            }, delayMillis)
        }

        override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
        override fun onAdDisplayed(maxAd: MaxAd) {}
        override fun onAdClicked(maxAd: MaxAd) {}
        override fun onAdHidden(maxAd: MaxAd) {
            preloadRewardedAd() // Preload the next ad
        }

        override fun onUserRewarded(maxAd: MaxAd, maxReward: MaxReward) {
            listener?.onUserRewarded(maxReward)
        }
    }
}