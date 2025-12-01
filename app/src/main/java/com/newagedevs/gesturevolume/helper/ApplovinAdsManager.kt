package com.newagedevs.gesturevolume.helper

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class ApplovinAdsManager(
    private val context: Activity,
    private val preferences: SharedPref
) {
    private var interstitialAd: MaxInterstitialAd? = null
    private var retryAttempt = 0.0
    private var isShowingAd = false

    private val bannerId = BuildConfig.AD_UNIT_BANNER
    private val interstitialId: String = BuildConfig.AD_UNIT_INTERSTITIAL
    private val nativeAdUnitId: String = BuildConfig.AD_UNIT_NATIVE

    init {
        preloadInterstitialAd()
    }

    // Compose-compatible banner ad
    @Composable
    fun BannerAdView() {
        var isAdLoaded by remember { mutableStateOf(false) }

        AndroidView(
            factory = { ctx ->
                MaxAdView(bannerId).apply {
                    setListener(object : MaxAdViewAdListener {
                        override fun onAdLoaded(maxAd: MaxAd) { isAdLoaded = true }
                        override fun onAdDisplayed(maxAd: MaxAd) { }
                        override fun onAdHidden(maxAd: MaxAd) { isAdLoaded = false }
                        override fun onAdLoadFailed(maxAdUnitId: String, error: MaxError) { isAdLoaded = false }
                        override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) { isAdLoaded = false }
                        override fun onAdClicked(maxAd: MaxAd) { }
                        override fun onAdExpanded(maxAd: MaxAd) { }
                        override fun onAdCollapsed(maxAd: MaxAd) { }
                    })
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        ctx.resources.getDimensionPixelSize(R.dimen.banner_height)
                    )
                    loadAd()
                }
            },
            update = { adView ->
                adView.visibility = if (isAdLoaded) View.VISIBLE else View.GONE
            }
        )
    }

    @Composable
    fun NativeAdWidget(
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var nativeAdLoader by remember { mutableStateOf<MaxNativeAdLoader?>(null) }
        var nativeAd by remember { mutableStateOf<MaxAd?>(null) }
        var nativeAdView by remember { mutableStateOf<MaxNativeAdView?>(null) }
        var isAdVisible by remember { mutableStateOf(false) }
        var retryAttempt by remember { mutableIntStateOf(0) }
        var isRetrying by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            // Check if context is valid
            if (context !is Activity || context.isFinishing || context.isDestroyed) {
                return@DisposableEffect onDispose { }
            }

            // Create native ad loader
            val loader = MaxNativeAdLoader(nativeAdUnitId).apply {
                setNativeAdListener(object : MaxNativeAdListener() {
                    override fun onNativeAdLoaded(newNativeAdView: MaxNativeAdView?, ad: MaxAd) {
                        // Check if context is still valid
                        if (context.isFinishing || context.isDestroyed) {
                            return
                        }

                        // Clean up any pre-existing native ad to prevent memory leaks
                        nativeAd?.let { oldAd ->
                            nativeAdLoader?.destroy(oldAd)
                        }

                        // Save reference to the ad
                        nativeAd = ad

                        // Reset retry attempt on successful load
                        retryAttempt = 0
                        isRetrying = false

                        // Create and render the native ad view
                        val adView = createNativeAdView(context)
                        this@apply.render(adView, ad)

                        nativeAdView = adView
                        isAdVisible = true
                    }

                    override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                        // Check if context is still valid before retrying
                        if (context.isFinishing || context.isDestroyed) {
                            return
                        }

                        // Limit retry attempts to prevent infinite retries
                        if (retryAttempt >= 5) {
                            isAdVisible = false
                            isRetrying = false
                            return
                        }

                        isRetrying = true
                        retryAttempt++

                        // Exponential backoff
                        val delayMillis = (2.0.pow(min(4.0, retryAttempt.toDouble())) * 1000).toLong()

                        scope.launch {
                            delay(delayMillis)
                            // Double-check context validity before retry
                            if (!context.isFinishing && !context.isDestroyed) {
                                this@apply.loadAd()
                            } else {
                                isRetrying = false
                            }
                        }
                    }

                    override fun onNativeAdClicked(ad: MaxAd) {
                        // Load a new ad after click (best practice for native ads)
                        if (!context.isFinishing && !context.isDestroyed) {
                            scope.launch {
                                // Small delay to avoid rapid successive loads
                                delay(500)
                                if (!context.isFinishing && !context.isDestroyed) {
                                    isRetrying = false
                                    retryAttempt = 0
                                    this@apply.loadAd()
                                }
                            }
                        }
                    }

                    override fun onNativeAdExpired(ad: MaxAd) {
                        // Check if context is still valid
                        if (!context.isFinishing && !context.isDestroyed) {
                            // Clean up the expired ad
                            nativeAd?.let { expiredAd ->
                                nativeAdLoader?.destroy(expiredAd)
                            }
                            nativeAd = null
                            nativeAdView = null
                            isAdVisible = false

                            // Reset retry state and load fresh ad
                            isRetrying = false
                            retryAttempt = 0
                            this@apply.loadAd()
                        }
                    }
                })
            }

            nativeAdLoader = loader
            loader.loadAd()

            onDispose {
                nativeAd?.let { loader.destroy(it) }
                nativeAd = null
                nativeAdView = null
                loader.destroy()
            }
        }

        // Display the native ad using AndroidView
        AnimatedVisibility(
            visible = isAdVisible && nativeAdView != null,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            AndroidView(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 8.dp),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                },
                update = { container ->
                    container.removeAllViews()
                    nativeAdView?.let { adView ->
                        // Remove from previous parent if exists
                        (adView.parent as? FrameLayout)?.removeView(adView)
                        container.addView(adView)
                    }
                }
            )
        }
    }

    // Function to create a native ad view binder
    private fun createNativeAdBinder(): MaxNativeAdViewBinder {
        return MaxNativeAdViewBinder.Builder(R.layout.applovin_small_native_ad_layout)
            .setTitleTextViewId(R.id.title_text_view)
            .setBodyTextViewId(R.id.body_text_view)
            .setAdvertiserTextViewId(R.id.advertiser_text_view)
            .setMediaContentViewGroupId(R.id.media_view_container)
            .setOptionsContentViewGroupId(R.id.options_view)
            .setStarRatingContentViewGroupId(R.id.star_rating_view)
            .setCallToActionButtonId(R.id.cta_button)
            .build()
    }

    // Function to create a native ad view
    private fun createNativeAdView(context: Activity): MaxNativeAdView {
        return MaxNativeAdView(createNativeAdBinder(), context)
    }

    private fun preloadInterstitialAd() {
        // Check if context is still valid
        if (context.isFinishing || context.isDestroyed) {
            return
        }

        interstitialAd = MaxInterstitialAd(interstitialId).apply {
            setListener(InterstitialAdsListener())

            if(!isReady) {
                loadAd()
            }
        }
    }

    fun showInterstitialAd(context: Activity): Boolean {
        // Check if cooldown allows showing the ad
        if (!preferences.shouldShowInterstitialAd()) {
            preferences.getInterstitialAdCooldownRemaining()
            return false
        }

        // Check if ad is ready
        if (interstitialAd?.isReady != true) {
            preloadInterstitialAd()
            return false
        }

        // Check if already showing
        if (isShowingAd) {
            return false
        }

        interstitialAd?.showAd(context)
        return true
    }

    fun getInterstitialCooldownRemaining(): Long {
        return preferences.getInterstitialAdCooldownRemaining()
    }

    fun destroyAds() {
        interstitialAd?.destroy()
        interstitialAd = null
    }

    inner class InterstitialAdsListener : MaxAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            retryAttempt = 0.0
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            // Check if context is still valid
            if (context.isFinishing || context.isDestroyed) {
                return
            }

            retryAttempt++

            // Limit retry attempts
            if (retryAttempt > 5) {
                return
            }

            val delayMillis = TimeUnit.SECONDS.toMillis(
                2.0.pow(6.0.coerceAtMost(retryAttempt)).toLong()
            )

            Handler(Looper.getMainLooper()).postDelayed({
                if (!context.isFinishing && !context.isDestroyed) {
                    preloadInterstitialAd()
                }
            }, delayMillis)
        }

        override fun onAdDisplayFailed(maxAd: MaxAd, error: MaxError) {
            isShowingAd = false

            if (!context.isFinishing && !context.isDestroyed) {
                preloadInterstitialAd()
            }
        }

        override fun onAdDisplayed(maxAd: MaxAd) {
            isShowingAd = true
        }

        override fun onAdClicked(maxAd: MaxAd) {}

        override fun onAdHidden(maxAd: MaxAd) {
            isShowingAd = false
            if (!context.isFinishing && !context.isDestroyed) {
                preloadInterstitialAd()
            }
        }
    }
}