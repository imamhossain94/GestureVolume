package com.newagedevs.gesturevolume.helper

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityMainBinding
import com.newagedevs.gesturevolume.view.ui.main.MainViewModel
import java.util.concurrent.TimeUnit
import kotlin.math.pow

interface ApplovinAdsCallback {
    fun onInterstitialAdLoaded()
    fun onInterstitialAdFailed(error: MaxError)
}

class ApplovinAdsManager(
    private val context: Activity,
    private val viewModel: MainViewModel,
    private val binding: ActivityMainBinding,
    private val adsCallback: ApplovinAdsCallback
) {
    private var loadingDialog: AlertDialog? = null

    private val interstitialId:String = BuildConfig.interstitial_AdUnit
    private val bannerId = BuildConfig.banner_AdUnit


    // Function to create and show interstitial ads
    fun createAndShowInterstitialAd() {
        // Initialize the interstitial ad
        viewModel.interstitialAd = MaxInterstitialAd(interstitialId, context)
        viewModel.interstitialAd?.setListener(interstitialAdsListener)

        // Show the loading dialog
        showLoadingDialog()

        // Load the ad
        viewModel.interstitialAd?.loadAd()
    }

    // Function to create banner ads
    fun createBannerAd() {

        val adView = MaxAdView(bannerId, context).apply {
            setListener(bannerAdsListener)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.banner_height)
            )
        }
        binding.adsContainer.addView(adView)
        adView.loadAd()
    }

    // Listener for interstitial ads
    private val interstitialAdsListener = object : MaxAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            viewModel.retryAttempt = 0.0
            hideLoadingDialog()
            viewModel.interstitialAd?.showAd()
            adsCallback.onInterstitialAdLoaded()
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            viewModel.retryAttempt++
            val delayMillis = TimeUnit.SECONDS.toMillis(
                2.0.pow(6.0.coerceAtMost(viewModel.retryAttempt)).toLong()
            )
            Handler(Looper.getMainLooper()).postDelayed({
                createAndShowInterstitialAd()
            }, delayMillis)
            hideLoadingDialog()
            adsCallback.onInterstitialAdFailed(error)
        }

        override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
            hideLoadingDialog()
        }

        override fun onAdDisplayed(maxAd: MaxAd) {
            hideLoadingDialog()
        }

        override fun onAdClicked(maxAd: MaxAd) { }

        override fun onAdHidden(maxAd: MaxAd) { }
    }

    // Listener for banner ads
    private val bannerAdsListener = object : MaxAdViewAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            binding.adsContainer.visibility = View.VISIBLE
        }

        override fun onAdDisplayed(maxAd: MaxAd) {
            binding.adsContainer.visibility = View.VISIBLE
        }

        override fun onAdHidden(maxAd: MaxAd) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdClicked(maxAd: MaxAd) { }

        override fun onAdLoadFailed(maxAdUnitId: String, error: MaxError) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdExpanded(maxAd: MaxAd) { }

        override fun onAdCollapsed(maxAd: MaxAd) { }
    }

    // Helper functions for showing and hiding the loading dialog
    private fun showLoadingDialog() {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Loading")
        builder.setMessage("Please wait while the ad is loading.")
        builder.setCancelable(false)
        loadingDialog = builder.create()
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }
}
