package com.vahitkeskin.bluenix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters // <-- Bunu ekle

@Database(entities = [MessageEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}