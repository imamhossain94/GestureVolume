@file:Suppress("unused", "DEPRECATION")

package com.newagedevs.gesturevolume

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.helper.AdRevenueTracker
import com.newagedevs.gesturevolume.utils.Constants
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GestureApplication : Application() {

    @Inject
    lateinit var preferences: SharedPref

    private lateinit var appOpenManager: AppOpenManager

    @Volatile
    private var hasInitializedAds = false

    // Use application scope for background tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Configure WebView early
        configureWebView()

        // Stamp the install time before anything can clear the first-launch flag — AppOpenManager
        // does exactly that on its first ON_START. This is what the post-install ad grace period
        // counts from.
        preferences.initInstallTimeIfNeeded()

        if (BuildConfig.DEBUG && preferences.isInAdGracePeriod()) {
            val minutes = preferences.getAdGraceRemainingMillis() / 60_000
            Log.d("GestureApp", "Ad grace period active: $minutes minutes remaining")
        }

        // Defer the ad SDK init — and therefore its consent/CMP prompt — until onboarding is
        // complete, so the consent sheet never covers the first-launch walkthrough. New users
        // trigger init from WalkthroughScreen.onComplete via initializeAdsIfNeeded(); returning
        // users initialize here.
        if (!preferences.isFirstLaunch()) {
            initializeAdsIfNeeded()
        }
    }

    /**
     * Initializes the AppLovin SDK once, if the user isn't Pro. Idempotent and safe to call
     * from multiple entry points (app start for returning users, walkthrough completion for
     * new users).
     */
    fun initializeAdsIfNeeded() {
        if (hasInitializedAds) return
        if (preferences.isProFeatureActivated()) return
        hasInitializedAds = true
        initializeAppLovinSdk()
    }

    private fun configureWebView() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && packageName != getProcessName()) {
                WebView.setDataDirectorySuffix(getProcessName() ?: "default")
            }
        } catch (e: Exception) {
            // Handle gracefully if WebView configuration fails
            Log.e("GestureApp", "Failed to configure WebView", e)
        }
    }

    private fun initializeAppLovinSdk() {
        try {
            val appLovinSdk = AppLovinSdk.getInstance(this)

            // Enable AppLovin's built-in Terms & Privacy Policy flow (Google UMP CMP).
            // Without a consent string, premium demand (e.g. Meta) bids nearly blind — the
            // primary cause of the very low banner eCPM and the exchange winning blind
            // inventory at $0.03. Must be configured BEFORE initialize(). The matching CMP
            // must also be enabled in the AppLovin MAX dashboard (Privacy → CMP → Google UMP).
            appLovinSdk.settings.termsAndPrivacyPolicyFlowSettings.apply {
                isEnabled = true
                privacyPolicyUri = Uri.parse(Constants.PRIVACY_POLICY_URL)
                termsOfServiceUri = Uri.parse(Constants.TERMS_OF_SERVICE_URL)
                // Force the GDPR flow in debug so we can verify the prompt outside the EEA.
                if (BuildConfig.DEBUG) {
                    debugUserGeography = AppLovinSdkConfiguration.ConsentFlowUserGeography.GDPR
                }
            }

            val initConfig = AppLovinSdkInitializationConfiguration.builder(BuildConfig.APPLOVIN_SDK_KEY)
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .apply {
                    // Only add test device IDs in debug builds
                    if (BuildConfig.DEBUG) {
                        testDeviceAdvertisingIds = listOf("14747b03-bb4a-45c1-8654-d32632fa4812")
                    }
                }
                .build()

            appLovinSdk.initialize(initConfig) {
                // Initialize app open manager after SDK is ready
                appOpenManager = AppOpenManager(this)
            }

        } catch (e: Exception) {
            Log.e("GestureApp", "Failed to initialize AppLovin SDK", e)
        }
    }

    inner class AppOpenManager(private val context: Context) : MaxAdListener {

        private var appOpenAd: MaxAppOpenAd? = null
        private val adUnitId = BuildConfig.AD_UNIT_APP_OPEN
        private var isLoadingAd = false
        private var isShowingAd = false

        private val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                // Check conditions before showing ad
                if (shouldShowAd()) {
                    showAdIfReady()
                }
            }
        }

        init {
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
            loadAd()
        }

        private fun shouldShowAd(): Boolean {
            // Don't show on first launch
            if (preferences.isFirstLaunch()) {
                preferences.setFirstLaunchCompleted()
                return false
            }

            // Check if user is pro
            if (preferences.isProFeatureActivated()) {
                return false
            }

            // Check if ad is ready
            if (appOpenAd?.isReady != true) {
                return false
            }

            // Check if currently showing ad
            if (isShowingAd) {
                return false
            }

            // Don't show if draw-over-other-apps permission is not granted
            if (!Settings.canDrawOverlays(context)) {
                return false
            }

            // Check if app open ads are paused (e.g., during permission request screens)
            if (preferences.isAppOpenAdPaused()) {
                return false
            }

            // Check cooldown using the new method
            val shouldShow = preferences.shouldShowAppOpenAd()

            if (!shouldShow && BuildConfig.DEBUG) {
                val remaining = preferences.getAppOpenAdCooldownRemaining()
                Log.d("GestureApp", "App open ad cooldown: $remaining seconds remaining")
            }

            return shouldShow
        }

        private fun loadAd() {
            if (isLoadingAd || appOpenAd?.isReady == true) {
                return
            }

            isLoadingAd = true

            if (appOpenAd == null) {
                appOpenAd = MaxAppOpenAd(adUnitId).apply {
                    setListener(this@AppOpenManager)
                    setRevenueListener { ad -> AdRevenueTracker.logAdRevenue(context, ad) }
                }
            }

            appOpenAd?.loadAd()
        }

        private fun showAdIfReady() {
            if (appOpenAd?.isReady == true && !isShowingAd) {
                isShowingAd = true
                appOpenAd?.showAd(adUnitId)
            }
        }

        override fun onAdLoaded(ad: MaxAd) {
            isLoadingAd = false
            Log.d("GestureApp", "App open ad loaded")
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            isLoadingAd = false
            Log.e("GestureApp", "App open ad load failed: ${error.message}")

            // Retry loading after a delay
            applicationScope.launch {
                delay(30000)
                loadAd()
            }
        }

        override fun onAdDisplayed(ad: MaxAd) {
            isShowingAd = true
            // Save the time when app open ad was displayed
            preferences.saveAppOpenAdTime()
            Log.d("GestureApp", "App open ad displayed")
        }

        override fun onAdClicked(ad: MaxAd) {
            Log.d("GestureApp", "App open ad clicked")
        }

        override fun onAdHidden(ad: MaxAd) {
            isShowingAd = false
            Log.d("GestureApp", "App open ad hidden")
            // Load next ad
            loadAd()
        }

        override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
            isShowingAd = false
            Log.e("GestureApp", "App open ad display failed: ${error.message}")
            // Load next ad
            loadAd()
        }

        fun destroy() {
            try {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
                appOpenAd?.destroy()
                appOpenAd = null
            } catch (e: Exception) {
                Log.e("GestureApp", "Error destroying AppOpenManager", e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        if (::appOpenManager.isInitialized) {
            appOpenManager.destroy()
        }
    }
}