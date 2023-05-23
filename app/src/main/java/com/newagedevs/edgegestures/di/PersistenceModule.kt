package com.newagedevs.edgegestures.di

import androidx.room.Room
import com.newagedevs.edgegestures.R
import com.newagedevs.edgegestures.persistence.AppDatabase
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

}
