package com.geeui.aiui

import android.content.Context
import android.os.Bundle
import com.geeui.aiui.observer.*
import com.geeui.aiui.utils.AppUtils
import com.geeui.aiui.utils.Storage
import com.google.gson.Gson
import com.iflytek.aiui.*
import com.iflytek.cloud.*
import com.renhejia.robot.commandlib.log.LogUtils
import org.json.JSONObject
import java.nio.charset.Charset


class AIUIAudioManager {
    companion object {
        private var aiuiAudioManager: AIUIAudioManager? = null

        fun getInstance(): AIUIAudioManager {
            if (aiuiAudioManager == null) {
                aiuiAudioManager = AIUIAudioManager()
            }
            return aiuiAudioManager!!
        }
    }

    private var mContext: Context? = null
    private var mAIUIAgent: AIUIAgent? = null
    var mAIUIState = AIUIConstant.STATE_READY

    private val wakeupStateChangeCallbackList = arrayListOf<WakeupStateChangeCallback>()
    private val wakeupResultCallbackList = arrayListOf<WakeupResultCallback>()
    private val wakeupDoaCallbackList = arrayListOf<WakeupDoaCallback>()
    private val ttsStateChangeCallbackList = arrayListOf<TtsStateChangeCallback>()
    private val commandCallbackList = arrayListOf<CommandCallback>()

    private val mAIUIListener = AIUIListener { event ->
        when (event.eventType) {
            AIUIConstant.EVENT_WAKEUP -> {
                val info = event.info
                LogUtils.logd("AIUIAudioManager", "on EVENT_WAKEUP: $info")
                if (info != null && info.isNotEmpty()) {
                    val jsInfo = JSONObject(info)
                    val ivwResult = jsInfo.getString("ivw_result")
                    val ivwInfo = JSONObject(ivwResult)
                    val keyword = ivwInfo.getString("keyword")
                    LogUtils.logd("AIUIAudioManager", "本次唤醒为：$keyword")
                    wakeupStateChangeCallbackList.forEach {
                        it.onState("$keyword")
                    }
                }
//                initIat()

            }
            //结果事件（包含听写，语义，离线语法结果）
            AIUIConstant.EVENT_RESULT -> {
                processResult(event)
            }
            AIUIConstant.EVENT_VAD -> {
                logEvent(event)
            }
            //休眠事件
            AIUIConstant.EVENT_SLEEP -> {
            }
            // 状态事件
            AIUIConstant.EVENT_STATE -> {
                LogUtils.logd("AIUIAudioManager", "mAIUIState: ${event.arg1}")
                mAIUIState = event.arg1
                if (AIUIConstant.STATE_IDLE == mAIUIState) {
                    // 闲置状态，AIUI未开启
                } else if (AIUIConstant.STATE_READY == mAIUIState) {
                    // AIUI已就绪，等待唤醒
                    showTip("AIUI已就绪，等待唤醒")
                    startRecord()
                } else if (AIUIConstant.STATE_WORKING == mAIUIState) {
                    // AIUI工作中，可进行交互
                    showTip("AIUI工作中，可进行交互")
                }
            }
            //错误事件
            AIUIConstant.EVENT_ERROR -> {
            }
            AIUIConstant.EVENT_CONNECTED_TO_SERVER -> {

            }
            AIUIConstant.EVENT_STOP_RECORD -> {
                stopRecordAudio()
            }

            AIUIConstant.EVENT_TTS -> {

                when (event.arg1) {
                    AIUIConstant.TTS_SPEAK_BEGIN -> {
                        showTip("开始播放")
                        ttsStateChangeCallbackList.forEach {
                            it.onSpeakBegin()
                        }
                    }
                    AIUIConstant.TTS_SPEAK_PROGRESS -> {
//                        showTip(
//                            ", 播放进度为" + event.data.getInt("percent")
//                        ) // 播放进度
                    }
                    AIUIConstant.TTS_SPEAK_PAUSED -> showTip("暂停播放")
                    AIUIConstant.TTS_SPEAK_RESUMED -> showTip("恢复播放")
                    AIUIConstant.TTS_SPEAK_COMPLETED -> {
                        showTip("播放完成")
                        ttsStateChangeCallbackList.forEach {
                            it.onSpeakEnd("status:${event.arg1}", 0)
                        }
                    }
                    else -> {}
                }
            }
            else -> {
                logEvent(event)
            }
        }
    }

    private fun processResult(event: AIUIEvent) {
        val data = JSONObject(event.info).getJSONArray("data").getJSONObject(0)
        val sub = data.getJSONObject("params").optString("sub")
        val content = data.getJSONArray("content").getJSONObject(0)
        val cnt_id = content.getString("cnt_id")
        var byteAr = event.data.getByteArray(cnt_id)
        var cntStr = String(byteAr!!, Charset.forName("utf-8"))
        val dte = content.getString("dte")
        LogUtils.logd(
            "AIUIAudioManager",
            "processResult: content  sub:$sub   dte  ${content.getString("dte")}"
        )
        if (!dte.equals("pcm")) {
            LogUtils.logd("AIUIAudioManager", "processResult: cntStr: $cntStr")
        }
        when (sub) {
            "iat" -> {
                dealIatResult(cntStr)
            }
            "tts" -> {}
            "pcm" -> {}
            "json" -> {
                if (content.has("cnt_id")) {
                    LogUtils.logd("AIUIAudioManager", "processResult: cnt_id:$cnt_id");
                    LogUtils.logd(
                        "AIUIAudioManager", "processResult: byteAr:${byteAr.contentToString()}"
                    )
                    if (byteAr != null) {
                        val result = JSONObject(cntStr)
                        LogUtils.logd("AIUIAudioManager", "processResult: $result")
                    }
                }
            }
            else -> {
                logEvent(event)
            }
        }
    }

    private fun dealIatResult(cntStr: String) {
        var result = Gson().fromJson(cntStr, IatModel::class.java)
        if (result.text.ls) {
            var list = result.text.ws
            var asrText = "";
            list.forEach {
                asrText += it.cw[0].w
            }
            LogUtils.logd("AIUIAudioManager", "dealIatResult: 最终的文案：${asrText}")
//            if (true) {
//                sendMessage(AIUIMessage(AIUIConstant.CMD_RESET, 0, 0, null, null))
//            }
        }
    }

    fun speakText(ttsStr: String) {

// 转为二进制数据
        val ttsData = ttsStr.toByteArray(charset("utf-8"))
// 构建合成参数，一般包含发音人、语速、音调、音量
        val params = StringBuffer()
// 发音人，发音人列表：https://aiui-doc.xf-yun.com/project-1/doc-93/
        params.append("vcn=x4_lingxiaoying_em_v2")
// 语速，取值范围[0,100]
        params.append(",speed=50")
// 音调，取值范围[0,100]
        params.append(",pitch=50")
// 音量，取值范围[0,100]
        params.append(",volume=50")
//开始合成
        val startTTS =
            AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.START, 0, params.toString(), ttsData)
        sendMessage(startTTS)
    }

    fun sendMessage(aiuiMessage: AIUIMessage) {
        mAIUIAgent?.sendMessage(aiuiMessage)
    }

    fun shutupTTS() {
        val stopTTS = AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.CMD_STOP, 0, null, null)
        mAIUIAgent!!.sendMessage(stopTTS)
    }


    private fun startRecord() {
        val params = "sample_rate=16000,data_type=audio"
        val writeMsg = AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, params, null)
        mAIUIAgent!!.sendMessage(writeMsg)
    }

    private fun stopRecordAudio() {
        mAIUIAgent!!.sendMessage(
            AIUIMessage(
                AIUIConstant.CMD_STOP_RECORD, 0, 0, "data_type=audio,sample_rate=16000", null
            )
        )
    }

    fun initAudio(context: Context) {
        mContext = context
        val ASSETS_CONFIG_PATH = "cfg/aiui_phone.cfg"
        var mStorage = Storage(context)
        var currentAssetConfig = mStorage.readAssetFile(ASSETS_CONFIG_PATH)
        mStorage.copyAssetFolder("ivw/vtn", "/sdcard/AIUI/ivw/vtn")
        var mAIUIConfig = JSONObject(currentAssetConfig)
        LogUtils.logd("AIUIAudioManager", "initAudio: ${mAIUIConfig.toString()}");
        //为每一个设备设置对应唯一的SN（最好使用设备硬件信息(mac地址，设备序列号等）生成），以便正确统计装机量，避免刷机或者应用卸载重装导致装机量重复计数
        AIUISetting.setSystemInfo(
            AIUIConstant.KEY_SERIAL_NUM, AppUtils.getMacReadSystemFile(context)
        )
        //创建AIUIAgent
        mAIUIAgent = AIUIAgent.createAgent(context, mAIUIConfig.toString(), mAIUIListener)
    }

    private var mIat: SpeechRecognizer? = null
    private val mInitListener =
        InitListener { p0 -> LogUtils.logd("AIUIAudioManager", "onInit: $p0") }
    private val mRecogListener = object : RecognizerListener {
        override fun onVolumeChanged(p0: Int, p1: ByteArray?) {
            LogUtils.logd("AIUIAudioManager", "onVolumeChanged: $p0")
        }

        override fun onBeginOfSpeech() {
            LogUtils.logd("AIUIAudioManager", "onBeginOfSpeech: ")
        }

        override fun onEndOfSpeech() {
            LogUtils.logd("AIUIAudioManager", "onEndOfSpeech: ")
        }

        override fun onResult(p0: RecognizerResult?, p1: Boolean) {
            LogUtils.logd("AIUIAudioManager", "onResult: $p0")
        }

        override fun onError(p0: SpeechError?) {
            LogUtils.logd("AIUIAudioManager", "onError: $p0")
        }

        override fun onEvent(p0: Int, p1: Int, p2: Int, p3: Bundle?) {
            LogUtils.logd("AIUIAudioManager", "onEvent: $p0")
        }
    }

    private fun initIat() {
        //初始化识别无UI识别对象
//使用SpeechRecognizer对象，可根据回调消息自定义界面；

//        if (mIat == null) {
        mIat = SpeechRecognizer.createRecognizer(mContext, mInitListener);
        mIat?.let {
//设置语法ID和 SUBJECT 为空，以免因之前有语法调用而设置了此参数；或直接清空所有参数，具体可参考 DEMO 的示例。
            it.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
            it.setParameter(SpeechConstant.SUBJECT, null);
//设置返回结果格式，目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容
            it.setParameter(SpeechConstant.RESULT_TYPE, "json");
//此处engineType为“cloud”
            it.setParameter(SpeechConstant.ENGINE_TYPE, "cloud");
//设置语音输入语言，zh_cn为简体中文
            it.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
//设置结果返回语言
            it.setParameter(SpeechConstant.ACCENT, "mandarin");
// 设置语音前端点:静音超时时间，单位ms，即用户多长时间不说话则当做超时处理
//取值范围{1000～10000}
            it.setParameter(SpeechConstant.VAD_BOS, "4000");
//设置语音后端点:后端点静音检测时间，单位ms，即用户停止说话多长时间内即认为不再输入，
//自动停止录音，范围{0~10000}
            it.setParameter(SpeechConstant.VAD_EOS, "1000");
//设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
            it.setParameter(SpeechConstant.ASR_PTT, "1");
        }
//        }
//开始识别，并设置监听器
        mIat = SpeechRecognizer.getRecognizer()
        LogUtils.logd("AIUIAudioManager", "initIat: ${mIat == null}")
        mIat?.startListening(mRecogListener)
    }

    fun setWakeupStateChangeCallback(wakeupStateChangeCallback: WakeupStateChangeCallback) {
        wakeupStateChangeCallbackList.add(wakeupStateChangeCallback)
    }

    fun setWakeupResultCallback(wakeupResultCallback: WakeupResultCallback) {
        wakeupResultCallbackList.add(wakeupResultCallback)
    }

    fun setWakeupDoaCallback(wakeupDoaCallback: WakeupDoaCallback) {
        wakeupDoaCallbackList.add(wakeupDoaCallback)
    }

    fun setTtsStateChangeCallback(ttsStateChangeCallback: TtsStateChangeCallback) {
        ttsStateChangeCallbackList.add(ttsStateChangeCallback)
    }

    fun setCommandCallback(commandCallback: CommandCallback) {
        commandCallbackList.add(commandCallback)
    }

    private fun showTip(string: String) {
        LogUtils.logd("AIUIAudioManager", "showTip: $string");
    }

    private fun logEvent(event: AIUIEvent) {
        LogUtils.logd(
            "AIUIAudioManager",
            "logEvent: ${event.arg1}  ${event.arg2} ${event.eventType} ${event.info} ${event.info}"
        )

    }
}