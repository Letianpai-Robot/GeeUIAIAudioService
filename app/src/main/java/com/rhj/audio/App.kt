package com.rhj.audio

import android.app.Application
import androidx.room.Room
import com.letianpai.robot.components.utils.GeeUILogUtils
import com.rhj.audio.database.AppDatabase

class App : Application() {
    private var db: AppDatabase? = null
    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext, AppDatabase::class.java, "speechData"
        ).fallbackToDestructiveMigration().build()
        initXlog2()
    }

    fun getDatabase(): AppDatabase {
        return db!!
    }

    private fun initXlog2() {
        GeeUILogUtils.initXlog2("speech", this)
    }
}