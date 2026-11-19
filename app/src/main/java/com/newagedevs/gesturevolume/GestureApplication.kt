@file:Suppress("unused", "DEPRECATION")

package com.newagedevs.gesturevolume

import android.app.Activity
import android.app.Application
import android.content.Context
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
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.helper.AdRevenueTracker
import com.newagedevs.gesturevolume.utils.AdPacing
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GestureApplication : Application() {

    @Inject
    lateinit var preferences: SharedPref

    private lateinit var appOpenManager: AppOpenManager

    @Volatile
    private var hasInitializedAds = false

    /** True while a consent check is in flight, so a second Activity does not start another. */
    @Volatile
    private var consentCheckRunning = false

    private val _adsReady = MutableStateFlow(false)

    /**
     * True once the AppLovin SDK has initialised, which waits for Google's consent form wherever
     * there is one. Nothing may load an ad before: MAX cannot load one yet, and in the EEA, the UK
     * and Switzerland no ad may be requested before the form has been answered. See
     * ApplovinAdsManager.whenSdkReady.
     */
    val adsReady: StateFlow<Boolean> = _adsReady.asStateFlow()

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

        // Stamps every return to the foreground, which starts the interstitial launch window
        // (AdPacing.FOREGROUND_QUIET_MS), and every trip to the background, which an app-open ad
        // measures its time away from (AdPacing.MIN_TIME_AWAY_MS). The process lifecycle rather
        // than MainActivity's: an interstitial is itself an Activity, and returning from one must
        // not count as a launch. Registered before AppOpenManager's observer, so both stamps are
        // current by the time it decides.
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> AdPacing.Session.onAppForeground()
                Lifecycle.Event.ON_STOP -> AdPacing.Session.onAppBackground()
                else -> Unit
            }
        })

        if (BuildConfig.DEBUG && preferences.isInAdGracePeriod()) {
            val minutes = preferences.getAdGraceRemainingMillis() / 60_000
            Log.d("GestureApp", "Ad grace period active: $minutes minutes remaining")
        }

        // The ad SDK is not started here. Google's consent form needs an Activity to show over,
        // and a process the system starts only for the bar — at boot, after an update, after a
        // kill — has none and shows no ads. See requestConsentAndStartAds.
    }

    /**
     * Checks the user's ad consent with Google's User Messaging Platform, shows Google's form where
     * the AdMob message asks for consent (the EEA, the UK and Switzerland), then starts the
     * AppLovin SDK once ads may be requested. Everywhere else there is no form and the SDK starts
     * straight away.
     *
     * Google's form directly, not through MAX's Terms & Privacy Policy flow: that one also showed
     * everyone outside the EEA an AppLovin "accept the terms" alert that no ad network reads.
     * AppLovin asks that a form used directly is answered before its SDK starts, so this waits.
     *
     * Called by MainActivity for returning users, and when the walkthrough finishes for new ones,
     * so the form never covers the walkthrough. Google asks for the consent check at every launch,
     * and the bar's foreground service can keep this process alive for days, so the check runs
     * every time an Activity asks; the SDK starts only once.
     */
    fun requestConsentAndStartAds(activity: Activity) {
        // A build made with -PnoAds, for recording videos: no SDK, so no ad of any kind.
        if (BuildConfig.ADS_DISABLED) return
        if (preferences.isProFeatureActivated()) return
        if (consentCheckRunning) return
        consentCheckRunning = true
        val consent = UserMessagingPlatform.getConsentInformation(this)
        val done = {
            consentCheckRunning = false
            // Not allowed means no answer yet, now or before (offline on the first launch, say):
            // the next launch asks again.
            startAdsIfAllowed()
        }
        try {
            consent.requestConsentInfoUpdate(activity, consentRequestParameters(), {
                if (activity.isFinishing || activity.isDestroyed) {
                    done()
                } else {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                        if (error != null) Log.w("GestureApp", "Consent form: ${error.message}")
                        done()
                    }
                }
            }, { error ->
                Log.w("GestureApp", "Consent info: ${error.message}")
                done()
            })
        } catch (e: Exception) {
            Log.e("GestureApp", "Consent check failed", e)
            done()
        }
    }

    /**
     * Starts the AppLovin SDK, once per process, if the user is not Pro and Google's consent
     * answer allows ads to be requested. Also called after "Privacy choices", where a first answer
     * can arrive when the launch form could not load.
     */
    fun startAdsIfAllowed() {
        if (hasInitializedAds) return
        if (BuildConfig.ADS_DISABLED) return
        if (preferences.isProFeatureActivated()) return
        val allowed = runCatching {
            UserMessagingPlatform.getConsentInformation(this).canRequestAds()
        }.getOrDefault(false)
        if (!allowed) return
        hasInitializedAds = true
        initializeAppLovinSdk()
    }

    /** Debug builds count as in the EEA on any device, so the form can be checked from anywhere. */
    private fun consentRequestParameters(): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(this)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    // The debug geography only applies to test devices; this makes every device one.
                    .setForceTesting(true)
                    .build()
            )
        }
        return builder.build()
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

            // No Terms & Privacy Policy flow: Google's consent form has been answered already,
            // where there is one (requestConsentAndStartAds), and its consent string is what
            // Google, Meta and the other networks read. Without it their bids go nearly blind.
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
                // And the screens' ads, which wait for this.
                _adsReady.value = true
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

        /**
         * The activity whose start brought the process to the foreground, by class name.
         *
         * The process lifecycle starts for *any* of this app's activities, and two of them — the QR
         * scanner and voice search — are transparent helpers the Deck launches on top of whatever
         * app the user is in. An app-open ad shown then would cover another app, which is the kind
         * of out-of-context ad Play's policy forbids. So an app-open ad is shown only when the
         * app's own main screen is the one being opened.
         */
        private var startedActivity: String? = null

        private val activityTracker = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: android.app.Activity) {
                startedActivity = activity.javaClass.name
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }

        init {
            // Before the process observer, so the activity is known by the time ON_START arrives.
            registerActivityLifecycleCallbacks(activityTracker)
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
            loadAd()
        }

        private fun shouldShowAd(): Boolean {
            // Only over the app's own main screen. See [startedActivity].
            if (startedActivity != com.newagedevs.gesturevolume.ui.activities.MainActivity::class.java.name) {
                return false
            }

            // Don't show on first launch
            if (preferences.isFirstLaunch()) {
                preferences.setFirstLaunchCompleted()
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

            // Pro, grace period, a permission flow in progress, another ad on screen, the
            // cooldown, the daily cap and a too-short time away: see AdPacing.
            val shouldShow = AdPacing.mayShowAppOpen(preferences, preferences.isProFeatureActivated())

            if (!shouldShow && BuildConfig.DEBUG) {
                Log.d("GestureApp", "App open ad held back by AdPacing (${preferences.getAppOpenAdsToday()} today)")
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
            AdPacing.Session.onAppOpenAdShowing(true)
            // Save the time when app open ad was displayed
            preferences.saveAppOpenAdTime()
            Log.d("GestureApp", "App open ad displayed")
        }

        override fun onAdClicked(ad: MaxAd) {
            Log.d("GestureApp", "App open ad clicked")
        }

        override fun onAdHidden(ad: MaxAd) {
            isShowingAd = false
            // Also stamps the dismissal, which starts the interstitial quiet period.
            AdPacing.Session.onAppOpenAdShowing(false)
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
                unregisterActivityLifecycleCallbacks(activityTracker)
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