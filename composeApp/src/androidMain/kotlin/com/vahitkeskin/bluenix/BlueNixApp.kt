package com.vahitkeskin.bluenix

import android.app.Application
import com.vahitkeskin.bluenix.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class BlueNixApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Koin Dependency Injection Başlatılıyor
        initKoin {
            androidLogger()
            androidContext(this@BlueNixApp)
        }
    }
}