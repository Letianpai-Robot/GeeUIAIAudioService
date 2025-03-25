package com.rhj.audio.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class AIUIAudioService : Service() {

    override fun onCreate() {
        super.onCreate()
        initAIUI()
    }

    private fun initAIUI() {

    }


    override fun onBind(p0: Intent?): IBinder? {
        return myBinder
    }

    var myBinder: MyBinder? = null

    inner class MyBinder : Binder() {
        val service: AIUIAudioService
            get() = this@AIUIAudioService
    }
}