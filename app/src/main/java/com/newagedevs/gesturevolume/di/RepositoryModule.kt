package com.newagedevs.gesturevolume.di

import com.newagedevs.gesturevolume.repository.MainRepository
import org.koin.dsl.module

val repositoryModule = module {

    single { MainRepository(get()) }

}
