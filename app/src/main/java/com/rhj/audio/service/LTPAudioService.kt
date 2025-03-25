package com.rhj.audio.service

import android.app.ActivityManager
import android.app.Service
import android.content.*
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.*
import com.aispeech.dui.dds.DDS
import com.aispeech.dui.dds.agent.ASREngine
import com.aispeech.dui.dds.agent.ASREngine.ListeningParams
import com.aispeech.dui.dds.agent.SkillIntent
import com.aispeech.dui.dds.exceptions.DDSNotInitCompleteException
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.letianpai.robot.components.network.nets.AppStoreCmdConsts
import com.letianpai.robot.components.parser.appstore.AppStoreInfos
import com.letianpai.robot.components.parser.voiceconfig.VoiceConfig
import com.letianpai.robot.components.storage.RobotSubConfigManager
import com.letianpai.robot.components.utils.GeeUILogUtils
import com.letianpai.robot.components.utils.SpeechCmdUtil
import com.letianpai.robot.letianpaiservice.*
import com.renhejia.robot.commandlib.consts.AppCmdConsts
import com.renhejia.robot.commandlib.consts.MCUCommandConsts
import com.renhejia.robot.commandlib.consts.RobotRemoteConsts
import com.renhejia.robot.commandlib.consts.SpeechConst
import com.renhejia.robot.commandlib.parser.antennalight.AntennaLight
import com.renhejia.robot.commandlib.parser.antennamotion.AntennaMotion
import com.renhejia.robot.commandlib.parser.face.Face
import com.renhejia.robot.commandlib.parser.motion.Motion
import com.renhejia.robot.commandlib.parser.power.PowerMotion
import com.renhejia.robot.commandlib.parser.sound.Sound
import com.renhejia.robot.commandlib.threadpool.GestureDataThreadExecutor
import com.renhejia.robot.commandlib.utils.SystemUtil
import com.renhejia.robot.gesturefactory.manager.GestureCenter
import com.renhejia.robot.gesturefactory.parser.GestureData
import com.renhejia.robot.letianpaiservice.ILetianpaiService
import com.rhj.audio.App
import com.rhj.audio.Const
import com.rhj.audio.DmTaskResultBean
import com.rhj.audio.RhjAudioManager
import com.rhj.audio.bean.RemindBean
import com.rhj.audio.database.DMResultModel
import com.rhj.audio.model.IdentFaceModel
import com.rhj.audio.observer.RhjDMTaskCallback
import com.rhj.audio.observer.TtsStateChangeCallback
import com.rhj.audio.utils.SPUtils
import com.rhj.callback.CustomTipsUpdateCallback
import com.rhj.message.*
import com.rhj.player.PlayerService
import com.rhj.speech.MainActivity
import com.rhj.speech.broadcast.WIFIStateReceiver
import com.rhj.speech.http.SpeechDataRepository
import com.rhj.speech.model.*
import com.rhj.utils.CheckApkExist
import com.squareup.moshi.Moshi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.internal.sse.RealEventSource
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class LTPAudioService : Service() {

    private val INIT_FAILED_RETRY_COUNT = 5
    private var currentInitCount = 0
    private var recordManager: RecordManager? = null
    private var voiceType: String? = null
    private var isAiConfig: Boolean = false
    private var aiConfigId: String? = null
    private var aiConfigSecret: String? = null
    private var aiConfigKey: String? = null
    private var aiModelList: List<AIModel>? = null
    private var soundList: List<SoundConfigModel>? = null
    private var mWIFIStateReceiver: WIFIStateReceiver? = null
    private var danceConfig = DanceConfigModel()
    private var musicList: MessageMediaListBean? = null
    private var musicTTTsId: String = ""
    private var isConnectService = false
    private var iLetianpaiService: ILetianpaiService? = null
    private var mContext: Context? = null
    private val TAG = "LTPAudioService"
    private var gson: Gson = Gson()
    private var moshi = Moshi.Builder().build()
    private var playerService: PlayerService? = null
    private var singleSong = false
    private var hasMediaPlay = false
    private var hasDanceMediaPlay = false
    private var isAiModel = false
    private var aiCommandEnd = false
    private val MESSAGE_STOP_UI = 0x10001
    private val MESSAGE_START_WAKEUP = 0x100012
    private val MESSAGE_START_BIRTHDAY = 0x100013
    private val MESSAGE_CLOSE_ACTIVITY = 0x100014
    private val CLOSE_ACTIVITY_DELAY_TIME = 3000L
    private var lastTTSId = ""

    //本地星火大模型生成的id
    private var localTTSId = ""

    //本地是否停止
    private var localAiMessageEnd = false

    private var aiMessageEnd = false
    private var startSpeakAiMessage = false
    private var activity: MainActivity? = null
    private var asrEngine: ASREngine? = null
    private var asrEngineListening: Boolean = false
    private var robotStatus = ROBOT_STATUS_OTHER
    private var databaseSingleThread = Executors.newSingleThreadExecutor()
    private val speechDataRepository = SpeechDataRepository(this)
    private var isXuanYaStop = false
    private lateinit var request: Request
    private lateinit var okHttpClient: OkHttpClient
    private var stringBuilder: StringBuilder? = StringBuilder()

    //    private var isSpeechCommandOpened: Boolean = true;
    private var isSpeechCommandOpened: Boolean = false;
    private final val REMOTE_SPEECH_COMMAND: String = "updateDeviceVoiceConfig";//TODO 等待永保提交后更换

    /**
     * 当前模式是
     * Chatgpt 0
     * 星火 1
     * 其他 2
     */
    private var aiModel = 1

    /**
     * 当前命中的技能
     */
    private var currentSkill: String = ""
    private var lastMusicSkill: String = ""

    /**
     * 当前唤醒状态
     */
    private var currentWakeState: String = ""

    private var isCharging = false

    /**
     * 唤醒的时候是否执行动作
     */
    private var wakeUpOnlyShowFace = true
    private val chargingStatueReceiver = ChargingReceiver();

    /**
     * 闲聊模式的ID
     */
    private var chatId = "1900060100000061"

    /**
     * 百科
     */
    private var baikeId = "1900060100000062"

    /**
     * 闲聊大全
     */
    private var chatAllId = "ZH2019110100000103"


    /**
     * 有闹钟等事件存在的时候，直接不进入对话
     */
    private var wakeupShutdown = false
    private val params = ListeningParams()
    private var faceChangeListener: OnFaceChangeListener? = null
    private var handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MESSAGE_STOP_UI -> {
                    GeeUILogUtils.logd("LTPAudioService", "handleMessage: MESSAGE_STOP_UI")
                    stopUI()
                }

                MESSAGE_START_WAKEUP -> {
                    GeeUILogUtils.logd("LTPAudioService", "handleMessage: MESSAGE_START_WAKEUP")
                    RhjAudioManager.getInstance().enableWakeup()
                }
                MESSAGE_START_BIRTHDAY -> {
                    birthdayDBListener()
                }
                MESSAGE_CLOSE_ACTIVITY -> {
                    closeActivity()
                }
            }
        }
    }


    private val connectionPlayerService: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            GeeUILogUtils.logd(TAG, "LTPAudioService onServiceConnected: " + service.javaClass)
            val myBinder = service as PlayerService.MyBinder
            val p = myBinder.service
            GeeUILogUtils.logd(TAG, "LTPAudioService onServiceConnected: $p")
            playerService = p
            playerService!!.setOnFinishListener { finishMusic() }
            if (activity == null) {
                unbindPlayer()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            GeeUILogUtils.logd(TAG, "onServiceDisconnected: ")
            hasMediaPlay = false
            playerService = null
        }
    }

    private fun unbindPlayer() {
        GeeUILogUtils.logd(TAG, "unbindPlayer: ${playerService != null}")
        if (playerService != null) {
            playerService?.pause()
            unbindService(connectionPlayerService)
            hasMediaPlay = false
            playerService = null
        }
    }

    private val mChatConnection: ServiceConnection? = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            GeeUILogUtils.logd("LTPAudioService", "onServiceConnected: mChatConnection")
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            GeeUILogUtils.logd("LTPAudioService", "onServiceDisconnected: mChatConnection")
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            GeeUILogUtils.logd("LTPAudioService", "LTPAudioService 乐天派 完成AIDLService服务")
            iLetianpaiService = ILetianpaiService.Stub.asInterface(service)
            try {
                iLetianpaiService?.registerRobotStatusCallback(ltpRobotStatusCallback)
                iLetianpaiService?.registerLCCallback(ltpLongConnectCallback)
                iLetianpaiService?.registerTTSCallback(ltpTTSCallback)
                iLetianpaiService?.registerSensorResponseCallback(ltpSensorResponseCallback)
                iLetianpaiService?.registerAppCmdCallback(ltpAppCmdCallback)
                addTipsUpdateCallback()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isConnectService = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            GeeUILogUtils.logd(
                "LTPAudioService",
                "LTPAudioService 乐天派 无法绑定aidlserver的AIDLService服务"
            )
            isConnectService = false
        }
    }
    private val ltpRobotStatusCallback: LtpRobotStatusCallback.Stub =
        object : LtpRobotStatusCallback.Stub() {
            override fun onRobotStatusChanged(command: String, data: String) {
                GeeUILogUtils.logd("ltpRobotStatusCallback", "command: $command-----$data")
                when (command) {
                    COMMAND_TYPE_START_AUDIO_SERVICE -> {
                        RhjAudioManager.getInstance().enableWakeup()
                    }

                    COMMAND_TYPE_STOP_AUDIO_SERVICE -> {
                        RhjAudioManager.getInstance().disableWakeup()
                    }

                    COMMAND_TYPE_SHUT_DOWN_AUDIO_SERVICE -> {
                        RhjAudioManager.getInstance().stopDialog()
                    }

                    AppCmdConsts.COMMAND_TYPE_SET_ROBOT_MODE -> {
                        when (data) {
                            AppCmdConsts.COMMAND_VALUE_CLOCK_START -> {
                                wakeupShutdown = true
                                DDS.getInstance().agent.wakeupEngine.updateWkupRouter("partner")
                            }

                            AppCmdConsts.COMMAND_VALUE_CLOCK_STOP -> {
                                wakeupShutdown = false
                                DDS.getInstance().agent.wakeupEngine.updateWkupRouter("dialog")
                            }
                        }
                    }

                    RobotRemoteConsts.COMMAND_TYPE_ROBOT_STATUS -> {
                        robotStatus = if (data == RobotRemoteConsts.COMMAND_VALUE_GO_TO_SLEEP) {
                            ROBOT_STATUS_SLEEP
                        } else {
                            ROBOT_STATUS_OTHER
                        }
                    }
                }
            }
        }
    private val ltpLongConnectCallback: LtpLongConnectCallback.Stub =
        object : LtpLongConnectCallback.Stub() {
            override fun onLongConnectCommand(command: String, data: String) {
                GeeUILogUtils.logd("ltpAppCmdCallback", "command: $command-----$data")
                when (command) {
                    "removeDevice" -> {
                        GlobalScope.launch {
                            closeActivity()
                            delay(1000)
                            System.exit(0)
                        }
                    }
                    "updateAwakeConfig" -> {
                        GlobalScope.launch {
                            val a = moshi.adapter<WakeUpModel>(WakeUpModel::class.java)
                            val wakeUp = a.fromJson(data)
                            wakeUp?.let { updateWakeConfig(it) }
                        }
                    }
                    //长连下发星火大模型的配置更新
                    "updateVoiceAideEventData" -> {
                        getAIConfiguration()
                    }
                    "speechDance" -> {
                        sendAidlMessage(SpeechConst.COMMAND_WAKE_UP_STATUS, "avatar.listening")
                        danceAction(true, true)
                    }
                    "speechMusic" -> {
                        sendAidlMessage(SpeechConst.COMMAND_WAKE_UP_STATUS, "avatar.listening")
                        danceAction(false, true)
                    }
                    "updateWeatherConfig" -> {
                        try {
                            val weather = JSONObject(data).optString("default_city_name")
                            if (weather != null) {
                                RhjAudioManager.getInstance().updateWeatherLocation(weather)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    REMOTE_SPEECH_COMMAND -> {
                        updateSpeechCommandStatus(data)
                    }
                }
            }
        }

    private fun updateSpeechCommandStatus(data: String) {
        //TODO 解析data数据给 isSpeechCommandOpened 赋值
        val voiceConfig = gson?.fromJson(data, VoiceConfig::class.java)
        voiceConfig?.is_close_voice_aide?.let {
            isSpeechCommandOpened = voiceConfig?.is_close_voice_aide == 0
        }
        var routerCommand = "";
        if (isSpeechCommandOpened) {
            routerCommand = "dialog"
        } else {
            routerCommand = "partner"
        }
        RobotSubConfigManager.getInstance(mContext).speechCommandSwitch = isSpeechCommandOpened;
        RobotSubConfigManager.getInstance(mContext).commit();

        try {
            DDS.getInstance().agent.wakeupEngine.updateWkupRouter(routerCommand)
        } catch (e: DDSNotInitCompleteException) {
            e.printStackTrace()
        }

//        if (isSpeechCmdSwitchOpened){
//            try {
//                DDS.getInstance().agent.wakeupEngine.updateWkupRouter("dialog")
//            } catch (e: DDSNotInitCompleteException) {
//                e.printStackTrace()
//            }
//        }else{
//            try {
//                DDS.getInstance().agent.wakeupEngine.updateWkupRouter("partner")
//            } catch (e: DDSNotInitCompleteException) {
//                e.printStackTrace()
//            }
//        }

    }

    private fun updateWakeConfig(wakeUp: WakeUpModel) {
        RhjAudioManager.getInstance().setSpeaker(mContext, wakeUp?.selectedVoiceId)
        RhjAudioManager.getInstance().setWakeupWord(
            mContext,
            wakeUp?.xiaoleSwitch == 1,
            wakeUp?.xiaopaiSwitch == 1,
            wakeUp?.xiaotianSwitch == 1,
            wakeUp?.letianpaiSwitch == 1,
            wakeUp?.customSwitch == 1,
            wakeUp?.customTitle,
            wakeUp?.customPinyin
        )
        SPUtils.getInstance(this@LTPAudioService)
            .putInt(WAKEUP_SHOW_MOTION, wakeUp?.moreModalSwitch ?: 0)
        wakeUpOnlyShowFace = wakeUp?.moreModalSwitch == 0
        RhjAudioManager.getInstance().enableWakeup()
    }

    private val ltpTTSCallback: LtpTTSCallback.Stub = object : LtpTTSCallback.Stub() {
        override fun onTTSCommand(command: String, data: String) {
            GeeUILogUtils.logd(
                "LTPAudioService", "onTTSCommand: 需要tts的command：$command  内容：$data"
            )
            when (command) {
                "speakText" -> {
                    if (hasMediaPlay) {
                        if ("暂停" == data) {
                            pauseMusic()
                        }
                    } else if ("shutdown" == data) {
                        shutdownTts()
                    } else if ("closeAiAutoStart" == data) {
                        aiCommandEnd = true
                    } else if ("openAiAutoStart" == data) {
                        aiCommandEnd = false
                    } else {
                        if (data.contains("拍照成功，请在手机端查看")) {
                            showWakeGesture("avatar.speaking")
                            lastTTSId = TTS_TAKE_PHOTO_END
                            RhjAudioManager.getInstance().speak(data)
                        } else {
                            RhjAudioManager.getInstance().speak(data)
                        }
                    }
                }

                "sendToSpeech" -> {
                    //收到ai 的结果
                    if ("onClosed" == data) {
                        GeeUILogUtils.logd(TAG, "onTTSCommand:思必驰接受到onClosed指令 $data")
                        aiMessageEnd = true
                    } else {
                        if (!aiMessageEnd) {
                            receiveAiData(data)
                        }
                    }

                }

                "shutdown" -> {
                    shutdownTts()
                }

                SpeechConst.COMMAND_CLOSE_SPEECH_AUDIO -> {
                    shutdownAll(false)
                }

                AppCmdConsts.COMMAND_CLIFF_TRIGGER -> {
                    shutdownAll(false)
                }

                SpeechConst.COMMAND_CLOSE_SPEECH_AUDIO_AND_LISTENING -> {
                    shutdownAll(true)
                }

                else -> {
                }
            }
        }
    }

    private val ltpSensorResponseCallback = object : LtpSensorResponseCallback.Stub() {
        override fun onSensorResponse(command: String?, data: String?) {
            GeeUILogUtils.logd("LTPAudioService", "onSensorResponse: $command   data:$data")
            when (command) {
                RobotRemoteConsts.COMMAND_TYPE_CONTROL_FALL_BACKEND, RobotRemoteConsts.COMMAND_TYPE_CONTROL_FALL_FORWARD, RobotRemoteConsts.COMMAND_TYPE_CONTROL_FALL_LEFT, RobotRemoteConsts.COMMAND_TYPE_CONTROL_FALL_RIGHT, RobotRemoteConsts.COMMAND_TYPE_CONTROL_PRECIPICE_START_DATA -> {
                    GeeUILogUtils.logd("LTPAudioService", "onSensorResponse: 传感器触发，关闭语音")
                    shutdownXuanYa()
                }
            }
        }
    }
    private val ltpAppCmdCallback = object : LtpAppCmdCallback.Stub() {
        override fun onAppCommandReceived(command: String?, data: String?) {
            GeeUILogUtils.logd(TAG, "onAppCommandReceived: $command  $hasBirthday   data:$data")
            when (command) {
                AppStoreCmdConsts.COMMAND_INSTALL_APP_STORE_SUCCESS -> {
                    val appInfo = gson?.fromJson(data, AppStoreInfos::class.java)
                    appInfo?.displayName?.let {
                        RhjAudioManager.getInstance().updateVocabs(arrayListOf(it))
                    }
                }
                "identFaceResult" -> {
                    if (hasBirthday) {
                        identFaceResult(data)
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * 是否有任务要执行
     * 需要等待任务执行完毕才能stopui
     */
    private var hasMotion = false
    private var hasBirthday = false
    private var lastAiWorkID = ""

    private var identFaceResultList: List<IdentFaceModel>? = null
    val faceType = object : TypeToken<List<IdentFaceModel>>() {}.type
    private fun identFaceResult(data: String?) {
        if (data != null) {
            identFaceResultList = gson.fromJson<List<IdentFaceModel>>(data, faceType)
        }
    }

    private fun birthdayDBListener() {
        recordManager = RecordManager(this@LTPAudioService)
        recordManager?.dbChangeListener = object : RecordManager.OnDBChangeListener {
            override fun onChange(dbNumber: Int) {
                GeeUILogUtils.logd(
                    TAG,
                    "onChange:dbNumber: $dbNumber  ${identFaceResultList.toString()}"
                )
                if (dbNumber > 60 && !identFaceResultList.isNullOrEmpty()) {
                    var maxAreaPercent = 0.0

                    identFaceResultList?.forEach {
                        maxAreaPercent = it.areaPercent?.let { percent ->
                            Math.max(
                                maxAreaPercent, percent
                            )
                        } ?: maxAreaPercent
                    }
                    GeeUILogUtils.logd(TAG, "onChange: 有人脸 且分贝大于60 最大人脸面积：$maxAreaPercent")
                    if (maxAreaPercent > 8) {
                        blowOutCandles()
                    }
                }
            }
        }
        recordManager?.startRecording()
        startIdentFaceService()
    }

    private fun blowOutCandles() {
        hasBirthday = false
        recordManager?.stopRecording()
        stopIdentFaceService()
        showGestures(
            GestureCenter.birthdayBlowOutGestureData(), GESTURE_MOTION_HAPPY_BIRTHDAY_BLOW_OUT
        )
    }

    private fun startIdentFaceService() {
        val inte = Intent().apply {
            component = ComponentName("com.ltp.ident", "com.ltp.ident.services.IdentFaceService")
            putExtra("identNeedOwenr", false)
            putExtra("identAutoStop", false)
            putExtra("motionNumber", 22)
            putExtra("hasMotion", false)
            putExtra("identNeedEveryFrameResult", true)
        }
        startService(inte)
    }

    private fun stopIdentFaceService() {
        iLetianpaiService?.setAppCmd("killProcess", "com.ltp.ident")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        GeeUILogUtils.logd(
            TAG,
            "onStart speech: " + SystemUtil.get(SystemUtil.REGION_LANGUAGE, "zh")
        )
        if ("zh" != SystemUtil.get(SystemUtil.REGION_LANGUAGE, "zh")) {
            GeeUILogUtils.logd(TAG, "onCreate: stopself speech")
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        mContext = this
        delCache()
        init()
        initChargingStatus()
    }

    private fun delCache() {
        val path = cacheDir.absolutePath + "tmp.pcm"
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun initChargingStatus() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED)
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        registerReceiver(chargingStatueReceiver, intentFilter)

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        isCharging =
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun init() {
        gson = Gson()
        wakeUpOnlyShowFace =
            SPUtils.getInstance(this@LTPAudioService).getInt(WAKEUP_SHOW_MOTION) == 0
        connectService()
//        bingChatGptServi()
//        bingGuoneiServi()
        if (WIFIStateReceiver.isWifiConnected(this)) {
            initAudio()
            initConfig()
            getAIConfiguration()
        } else {
            registerWifiReceiver()
        }
    }


    private fun initConfig() {
        databaseSingleThread.execute {
            GlobalScope.launch {
                var all = (application as App).getDatabase().dmResultDao().loadAll()
                postLog(all)
                getConfig()
                getWakeConfig()
//                getAiConfig()
            }
        }
    }

    private fun addTipsUpdateCallback() {
        CustomTipsUpdateCallback.instance.setCountDownInfoUpdateListener(object :
            CustomTipsUpdateCallback.CustomTipsUpdateListener {
            override fun updateCustomTips(showTips: Boolean) {
                if (!showTips) {
                    try {
                        iLetianpaiService?.setAppCmd(
                            RobotRemoteConsts.COMMAND_SET_APP_MODE, "hide_charging"
//                            RobotRemoteConsts.COMMAND_HIDE_TEXT
                        )
                    } catch (e: RemoteException) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    private var audioInput: String = ""
    private fun initAudio() {
        RhjAudioManager.getInstance().setInitCallback {
            initSpeechCommandStatus();
        }

        RhjAudioManager.getInstance().setAuthStatusCallback { initSuccess ->
            if (!initSuccess) {
                GlobalScope.launch {
                    if (currentInitCount < INIT_FAILED_RETRY_COUNT) {
                        delay(3000)
                        RhjAudioManager.getInstance()
                            .init(this@LTPAudioService, "72ff19dd886a72ff19dd886a645625bb")
                    }
                    currentInitCount++
                }
            }
        }

        RhjAudioManager.getInstance().init(this, "72ff19dd886a72ff19dd886a645625bb")
        RhjAudioManager.getInstance().setWakeupStateChangeCallback { stateData: String ->
            GeeUILogUtils.logd("LTPAudioService", "setWakeupStateChangeCallback: $stateData")
            showWakeGesture(stateData)
        }
        RhjAudioManager.getInstance().setVadBeginCallbackList {
            showWakeGesture("sys.vad.begin")
        }
        RhjAudioManager.getInstance().setWakeupDoaCallback { doaData: String ->
            GeeUILogUtils.logd(
                "LTPAudioService", "setWakeupDoaCallback: $doaData"
            )
//            dealDoaData(doaData)
        }
        RhjAudioManager.getInstance().setWakeupResultCallback { result: String ->
            GeeUILogUtils.logd("LTPAudioService", "setWakeupResultCallback result:$result")
            dealWakeupResult(result)
        }
        RhjAudioManager.getInstance().setCommandCallback { command: String, data: String ->
            dealCommand(
                command, data
            )
        }
        RhjAudioManager.getInstance()
            .setMessageCallback { messageBean: MessageBean -> dealMessage(messageBean) }
        RhjAudioManager.getInstance().setTtsStateChangeCallback(object : TtsStateChangeCallback {
            override fun onSpeakBegin() {
                GeeUILogUtils.logi("LTPAudioService", "tts ======= onSpeakBegin")
                //                setText("setTtsStateChangeCallback", "1");
            }

            override fun onSpeakEnd(ttsId: String, errorCode: Int) {
                GeeUILogUtils.logi(
                    "LTPAudioService",
                    "tts ======= onSpeakEnd $ttsId  $musicTTTsId  isAiModel:$isAiModel"
                )
                if (activity == null) {
                    return
                }
                if (isAiModel && ttsId == lastTTSId && aiMessageEnd) {
                    startSpeakAiMessage = false
                    startASRGesture()
                }
                //本地大模型播报
                else if (ttsId == localTTSId && localAiMessageEnd) {
                    GeeUILogUtils.logd(
                        "LTPAudioService",
                        "onSpeakEnd:  ${localTTSId}  ${localAiMessageEnd}  $aiCommandEnd" + " 478"
                    );
                    if (aiCommandEnd) {
                        stopUI()
                    } else {
                        RhjAudioManager.getInstance().startDialog()
                        localAiMessageEnd = false
                    }
                } else if (!musicTTTsId.isNullOrEmpty() && ttsId == musicTTTsId) {
                    startMediaPlayer()
                } else if (lastTTSId == TTS_TAKE_PHOTO_END) {
                    lastTTSId = ""
                    iLetianpaiService?.setAppCmd(
                        AppCmdConsts.COMMAND_TYPE_TAKE_PHOTO,
                        "${AppCmdConsts.COMMAND_VALUE_EXIT_TAKE_PHOTO}"
                    )
                }
            }

            override fun onSpeakProgress(current: Int, total: Int) {
                GeeUILogUtils.logd(
                    "LTPAudioService", "tts ======= current $current total $total"
                )
            }

            override fun error(s: String) {
                GeeUILogUtils.logd("LTPAudioService", "tts ======= error: $s")
                //                setText("setTtsStateChangeCallback", "4");
            }
        })
        RhjAudioManager.getInstance().setFmodStateChangeCallback {
            GeeUILogUtils.logd("LTPAudioService", "onFinish: ")
            if (isAiModel) {
                startASRGesture()
            }
        }
        RhjAudioManager.getInstance().setRhjDMTaskCallback(object : RhjDMTaskCallback {
            override fun dealResult(dmResult: DmTaskResultBean): Boolean {
                var result = false
                try {
                    //进入闲聊模式
                    //输入为空走默认的方法
                    //匹配之后走大模型的方法
                    if ((dmResult.skillId == chatId || dmResult.skillId == baikeId || dmResult.skillId == chatAllId || dmResult.skillId.isEmpty()) && dmResult.dmInput.isNotEmpty() && isAiConfig) {

                        audioInput = dmResult.dmInput

//                        RhjAudioManager.getInstance().stopDialog()
                        GeeUILogUtils.logd("LTPAudioService", "dealResult:voiceType:$voiceType ")
//                        dealAiRequest(dmResult.dmInput)
                        result = true
                    }
                    saveDmResult2Database(dmResult)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return result
            }

            override fun dealErrorResult() {
            }
        })
    }

    private fun initSpeechCommandStatus() {
        var routerCommand = "";
        if (RobotSubConfigManager.getInstance(mContext).speechCommandSwitch) {
            routerCommand = "dialog"
        } else {
            routerCommand = "partner"
        }
        try {
            DDS.getInstance().agent.wakeupEngine.updateWkupRouter(routerCommand)
        } catch (e: DDSNotInitCompleteException) {
            e.printStackTrace()
        }
    }

    private fun dealAiRequest(inputPrams: String) {
        audioInput = ""
//        hasMotion = true
        var currentUUID = UUID.randomUUID().toString()
        lastAiWorkID = currentUUID
        databaseSingleThread.execute {
            GlobalScope.launch {
                when (voiceType) {
                    "xh", "wx" -> {
                        sendToAiXFAndWX(inputPrams, currentUUID)
                    }
                    "other", "other4", "sysGpt" -> {
                        sendToChatGpt(inputPrams, currentUUID)
                    }
                    else -> {
                        sendToAiXFAndWX(inputPrams, currentUUID)
                    }
                }
            }
        }
    }

    private fun saveDmResult2Database(d: DmTaskResultBean) {
        databaseSingleThread.execute {
            val dm = DMResultModel(
                d.display,
                d.dmInput,
                d.endSessionReason?.toString(),
                d.from,
                d.intentName,
                d.nlg,
                d.recordId,
                d.sessionId,
                d.shouldEndSession,
                d.skillId,
                d.skillName,
                d.speakUrl,
                d.ssml,
                d.task,
                d.taskId,
                d.watchId
            )
            GlobalScope.launch {
                (application as App).getDatabase().dmResultDao().insertAll(dm!!)
                val all = (application as App).getDatabase().dmResultDao().loadAll()
                postLog(all)
            }
        }
    }

    private fun getFormChatgpt(callback: EventSourceListener?, videoInput: String?) {
        val urlBuilder: HttpUrl.Builder =
            "http://yourdomain/apipath".toHttpUrlOrNull()!!.newBuilder()
        val sn = Build.getSerial()
        urlBuilder.addQueryParameter("openai_key", aiConfigKey)
        urlBuilder.addQueryParameter("q", videoInput)
        urlBuilder.addQueryParameter("sn", sn)
        urlBuilder.addQueryParameter("country", "cn")
        urlBuilder.addQueryParameter("key", "")
        urlBuilder.addQueryParameter("voice_type", voiceType)
        val url: String = urlBuilder.build().toString()
//        request = Builder().url(url).build()
        request = Request.Builder().url(url).build()
        okHttpClient = OkHttpClient.Builder().connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES) //这边需要将超时显示设置长一点，不然刚连上就断开，之前以为调用方式错误被坑了半天
            .build()
        // 实例化EventSource，注册EventSource监听器
        val realEventSource = RealEventSource(request, callback!!)
        realEventSource.connect(okHttpClient) //真正开始请求的一步
    }

    private fun getFormXFAndWX(callback: EventSourceListener?, videoInput: String?) {
        var currentTimeMillis = System.currentTimeMillis()
        val sn = Build.getSerial()
        val urlBuilder: HttpUrl.Builder =
            "https://yourdomain.com/apipath".toHttpUrlOrNull()!!
                .newBuilder()
        // 读取SD卡文件里面的内容
        urlBuilder.addQueryParameter("q", videoInput)
        urlBuilder.addQueryParameter("sn", sn)
        urlBuilder.addQueryParameter("key", "")
        urlBuilder.addQueryParameter("ts", currentTimeMillis.toString())
        urlBuilder.addQueryParameter("voice_type", voiceType)
        urlBuilder.addQueryParameter("country", "cn")
        urlBuilder.addQueryParameter("aide", voiceType)
        val url: String = urlBuilder.build().toString()
        request = Request.Builder().url(url).build()
        okHttpClient = OkHttpClient.Builder().connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES) //这边需要将超时显示设置长一点，不然刚连上就断开，之前以为调用方式错误被坑了半天
            .build()
        // 实例化EventSource，注册EventSource监听器
        val realEventSource = RealEventSource(request, callback!!)
        realEventSource.connect(okHttpClient) //真正开始请求的一步
    }

    private fun sendToAiXFAndWX(data: String, currentUUID: String) {
        GeeUILogUtils.logd("LTPAudioService", "sendToAiXFAndWX: ${data} " + " 601");
        showGestures(aiUnderstandGesture, -1)
        getFormXFAndWX(object : EventSourceListener() {
            override fun onClosed(eventSource: EventSource) {
                super.onClosed(eventSource)
                GeeUILogUtils.logd(
                    "LTPAudioService",
                    "onClosed:  ${eventSource}  ${localAiMessageEnd}  ${stringBuilder.toString()}" + " 572"
                );
                if (lastAiWorkID != currentUUID) {
                    GeeUILogUtils.logd("onEvent: ", "onclose更换id 不在处理")
                    return
                }
                localAiMessageEnd = true
                if (!stringBuilder.isNullOrEmpty()) {
                    readSmartResult(stringBuilder.toString())
                    stringBuilder!!.setLength(0)
                }
            }

            override fun onEvent(
                eventSource: EventSource, id: String?, type: String?, data: String
            ) {
                super.onEvent(eventSource, id, type, data)
                GeeUILogUtils.logd("LTPAudioService", "onEvent:星火返回的数据 ${data}  ${type} " + " 616");
                if (lastAiWorkID != currentUUID) {
                    GeeUILogUtils.logd("onEvent: ", "更换id 不在处理")
                    return
                }
                if ("message" == type) {
                    stringBuilder?.append(data.replace("$", ""))
                    val fullString = stringBuilder.toString()
                    if (fullString.contains("。")) {
                        val indexOfComma = fullString.lastIndexOf('。')
                        if (indexOfComma != -1) {
                            readSmartResult(fullString.substring(0, indexOfComma))
                            stringBuilder!!.setLength(0)
                            var endString = fullString.substring(
                                indexOfComma, fullString.length
                            )
                            if (endString.length > 1) {
                                stringBuilder!!.append(
                                    endString.substring(1, endString.length)
                                )
                            }
                        }
                    } else if (fullString.contains("，")) {
                        val indexOfComma = fullString.lastIndexOf("，")
                        if (indexOfComma != -1) {
                            readSmartResult(fullString.substring(0, indexOfComma))
                            stringBuilder!!.setLength(0)
                            var endString = fullString.substring(
                                indexOfComma, fullString.length
                            )
                            if (endString.length > 1) {
                                stringBuilder!!.append(
                                    endString.substring(1, endString.length)
                                )
                            }
                        }
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                super.onFailure(eventSource, t, response)
                GeeUILogUtils.loge("LTPAudioService", "onFailure: ${response} ")
            }

            override fun onOpen(eventSource: EventSource, response: Response) {
                super.onOpen(eventSource, response)
                GeeUILogUtils.logd("LTPAudioService", "onOpen:  " + " 638");
//                GeeUILogUtils.logd("XunFeiService", "onOpen:  $response" + " 212");
            }
        }, data)
    }

    private fun sendToChatGpt(data: String, currentUUID: String) {
        GeeUILogUtils.logd("LTPAudioService", "sendToChatGpt:  $data  $currentUUID");
        showGestures(aiUnderstandGesture, -1)
        getFormChatgpt(object : EventSourceListener() {
            override fun onClosed(eventSource: EventSource) {
                super.onClosed(eventSource)
                GeeUILogUtils.logd(
                    "LTPAudioService", "sendToChatGpt onClosed:  ${lastAiWorkID} ${currentUUID} "
                );
                if (lastAiWorkID != currentUUID) {
                    GeeUILogUtils.logd("onEvent: ", "sendToChatGpt onClose更换id 不在处理")
                    return
                }
                localAiMessageEnd = true
                if (!stringBuilder.isNullOrEmpty()) {
                    readSmartResult(stringBuilder.toString())
                    stringBuilder!!.setLength(0)
                }
//                setText(AiConsts.SEND_TO_SPEACH, "onClosed")
//                GeeUILogUtils.logd("MyService", "onClosed: $eventSource 98")
            }

            override fun onEvent(
                eventSource: EventSource, id: String?, type: String?, data: String
            ) {
                super.onEvent(eventSource, id, type, data)
                GeeUILogUtils.logd(
                    "LTPAudioService",
                    "sendToChatGpt onEvent:  ${lastAiWorkID} ${currentUUID}  eventSource:$eventSource "
                );
                if (lastAiWorkID != currentUUID) {
                    GeeUILogUtils.logd("onEvent: ", "sendToChatGpt onEvent 更换id 不在处理")
                    return
                }
                if ("message" == type) {
//                            String regex = "\\p{Punct}";
                    stringBuilder?.append(data.replace("$", ""))
                    if (stringBuilder.toString().contains("。")) {
                        readSmartResult(stringBuilder.toString())
                        stringBuilder!!.setLength(0)
                    } else {
                    }
                } else {
                    readSmartResult(data)
//                    setText(AiConsts.SEND_TO_SPEACH, data)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                super.onFailure(eventSource, t, response)
//                GeeUILogUtils.logd("MyService", "onFailure: $response $t 124")
                GeeUILogUtils.logd("LTPAudioService", "onFailure:  " + " 681");
            }

            override fun onOpen(eventSource: EventSource, response: Response) {
                super.onOpen(eventSource, response)
//                GeeUILogUtils.logd("MyService", "onOpen: $response 130")
                GeeUILogUtils.logd("LTPAudioService", "onOpen:  " + " 687");
            }
        }, data)
    }

    private fun readSmartResult(result: String) {
        if (result.isNullOrEmpty()) {
            return
        }
        localTTSId = UUID.randomUUID().toString()
        GeeUILogUtils.logd(
            "LTPAudioService",
            "readResult:发送的数据 ${localTTSId}  ${result} " + " 714"
        );
//        showGestures(aiSpeakGesture, -1)
//        showGestures(GestureCenter.getSpeakingGesture(), -1)
        showGestures(GestureCenter.getSpeakingWithAIGesture(), -1)
        RhjAudioManager.getInstance().speak(result, localTTSId)
    }

    private fun dealWakeupResult(result: String) {
        handler.removeMessages(MESSAGE_CLOSE_ACTIVITY)
        if (isAiModel) {
            GeeUILogUtils.logd(TAG, "dealWakeupResult: isAiModel:${isAiModel} ")
            if (asrEngine != null && asrEngineListening) {
                asrEngine!!.cancel()
                asrEngine!!.stopListening()
            }
            startASRGesture()
        }
        if (hasBirthday) {
            blowOutCandles()
        }
        shutDown()
        pauseMusic()
        openDisableView();
    }

    private fun openDisableView() {
        if (RobotSubConfigManager.getInstance(mContext).speechCommandSwitch) {
            return
        }
        if (activity == null || ((getTopActivityName(this) != "com.rhj.speech.MainActivity"))) {
            isXuanYaStop = false
            val intent = Intent(this@LTPAudioService, MainActivity::class.java)
            intent.putExtra(
                SpeechCmdUtil.SPEECH_OPEN_TYPE, SpeechCmdUtil.SPEECH_CMD_STATUS_CLOSE
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)

        }
    }

    /**
     * 唤醒的角度回掉回来
     *
     * @param doaData {"doa":355,"wakeupWord":"嗨，小乐","wakeupType":"major"}
     */
    private fun dealDoaData(doaData: String) {
        try {
            val jsonObject = JSONObject(doaData)
            val degree = jsonObject.getInt("doa")
            GeeUILogUtils.logd(TAG, "LTPAudioService dealDoaData: 唤醒的角度：$degree")
            //旋转一定的角度
//            CommandResponseCallback.getInstance().setCommand("voice", MCUCommandConsts.COMMAND_AUDIO_TURN_AROUND, 9);
            //再拉起来某个应用
//            openFaceIdent();
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun dealCommand(command: String, data: String) {
        GeeUILogUtils.logd(TAG, "收到命令指令: $command  data $data")
        when (command) {
            Const.Remind.Insert -> {
                GeeUILogUtils.logd("letianpai_12346: ", "addClock_times.length:0000-1 ")
                try {
                    val jsonObject = JSONObject(data)
                    val extra = jsonObject.optString("extra")
                    val remind = gson!!.fromJson(extra, RemindBean::class.java)
                    GeeUILogUtils.logd(TAG, "MainActivity dealCommand: remind:" + remind.content[0])
                    GeeUILogUtils.logd("letianpai_12346: ", "addClock_times.length:0000-2 ")
                    //闹钟是每一个都得发还是统一发一个
                    setRemindAction(remind, true)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            Const.Remind.Remove -> {
                GeeUILogUtils.logd("letianpai_12346: ", "addClock_times.length:0000-1 ")
                try {
                    val jsonObject = JSONObject(data)
                    val extra = jsonObject.optString("extra")
                    val remind = gson!!.fromJson(extra, RemindBean::class.java)
                    GeeUILogUtils.logd(TAG, "MainActivity dealCommand: remind:" + remind.content[0])
                    GeeUILogUtils.logd("letianpai_12346: ", "addClock_times.length:0000-2 ")
                    setRemindAction(remind, false)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            Const.RhjController.GoBack -> {}
            Const.RhjController.GoHome -> {}
            Const.MediaController.Play -> if (playerService != null) {
                hasMediaPlay = true
                playerService!!.play()
                if ("2019040400000411" == lastMusicSkill) {
                    currentSkill = lastMusicSkill
                }
                showMediaPlayGesture()
            }

            Const.MediaController.Pause -> pauseMusic()
            Const.MediaController.Stop -> if (playerService != null) {
                pauseMusic()
                unbindPlayer()
            }

            Const.MediaController.Prev -> if (playerService != null) {
                hasMediaPlay = true
                playerService!!.prev()
                if ("2019040400000411" == lastMusicSkill) {
                    currentSkill = lastMusicSkill
                }
                showMediaPlayGesture()
            }

            Const.MediaController.Next -> if (playerService != null) {
                hasMediaPlay = true
                playerService!!.next()
                if ("2019040400000411" == lastMusicSkill) {
                    currentSkill = lastMusicSkill
                }
                showMediaPlayGesture()
            }

            Const.RhjController.open -> {
                val jsonObject = JSONObject(data)
                var appname = jsonObject.optString("appname")
                if (appname != null) {
                    GeeUILogUtils.logd("LTPAudioService", "dealCommand: 打开应用：$appname")
                    when (appname) {
                        "智能模式" -> {
                            aiModel = 0
                            enterAi()
                        }

                        "星火智能" -> {
                            aiModel = 1
                            enterAi()
                        }

                        "文心一言" -> {
                            aiModel = 2
                            enterAi()
                        }

                        "亚马逊" -> {
                            startAmazonLex()
                        }

                        else -> {
//                            waitOtherCloseMe
                            //doSomethingAndWaitYourContonlMe
                            sendAidlMessage(SpeechConst.COMMAND_OPEN_APP, appname)

                        }
                    }
                }
            }

            Const.RhjController.close -> {
                val jsonObject = JSONObject(data)
                var appname = jsonObject.optString("appname")
                if (appname != null) {
                    GeeUILogUtils.logd("LTPAudioService", "dealCommand: 关闭应用：$appname")
                    when (appname) {
                        "智能模式" -> {
                            aiModel = 1
                            quitAI()
                        }
//                        "星火智能" -> {
//                            startAmazonLex()
//                        }
                        else -> {
                            sendAidlMessage(SpeechConst.COMMAND_CLOSE_APP, appname)
                        }
                    }
                }
            }

            Const.RhjController.turn, Const.RhjController.move -> robotMove(data)
            Const.RhjController.show -> {
                robotShow(data)
            }

            Const.RhjController.motion -> {
                robotMotion(data)
            }

            Const.RhjController.earlightcolor -> {
                robotEarLight(data)
            }

            Const.RhjController.earlightcolorOff -> {
                robotEarLightOff(data)
            }

            Const.RhjController.earmotion -> {
                robotEarMotion(data)
            }

            Const.RhjController.motionHappy -> {
                motionHappy()
            }

            Const.RhjController.motionSad -> {
                motionSad()
            }

            Const.RhjController.congraturationBirthday -> {
                GeeUILogUtils.logd(TAG, "MainActivity dealCommand: birthday")
//                sendAidlMessage(SpeechConst.COMMAND_BIRTHDAY, "===")
                motionBirthday()
            }

            Const.DUIController.ShutDown -> {
//                sendAidlMessage(SpeechConst.ShutDown, "1")
                powerOff("shutdown")
            }

            Const.DUIController.Reboot -> {
//                sendAidlMessage(SpeechConst.Reboot, "1")
                (getSystemService(POWER_SERVICE) as PowerManager).reboot("voice command")
            }

            Const.DUIController.SetVolumeWithNumber -> {
                dealVolume(data)
            }
            Const.RhjController.motionAiApp -> {
                sendAidlMessage(Const.RhjController.motionAiApp, data)
            }
            Const.RhjController.motionAiAppClose -> {
                sendAidlMessage(Const.RhjController.motionAiAppClose, data)
            }
            Const.RhjController.motionHandEnter -> {
                GeeUILogUtils.logd(TAG, " dealCommand: 手势控制")
                sendAidlMessage(SpeechConst.COMMAND_HAND_ENTER, "1")
            }

            Const.RhjController.motionhandExit -> {
                GeeUILogUtils.logd(TAG, " dealCommand: 退出手势控制")
                sendAidlMessage(SpeechConst.COMMAND_HAND_EXIT, "0")
            }

            Const.RhjController.fingerGuessEnter -> {
                GeeUILogUtils.logd(TAG, " dealCommand: 打开猜拳游戏")
                sendAidlMessage(SpeechConst.COMMAND_FINGER_GUEESS_ENTER, "1")
//                whoYouAre()
            }

            Const.RhjController.fingerGuessExit -> {
                GeeUILogUtils.logd(TAG, " dealCommand: 退出猜拳游戏")
                sendAidlMessage(SpeechConst.COMMAND_FINGER_GUEESS_EXIT, "0")
//                motionThinking()
            }

            Const.RhjController.bodyEnter -> {
                sendAidlMessage(SpeechConst.COMMAND_BODY_ENTER, "1")
            }

            Const.RhjController.bodyExit -> {
                sendAidlMessage(SpeechConst.COMMAND_BODY_EXIT, "0")
            }

            Const.RhjController.searchPeople -> {
                sendAidlMessage(SpeechConst.COMMAND_SEARCH_PEOPLE, "1")
            }

            Const.RhjController.takePhoto -> {
                sendAidlMessage(SpeechConst.COMMAND_TAKE_PHOTO, "1")
            }

            Const.RhjController.motionThinking -> {
                motionThinking()
            }

            Const.RhjController.motionWho -> {
                whoYouAre()
            }
            Const.RhjController.dance -> {
//                whoYouAre()
                danceAction()
            }
            Const.RhjController.videocall -> {
                callMethod("voice_video_call")
            }
            Const.RhjController.urgentcall -> {
                callMethod("voice_help")
            }
            Const.RhjController.remind -> {
                try {
                    sendAidlMessage(
                        "rhj.controller.openreminder", JSONObject(data).optString("data")
                    )
                } catch (e: Exception) {

                }
            }
            else -> GeeUILogUtils.logd(TAG, "dealCommand: 未处理此命令")
        }
    }

    fun powerOff(command: String) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val clazz: Class<*> = pm.javaClass
        try {
            val shutdown: Method = clazz.getMethod(
                command,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            shutdown.invoke(pm, false, command, false)
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
        }
    }

    private fun callMethod(key: String) {
        GeeUILogUtils.logd("LTPAudioService", "callMethod: $key")
        val sn = Build.getSerial()
        val ts = (System.currentTimeMillis() / 1000).toString()
        val map = mutableMapOf(Pair("cmd", key))

        var mediaType = "application/json; charset=utf-8".toMediaTypeOrNull();
        val jsonObject = JSONObject()
        jsonObject.put("cmd", key)
        val requestBody = RequestBody.create(mediaType, jsonObject.toString())
        GlobalScope.launch {
            speechDataRepository.uploadCall(sn, ts, requestBody).collect {}
        }
    }

    private fun danceAction(withMotion: Boolean? = true, single: Boolean? = false) {
        hasDanceMediaPlay = withMotion == true
        singleSong = single == true
        val skillIntent = SkillIntent(
            "2019040400000411", "音乐", "播放音乐", JSONObject().toString()
        )
        skillIntent.setInput("放首歌")
        DDS.getInstance().agent.triggerIntent(skillIntent)
    }

    private fun startAmazonLex() {
        GeeUILogUtils.logd("LTPAudioService", "startAmazonLex: ")
        val intent = Intent()
        intent.component = ComponentName("com.geeui.lex", "com.geeui.lex.services.BotService")
        startService(intent)
    }

    private fun motionBirthday() {
        handler.sendEmptyMessageDelayed(MESSAGE_START_BIRTHDAY, 3000)
        hasMotion = true
        hasBirthday = true
        identFaceResultList = null
        showGestures(GestureCenter.birthdayGestureData(), GESTURE_MOTION_HAPPY_BIRTHDAY)
    }

    private fun motionHappy() {
        hasMotion = true
        showGestures(GestureCenter.getHappyGesture(), GESTURE_MOTION_HAPPY)
    }

    private fun motionSad() {
        hasMotion = true
        showGestures(GestureCenter.getSadGesture(), GESTURE_MOTION_SAD)
    }

    private fun whoYouAre() {
        hasMotion = true
        showGestures(whoGestureData.toMutableList() as ArrayList<GestureData>, GESTURE_WHO_YOU_ARE)
    }

    private fun motionThinking() {
        hasMotion = true
        showGestures(GestureCenter.hourGestureData(getRandomIndex(24)), GESTURE_MOTION_THINKING)
    }

    /**
     * 调整声音
     * {"volume":"+","intentName":"参数调节","skillId":"2019032500000070","skillName":"false","taskName":"中控"}
     */
    private fun dealVolume(data: String) {
        val jsonObject = JSONObject(data)
        val volume = jsonObject.optString("volume")
        GeeUILogUtils.logd("LTPAudioService", "dealVolume: " + data)
        if (!volume.isNullOrEmpty()) {
            sendAidlMessage(SpeechConst.SetVolume, volume)
            when (volume) {
                "+" -> {
                    //声音大一点
                }

                "-" -> {
                    //声音小一点
                }

                else -> {
                    //具体声音数字
                }
            }
        }

    }

    private fun robotEarMotion(data: String) {
        hasMotion = true
        val jsonObject = JSONObject(data)
        val action = jsonObject.optString("number")
        GeeUILogUtils.logd("LTPAudioService", "robotEarMotion: $action")
        val list = ArrayList<GestureData>()
        val gestureData = GestureData()
        var number = if (action.isNullOrEmpty()) 0 else action.toInt()
        gestureData.earAction = AntennaMotion(number)
        gestureData.expression = Face("h0108")
        gestureData.interval = 2000
        list.add(gestureData)
        showGestures(list, GESTURE_FINISH_MOVE)
    }

    private fun robotEarLight(data: String) {
        hasMotion = true
        val jsonObject = JSONObject(data)
        val action = jsonObject.optString("color")
        GeeUILogUtils.logd("LTPAudioService", "robotEarLight: $action")
        if (action != null) {
            val list = ArrayList<GestureData>()
            val gestureData = GestureData()
            var number = if (action.isNullOrEmpty()) Random().nextInt(9) else action.toInt()
            gestureData.antennalight = AntennaLight("on", number)
            gestureData.expression = Face("h0047")
            gestureData.soundEffects = Sound("a0020")
            gestureData.interval = 3000
            list.add(gestureData)
            showGestures(list, GESTURE_FINISH_MOVE)
        }
    }

    private fun robotEarLightOff(data: String) {
        hasMotion = true
        val list = ArrayList<GestureData>()
        val gestureData = GestureData()
        gestureData.antennalight = AntennaLight("off", 0)
        gestureData.expression = Face("h0055")
        gestureData.soundEffects = Sound("a0138")
        gestureData.interval = 3000
        list.add(gestureData)
        showGestures(list, GESTURE_FINISH_MOVE)
    }

    private fun robotMotion(data: String) {
        hasMotion = true
        val jsonObject = JSONObject(data)
        val action = jsonObject.optString("action")
        GeeUILogUtils.logd("LTPAudioService", "robotShow: $action")
        if (action != null) {
            val list = ArrayList<GestureData>()
            val gestureData = GestureData()
            var number = action.toInt()
            gestureData.footAction = Motion(null, number.toInt(), 1)
            gestureData.expression = Face("h0108")
            gestureData.interval = 2000
            list.add(gestureData)
            showGestures(list, GESTURE_FINISH_MOVE)
        }
    }

    private fun robotShow(data: String) {
        hasMotion = true
        try {
            val jsonObject = JSONObject(data)
            val face = jsonObject.optString("action")
            GeeUILogUtils.logd("LTPAudioService", "robotShow: $face")
            val list = ArrayList<GestureData>()
            val gestureData = GestureData()
            gestureData.expression = Face(face)
            val interval = getVideoTotalTime(face)
            GeeUILogUtils.logd("LTPAudioService", "robotShow:interval: $interval")
            gestureData.interval = interval
            list.add(gestureData)
            showGestures(list, GESTURE_FINISH_MOVE)
        } catch (e: Exception) {
            e.printStackTrace()
            hasMotion = false
        }

    }

    /**
     * 返回视频播放总时长
     * @param vedioFile
     * @return
     */
    private fun getVideoTotalTime(path: String): kotlin.Long {
        var fi = assets.openFd("video/${path}.mp4")
        GeeUILogUtils.logd("LTPAudioService", "getVedioTotalTime: fi:${fi.fileDescriptor}")
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(fi.fileDescriptor, fi.startOffset, fi.length)
        val timeString: String =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toString()
        return timeString.toLong() ?: 2000L
    }

    private fun robotMove(data: String) {
        if (isCharging) {
            RhjAudioManager.getInstance().speak("我在充电，不能动哦")
        }
        startXuanya()
        try {
            hasMotion = true
            val jsonObject = JSONObject(data)
            val direction = jsonObject.optString("direction")
            if (direction != null) {
                val list = ArrayList<GestureData>()
                val gestureData = GestureData()
                var n = jsonObject.optString("number")
                if (n == null || n.isEmpty()) {
                    n = (Random().nextInt(3) + 1).toString() + ""
                }
                val number = n.toInt()
                var speed = if (direction.toInt() == 63 || direction.toInt() == 64) 2 else 3
                gestureData.footAction = Motion(direction.toInt(), number, speed)
                gestureData.expression = Face("h0108")
                gestureData.interval = (number * 2000).toLong()
                list.add(gestureData)
                showGestures(list, GESTURE_FINISH_MOVE)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun dealMessage(messageBean: MessageBean) {
        GeeUILogUtils.logd(
            "LTPAudioService",
            "dealMessage: " + messageBean.message_type + "   " + messageBean.toString()
        )
        var str = ""
        when (messageBean.message_type) {
            MessageBean.TYPE_OUTPUT -> {
                //音乐技能id 2019040400000411
                //笑话技能id 2022092900000137
                currentSkill = (messageBean as MessageOutputTextBean).skillId

                GeeUILogUtils.logd(
                    "LTPAudioService",
                    "dealMessage TYPE_OUTPUT: ${(messageBean as MessageOutputTextBean)}"
                )
            }

            MessageBean.TYPE_INPUT -> {
                val messageResultInputBean = messageBean as MessageResultInputBean
            }

            MessageBean.TYPE_WIDGET_CONTENT -> {
                str = (messageBean as MessageWidgetContentBean).toString()
            }

            MessageBean.TYPE_WIDGET_LIST -> {
                str = (messageBean as MessageWidgetListBean).toString()
            }

            MessageBean.TYPE_WIDGET_WEB -> {
                str = (messageBean as MessageWidgetWebBean).toString()
            }

            MessageBean.TYPE_WIDGET_MEDIA -> {
                hasMediaPlay = true
                musicList = (messageBean as MessageMediaListBean)
                if (danceConfig.jumpGuide == true) {
                    startMediaPlayer()
                } else {
                    musicTTTsId = UUID.randomUUID().toString()
                    var musicTipText = ""
                    if (hasDanceMediaPlay) {
                        var tipsArray = danceConfig.danceTipsTexts?.split(";")
                        if (tipsArray != null) {
                            musicTipText = tipsArray[kotlin.random.Random.nextInt(tipsArray.size)]
                        }
                        if (musicTipText.isNullOrEmpty()) {
                            musicTipText = "想停止跳舞，请拍拍我"
                        }
                    } else {
                        var tipsArray = danceConfig.musicTipsTexts?.split(";")
                        if (tipsArray != null) {
                            musicTipText = tipsArray[kotlin.random.Random.nextInt(tipsArray.size)]
                        }
                        if (musicTipText.isNullOrEmpty()) {
                            musicTipText = "想停止播放，请拍拍我"
                        }
                    }
                    RhjAudioManager.getInstance().speak(musicTipText, musicTTTsId, 0)
                }
            }

            MessageBean.TYPE_VAD_TIMEOUT -> {
                run { GeeUILogUtils.logd("LTPAudioService", "dealMessage: asr 检测超时，声音为空") }
            }
            MessageBean.TYPE_DIALOG_START -> {
                shutdownTts()
            }
            MessageBean.TYPE_DIALOG_END -> {}
            else -> {
                GeeUILogUtils.logd(
                    "LTPAudioService", "dealMessage: default:" + messageBean.message_type
                )
            }
        }
    }

    private fun enterAi() {
        GeeUILogUtils.logd(TAG, "进入enterAI(): " + CheckApkExist.checkAIExist(this))
        if (!CheckApkExist.checkAIExist(this)) {
            RhjAudioManager.getInstance().speak("未安装智能模式")
            return
        }
        isAiModel = true
        startAiService()
        if (hasMediaPlay) {
            pauseMusic()
        }
        //        sendAidlMessage(SpeechConst.COMMAND_ENTER_CHAT_GPT, "1");
        try {
            DDS.getInstance().agent.wakeupEngine.updateWkupRouter("partner")
        } catch (e: DDSNotInitCompleteException) {
            e.printStackTrace()
        }
        startASRGesture()
    }

    private fun quitAI() {
        isAiModel = false
        if (asrEngineListening) {
            asrEngine?.cancel()
        }
        shutdownTts()
        aiMessageEnd = true
        try {
            DDS.getInstance().agent.wakeupEngine.updateWkupRouter("dialog")
        } catch (e: DDSNotInitCompleteException) {
            e.printStackTrace()
        }
        mChatConnection?.let { unbindService(it) }
        GeeUILogUtils.logd("LTPAudioService", "quitAI: ")
//        stopUI()
        handler.sendEmptyMessageDelayed(MESSAGE_STOP_UI, 1000)
    }

    /**
     * 开始监听
     */
    private fun startASRGesture() {
        aiMessageEnd = false
        showGestures(GestureCenter.getListenAiGesture(aiModel), GESTURE_START_AI_LISENTEN)
    }

    private fun startASR() {
        if (!isAiModel) {
            return
        }
        aiMessageEnd = true
        shutdownTts()
        if (asrEngine == null) {
            asrEngine = DDS.getInstance().agent.asrEngine
            params.setVadEnable(true)
            asrEngine!!.vadPauseTime = 500L
        }
        try {
            GeeUILogUtils.logd(
                "LTPAudioService",
                "startASR: ${asrEngine!!.vadTimeout}   后端超时：${asrEngine!!.vadPauseTime}"
            )
            asrEngine!!.cancel()
            asrEngine!!.startListening(params, object : ASREngine.Callback {
                override fun beginningOfSpeech() {
                    asrEngineListening = true
                    GeeUILogUtils.logd("LTPAudioService", "beginningOfSpeech: 开始听 asr")
                }

                override fun endOfSpeech() {
                    asrEngineListening = false
                    GeeUILogUtils.logd("LTPAudioService", "endOfSpeech: ")
                }

                override fun bufferReceived(bytes: ByteArray) {}
                override fun partialResults(s: String) {}
                override fun finalResults(s: String) {
                    asrEngineListening = false
                    GeeUILogUtils.logd("LTPAudioService", "finalResults: $s")
                    var jsonObject: JSONObject? = null
                    var text: String? = null
                    try {
                        jsonObject = JSONObject(s)
                        text = jsonObject.optString("text")
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                    GeeUILogUtils.logd("LTPAudioService", "finalResults text: $text")
                    sendAiMessage(text)
                }

                override fun error(s: String) {
                    asrEngineListening = false
                    GeeUILogUtils.logd("LTPAudioService", "error===: $s")
                    GeeUILogUtils.logd(TAG, "error: 没有听到说话")
                    quitAI()
                }

                override fun rmsChanged(v: Float) {}
            })
        } catch (e: DDSNotInitCompleteException) {
            e.printStackTrace()
        }
    }

    /**
     * 设置闹钟
     *
     *
     * //     * @param remind object:闹钟，日程，备忘录，倒计时
     * {
     * "time": "17:00:00", //时间，三段式
     * "object": "闹钟", //创建对象
     * "period": "下午", //时间段
     * "absuolutely": true, //是否为绝对时间，五点为绝对时间，五分钟后为相对时间
     * "date": "20190222", //日期
     * "time_interval":"00:00:30",
     * "time_left":30,
     * "vid": "17146409489307697948", //闹钟、提醒的id，相同时间的闹钟，相同时间相同事件的提醒会被覆盖
     * "recent_tsp": 1550826000,
     * "timestamp": 1550826000
     * }
     */
    private fun setRemindAction(remind: RemindBean?, responseType: Boolean) {
        GeeUILogUtils.logd(TAG, "MainActivity setRemindAction: " + remind!!.content[0])
        if (remind != null && remind.content.size > 0) {
            for (content in remind.content) {
                when (content.getObject()) {
                    "闹钟" -> {
                        if (responseType) {
                            sendAidlMessage(
                                SpeechConst.COMMAND_ADD_CLOCK,
                                remind.content[0].time + "-" + remind.content[0].date
                            )
                        } else {
                            sendAidlMessage(
                                SpeechConst.COMMAND_REMOVE_CLOCK,
                                remind.content[0].time + "-" + remind.content[0].date
                            )
                        }

                    }

                    "倒计时" -> sendAidlMessage(
                        SpeechConst.COMMAND_ADD_REMINDER, remind.content[0].time_interval
                    )

                    "日程" -> sendAidlMessage(
                        SpeechConst.COMMAND_ADD_NOTICE,
                        remind.content[0].timestamp.toString() + "-" + remind.content[0].event
                    )

                    "备忘录" -> {}
                    else -> {}
                }
            }
        }
    }

    private fun startMediaPlayer() {
        if (activity == null) {
            return
        }
        GeeUILogUtils.logd(TAG, " startMediaPlayer: $musicList")
        musicTTTsId = ""
        if (musicList == null || musicList?.list?.isEmpty() == true) {
            GeeUILogUtils.logd("LTPAudioService", "startMediaPlayer: 没有音乐播放")
            return
        }
        val intent = Intent(this@LTPAudioService, PlayerService::class.java)
        val gson = Gson()
        if (singleSong) {
            var listMusic = musicList?.list
            musicList?.list = listMusic?.take(1)
        }
        val json = gson.toJson(musicList)
        intent.putExtra("data", json)
        PlayerService.playerWordId = "play"
        if (playerService == null) {
            hasMediaPlay = true
            bindService(intent, connectionPlayerService, BIND_AUTO_CREATE)
        } else {
            hasMediaPlay = true
            playerService!!.setNewMusic(json)
        }
        if (danceConfig.controlWakeup == true) {
            RhjAudioManager.getInstance().disableWakeup()
        }
        //关闭悬崖
        iLetianpaiService!!.setMcuCommand(
            MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(5, 0).toString()
        )
        sendAidlMessage(SpeechConst.COMMAND_WAKE_UP_STATUS, "avatar.music")
        showMediaPlayGesture()
    }

    private fun finishMusic() {
        if (hasMediaPlay) {
            if (playerService != null) {
                playerService!!.pause()
                hasMediaPlay = false
                iLetianpaiService!!.setMcuCommand(
                    MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(5, 1).toString()
                )
                singleSong = false
                showGestures(standByGestureData, 0)
            }
            GeeUILogUtils.logd("LTPAudioService", "finishMusic: ")
            stopUI()
        }
    }

    private fun showMediaPlayGesture() {
        GeeUILogUtils.logd("LTPAudioService", "showMediaPlayGesture: $currentSkill")
        lastMusicSkill = currentSkill
        if ("2019040400000411" == currentSkill) {
            showGestures(
                danceMusicGesture(), GESTURE_MUSIC_PLAYING, startForegroundIfNot = true
            )
        } else {
            showGestures(GestureCenter.getSpeakingGesture(), 2)
        }
    }

    private fun startAiService() {
        GeeUILogUtils.logd("LTPAudioService", "startAiService: ")
        val intent = Intent()
        intent.putExtra(
            SpeechConst.COMMAND_ENTER_CHAT_GPT, SpeechConst.COMMAND_ENTER_CHAT_GPT
        )
        when (aiModel) {
            0 -> {
                intent.component = ComponentName("com.rhj.ai", "com.rhj.ai.MyService")
            }

            1 -> {
                intent.component = ComponentName("com.rhj.ai", "com.rhj.ai.XunFeiService")
            }

            2 -> {
                intent.component = ComponentName("com.rhj.ai", "com.rhj.ai.WenXinService")
            }

            else -> {
                intent.component = ComponentName("com.rhj.ai", "com.rhj.ai.MyService")
            }
        }
        bindService(intent, mChatConnection!!, BIND_AUTO_CREATE)
    }

    /**
     * 收到ai 的答案
     *
     * @param data
     */
    private fun receiveAiData(data: String) {
        GeeUILogUtils.logd("LTPAudioService", "receiveAiData: $data")
        lastTTSId = UUID.randomUUID().toString()
        RhjAudioManager.getInstance().speak(data, lastTTSId)
        if (!startSpeakAiMessage) {
            startSpeakAiMessage = true
            showGestures(GestureCenter.getSpeakingAiGesture(aiModel), 4)
        }
    }

    private fun sendAiMessage(data: String?) {
        if (data == null || data.isEmpty()) {
            GeeUILogUtils.logd(
                "LTPAudioService", "sendAiMessage: 数据为空========终止流程，需继续唤醒使用"
            )
            return
        }
        if (data.contains("退出") || data.contains("关闭")) {
            quitAI()
            return
        }
        showGestures(GestureCenter.getAiUnderstandGesture(aiModel), 3)
        try {
            if (iLetianpaiService != null) {
                GeeUILogUtils.logd(
                    "LTPAudioService", "sendAiMessage 发送的给ai的问题: before: $data"
                )
                iLetianpaiService!!.setSpeechCmd("sendToAi", data)
                aiMessageEnd = false
                GeeUILogUtils.logd(
                    "LTPAudioService", "sendAiMessage 发送的给ai的问题:end: $data"
                )
            } else {
                GeeUILogUtils.logd("LTPAudioService", "sendAiMessage iLetianpaiService为空")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendAidlMessage(input: String, data: String) {
        try {
            if (iLetianpaiService != null) {
                GeeUILogUtils.logd(
                    "LtpRobotStatusCallback", "sendAidlMessage command  $input  data：$data"
                )
                iLetianpaiService!!.setSpeechCmd(input, data)
            } else {
                GeeUILogUtils.logd("LtpRobotStatusCallback", "iLetianpaiService为空")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * avatar.silence//静止状态。未唤醒时、对话结束后，处于该状态。
     * avatar.listening//倾听状态。识别过程中，处于该状态。
     * avatar.understanding//理解状态。从识别结束，到对话结果返回，处于该状态。
     * avatar.speaking//说话状态。从合成开始，到播放结束，处于该状态。
     *
     * avatar.standby//待命状态。仅遥控器交互场景，且多轮对话未结束时，处于该状态。
     *
     * @param stateData
     */
    private fun showWakeGesture(stateData: String) {
        GeeUILogUtils.logd(
            "LTPAudioService", "showWakeGesture:==--==  aaa $stateData  isAiModel:$isAiModel"
        )
        currentWakeState = stateData
        when (stateData) {
            "avatar.silence" -> {
                if (audioInput.isNullOrEmpty()) {
                    handler.sendEmptyMessageDelayed(MESSAGE_STOP_UI, 1000)
                } else {
                    dealAiRequest(audioInput)
                }
            }

            "avatar.listening" -> {
                currentSkill = ""
                hasMotion = false
                localAiMessageEnd = false
                lastAiWorkID = ""
                stringBuilder?.setLength(0)
                handler.removeMessages(MESSAGE_STOP_UI)
                handler.removeMessages(MESSAGE_START_BIRTHDAY)
                if (robotStatus == ROBOT_STATUS_SLEEP) {
                    robotStatus = ROBOT_STATUS_OTHER
                    showGestures(GestureCenter.getWakeupFennuGesture(), 1, wakeUpOnlyShowFace, true)
                } else {
                    showGestures(GestureCenter.getWakeupGesture(), 1, wakeUpOnlyShowFace, true)

                }
            }
            "sys.vad.begin" -> {
                showGestures(GestureCenter.getVadBeginGesture(), 1, wakeUpOnlyShowFace, true)
            }
            "avatar.understanding" -> {
                showGestures(GestureCenter.getUnderstandGesture(), 11, wakeUpOnlyShowFace)
            }

            "avatar.speaking" -> {
//                if ("2019040400000411" != currentSkill) {
                showGestures(
                    GestureCenter.getFirstSpeakingGesture(),
                    GESTURE_WAKE_STATE_SPEAK,
                    wakeUpOnlyShowFace
                )
//                }
            }
        }
    }

    private fun shutDown() {
        if (wakeupShutdown) {
            GeeUILogUtils.logd("LTPAudioService", "shutDown: ")
            sendAidlMessage(SpeechConst.COMMAND_WAKE_UP_STATUS, "avatar.listening")
            RhjAudioManager.getInstance().stopDialog()
            wakeupShutdown = false
            DDS.getInstance().agent.wakeupEngine.updateWkupRouter("dialog")
        }
    }

    private fun shutdownTts() {
        RhjAudioManager.getInstance().shutupTts()
    }

    private fun pauseMusic(pauseAndExit: Boolean? = false) {
        if (hasMediaPlay) {
            if (playerService != null) {
                playerService!!.pause()
                showGestures(standByGestureData, 0)
            }
            hasMediaPlay = false
            //关闭音乐打开传感器
            iLetianpaiService!!.setMcuCommand(
                MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(5, 1).toString()
            )
            singleSong = false
            hasDanceMediaPlay = false
            if (danceConfig.controlWakeup == true) {
                GeeUILogUtils.logd("LTPAudioService", "pauseMusic:1 ")
                handler.sendEmptyMessageDelayed(MESSAGE_START_WAKEUP, 1000)
            }
            unbindPlayer()
            if (pauseAndExit == true) {
                GeeUILogUtils.logd("LTPAudioService", "pauseMusic:2 ")
                stopUI()
//                handler.sendEmptyMessageDelayed(MESSAGE_STOP_UI, 1000)
            }
        }
    }

    private fun shutdownXuanYa() {
        if (activity == null) {
            GeeUILogUtils.logd("LTPAudioService", "shutdownXuanYa:不在前台不处理")
            return
        }
        isXuanYaStop = true
        if (isAiModel) {
            quitAI()
        }
        if (hasMotion) {
            hasMotion = false
        }
        if (hasMediaPlay) {
            pauseMusic(false)
        }
        showGestures(arrayListOf(), -2)
        if (currentWakeState != "avatar.silence") {
            RhjAudioManager.getInstance().stopDialog()
        } else {
//            stopUI()
            handler.sendEmptyMessageDelayed(MESSAGE_STOP_UI, 1000)
        }
    }

    private fun shutdownAll(enterStartDialog: Boolean) {
        handler.removeMessages(MESSAGE_START_BIRTHDAY)
        currentSkill = ""
        lastAiWorkID = ""
        shutdownTts()
        if (isAiModel) {
            quitAI()
        } else {
            GeeUILogUtils.logd(TAG, "shutdownAll: ")
            if (hasMediaPlay) {
                pauseMusic(true)
            } else {
                if (enterStartDialog && danceConfig.singTapToListen == true) {
                    RhjAudioManager.getInstance().startDialog()
                } else {
                    GeeUILogUtils.logd("LTPAudioService", "shutdownAll: stop")
                    RhjAudioManager.getInstance().stopDialog()
                    handler.sendEmptyMessage(MESSAGE_CLOSE_ACTIVITY)
                }
            }
        }
    }

    private fun showGestures(
        list: Array<GestureData>, taskId: Int, onlyShowFace: Boolean? = false
    ) {
        var list = list.toList()
        var li = java.util.ArrayList<GestureData>()
        li.addAll(list)
        showGestures(li, taskId, onlyShowFace)
    }

    private fun showGestures(
        list: ArrayList<GestureData>,
        taskId: Int,
        onlyShowFace: Boolean? = false,
        startForegroundIfNot: Boolean? = false
    ) {
        startUI(list, startForegroundIfNot)
        GestureDataThreadExecutor.getInstance().execute {
            GeeUILogUtils.logd("LTPAudioService", "run start: taskId:$taskId")
            for (gestureData in list) {
                responseGestureData(gestureData, iLetianpaiService, onlyShowFace ?: false)
                if (gestureData.interval == 0L) {
                    Thread.sleep(2000)
                } else {
                    Thread.sleep(gestureData.interval)
                }
            }
            GeeUILogUtils.logd("LTPAudioService", "run end: taskId:$taskId")
            onGesturesComplete("list", taskId)
        }
    }

    /**
     * 动作执行完成
     *
     * @param list
     * @param taskId
     */
    private fun onGesturesComplete(list: String, taskId: Int) {
        when (taskId) {
            GESTURE_FINISH_MOVE, GESTURE_FINISH_TURN, GESTURE_WHO_YOU_ARE, GESTURE_MOTION_THINKING, GESTURE_MOTION_HAPPY, GESTURE_MOTION_SAD -> {
                GeeUILogUtils.logd("LTPAudioService", "onGesturesComplete: ")
                stopXuanya()
                RhjAudioManager.getInstance().dds.agent.startDialog()
            }

            GESTURE_MUSIC_PLAYING -> if (hasMediaPlay && (currentSkill == "2019040400000411")) {
                showGestures(
                    danceMusicGesture(), GESTURE_MUSIC_PLAYING, startForegroundIfNot = true
                )
            }

            GESTURE_START_AI_LISENTEN -> {
                startASR()
            }
            GESTURE_MOTION_HAPPY_BIRTHDAY -> {
                showGestures(
                    GestureCenter.birthdayWaitGestureData(), GESTURE_MOTION_HAPPY_BIRTHDAY_WAIT
                )
            }
            GESTURE_MOTION_HAPPY_BIRTHDAY_WAIT -> {
                blowOutCandles()
            }
            GESTURE_MOTION_HAPPY_BIRTHDAY_BLOW_OUT -> {
                hasMotion = false
                handler.sendEmptyMessage(MESSAGE_STOP_UI)
            }
            GESTURE_WAKE_STATE_SPEAK -> {
                showGestures(GestureCenter.getSpeakingGesture(), 0, wakeUpOnlyShowFace)
            }
        }
    }

    private fun startUI(list: ArrayList<GestureData>, startForegroundIfNot: Boolean?) {
        GeeUILogUtils.logd(
            "LTPAudioService",
            "startUI: $activity     top activity ：${getTopActivityName(this)}  startForegroundIfNot:$startForegroundIfNot    if=:${
                (startForegroundIfNot == true && (getTopActivityName(this) != "com.rhj.speech.MainActivity"))
            }"
        )
//        if (activity == null || (getTopActivityName(this) != "com.rhj.speech.MainActivity")) {

        if (activity == null || (startForegroundIfNot == true && (getTopActivityName(this) != "com.rhj.speech.MainActivity"))) {
            isXuanYaStop = false
            GeeUILogUtils.logd("LTPAudioService", "startUI: 启动activity")
            val intent = Intent(this@LTPAudioService, MainActivity::class.java)

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!list.isEmpty() && list[0].expression != null && list[0].expression.face != null) {
                intent.putExtra("faceName", list[0].expression.face)
            }
            startActivity(intent)

        }
    }

    fun getTopActivityName(context: Context): String? {
        val am = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningTasks = am.getRunningTasks(1)
        if (runningTasks != null && runningTasks.size > 0) {
            val taskInfo = runningTasks[0]
            val componentName = taskInfo.topActivity
            if (componentName != null && componentName.className != null) {
                return componentName.className
            }
        }
        return null
    }

    private fun changePowerState() {
        if (activity != null && iLetianpaiService != null) {
            try {
                if (isCharging || (!RobotSubConfigManager.getInstance(mContext).speechCommandSwitch)) {
                    iLetianpaiService!!.setMcuCommand(
                        MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(3, 0).toString()
                    )
//                    iLetianpaiService!!.setMcuCommand(
//                        MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(5, 0).toString()
//                    )
                } else {
                    iLetianpaiService!!.setMcuCommand(
                        MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(3, 1).toString()
                    )
//                    iLetianpaiService!!.setMcuCommand(
//                        MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(5, 1).toString()
//                    )
                }
                sendAidlMessage(SpeechConst.COMMAND_WAKE_UP_STATUS, "avatar.listening")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startXuanya() {
        iLetianpaiService!!.setMcuCommand(
            MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(5, 1).toString()
        )
    }

    private fun stopXuanya() {
        iLetianpaiService!!.setMcuCommand(
            MCUCommandConsts.COMMAND_TYPE_POWER_CONTROL, PowerMotion(5, 0).toString()
        )
    }

    /**
     * 关闭界面展示表情
     * 当前判断条件为：
     */
    private fun stopUI() {
        handler.removeMessages(MESSAGE_STOP_UI)
        PlayerService.playerWordId = ""
        //进入ai 不用退出当前展示
        GeeUILogUtils.logd("LTPAudioService", "stopUI: ")
        if (isAiModel) {
            return
        } else if (hasMotion) {
            GeeUILogUtils.logd("LTPAudioService", "stopUI: 有任务正在执行，不能退出")
            return
        } else if (hasMediaPlay) {
            GeeUILogUtils.logd("LTPAudioService", "stopUI: 有音乐正在播放，不会退出")
            return
        } else {
            GeeUILogUtils.logd("LTPAudioService", "stopUI: 没有任务执行，可以关闭" + isXuanYaStop)
            unbindPlayer()
            handler.sendEmptyMessageDelayed(MESSAGE_CLOSE_ACTIVITY, CLOSE_ACTIVITY_DELAY_TIME)
            //TODO 模拟退出指令
            if (!isXuanYaStop) {
                sendAidlMessage(SpeechConst.COMMAND_WAKE_UP_STATUS, "avatar.silence")
            }
        }
    }

    private fun closeActivity() {
        GeeUILogUtils.logd(TAG, "closeActivity: ")
        handler.removeMessages(MESSAGE_CLOSE_ACTIVITY)
        if (activity != null) {
            activity!!.finish()
            activity = null
        }
    }

    private fun responseGestureData(
        gestureData: GestureData?, iLetianpaiService: ILetianpaiService?, onlyShowFace: Boolean
    ) {
        logGestureData(gestureData)
        if (gestureData == null) {
            return
        }
        try {
            GeeUILogUtils.logd(
                "LTPAudioService",
                "responseGestureData: onlyShowFace:" + onlyShowFace
            )
            if (!onlyShowFace && gestureData.ttsInfo != null) {
                //响应单元在Launcher
                RhjAudioManager.getInstance().speak(gestureData.ttsInfo.tts)
            }
            if (gestureData.expression != null) {
                if (faceChangeListener != null) {
                    GeeUILogUtils.logd(
                        "LTPAudioService",
                        "responseGestureData: 发送给表情切换：" + gestureData.expression.face
                    )
                    faceChangeListener!!.changeFace(gestureData.expression.face)
                } else {
                    GeeUILogUtils.logd(
                        "LTPAudioService",
                        "responseGestureData: 没有发送给表情切换：" + gestureData.expression.face
                    )
                }
            }
            if (!onlyShowFace && gestureData.soundEffects != null) {
                //响应单元在AudioService
                iLetianpaiService!!.setAudioEffect(
                    RobotRemoteConsts.COMMAND_TYPE_SOUND, gestureData.soundEffects.sound
                )
            }
            if (!onlyShowFace && !isCharging && gestureData.footAction != null) {
                //响应单元在MCUservice
                iLetianpaiService!!.setMcuCommand(
                    RobotRemoteConsts.COMMAND_TYPE_MOTION, gestureData.footAction.toString()
                )
            }
            if (!onlyShowFace && gestureData.earAction != null) {
                //响应单元在MCUservice
                iLetianpaiService!!.setMcuCommand(
                    RobotRemoteConsts.COMMAND_TYPE_ANTENNA_MOTION, gestureData.earAction.toString()
                )
            }
            if (!onlyShowFace && gestureData.antennalight != null) {
                //响应单元在MCUservice
                val ranNumber = Random().nextInt(10)
                if (ranNumber == 1) {
                    iLetianpaiService!!.setMcuCommand(
                        RobotRemoteConsts.COMMAND_TYPE_ANTENNA_LIGHT,
                        gestureData.antennalight.toString()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun logGestureData(gestureData: GestureData?) {
        GeeUILogUtils.logd(
            "LTPAudioService", "解析给实际执行单元 ${iLetianpaiService == null} $gestureData"
        )
    }

    //    链接服务端
    private fun connectService() {
        val intent = Intent()
        intent.setPackage("com.renhejia.robot.letianpaiservice")
        intent.action = "android.intent.action.LETIANPAI"
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun registerWifiReceiver() {
        mWIFIStateReceiver = WIFIStateReceiver()
        mWIFIStateReceiver?.setOnWifiConnectedChange(object :
            WIFIStateReceiver.OnWifiConnectedChange {
            override fun onConnect(isConnected: Boolean) {
                GeeUILogUtils.logd("LTPAudioService", "registerWifiReceiver: $isConnected")
                if (isConnected && (!RhjAudioManager.getInstance().isAuthSuccess || !RhjAudioManager.getInstance().isInitComplete)) {
                    initAudio()
                    initConfig()
                    getAIConfiguration()
                    unregisterReceiver(mWIFIStateReceiver)
                }
            }
        })

        val filter = IntentFilter()
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION)
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(mWIFIStateReceiver, filter)
    }

    private fun unregisterWifiReceiver() {
        unregisterReceiver(mWIFIStateReceiver)
    }

    override fun onUnbind(intent: Intent): Boolean {
        GeeUILogUtils.logd(
            "LTPAudioService", "onUnbind: " + hasMediaPlay + "   " + (playerService == null)
        )
        if (hasMediaPlay) {
            pauseMusic()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        GeeUILogUtils.logd("LTPAudioService", "onDestroy: ")
        unregisterReceiver(chargingStatueReceiver)
        RhjAudioManager.getInstance().unInit(this)
        try {
            iLetianpaiService?.unregisterRobotStatusCallback(ltpRobotStatusCallback)
            iLetianpaiService?.unregisterLCCallback(ltpLongConnectCallback)
            iLetianpaiService?.unregisterTTSCallback(ltpTTSCallback)
            iLetianpaiService?.unregisterSensorResponseCallback(ltpSensorResponseCallback)
            iLetianpaiService?.unregisterAppCmdCallback(ltpAppCmdCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        unbindPlayer()
        if (isConnectService) {
            unbindService(serviceConnection)
            isConnectService = false
        }
    }

    fun setActivity(activity: MainActivity?) {
        GeeUILogUtils.logd("LTPAudioService", "setActivity:设置activity$activity")
        this.activity = activity
        stopXuanya()
        changePowerState()
    }

    suspend fun postLog(all: List<DMResultModel>) {
        if (all.size < 3) {
            return
        }
        val sn = Build.getSerial()
        val ts = (System.currentTimeMillis() / 1000).toString()
        var hashMap = mutableMapOf<String, Any>()
        hashMap.put("data", Gson()?.toJson(all).toString())

        val jsonObject = JSONObject()
        jsonObject.put("data", gson?.toJson(all).toString())
        jsonObject.put("sn", Build.getSerial())
        var mediaType = "application/json; charset=utf-8".toMediaTypeOrNull();
        val requestBody = RequestBody.create(mediaType, jsonObject.toString())
        speechDataRepository.uploadLog(sn, ts, requestBody).collect {
            (application as App).getDatabase().dmResultDao().deleteAll()
        }
    }

    private suspend fun getConfig() {
        val sn = Build.getSerial()
        val ts = (System.currentTimeMillis() / 1000).toString()
        val map = mutableMapOf(Pair("config_key", "robot_speech_dance"))
        speechDataRepository.getConfigData(sn, ts, map).collect {
            GeeUILogUtils.logd("LTPAudioService", "getConfig==: ${it}")
            if (it.data != null) {
                danceConfig = (it.data as Data<DanceConfigModel>).configData
            }
        }
    }

    private suspend fun getWakeConfig() {
        val config = SPUtils.getInstance(this).getString("wakeConfig")
        if (!config.isNullOrEmpty()) {
            gson?.fromJson(config, WakeUpModel::class.java)?.let { updateWakeConfig(it) }
        }
        val sn = Build.getSerial()
        val ts = (System.currentTimeMillis() / 1000).toString()
        speechDataRepository.getWakeConfig(sn, ts).collect {
            GeeUILogUtils.logd("LTPAudioService", "getWakeConfig: $it")
            if (it?.data != null) {
                SPUtils.getInstance(this).putString("wakeConfig", gson?.toJson(it.data))
                updateWakeConfig(it.data as WakeUpModel)
            }
        }
    }

    /**
     * 拿到星火大模型 chatgpt 等模型的配置
     */
    private fun getAIConfiguration() {
        GlobalScope.launch {
            val sn = Build.getSerial()
            val ts = (System.currentTimeMillis() / 1000).toString()
            isAiConfig = false
            aiConfigKey = null
            aiConfigSecret = null
            aiConfigId = null
            voiceType = null
            speechDataRepository.getAiConfig(sn, ts).collect {
//            GeeUILogUtils.logd("LTPAudioService", "getAiConfig: $it")
                if (it?.data != null) {
                    aiModelList = (it?.data as List<AIModel>)
//                GeeUILogUtils.logd(
//                    "LTPAudioService",
//                    "getAiConfig: ${aiModelList?.toTypedArray().contentToString()} " + " 1834"
//                );
                    GeeUILogUtils.logd(
                        "LTPAudioService",
                        "getAiConfig: ${aiModelList?.toTypedArray().contentToString()} " + " 1834"
                    );
                    aiModelList?.forEach {
//                        if ("xh" == it.voiceType || "wx" == it.voiceType || "other" == it.voiceType) {
                        if ("sys" != it.voiceType) {
                            if (it.isAide == 1) {
                                isAiConfig = true
                                aiConfigKey = it.apiKey
                                aiConfigSecret = it.apiSecret
                                aiConfigId = it.appId
                                voiceType = it.voiceType
                            }
                        }
                    }
//                updateWakeConfig(it.data as WakeUpModel)
                }
            }
            GeeUILogUtils.logd(
                "LTPAudioService",
                "getAiConfig: ${aiConfigKey}  ${aiConfigSecret}   ${aiConfigId}  ${voiceType}" + " 2031"
            )
        }
    }


    fun setFaceChangeListener(faceChangeListener: OnFaceChangeListener?) {
        this.faceChangeListener = faceChangeListener
    }

    interface OnFaceChangeListener {
        fun changeFace(faceName: String?)
    }

    override fun onBind(intent: Intent): IBinder? {
        if (myBinder == null) {
            myBinder = MyBinder()
        }
        return myBinder
    }

    private var myBinder: MyBinder? = null

    inner class MyBinder : Binder() {
        val service: LTPAudioService
            get() = this@LTPAudioService
    }

    inner class ChargingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    GeeUILogUtils.logd("ChargingReceiver", "onReceive: ACTION_POWER_DISCONNECTED")
                }

                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    GeeUILogUtils.logd("ChargingReceiver", "onReceive: ACTION_POWER_CONNECTED")
                }
            }
            changePowerState()
        }
    }

    private fun getRandomString(group: Array<String>): String? {
        val r = Random()
        return group[r.nextInt(group.size)]
    }

    //    private val danceFace = "h0212"
    private fun danceFace() = if (hasDanceMediaPlay) "h0134" else "h0369"

    private fun danceGestureData1() = arrayOf(GestureData().apply {
        footAction = Motion(27, 10, 3)
        antennalight = getRandomAntennaLight()
        expression = Face(danceFace())
        interval = (3 * 1000).toLong()
    })

    private fun danceGestureData2() = arrayOf(GestureData().apply {
        footAction = Motion(7, 3, 2)
//              antennalight = getRandomAntennaLight()
        expression = Face(danceFace())
        interval = (5 * 1000).toLong()
    }, GestureData().apply {
        footAction = Motion(8, 3, 2)
        antennalight = getRandomAntennaLight()
        interval = (5 * 1000).toLong()
    })

    private fun danceGestureData3() = arrayOf(GestureData().apply {
        footAction = Motion(60, 4, 3)
        antennalight = getRandomAntennaLight()
        expression = Face(danceFace())
        interval = (4 * 1000).toLong()
    })

    private fun danceGestureData4() = arrayOf(GestureData().apply {
        footAction = Motion(19, 10, 1)
        antennalight = getRandomAntennaLight()

        expression = Face(danceFace())
        interval = (4 * 1000).toLong()
    })

    private fun danceGestureData5() = arrayOf(GestureData().apply {
        footAction = Motion(79, 10, 2)
        antennalight = getRandomAntennaLight()

        expression = Face(danceFace())
        interval = (10 * 1000).toLong()
    })

    private fun danceGestureData6() = arrayOf(GestureData().apply {
        footAction = Motion(9, 5, 1)
        antennalight = getRandomAntennaLight()

        expression = Face(danceFace())
        interval = (3 * 1000).toLong()
    }, GestureData().apply {
        footAction = Motion(10, 5, 1)
        antennalight = getRandomAntennaLight()
        interval = (3 * 1000).toLong()
    })

    private fun danceGestureData7() = arrayOf(GestureData().apply {
        footAction = Motion(80, 10, 6)
        earAction = AntennaMotion(3, 1, 300)
        antennalight = getRandomAntennaLight()
        expression = Face(danceFace())
        interval = (8 * 1000).toLong()
    })

    private fun danceGestureData8() = arrayOf(GestureData().apply {
        footAction = Motion(85, 5, 3)
        antennalight = getRandomAntennaLight()

        expression = Face(danceFace())
        interval = (5 * 1000).toLong()
    }, GestureData().apply {
        footAction = Motion(86, 5, 3)
        antennalight = getRandomAntennaLight()
        interval = (5 * 1000).toLong()
    })

    private fun danceGestureData9() = arrayOf(GestureData().apply {
        footAction = Motion(89, 5, 3)
        antennalight = getRandomAntennaLight()

        expression = Face(danceFace())
        interval = (5 * 1000).toLong()
    }, GestureData().apply {
        footAction = Motion(90, 5, 3)
        antennalight = getRandomAntennaLight()
        interval = (5 * 1000).toLong()
    })

    private val whoGestureData = mutableListOf<GestureData>(GestureData().apply {
        footAction = Motion(27, 3, 3)
        antennalight = getRandomAntennaLight()
        expression = Face("h0205")
        interval = (5 * 1000).toLong()
    }).apply {
        for (i in 0 until 20) {
            this.add(GestureData().apply {
                footAction = Motion(
                    getRandomMotion(
                        intArrayOf(
                            27, 43, 44, 45, 46, 47, 51, 52, 63, 64, 85, 86, 87, 88, 89, 90
                        )
                    ), 1, 3
                )
                antennalight = getRandomAntennaLight()
                interval = (3650).toLong()
            })
        }
    }

    private fun danceMusicGesture(): ArrayList<GestureData> {
        val list = ArrayList<GestureData>()
        val hashMap = HashMap<Int, Array<GestureData>>()
        hashMap[0] = danceGestureData1()
        hashMap[1] = danceGestureData2()
        hashMap[2] = danceGestureData3()
        hashMap[3] = danceGestureData4()
        hashMap[4] = danceGestureData5()
        hashMap[5] = danceGestureData6()
        hashMap[6] = danceGestureData7()
        hashMap[7] = danceGestureData8()
        hashMap[8] = danceGestureData9()

        hashMap[getRandomMotion(
            intArrayOf(
                0, 2,
                1,
                3,
                4,
                5,
                6,
                7,
                8
            )
        )]?.let { list.addAll(it.toList()) }
//        hashMap[getRandomIndex(9)]?.let { list.addAll(it.toList()) }
//        list.addAll(danceGestureData1)
//        list.addAll(danceGestureData2)
//        list.addAll(danceGestureData3)
//        list.addAll(danceGestureData4)
//        list.addAll(danceGestureData5)
//        list.addAll(danceGestureData6)
//        list.addAll(danceGestureData7)
//        list.addAll(danceGestureData8)
//        list.addAll(danceGestureData9)

        val result = ArrayList<GestureData>()
        list.forEach {
            result.add(GestureData().apply {
                if (hasDanceMediaPlay) {
                    footAction = it.footAction
                }
                antennalight = it.antennalight
                expression = it.expression
                earAction = it.earAction
                interval = it.interval
                soundEffects = it.soundEffects
            })
        }
        GeeUILogUtils.logd("LTPAudioService", "danceMusicGesture: $result")
        return result
    }

    private val standByGestureData = arrayOf(GestureData().apply {
        footAction = Motion(0, 0, 3)
        interval = 100
    })

    private var aiUnderstandGesture = arrayOf(GestureData().apply {
        expression = Face("h0242")
//        footAction =
//            Motion(GestureCenter.getRandomMotion(intArrayOf(25, 26, 46, 47, 49)))
        antennalight = getRandomAntennaLight()
        interval = 1000
    })
    private var aiSpeakGesture = arrayOf(GestureData().apply {
        expression = Face("h0292")
//        footAction =
//            Motion(GestureCenter.getRandomMotion(intArrayOf(25, 26, 46, 47, 49)))
        antennalight = getRandomAntennaLight()
        interval = 1000
    })

    private fun getRandomIndex(length: Int): Int {
        val r = Random()
        return r.nextInt(length)
    }

    private fun getRandomMotion(group: IntArray): Int {
        val r = Random()
        return group[r.nextInt(group.size)]
    }

    private fun getRandomAntennaLight(): AntennaLight {
        val r = Random()
        return AntennaLight("on", getRandomIndex(9) + 1)
    }

    /**
     * activity 进入不可见的状态
     */
    fun uiEnterPause() {
        GeeUILogUtils.logd("LTPAudioService", "uiEnterPause: 11进入不可见的状态")
//        if (hasMediaPlay) {
//            pauseMusic()
//        }
    }

    companion object {
        const val GESTURE_FINISH_MOVE = 2000
        const val GESTURE_FINISH_TURN = 2001

        /**
         * 播放音乐
         */
        const val GESTURE_MUSIC_PLAYING = 2003
        const val GESTURE_START_AI_LISENTEN = 2004
        const val GESTURE_WHO_YOU_ARE = 2005
        const val GESTURE_MOTION_THINKING = 2006
        const val GESTURE_MOTION_HAPPY = 2007
        const val GESTURE_MOTION_SAD = 2008
        const val GESTURE_MOTION_HAPPY_BIRTHDAY = 2009
        const val GESTURE_MOTION_HAPPY_BIRTHDAY_WAIT = 2010
        const val GESTURE_MOTION_HAPPY_BIRTHDAY_BLOW_OUT = 2011
        const val GESTURE_WAKE_STATE_SPEAK = 2012
        const val COMMAND_TYPE_START_AUDIO_SERVICE = "start_audio_service"
        const val COMMAND_TYPE_STOP_AUDIO_SERVICE = "stop_audio_service"
        const val COMMAND_TYPE_SHUT_DOWN_AUDIO_SERVICE = "shut_down_audio_service"//不进入唤醒
        const val ROBOT_STATUS_SLEEP = 1
        const val ROBOT_STATUS_OTHER = 0
        const val WAKEUP_SHOW_MOTION = "wakeup_show_motion"
        const val TTS_TAKE_PHOTO_END = "tts_take_photo_end"
        var playerWordId = ""

    }
    /*
    讯飞相关
        private fun initAIUI() {
            AIUIAudioManager.getInstance().initAudio(this)
            AIUIAudioManager.getInstance().setWakeupStateChangeCallback { state ->
                GeeUILogUtils.logd("LTPAudioService", "initAIUI: $state")
                showWakeWorkGesture()
            }
            AIUIAudioManager.getInstance().setWakeupDoaCallback { doaData ->
                GeeUILogUtils.logd("LTPAudioService", "initAIUI: $doaData")
            }
            AIUIAudioManager.getInstance().setWakeupResultCallback { wakeResult ->
                GeeUILogUtils.logd("LTPAudioService", "initAIUI: $wakeResult")
            }
            AIUIAudioManager.getInstance().setCommandCallback { command, data ->
                GeeUILogUtils.logd("LTPAudioService", "initAIUI: command:$command   data:$data");
            }
            AIUIAudioManager.getInstance()
                .setTtsStateChangeCallback(object : com.geeui.aiui.observer.TtsStateChangeCallback {
                    override fun onSpeakBegin() {
                        showSpeakBeginGesture()
                        GeeUILogUtils.logd("LTPAudioService", "onSpeakBegin: ");
                    }

                    override fun onSpeakEnd(ttsId: String?, errorCode: Int) {
                        GeeUILogUtils.logd("LTPAudioService", "onSpeakEnd: ");
                        showSpeakEndGesture()
                    }

                    override fun onSpeakProgress(current: Int, total: Int) {
                        GeeUILogUtils.logd("LTPAudioService", "onSpeakProgress: ");
                    }

                    override fun error(s: String?) {
                        GeeUILogUtils.logd("LTPAudioService", "error: ");
                    }

                })
        }

        private fun showSpeakBeginGesture() {
            showGestures(GestureCenter.getSpeakingGesture(), 10)
        }

        private fun showSpeakEndGesture() {
            stopUI()
        }

        private fun showWakeWorkGesture() {
            showGestures(GestureCenter.getWakeupGesture(), 11)
        }
        */
}
