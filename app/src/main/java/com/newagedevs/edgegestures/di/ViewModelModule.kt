package com.newagedevs.edgegestures.di

import com.newagedevs.edgegestures.view.ui.main.MainViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    viewModel { MainViewModel(get()) }

}
