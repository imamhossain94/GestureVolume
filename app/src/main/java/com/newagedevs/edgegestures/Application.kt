@file:Suppress("unused")

package com.newagedevs.edgegestures

import android.app.Application
import com.newagedevs.edgegestures.di.persistenceModule
import com.newagedevs.edgegestures.di.repositoryModule
import com.newagedevs.edgegestures.di.viewModelModule
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
