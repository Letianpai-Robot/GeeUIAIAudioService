package com.rhj.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.REPEAT_MODE_ALL
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.letianpai.robot.components.expression.ExpressionCenter
import com.letianpai.robot.components.expression.ExpressionPathCallback
import com.letianpai.robot.components.expression.ExpressionPathCallback.ExpressionPathListener
import com.letianpai.robot.components.storage.RobotSubConfigManager
import com.letianpai.robot.components.utils.GeeUILogUtils
import com.letianpai.robot.components.utils.SpeechCmdUtil
import com.rhj.audio.service.LTPAudioService
import com.rhj.callback.CustomTipsUpdateCallback
import com.rhj.speech.databinding.ActivityMainBinding
import com.rhj.utils.RawDataSourceProvider
import com.rhj.view.SpeechClosedView
import tv.danmaku.ijk.media.player.IjkMediaPlayer

class MainActivity : AppCompatActivity(), LTPAudioService.OnFaceChangeListener {
    private var surfaceAvailable: Boolean = false
    private lateinit var binding: ActivityMainBinding
    private lateinit var noVoiceCmdView: SpeechClosedView
    private lateinit var mSurfaceView: SurfaceView
    private var dispatchService: LTPAudioService? = null
    private var currentFace = ""
    private var intentFace = ""
    private var isPlaying: Boolean = false
    private var countDownTimer :CountDownTimer ?= null;
    private var dispatchConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder: LTPAudioService.MyBinder = service as LTPAudioService.MyBinder
            dispatchService = binder.service
            dispatchService?.setFaceChangeListener(this@MainActivity)
            dispatchService?.setActivity(this@MainActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dispatchService?.setActivity(null)
            dispatchService?.setFaceChangeListener(null)
            dispatchService = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        hideTitleAndNavigationBar()
        val decorView = window.decorView
        val uiOptions =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
        decorView.systemUiVisibility = uiOptions
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        var name = intent.getStringExtra("faceName")
        if (!name.isNullOrEmpty()) {
            intentFace = name
        }
        GeeUILogUtils.logd("MainActivity", "onCreate: 启动表情：" + name)
        bindDispatchService()
        initSurface()
        initCountDownTimer();
        noVoiceCmdView = (binding.speechClosedView)
        mSurfaceView = (binding.playerView)
        updateViews();
        //addCustomInfoUpdateListeners();

    }


    private fun updateViews() {
        if (RobotSubConfigManager.getInstance(this).speechCommandSwitch){
            noVoiceCmdView.visibility = View.GONE;
        }else{
            noVoiceCmdView.visibility = View.VISIBLE;
            countDownTimer?.start();
        }
    }



    private fun initCountDownTimer() {
        countDownTimer = object : CountDownTimer(5 * 1000, 500) {
            override fun onTick(millisUntilFinished: Long) {


            }
            override fun onFinish() {
                finish();
            }
        }

    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        var openType = intent?.getStringExtra(SpeechCmdUtil.SPEECH_OPEN_TYPE)
        GeeUILogUtils.logd(
            "MainActivity", "onNewIntent:启动 new intent_openType： $openType"
        )
        if (openType.equals(SpeechCmdUtil.SPEECH_CMD_STATUS_OPEN)){
            GeeUILogUtils.logd(
                "MainActivity", "onNewIntent:启动 new intent_openType ==== 1 ===="
            )
            noVoiceCmdView?.visibility = View.GONE;
            mSurfaceView?.visibility = View.VISIBLE;

        }else if (openType.equals(SpeechCmdUtil.SPEECH_CMD_STATUS_CLOSE)){
            GeeUILogUtils.logd(
                "MainActivity", "onNewIntent:启动 new intent_openType ==== 2 ===="
            )
            noVoiceCmdView?.visibility = View.VISIBLE;
            mSurfaceView?.visibility = View.GONE;

        }else{
            GeeUILogUtils.logd(
                "MainActivity", "onNewIntent:启动 new intent_openType ==== 3 ===="
            )
            mSurfaceView?.visibility = View.VISIBLE;
            noVoiceCmdView?.visibility = View.GONE;
        }

        if (!openType.equals(SpeechCmdUtil.SPEECH_CMD_STATUS_CLOSE)){
            var name = intent?.getStringExtra("faceName")
            if (!name.isNullOrEmpty()) {
                intentFace = name
            }

            GeeUILogUtils.logd(
                "MainActivity", "onNewIntent:启动 new intent： ${intent?.getStringExtra("faceName")}"
            )
        }

    }

    private fun addCustomInfoUpdateListeners() {

        ExpressionPathCallback.getInstance()
            .setExpressionPathListener(object : ExpressionPathListener {
                override fun updateExpressionPath(expressionPath: String) {
                    createMedia(expressionPath)
                }

                override fun expressionFileIsNoExit(fileName: String) {
                }
            })
    }

    private fun initSurface() {
        binding.playerView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceAvailable = true
                GeeUILogUtils.logd("MainActivity", "surfaceCreated: 启动初始化：" + intentFace)
                if (!intentFace.isNullOrBlank()) {
                    openVideo(intentFace)
                } else {
                    openVideo("h0006")
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int, width: Int, height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                GeeUILogUtils.logd("MainActivity", "surfaceDestroyed: ");
                surfaceAvailable = false
            }
        })
    }

    private fun bindDispatchService() {
        var intent = Intent(this@MainActivity, LTPAudioService::class.java)
        bindService(intent, dispatchConnection, Context.BIND_AUTO_CREATE)
    }

    override fun changeFace(faceName: String?) {
        GeeUILogUtils.logd(
            "MainActivity",
            "changeFace: ==--==$faceName" + (getWindow().getDecorView()
                .getVisibility() == View.VISIBLE)
        );

        faceName?.let { openVideo(it) }
    }

    private var mediaPlayer: IjkMediaPlayer? = null

    @Synchronized
    private fun openVideo(name: String) {
        GeeUILogUtils.logd(
            "MainActivity", "openVideo ======= 1 =======" + name
        );
        if (!surfaceAvailable) {
            GeeUILogUtils.logd(
                "MainActivity", "openVideo: 不展示视频1" + name
            );
            return
        }
        GeeUILogUtils.logd(
            "MainActivity", "openVideo ======= 2 =======" + name
        );

        if (currentFace.equals(name)) {
            GeeUILogUtils.logd(
                "MainActivity", "openVideo: 名字相同，暂不用切换新的表情" + name
            );
            return
        }
        GeeUILogUtils.logd(
            "MainActivity", "openVideo ======= 3 =======" + name
        );

        currentFace = name
//        releaseIjk()
//        mediaPlayer?.stop()
        binding.root.post {
            createMedia(name)
//            ExpressionCenter.getInstance(this@MainActivity).getExpressionPath(name)
        }

    }

    private var hasData = false
    private fun createMedia(name: String) {
        if (mediaPlayer == null) {
            mediaPlayer = IjkMediaPlayer()
            mediaPlayer?.let {
                //视频硬件解码
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1)
                //音频硬件解码
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1)
                //设置跳帧
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1)
                it.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1);
                it.setOnPreparedListener { mp ->
                    if (surfaceAvailable && mediaPlayer != null) {
                        it.setDisplay(binding.playerView.holder)
                        it.start()
                    }
                }
                it.setOnCompletionListener {
                    Thread.sleep(1)
                    it?.start()
                }
                it.setOnErrorListener { mp, what, extra ->
                    GeeUILogUtils.logd(
                        "TAG", "openVideo: onError: $mp  what:$what  $extra"
                    )
                    return@setOnErrorListener false
                }
            }
        }
        GeeUILogUtils.logd("MainActivity", "openVideo_name1: $name surfaceAvailable: $surfaceAvailable");
        mediaPlayer?.let {
            var afd = resources.assets.openFd("video/$name.mp4")
            var assetsFile = RawDataSourceProvider(afd)
            GeeUILogUtils.logd("MainActivity", "createMedia: $hasData")
            if (hasData) {
                it.setDataSource(assetsFile)
//                it!!.setDataSource(this@MainActivity, Uri.parse(name))
//                it.setDataSource(this@MainActivity, Uri.parse(name))
                it.prepareAsync()
                hasData = true
            } else {
                it.reset()
//                it.setDisplay(binding.playerView.holder)
                it.setDataSource(assetsFile)
//                it!!.setDataSource(this@MainActivity, Uri.parse(name))
//                it.setDataSource(this@MainActivity, Uri.parse(name))
                it.prepareAsync()
            }
        }
    }


    var exoPlayer: SimpleExoPlayer? = null
    private fun openVideoExoPlayer(name: String) {

        if (!surfaceAvailable) {
            GeeUILogUtils.logd("MainActivity", "openVideo: 不展示视频");
            return
        }


        if (currentFace.equals(name)) {
            GeeUILogUtils.logd("MainActivity", "openVideo1: 名字相同，暂不用切换新的表情" + name);
            return
        }
        currentFace = name
        if (exoPlayer == null) {
            exoPlayer = SimpleExoPlayer.Builder(this@MainActivity).build()
        }

        if (exoPlayer?.isPlaying == true) {
            exoPlayer?.stop()
//            exoPlayer?.release()
        }

        GeeUILogUtils.logd(
            "MainActivity", "openVideo_name: $name surfaceAvailable: $surfaceAvailable"
        );

        exoPlayer?.let {
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    if (playbackState == Player.STATE_READY) {
                        // 媒体资源准备好播放了
                        if (surfaceAvailable && exoPlayer != null) {
                            runOnUiThread {
                                exoPlayer!!.setVideoSurfaceHolder(binding.playerView.holder)
                                exoPlayer!!.play()
                                exoPlayer!!.repeatMode = REPEAT_MODE_ALL
                            }
                        }
                    }
                }
            })

            var path = "file:///android_asset/" + "video/$name.mp4"

            val mediaSource =
                ProgressiveMediaSource.Factory(DefaultDataSourceFactory(this@MainActivity))
                    .createMediaSource(MediaItem.fromUri(path))
            //            it.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            GeeUILogUtils.logd("RhjAudioManager", "openVideo: afd" + path)
            it.setMediaSource(mediaSource)
            it.prepare()
        }

    }

    private fun hideTitleAndNavigationBar() {
        /*val decorView = window.decorView
        decorView.setOnSystemUiVisibilityChangeListener { visibility: Int ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                // 当导航栏可见时，隐藏导航栏
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }

        //title
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY*/
        val decorView = window.decorView
        val uiOptions =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN
        decorView.systemUiVisibility = uiOptions
    }

    override fun onResume() {
        super.onResume()
        binding.root.keepScreenOn = true
        CustomTipsUpdateCallback.instance.updateCustomTips(false)
    }

    override fun onPause() {
        super.onPause()
        GeeUILogUtils.logd("MainActivity", "onPause: ")
        dispatchService?.uiEnterPause()
        if (mediaPlayer != null) {
            mediaPlayer!!.pause()
        }
//        if (mediaPlayer != null) {
//            releaseIjk()
//            finish()
//        } else {
//            isPlaying = false
//        }
    }

    private fun releaseIjk() {
        if (mediaPlayer != null) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } else {
            isPlaying = false
        }
    }

    override fun onStop() {
        super.onStop()
        if (mediaPlayer != null) {
            releaseIjk()
//            finish()
        } else {
            isPlaying = false
        }
        GeeUILogUtils.logd("MainActivity", "AIAudio onStop: ");
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(dispatchConnection)
        GeeUILogUtils.logd("speech MainActivity", "onDestroy: ")
    }

}