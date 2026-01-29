package com.vahitkeskin.bluenix.di

import com.vahitkeskin.bluenix.ui.chat.ChatHistoryViewModel
import com.vahitkeskin.bluenix.ui.chat.ChatViewModel // ChatViewModel'i import et
import com.vahitkeskin.bluenix.ui.home.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

// 1. BEKLENTİ (EXPECT): Platforma (Android/iOS) diyoruz ki:
// "Bana 'platformModule' adında bir modül sağlamak zorundasın."
expect val platformModule: Module

// 2. ORTAK MODÜL: ViewModel'ler burada tanımlanır.
val appModule = module {
    // HomeViewModel (LocationService ve BluetoothService istiyor)
    factory { HomeViewModel(get(), get(), get(), get()) }

    // ChatViewModel (ChatController istiyor)
    // "NoDefinitionFoundException" hatası almamak için bunu MUTLAKA ekle
    factory { ChatViewModel(get(), get(), get(), get()) }
    factory { ChatHistoryViewModel(get()) }
}

// 3. BAŞLATICI
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    // Hem platformdan geleni hem de bizim yazdığımız appModule'ü birleştir
    modules(platformModule, appModule)
}