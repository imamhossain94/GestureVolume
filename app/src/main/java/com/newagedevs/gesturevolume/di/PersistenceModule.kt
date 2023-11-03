package com.newagedevs.gesturevolume.di

import androidx.room.Room
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.persistence.AppDatabase
import com.newagedevs.gesturevolume.repository.SharedPrefRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val persistenceModule = module {

  single {
    Room
      .databaseBuilder(
        androidApplication(),
        AppDatabase::class.java,
        androidApplication().getString(R.string.database)
      )
      .allowMainThreadQueries()
      .fallbackToDestructiveMigration()
      .build()
  }

  single { get<AppDatabase>().handlerDao() }

  single { SharedPrefRepository(get()) }

}
