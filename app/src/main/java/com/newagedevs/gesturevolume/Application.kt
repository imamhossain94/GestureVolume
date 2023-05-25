@file:Suppress("unused")

package com.newagedevs.gesturevolume

import android.app.Application
import com.newagedevs.gesturevolume.di.persistenceModule
import com.newagedevs.gesturevolume.di.repositoryModule
import com.newagedevs.gesturevolume.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class Application : Application() {

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

}


