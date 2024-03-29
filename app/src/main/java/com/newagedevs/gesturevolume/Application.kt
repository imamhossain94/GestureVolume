@file:Suppress("unused")

package com.newagedevs.gesturevolume

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.sdk.AppLovinSdk
import com.newagedevs.gesturevolume.di.persistenceModule
import com.newagedevs.gesturevolume.di.repositoryModule
import com.newagedevs.gesturevolume.di.viewModelModule
import com.newagedevs.gesturevolume.repository.SharedPrefRepository
import com.newagedevs.gesturevolume.utils.SharedData.Companion.shouldShowAppOpenAds
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class Application : Application() {

  private lateinit var appOpenManager: AppOpenManager

  override fun onCreate() {
    super.onCreate()

    startKoin {
      androidContext(this@Application)

      //Adding Module
      modules(viewModelModule)
      modules(repositoryModule)
      modules(persistenceModule)
    }

    initializeAppLovinSdk()

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
  }


  private fun initializeAppLovinSdk() {
//        val settings = AppLovinSdkSettings(this@Application)
//        settings.testDeviceAdvertisingIds = listOf("d4456f44-7fc5-4102-8999-6b5714de2d8f")
//        val sdk = AppLovinSdk.getInstance(settings, this@Application)
//        sdk.showMediationDebugger()

    val sdk = AppLovinSdk.getInstance(this@Application)
    sdk.mediationProvider = "max"
    sdk.initializeSdk {
      appOpenManager = AppOpenManager(this@Application)
    }
  }

}

@Suppress("PrivatePropertyName", "DEPRECATION")
class AppOpenManager(context: Context) : LifecycleObserver,
  MaxAdListener {
  private val appOpenAd: MaxAppOpenAd?
  private val context: Context

  //Ads ID here
  private val ADS_UNIT = BuildConfig.appOpen_AdUnit

  init {
    ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    this.context = context
    appOpenAd = MaxAppOpenAd(ADS_UNIT, context)
    appOpenAd.setListener(this)
    appOpenAd.loadAd()
  }

  private fun showAdIfReady() {
    if (appOpenAd == null || !AppLovinSdk.getInstance(context).isInitialized) return

    if (appOpenAd.isReady && shouldShowAppOpenAds && SharedPrefRepository(context).shouldShowAppOpenAds()) {
      appOpenAd.showAd(ADS_UNIT)
    } else {
      appOpenAd.loadAd()
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_START)
  fun onStart() {
    showAdIfReady()
  }

  override fun onAdLoaded(ad: MaxAd) { }
  override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
  override fun onAdDisplayed(ad: MaxAd) { }
  override fun onAdClicked(ad: MaxAd) {}
  override fun onAdHidden(ad: MaxAd) {
    appOpenAd!!.loadAd()
  }

  override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
    appOpenAd!!.loadAd()
  }
}


