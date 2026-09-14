package com.newagedevs.gesturevolume.helper

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkUtils
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.AdPacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class ApplovinAdsManager(
    private val context: Activity
) {
    private var retryAttempt = 0.0
    private var interstitialAd: MaxInterstitialAd? = null

    private val bannerId = BuildConfig.AD_UNIT_BANNER
    private val interstitialId: String = BuildConfig.AD_UNIT_INTERSTITIAL
    private val nativeAdUnitId: String = BuildConfig.AD_UNIT_NATIVE

    init {
        preloadInterstitialAd()
    }

    /** Whether this manager was built for [activity], rather than for one since destroyed. */
    fun isBoundTo(activity: Activity): Boolean = context === activity

    private companion object {
        const val SDK_READY_POLL_MS = 1000L
        const val SDK_READY_MAX_ATTEMPTS = 30 // ~30s, then give up quietly
    }

    /**
     * Runs [action] once the AppLovin SDK has finished initializing, or drops it if the SDK never
     * comes up.
     *
     * SDK init is deliberately deferred until onboarding completes (see
     * GestureApplication.initializeAdsIfNeeded) so the consent sheet cannot cover the walkthrough.
     * MainActivity, however, builds this manager from onCreate on *every* launch — including the
     * very first, while the SDK is still cold. Calling loadAd() in that window is fatal: MAX's
     * mediation service dereferences state it only populates during init and takes the process
     * down with an NPE. Every load in this class therefore goes through here.
     */
    private fun whenSdkReady(attempt: Int = 0, action: () -> Unit) {
        if (context.isFinishing || context.isDestroyed) return

        if (AppLovinSdk.getInstance(context).isInitialized) {
            // An ad failing must never be able to take the app down with it.
            try {
                action()
            } catch (e: Exception) {
                Log.e("ApplovinAdsManager", "Ad load failed", e)
            }
            return
        }

        // Bounded poll. If the SDK never initializes — no network, a bad key, a user who is still
        // mid-walkthrough — the app simply runs without ads instead of polling forever.
        if (attempt >= SDK_READY_MAX_ATTEMPTS) return
        Handler(Looper.getMainLooper()).postDelayed(
            { whenSdkReady(attempt + 1, action) },
            SDK_READY_POLL_MS
        )
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
                    setRevenueListener { ad -> AdRevenueTracker.logAdRevenue(ctx, ad) }
                    // Adaptive banners fill better and earn more than a fixed 320x50.
                    setExtraParameter("adaptive_banner", "true")
                    val heightDp = if (AppLovinSdkUtils.isTablet(ctx)) 90 else 50
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        AppLovinSdkUtils.dpToPx(ctx, heightDp)
                    )
                    whenSdkReady { loadAd() }
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
        // Read here, in composition, so a theme change restyles the ad on the next update.
        val colorScheme = MaterialTheme.colorScheme
        val style = remember(colorScheme) { NativeAdStyle.from(colorScheme) }

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
                setRevenueListener { ad -> AdRevenueTracker.logAdRevenue(context, ad) }
            }

            nativeAdLoader = loader
            whenSdkReady { loader.loadAd() }

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
                    .padding(horizontal = 0.dp, vertical = 0.dp),
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
                        styleNativeAdView(adView, style)
                        container.addView(adView)
                    }
                }
            )
        }
    }

    // Function to create a native ad view binder
    private fun createNativeAdBinder(): MaxNativeAdViewBinder {
        return MaxNativeAdViewBinder.Builder(R.layout.view_small_native_ads)
            .setTitleTextViewId(R.id.title_text_view)
            .setIconImageViewId(R.id.icon_image_view)
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

    /**
     * The home cards' colours, taken from the Compose theme for the native ad's views.
     *
     * The same roles [com.newagedevs.gesturevolume.ui.screens.main.NavigationCard] and the
     * Permissions card use: the title in onSurfaceVariant, secondary text at 70% of it, the Ad chip
     * in the status chips' primary on a 10% primary fill, the icon on the icon chip's 12% fill, and
     * the button a filled primary one.
     */
    private data class NativeAdStyle(
        val title: Int,
        val secondary: Int,
        val accent: Int,
        val onAccent: Int,
        val accentFill: Int,
        val chipFill: Int,
        val mediaFill: Int,
        val ripple: Int,
    ) {
        companion object {
            fun from(colors: ColorScheme) = NativeAdStyle(
                title = colors.onSurfaceVariant.toArgb(),
                secondary = colors.onSurfaceVariant.copy(alpha = 0.7f).toArgb(),
                accent = colors.primary.toArgb(),
                onAccent = colors.onPrimary.toArgb(),
                accentFill = colors.primary.copy(alpha = 0.10f).toArgb(),
                chipFill = colors.primary.copy(alpha = 0.12f).toArgb(),
                mediaFill = colors.onSurface.copy(alpha = 0.06f).toArgb(),
                ripple = colors.onPrimary.copy(alpha = 0.24f).toArgb(),
            )
        }
    }

    /**
     * Paints the rendered native ad in [style], so it reads as one of the home screen's cards.
     *
     * Done here rather than in the layout's XML because the app's theme is Compose's: it follows
     * the in-app Light/Dark choice, which colour resources cannot see, and applying it after the
     * network has filled the views means nothing the network sets overrides it.
     */
    private fun styleNativeAdView(adView: MaxNativeAdView, style: NativeAdStyle) {
        val density = adView.resources.displayMetrics.density
        fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
        }
        adView.findViewById<TextView>(R.id.title_text_view)?.setTextColor(style.title)
        adView.findViewById<TextView>(R.id.advertiser_text_view)?.setTextColor(style.secondary)
        adView.findViewById<TextView>(R.id.body_text_view)?.setTextColor(style.secondary)
        adView.findViewById<TextView>(R.id.ad_indicator_text_view)?.apply {
            setTextColor(style.accent)
            background = rounded(style.accentFill, 50f)
        }
        adView.findViewById<View>(R.id.icon_chip)?.apply {
            background = rounded(style.chipFill, 10f)
            clipToOutline = true
        }
        adView.findViewById<View>(R.id.media_view_container)?.apply {
            background = rounded(style.mediaFill, 12f)
            clipToOutline = true
        }
        adView.findViewById<Button>(R.id.cta_button)?.apply {
            backgroundTintList = null
            setTextColor(style.onAccent)
            background = RippleDrawable(
                ColorStateList.valueOf(style.ripple),
                rounded(style.accent, 12f),
                rounded(style.accent, 12f),
            )
        }
    }

    private fun preloadInterstitialAd() {
        // Check if context is still valid
        if (context.isFinishing || context.isDestroyed) {
            return
        }

        whenSdkReady {
            interstitialAd = MaxInterstitialAd(interstitialId).apply {
                setListener(InterstitialAdsListener())
                setRevenueListener { ad -> AdRevenueTracker.logAdRevenue(context, ad) }
                loadAd()
            }
        }
    }

    fun showInterstitialAd(loaded: () -> Unit = {}, failed: () -> Unit = {}) {
        if (interstitialAd?.isReady == true && !context.isFinishing && !context.isDestroyed) {
            interstitialAd?.showAd(context)
            loaded.invoke()
        } else {
            failed.invoke()
        }
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
            AdPacing.Session.onInterstitialShowing(false)
            if (!context.isFinishing && !context.isDestroyed) {
                preloadInterstitialAd()
            }
        }

        // Tracked so no other full-screen ad can be requested while this one is up. See AdPacing.
        override fun onAdDisplayed(maxAd: MaxAd) {
            AdPacing.Session.onInterstitialShowing(true)
        }

        override fun onAdClicked(maxAd: MaxAd) {}

        override fun onAdHidden(maxAd: MaxAd) {
            AdPacing.Session.onInterstitialShowing(false)
            if (!context.isFinishing && !context.isDestroyed) {
                preloadInterstitialAd()
            }
        }
    }
}