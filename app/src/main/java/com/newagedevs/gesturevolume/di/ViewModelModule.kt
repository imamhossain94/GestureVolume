package com.newagedevs.gesturevolume.di

import com.newagedevs.gesturevolume.view.ui.main.MainViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel { MainViewModel(get(), get()) }

}
