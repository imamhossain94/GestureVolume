package com.newagedevs.gesturevolume.di

import com.newagedevs.gesturevolume.persistence.SharedPrefRepository
import org.koin.dsl.module

val persistenceModule = module {

  single { SharedPrefRepository(get()) }

}
