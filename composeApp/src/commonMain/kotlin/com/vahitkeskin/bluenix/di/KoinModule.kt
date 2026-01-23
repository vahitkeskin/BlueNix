package com.vahitkeskin.bluenix.di

// LocationViewModel kullanmıyorsan importunu ve aşağıdan factory satırını silebilirsin
// import com.vahitkeskin.bluenix.ui.LocationViewModel
import com.vahitkeskin.bluenix.ui.home.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

expect val platformModule: Module

val appModule = module {
    factory { HomeViewModel(get(), get()) }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(platformModule, appModule)
}