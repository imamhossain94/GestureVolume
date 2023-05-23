package com.newagedevs.edgegestures.di

import com.newagedevs.edgegestures.repository.MainRepository
import org.koin.dsl.module

val repositoryModule = module {

    single { MainRepository(get()) }

}
