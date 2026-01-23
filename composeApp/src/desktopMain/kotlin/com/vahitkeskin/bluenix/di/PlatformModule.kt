package com.vahitkeskin.bluenix.di

actual val platformModule = module {
    single<LocationService> { DesktopLocationService() }
}