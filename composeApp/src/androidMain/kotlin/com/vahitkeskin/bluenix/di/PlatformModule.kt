package com.vahitkeskin.bluenix.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vahitkeskin.bluenix.core.repository.ChatRepository
import com.vahitkeskin.bluenix.core.service.AndroidBluetoothService
import com.vahitkeskin.bluenix.core.service.AndroidChatClient
import com.vahitkeskin.bluenix.core.service.AndroidChatController
import com.vahitkeskin.bluenix.core.service.AndroidLocationService
import com.vahitkeskin.bluenix.core.service.BluetoothService
import com.vahitkeskin.bluenix.core.service.ChatController
import com.vahitkeskin.bluenix.core.service.LocationService
import com.vahitkeskin.bluenix.data.local.AppDatabase
import com.vahitkeskin.bluenix.data.repository.AndroidChatRepository
import org.koin.core.module.Module
import org.koin.dsl.bind // Eklendi
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<LocationService> { AndroidLocationService(get()) }
    single<BluetoothService> { AndroidBluetoothService(get()) }

    single<AppDatabase> {
        val context = get<Context>()
        val dbFile = context.getDatabasePath("bluenix.db")
        Room.databaseBuilder<AppDatabase>(
            context = context,
            name = dbFile.absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(true)
            .build()
    }

    single { get<AppDatabase>().chatDao() }

    single { AndroidChatClient(get()) }

    // --- KRİTİK DEĞİŞİKLİK ---
    // Controller artık sadece Context alıyor.
    // 'bind ChatController::class' diyerek Interface olarak da erişilebilir yapıyoruz.
    // Ancak Repository somut sınıfı (AndroidChatController) istediği için cast ediyoruz.
    single {
        AndroidChatController(get())
    } bind AndroidChatController::class

    // Repository, yukarıdaki Controller'ı constructor'ında alıyor.
    // (Controller içinde de Repository'yi 'by inject' ile alıyoruz)
    single<ChatRepository> {
        AndroidChatRepository(get(), get(), get<AndroidChatController>())
    }

    // ViewModel'ler Interface (ChatController) isteyebilir
    single<ChatController> { get<AndroidChatController>() }
}