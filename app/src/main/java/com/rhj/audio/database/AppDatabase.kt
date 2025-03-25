package com.rhj.audio.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Speech::class, DMResultModel::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): SpeechDao
    abstract fun dmResultDao(): DmResultDao
}