package com.vahitkeskin.bluenix.di

import com.vahitkeskin.bluenix.core.service.IosLocationService
import com.vahitkeskin.bluenix.core.service.LocationService
import org.koin.dsl.module

actual val platformModule = module {
    single<LocationService> { IosLocationService() }
}