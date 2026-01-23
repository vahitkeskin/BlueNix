package com.vahitkeskin.bluenix.di

import com.vahitkeskin.bluenix.core.service.AndroidLocationService
import com.vahitkeskin.bluenix.core.service.LocationService
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // AndroidLocationService'i LocationService olarak tanıtıyoruz
    single<LocationService> { AndroidLocationService(get()) }
}