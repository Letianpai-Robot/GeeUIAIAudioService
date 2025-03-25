package com.rhj.speech

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.rhj.audio.service.LTPAudioService
import com.rhj.speech.databinding.ActivityLauncherBinding

class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decorView = window.decorView
        val uiOptions =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
        decorView.systemUiVisibility = uiOptions
        setContentView(ActivityLauncherBinding.inflate(layoutInflater).root)
        startService(Intent(this@LauncherActivity, LTPAudioService::class.java))
    }

}