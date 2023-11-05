package com.newagedevs.gesturevolume.view.ui.main

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityLandConfigBinding
import com.skydoves.bindables.BindingActivity
import com.skydoves.bundler.intentOf
import org.koin.android.viewmodel.ext.android.viewModel


class LandConfigActivity : BindingActivity<ActivityLandConfigBinding>(R.layout.activity_land_config) {

    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding {
            vm = viewModel
        }

        setupPreviewFrame()
        createBannerAd()
    }

    // ----------------------------------------------------------------
    private fun createBannerAd() {
        val bannerId = BuildConfig.banner_AdUnit
        val adView = MaxAdView(bannerId, this).apply {
            setListener(bannerAdsListener)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.banner_height)
            )
        }
        binding.adsContainer.addView(adView)
        adView.loadAd()
    }

    private val bannerAdsListener = object : MaxAdViewAdListener {
        override fun onAdLoaded(p0: MaxAd?) {
            binding.adsContainer.visibility = View.VISIBLE
        }

        override fun onAdDisplayed(p0: MaxAd?) {
            binding.adsContainer.visibility = View.VISIBLE
        }

        override fun onAdHidden(p0: MaxAd?) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdClicked(p0: MaxAd?) { }

        override fun onAdLoadFailed(p0: String?, p1: MaxError?) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdDisplayFailed(p0: MaxAd?, p1: MaxError?) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdExpanded(p0: MaxAd?) { }

        override fun onAdCollapsed(p0: MaxAd?) { }
    }
    // ----------------------------------------------------------------

    private fun setupPreviewFrame() {
        val preview = findViewById<ImageView>(R.id.bg)
        val wallpaperManager = WallpaperManager.getInstance(this)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try{
                val wallpaperDrawable = wallpaperManager.drawable
                preview.setImageDrawable(wallpaperDrawable)
            } catch (_: IllegalStateException) { }
        }
    }

    companion object {
        fun startActivity(
            context: Context,
        ) = context.intentOf<LandConfigActivity> {
            context.startActivity(intent, null)
        }
    }

}