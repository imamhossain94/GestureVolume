@file:Suppress("unused")

package com.newagedevs.gesturevolume

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import com.newagedevs.gesturevolume.di.persistenceModule
import com.newagedevs.gesturevolume.di.repositoryModule
import com.newagedevs.gesturevolume.di.viewModelModule
import com.newagedevs.gesturevolume.overlays.OverlayService
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class Application : Application(), ActivityLifecycleCallbacks {

  override fun onCreate() {
    super.onCreate()

    startKoin {
      androidContext(this@Application)

      //Adding Module
      modules(viewModelModule)
      modules(repositoryModule)
      modules(persistenceModule)
    }

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
  }

  override fun onActivityCreated(p0: Activity, p1: Bundle?) { }

  override fun onActivityStarted(p0: Activity) { }

  override fun onActivityResumed(p0: Activity) {
    OverlayService.isOverlayVisible = false
  }

  override fun onActivityPaused(p0: Activity) {
    OverlayService.isOverlayVisible = true
  }

  override fun onActivityStopped(p0: Activity) { }

  override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) { }

  override fun onActivityDestroyed(p0: Activity) { }
}


