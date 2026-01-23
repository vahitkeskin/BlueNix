package com.vahitkeskin.bluenix.di

import com.vahitkeskin.bluenix.core.service.LocationService
import com.vahitkeskin.bluenix.ui.LocationViewModel
import com.vahitkeskin.bluenix.ui.home.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

expect val platformModule: Module

val appModule = module {
    // ViewModel her yerde ortak
    factory { LocationViewModel(get()) }
    factory { HomeViewModel(get()) }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(platformModule, appModule)
}