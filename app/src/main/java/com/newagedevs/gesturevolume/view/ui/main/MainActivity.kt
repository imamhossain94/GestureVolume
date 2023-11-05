package com.newagedevs.gesturevolume.view.ui.main

import android.Manifest
import android.app.WallpaperManager
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityMainBinding
import com.newagedevs.gesturevolume.service.LockScreenUtil
import com.newagedevs.gesturevolume.service.OverlayService
import com.skydoves.bindables.BindingActivity
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit
import kotlin.math.pow


class MainActivity : BindingActivity<ActivityMainBinding>(R.layout.activity_main) {

    private val viewModel: MainViewModel by viewModel()

    private var retryAttempt = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding {
            vm = viewModel
        }

        setupPreviewFrame()

        OverlayService.stop(this)
        //this.stopService(Intent(this, OverlayService::class.java))

        createBannerAd()
        createInterstitialAd()
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

    // ----------------------------------------------------------------
    private fun createInterstitialAd() {
        val interstitialId = BuildConfig.interstitial_AdUnit
        viewModel.interstitialAd = MaxInterstitialAd(interstitialId, this)
        viewModel.interstitialAd.setListener(interstitialAdsListener)
        viewModel.interstitialAd.loadAd()
    }

    private val interstitialAdsListener = object : MaxAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            retryAttempt = 0.0
        }

        override fun onAdLoadFailed(adUnitId: String?, error: MaxError?) {
            retryAttempt++
            val delayMillis = TimeUnit.SECONDS.toMillis( 2.0.pow(6.0.coerceAtMost(retryAttempt)).toLong() )
            Handler(Looper.getMainLooper()).postDelayed( { viewModel.interstitialAd.loadAd()  }, delayMillis )
        }

        override fun onAdDisplayFailed(ad: MaxAd?, error: MaxError?) {
            this@MainActivity.finish()
        }

        override fun onAdDisplayed(maxAd: MaxAd) {
            this@MainActivity.finish()
        }

        override fun onAdClicked(maxAd: MaxAd) {
            this@MainActivity.finish()
        }

        override fun onAdHidden(maxAd: MaxAd) {
            this@MainActivity.finish()
        }
    }
    // ----------------------------------------------------------------

    private fun setupPreviewFrame() {
        val preview = findViewById<ImageView>(R.id.bg)
        val wallpaperManager = WallpaperManager.getInstance(this)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    WALLPAPER_REQUEST_CODE
                )
                return
            }

            val wallpaperDrawable = wallpaperManager.drawable
            preview.setImageDrawable(wallpaperDrawable)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                OverlayService.start(this)
            }
        } else if (requestCode == WALLPAPER_REQUEST_CODE) {
            setupPreviewFrame()
        } else if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {

            if (LockScreenUtil(this).active()) {
                viewModel.clickActionIcon = R.drawable.ic_lock
                viewModel.clickAction = "Lock"
            }
        }

    }

    override fun onPause() {
        super.onPause()
        if(!OverlayService.isRunning(this)){
            //OverlayService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        OverlayService.stop(this)
    }

    companion object {
        const val OVERLAY_REQUEST_CODE = 1
        const val WALLPAPER_REQUEST_CODE = 2
        const val DEVICE_ADMIN_REQUEST_CODE = 3
    }

}