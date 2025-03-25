package com.rhj.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaRecorder;

import com.aispeech.dui.dds.DDS;
import com.aispeech.dui.dds.DDSAuthListener;
import com.aispeech.dui.dds.DDSConfig;
import com.aispeech.dui.dds.DDSInitListener;
import com.aispeech.dui.dds.agent.ContextIntent;
import com.aispeech.dui.dds.agent.DMTaskCallback;
import com.aispeech.dui.dds.agent.VocabIntent;
import com.aispeech.dui.dds.agent.tts.TTSEngine;
import com.aispeech.dui.dds.agent.tts.bean.CustomAudioBean;
import com.aispeech.dui.dds.agent.wakeup.word.WakeupWord;
import com.aispeech.dui.dds.exceptions.DDSNotInitCompleteException;
import com.demon.fmodsound.FmodSound;
import com.google.gson.Gson;
import com.letianpai.robot.components.utils.GeeUILogUtils;
import com.rhj.audio.observer.AuthStatusCallback;
import com.rhj.audio.observer.CommandCallback;
import com.rhj.audio.observer.FmodStateChangeCallback;
import com.rhj.audio.observer.InitCallback;
import com.rhj.audio.observer.MessageCallback;
import com.rhj.audio.observer.RhjCommandObserver;
import com.rhj.audio.observer.RhjDMTaskCallback;
import com.rhj.audio.observer.RhjMessageObserver;
import com.rhj.audio.observer.TtsStateChangeCallback;
import com.rhj.audio.observer.VadBeginCallback;
import com.rhj.audio.observer.WakeupDoaCallback;
import com.rhj.audio.observer.WakeupResultCallback;
import com.rhj.audio.observer.WakeupStateChangeCallback;
import com.rhj.audio.utils.AppUtils;
import com.rhj.audio.utils.FileUtils;
import com.rhj.audio.utils.SPUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import ai.dui.sdk.log.BuildConfig;

public class RhjAudioManager {
    private final String TAG = "RhjAudioManager";
    public static final String ENABLE_WORDUP_XIAOLE = "enable_xiao_le";
    public static final String ENABLE_WORDUP_XIAOPAI = "enable_xiao_pai";
    public static final String ENABLE_WORDUP_XIAOTIAN = "enable_xiao_tian";

    public static final String ENABLE_WORDUP_LETIANPAI = "enable_xiao_letianpai";
    public static final String ENABLE_WORDUP_CUSTOM_SWITCH = "custom_switch";
    public static final String ENABLE_WORDUP_CUSTOM_TITLE = "custom_title";
    public static final String ENABLE_WORDUP_CUSTOM_PINYIN = "custom_pinyin";

    public static final String SPEAKER_NAME = "speakerName";

    private static volatile RhjAudioManager rhjAudioManager;
    private RhjMessageObserver mMessageObserver = RhjMessageObserver.getInstance();// 消息监听器
    private RhjCommandObserver mCommandObserver = RhjCommandObserver.getInstance();// 命令监听器
    private boolean initComplete = false;
    private boolean authSuccess = false;
    private AuthStatusCallback authStatusCallback;
    private InitCallback initCallback;
    private boolean enableWake = false;

    // dds初始状态监听器,监听init是否成功
    private DDSInitListener mInitListener = new DDSInitListener() {
        @Override
        public void onInitComplete(boolean isFull) {
            if (isFull) {
                initComplete = true;
                if (initCallback != null) {
                    initCallback.stateChange(true);
                }
                initComplete();
                GeeUILogUtils.logd("RhjAudioManager", "初始化成功");
            }
            GeeUILogUtils.logd("RhjAudioManager", "onInitComplete: " + isFull);
        }

        @Override
        public void onError(int what, final String msg) {
            initComplete = false;
            if (initCallback != null) {
                initCallback.stateChange(false);
            }

            GeeUILogUtils.logd("RhjAudioManager", "初始化失败 " + what + " msg:" + msg);
        }
    };

    // dds认证状态监听器,监听auth是否成功
    private DDSAuthListener mAuthListener = new DDSAuthListener() {
        @Override
        public void onAuthSuccess() {
            authSuccess = true;
            if (authStatusCallback != null) {
                authStatusCallback.onAuthStausStateChange(true);
            }
            GeeUILogUtils.logd("RhjAudioManager","mAuthListener 成功");
        }

        @Override
        public void onAuthFailed(final String errId, final String error) {
            authSuccess = false;
            if (authStatusCallback != null) {
                authStatusCallback.onAuthStausStateChange(false);
            }
            GeeUILogUtils.logd("RhjAudioManager","onAuthFailed 失败："+errId+"   error:"+error);
        }
    };

    private List<MessageCallback> messageCallbackList = new ArrayList<>();
    private List<WakeupStateChangeCallback> wakeupStateChangeCallbackList = new ArrayList<>();
    private List<WakeupDoaCallback> wakeupDoaCallbackList = new ArrayList<>();
    private List<TtsStateChangeCallback> ttsStateChangeCallbackList = new ArrayList<>();
    private List<CommandCallback> commandCallbackList = new ArrayList<>();
    private List<VadBeginCallback> vadBeginCallbackList = new ArrayList<>();
    private RhjDMTaskCallback rhjDMTaskCallback;
    private FmodStateChangeCallback fmodStateChangeCallback;
    private List<DmTaskResultBean> resultHistoryList = new ArrayList();
    public static final int RESULT_HISTORY_MAX_NUMBER = 10;
    private Gson mGson = new Gson();
    /**
     * 是否使用FMod 变声
     */
    private boolean useFmod;
    private boolean enableWakeupWordXiaole = true;
    private boolean enableWakeupWordXiaoPai = false;

    private boolean enableWakeupWordXiaoTian = false;
    private boolean enableWakeupWordLeTianPai = false;
    private Context context;
    private WakeupResultCallback wakeupResultCallback;

    public static RhjAudioManager getInstance() {
        if (rhjAudioManager == null) {
            rhjAudioManager = new RhjAudioManager();
        }
        return rhjAudioManager;
    }

    public void init(Context context, String apiKey) {
        this.context = context;
        DDS.getInstance().init(context.getApplicationContext(), createConfig(context, apiKey), mInitListener, mAuthListener);
    }

    public void unInit(Context context) {
        GeeUILogUtils.logd("RhjAudioManager", "unInit: ====================");
        messageCallbackList.clear();
        wakeupStateChangeCallbackList.clear();
        wakeupDoaCallbackList.clear();
        ttsStateChangeCallbackList.clear();
        commandCallbackList.clear();
        mMessageObserver.unregister();
        mCommandObserver.unregister();
        initComplete = false;
        enableWake = false;
        useFmod = false;
        authSuccess = false;
        DDS.getInstance().release();
    }

    private void initComplete() {
        try {
            DDS.getInstance().setDebugMode(5);
            DDS.getInstance().getAgent().getTTSEngine().enableFocus(true);
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
        setGreetingAudio();
//        setOneShot();
        setTtsListener();
        setMessageObserver();
        setCommandObserver();
        setResultWithoutSpeak();
        setWakeupInfo();
        setVadPauseTime(1000L);
//        testWord();
        try {
            //设置唤醒词不播放，播放的是提示音效，notice.wav
//            DDS.getInstance().getAgent().getWakeupEngine().setWakeupCallback(new WakeupCallback() {
//                @Override
//                public JSONObject onWakeup(JSONObject jsonObject) {
//                    GeeUILogUtils.logd("RhjAudioManager", "onWakeup: " + jsonObject);
//                    JSONObject result = new JSONObject();
//                    try {
//                        result.put("greeting", "");
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                    return result;
//                }
//            });
            DDS.getInstance().getAgent().getTTSEngine().setStreamType(AudioAttributes.CONTENT_TYPE_MUSIC); //可以设置TTS播报通道，默认使用ALARM通道。
            DDS.getInstance().getAgent().getTTSEngine().setVolume(300);//可以调整TTS音量，建议可以初始化授权成功后调用该接口设置TTS音量。
            DDS.getInstance().getAgent().getTTSEngine().setSpeed(1.0f);
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    private void setOneShot() {
        try {
            DDS.getInstance().getAgent().getWakeupEngine().enableOneShot();
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    private void setGreetingAudio() {
        copyAssets();

        CustomAudioBean audioBean = new CustomAudioBean();
        audioBean.setName("哎");
//        String filePath = "/sdcard/video/a0049.mp3";
//        String filePath = "/sdcard/video/a.mp3";
        String filePath = "/sdcard/video/notice.wav";
        GeeUILogUtils.logd("RhjAudioManager", "filePath:" + filePath);
        audioBean.setPath(filePath);

        ArrayList customAudioList = new ArrayList();
        customAudioList.add(audioBean);
        try {
            DDS.getInstance().getAgent().getTTSEngine().setCustomAudio(customAudioList);
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 打开唤醒
     */
    public void setWakeupInfo() {
        try {
            //设置音色
            //https://www.duiopen.com/docs/ct_cloud_TTS_Voice
            String speakerName = SPUtils.getInstance(context).getString(SPEAKER_NAME);
            if (speakerName == null) {
                speakerName = "lzliafp";//精品男童连连
            }
            setSpeaker(context, speakerName);
            //设置唤醒词
            enableWakeupWordXiaole = SPUtils.getInstance(context).getBooleanDefaultTrue(ENABLE_WORDUP_XIAOLE);
            enableWakeupWordXiaoPai = SPUtils.getInstance(context).getBoolean(ENABLE_WORDUP_XIAOPAI);
            enableWakeupWordXiaoTian = SPUtils.getInstance(context).getBoolean(ENABLE_WORDUP_XIAOTIAN);
            enableWakeupWordLeTianPai = SPUtils.getInstance(context).getBoolean(ENABLE_WORDUP_LETIANPAI);

            boolean custome = SPUtils.getInstance(context).getBoolean(ENABLE_WORDUP_CUSTOM_SWITCH);
            String custome_title = SPUtils.getInstance(context).getString(ENABLE_WORDUP_CUSTOM_TITLE);
            String custome_pinyin = SPUtils.getInstance(context).getString(ENABLE_WORDUP_CUSTOM_PINYIN);
            setWakeupWord(custome, custome_title, custome_pinyin);
            List<WakeupWord> result2 = DDS.getInstance().getAgent().getWakeupEngine().getMainWakeupWords();

            GeeUILogUtils.logd("RhjAudioManager", "RhjAudioManager 主唤醒词为--- " + Arrays.toString(result2.toArray()));
            DDS.getInstance().getAgent().getWakeupEngine().enableWakeupWhenAsr(true);
            DDS.getInstance().getAgent().getASREngine().enableVolume(true);
            enableWakeup();
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public void enableWakeup() {
        try {
            if (!isInitComplete()) {
                return;
            }
            GeeUILogUtils.logd("RhjAudioManager", "enableWakeup: " + enableWake);
            if (!enableWake) {
                DDS.getInstance().getAgent().getWakeupEngine().enableWakeup();
                enableWake = true;
            }
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭唤醒
     */
    public void disableWakeup() {
        try {
            GeeUILogUtils.logd("RhjAudioManager", "思必驰执行disableWakeup: " + enableWake);
            if (enableWake) {
                enableWake = false;
                DDS.getInstance().getAgent().getWakeupEngine().disableWakeup();
            }
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public void setWakeupWord(Context context, boolean enableXiaoLe, boolean enableXiaoPai, boolean enableXiaoTian, boolean enaleLeTianPai, boolean custome, String customTitle, String customPinyin) {
        SPUtils.getInstance(context).putBoolean(ENABLE_WORDUP_XIAOLE, enableXiaoLe);
        SPUtils.getInstance(context).putBoolean(ENABLE_WORDUP_XIAOPAI, enableXiaoPai);
        SPUtils.getInstance(context).putBoolean(ENABLE_WORDUP_XIAOTIAN, enableXiaoTian);
        SPUtils.getInstance(context).putBoolean(ENABLE_WORDUP_LETIANPAI, enaleLeTianPai);
        SPUtils.getInstance(context).putBoolean(ENABLE_WORDUP_CUSTOM_SWITCH, custome);
        SPUtils.getInstance(context).putString(ENABLE_WORDUP_CUSTOM_TITLE, customTitle);
        SPUtils.getInstance(context).putString(ENABLE_WORDUP_CUSTOM_PINYIN, customPinyin);
        enableWakeupWordXiaole = enableXiaoLe;
        enableWakeupWordXiaoPai = enableXiaoPai;
        enableWakeupWordXiaoTian = enableXiaoTian;
        enableWakeupWordLeTianPai = enaleLeTianPai;
        GeeUILogUtils.logd("RhjAudioManager", enableWakeupWordXiaole + " " + enableWakeupWordXiaoPai + " " + enableWakeupWordXiaoTian + " " + enableWakeupWordLeTianPai);
        setWakeupWord(custome, customTitle, customPinyin);
    }

    public void setSpeaker(Context context, String speakerName) {
        SPUtils.getInstance(context).putString(SPEAKER_NAME, speakerName);
        if (isInitComplete()) {
            if (speakerName.equals("robot")) {
                //https://www.duiopen.com/docs/ct_cloud_TTS_Voice
                try {
                    //默认机器人的底色是小军
                    DDS.getInstance().getAgent().getTTSEngine().setSpeaker("xijunmv4");
                    useFmod = true;
                } catch (DDSNotInitCompleteException e) {
                    e.printStackTrace();
                }
            } else {
                useFmod = false;
                //https://www.duiopen.com/docs/ct_cloud_TTS_Voice
                try {
                    GeeUILogUtils.logd("RhjAudioManager", "设置音色setSpeaker: " + speakerName);
                    if (speakerName.isEmpty()) {
                        //默认音色
                        speakerName = "xijunmv4";
                    }
                    DDS.getInstance().getAgent().getTTSEngine().setSpeaker(speakerName);
                    if ("lzliafp".equals(speakerName)) {
                        DDS.getInstance().getAgent().getTTSEngine().setSpeed(0.85f);
                    } else {
                        DDS.getInstance().getAgent().getTTSEngine().setSpeed(1.0f);
                    }

                    GeeUILogUtils.logd("RhjAudioManager", "setSpeaker: " + DDS.getInstance().getAgent().getTTSEngine().getSpeaker());
                } catch (DDSNotInitCompleteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setWakeupWord(boolean custome, String customTitle, String customPinyin) {
        try {
            GeeUILogUtils.logd("RhjAudioManager", enableWakeupWordXiaole + " " + enableWakeupWordLeTianPai + " " + enableWakeupWordXiaoTian + " " + enableWakeupWordLeTianPai);
            if (!isInitComplete()) {
                return;
            }
            ArrayList<WakeupWord> list = new ArrayList<>();
            if (enableWakeupWordXiaole) {
                WakeupWord mainWord = new WakeupWord().setPinyin("hai xiao le").setWord("嗨，小乐").setThreshold("0.15").addGreeting("哎");

                list.add(mainWord);
                GeeUILogUtils.logd("RhjAudioManager", list.toString());

            }
            if (enableWakeupWordXiaoPai) {
                WakeupWord mainWord = new WakeupWord().setPinyin("hai xiao pai").setWord("嗨，小派").setThreshold("0.20").addGreeting("哎");
                list.add(mainWord);
                GeeUILogUtils.logd("RhjAudioManager", list.toString());
            }

            if (enableWakeupWordXiaoTian) {
                WakeupWord mainWord = new WakeupWord().setPinyin("hai xiao tian").setWord("嗨，小天").setThreshold("0.20").addGreeting("哎");
                list.add(mainWord);
                GeeUILogUtils.logd("RhjAudioManager", list.toString());

            }
            if (enableWakeupWordLeTianPai) {
                WakeupWord mainWord = new WakeupWord().setPinyin("hai le tian pai").setWord("嗨，乐天派").setThreshold("0.15").addGreeting("哎");
                list.add(mainWord);
                GeeUILogUtils.logd("RhjAudioManager", list.toString());
            }
            if (custome && !customPinyin.isEmpty() && !customTitle.isEmpty()) {
                WakeupWord mainWord = new WakeupWord().setPinyin(customPinyin).setWord(customTitle).setThreshold("0.22").addGreeting("哎");
                list.add(mainWord);
            }
            GeeUILogUtils.logd("RhjAudioManager", "list+   " + list);
            DDS.getInstance().getAgent().getWakeupEngine().clearMainWakeupWord();
            DDS.getInstance().getAgent().getWakeupEngine().addMainWakeupWords(list);
            String[] result = DDS.getInstance().getAgent().getWakeupEngine().getWakeupWords();
            List<WakeupWord> result2 = DDS.getInstance().getAgent().getWakeupEngine().getMainWakeupWords();
            GeeUILogUtils.logd("RhjAudioManager", "RhjAudioManager 主唤醒词为--- " + Arrays.toString(result2.toArray()));
            GeeUILogUtils.logd("RhjAudioManager", "RhjAudioManager 唤醒词为--- " + Arrays.toString(result));
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 让当前唤醒状态进入下一个状态
     * 点击唤醒/停止识别/打断播报 操作接口
     */
    public void avatarClick() {
        try {
            DDS.getInstance().getAgent().avatarClick();
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置说话结束之后的停顿时间，默认500ms
     *
     * @param pauseTime
     */
    public void setVadPauseTime(Long pauseTime) {
        try {
            GeeUILogUtils.logd(TAG, "RhjAudioManager initComplete: " + DDS.getInstance().getAgent().getASREngine().getVadPauseTime());
            DDS.getInstance().getAgent().getASREngine().setVadPauseTime(pauseTime);
            GeeUILogUtils.logd(TAG, "RhjAudioManager 11 initComplete: " + DDS.getInstance().getAgent().getASREngine().getVadPauseTime());
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止所有播报
     */
    public void shutupTts() {
        try {
            DDS.getInstance().getAgent().getTTSEngine().shutup(null);
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public void updateWeatherLocation(String locationName) {
        JSONObject location = new JSONObject();
        try {
//            location.put("longitude", "xxxx");
//            location.put("latitude", "xxxx");
//            location.put("address", "测试地址");
            location.put("city", locationName);
//            location.put("time", "2018-08-22T12:46:16+0800");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        ContextIntent contextIntent = new ContextIntent("location", location.toString());
        try {
            DDS.getInstance().getAgent().updateProductContext(contextIntent);
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public void updateVocabs(List<String> list) {
        try {
            final String reqId = DDS.getInstance().getAgent().updateVocabs(
                    new VocabIntent()
                            .setName("应用名称")
                            .setAction(VocabIntent.ACTION_INSERT)
                            .setContents(list)
//                            .setContents(Arrays.asList("淘宝", "支付宝:支护宝,付款码"))
            );
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    private void setTtsListener() {
        try {
            DDS.getInstance().getAgent().getTTSEngine().setListener(new TTSEngine.CallbackOptimize() {
                @Override
                public void beginning(String s) {
                    GeeUILogUtils.logd("RhjAudioManager", "beginning: " + s + "   " + ttsStateChangeCallbackList.size());
//                    if (ttsStateChangeCallbackList.isEmpty()) {
//                        unInit(getDDS().mContext);
//                        init(getDDS().mContext, "72ff19dd886a72ff19dd886a645625bb");
//                    }
                    for (TtsStateChangeCallback ttsStateChangeCallback : ttsStateChangeCallbackList) {
                        ttsStateChangeCallback.onSpeakBegin();
                    }
                }

                @Override
                public void end(String ttsId, int i) {
                    GeeUILogUtils.logd("RhjAudioManager", "end: " + ttsId);
                    for (TtsStateChangeCallback ttsStateChangeCallback : ttsStateChangeCallbackList) {
                        ttsStateChangeCallback.onSpeakEnd(ttsId, i);
                    }
                }

                @Override
                public void error(String s) {
                    GeeUILogUtils.logd("RhjAudioManager", "error: ");
                    for (TtsStateChangeCallback ttsStateChangeCallback : ttsStateChangeCallbackList) {
                        ttsStateChangeCallback.error(s);
                    }
                }

                @Override
                public void onSpeechProgress(String ttsId, int currentFrame, int totalFrame, boolean isDataReady) {
                    super.onSpeechProgress(ttsId, currentFrame, totalFrame, isDataReady);
                    GeeUILogUtils.logd("RhjAudioManager", "currentFrame: " + currentFrame + " totalFrame " + totalFrame);
                    for (TtsStateChangeCallback ttsStateChangeCallback : ttsStateChangeCallbackList) {
                        ttsStateChangeCallback.onSpeakProgress(currentFrame, totalFrame);
                    }
                }

                @Override
                public void phoneReturnReceived(String s) {
                    GeeUILogUtils.logd("RhjAudioManager", "phoneReturnReceived: " + s);
                }
            });
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    private void setMessageObserver() {
        mMessageObserver.register(messageBean -> {
                    for (MessageCallback messageCallback : messageCallbackList) {
                        messageCallback.onMessage(messageBean);
                    }
                }, stateData -> {
                    for (WakeupStateChangeCallback wakeupStateChangeCallback : wakeupStateChangeCallbackList) {
                        wakeupStateChangeCallback.onState(stateData);
                    }
                }, doaData -> {
                    for (WakeupDoaCallback wakeupDoaCallback : wakeupDoaCallbackList) {
                        wakeupDoaCallback.onDoa(doaData);
                    }
                }, result -> {
                    wakeupResultCallback.getResult(result);
                }, () -> {
//                    speakBegin
                    for (VadBeginCallback vadBeginCallback : vadBeginCallbackList) {
                        vadBeginCallback.speakBegin();
                    }
                }

        );
    }

    private void setCommandObserver() {
        mCommandObserver.register((command, data) -> {
            for (CommandCallback commandCallback : commandCallbackList) {
                commandCallback.onCommand(command, data);
            }
        });
    }

    /**
     * 添加监听的命令项
     *
     * @param strings
     */
    public void addCommandSubscribeArray(String[] strings) {
        mCommandObserver.addSubscribe(strings);
    }

    /**
     * 添加消息监听项
     *
     * @param strings
     */
    public void addMessageSubscribeArray(String[] strings) {
        mMessageObserver.addSubscribe(strings);
    }

    /**
     * 获取语音处理结果所有内容
     * 如果需要白名单，可以在此处处理
     * {
     * "from":"dm.output",
     * "sessionId":"8489c5640ebc42e680c4e7dc951f38a2",
     * "recordId":"ecc7bca5ed6d4a94a6e5cfe641f47d70:f6070dd46d44498e88e2d39c57a6a967",
     * "skillId":"2019042500000544",
     * "skillName":false,
     * "taskId":"",
     * "shouldEndSession":true,
     * "intentName":"查询天气",
     * "task":"天气",
     * "nlg":"北京市今天全天多云，气温-8~2℃，和昨天差不多，有西南风转南风1级",
     * "ssml":"",
     * "speakUrl":"https:\/\/dds-ack.dui.ai\/runtime\/v1\/longtext\/ecc7bca5ed6d4a94a6e5cfe641f47d70:f6070dd46d44498e88e2d39c57a6a967?productId=279614681&aispeech-da-env=hd-ack",
     * "widget":Object{...},
     * "dmInput":"天气",
     * "endSessionReason":Object{...},
     * "display":"北京市今天全天多云，气温-8~2℃，和昨天差不多，有西南风转南风1级",
     * "watchId":"03e5f2552579412f8b3616accb510c8a"
     * }
     */
    private Thread thread;

    private void setResultWithoutSpeak() {
        DDS.getInstance().getAgent().setDMTaskCallback((dmTaskResult, type) -> {
            GeeUILogUtils.logd("RhjAudioManager", "RhjAudioManager onDMTaskResult: " + (rhjDMTaskCallback != null) + dmTaskResult);

            DmTaskResultBean dmTaskResultBean = mGson.fromJson(dmTaskResult.toString(), DmTaskResultBean.class);
            if (rhjDMTaskCallback != null) {
                if (rhjDMTaskCallback.dealResult(dmTaskResultBean)) {
                    try {
                        dmTaskResult.put("display", "");
                        dmTaskResult.put("nlg", "");
                        dmTaskResult.put("speakUrl", null); // 不设置为null的话，播放的还是原来的音频
                        dmTaskResult.put("shouldEndSession", true);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } finally {
                        return dmTaskResult;
                    }
                }
            }
            if (resultHistoryList.size() > RESULT_HISTORY_MAX_NUMBER) {
                resultHistoryList.remove(0);
            }
            GeeUILogUtils.logd("RhjAudioManager", "resultHistoryList: " + resultHistoryList.toString());

            try {
                if (type == DMTaskCallback.Type.DM_OUTPUT) {
                    if (useFmod) {
                        // if (true) {
                        GeeUILogUtils.logd("<<<", "进入fmod变声-----" + dmTaskResultBean.getSpeakUrl());
                        GeeUILogUtils.logd("RhjAudioManager", "onDMTaskResult: useFmod:" + useFmod);
                        new Thread(() -> {
                            GeeUILogUtils.logd("<<<", "进入fmod变声-----" + dmTaskResultBean.getSpeakUrl());
                            FmodSound.INSTANCE.playTts(dmTaskResultBean.getSpeakUrl(), new FmodSound.IPlaySoundListener() {
                                @Override
                                public void onFinish() {
                                    GeeUILogUtils.logd("<<<", "fmod 播放完成-----" + dmTaskResultBean.getSpeakUrl());
                                    if (fmodStateChangeCallback != null) {
                                        fmodStateChangeCallback.onFinish();
                                    }
                                }
                            });
                        }).start();
//                    FmodSound.INSTANCE.stopSound();
                        try {
                            //语音不播报，唤醒的回复会在。
                            dmTaskResult.put("nlg", "");
                            dmTaskResult.put("speakUrl", null); // 不设置为null的话，播放的还是原来的音频
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        GeeUILogUtils.logd("RhjAudioManager", "onDMTaskResult: useFmod:" + useFmod);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return dmTaskResult;
        });
    }

    HashMap<String, String> hashMap = new HashMap<>();

    // 创建dds配置信息≈
    private DDSConfig createConfig(Context context, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "";
        }
        DDSConfig config = new DDSConfig();
        // 基础配置项
        config.addConfig(DDSConfig.K_PRODUCT_ID, ""); // 产品ID -- 必填
        config.addConfig(DDSConfig.K_USER_ID, "");  // 用户ID -- 必填
        config.addConfig(DDSConfig.K_ALIAS_KEY, "prod");   // 产品的发布分支 -- 必填
        config.addConfig(DDSConfig.K_PRODUCT_KEY, "");// Product Key -- 必填
        config.addConfig(DDSConfig.K_PRODUCT_SECRET, "");// Product Secre -- 必填
        config.addConfig(DDSConfig.K_API_KEY, apiKey);  // 产品授权秘钥，服务端生成，用于产品授权 -- 必填

        String deviceId = getDeviceId(context);
        String deviceName = getDeviceName(deviceId);
        if (deviceName == null || deviceName.isEmpty()) {
            deviceName = deviceId;
        }
        config.addConfig(DDSConfig.K_DEVICE_ID, deviceId);//填入唯一的deviceId -- 选填
        config.addConfig(DDSConfig.K_DEVICE_NAME, deviceName);

        // 更多高级配置项,请参考文档: https://www.dui.ai/docs/ct_common_Andriod_SDK 中的 --> 四.高级配置项
        config.addConfig(DDSConfig.K_DUICORE_ZIP, "duicore.zip"); // 预置在指定目录下的DUI内核资源包名, 避免在线下载内核消耗流量, 推荐使用
        config.addConfig(DDSConfig.K_USE_UPDATE_DUICORE, "false"); //设置为false可以关闭dui内核的热更新功能，可以配合内置dui内核资源使用
        // 资源更新配置项
        config.addConfig(DDSConfig.K_CUSTOM_ZIP, "product.zip"); // 预置在指定目录下的DUI产品配置资源包名, 避免在线下载产品配置消耗流量, 推荐使用

        // TTS配置项
        config.addConfig(DDSConfig.K_STREAM_TYPE, AudioManager.STREAM_MUSIC); // 内置播放器的STREAM类型
        config.addConfig(DDSConfig.K_AUDIO_USAGE, AudioAttributes.USAGE_MEDIA);
        config.addConfig(DDSConfig.K_CONTENT_TYPE, AudioAttributes.CONTENT_TYPE_MUSIC);
        config.addConfig(DDSConfig.K_SOCKET_NODE_ENABLE, "false");
        config.addConfig(DDSConfig.K_TTS_MODE, "internal"); // TTS模式：external（使用外置TTS引擎，需主动注册TTS请求监听器）、internal（使用内置DUI TTS引擎）
        //唤醒配置项
        config.addConfig(DDSConfig.K_WAKEUP_ROUTER, "dialog"); //唤醒路由：partner（将唤醒结果传递给partner，不会主动进入对话）、dialog（将唤醒结果传递给dui，会主动进入对话）
        config.addConfig(DDSConfig.K_WAKEUP_DISABLE_CUSTOM_GREETING, "false");
        // 麦克风阵列配置项
        config.addConfig("PING_TIMEOUT", "10000");//单位ms,SDK初始化时，修改PING_TIMEOUT字段的值即可。默认10s
        // 全双工/半双工配置项
        config.addConfig(DDSConfig.K_DUPLEX_MODE, "HALF_DUPLEX");// 半双工模式
//        config.addConfig(DDSConfig.K_AEC_MODE, "external");//AEC模式，HAL层未集成AEC算法时，选择"internal"。HAL已经集成AEC算法时，选择"external"
        config.addConfig(DDSConfig.K_USE_SSPE, "true");//如果资源是SSPE资源，则需要将此配置置为true
        config.addConfig(DDSConfig.K_RECORDER_MODE, "internal");  //（适配了hal之后选内部，或者不写这一条，SDK默认是内部---录音机模式：external（使用外置录音机，需主动调用拾音接口）、internal（使用内置录音机，DDS自动录音）

        ///////////////////////////////////////////////////////////////////////////////////////

        config.addConfig(DDSConfig.K_RECORDER_MODE, "internal");  //（适配了hal之后选内部，或者不写这一条，SDK默认是内部---录音机模式：external（使用外置录音机，需主动调用拾音接口）、internal（使用内置录音机，DDS自动录音）
        config.addConfig(DDSConfig.K_MIC_TYPE, "1"); // （根据麦克风实际类型进行配置）设置硬件采集模组的类型 0：无。默认值。 1：单麦回消 2：线性四麦 3：环形六麦 4：车载双麦 5：家具双麦 6: 环形四麦  7: 新车载双麦 8: 线性6麦

        config.addConfig(DDSConfig.K_USE_SSPE, "true");//如果资源是SSPE资源，则需要将此配置置为true
        config.addConfig(DDSConfig.K_MIC_ARRAY_SSPE_BIN, "");//SSPE资源（放在test/src/main/assert文件夹下，或放到机器上指定绝对路径）(已包含aec算法)绝对路径，请务必保证绝对路径有可读写权限
        //config.addConfig(DDSConfig.K_WAKEUP_BIN, "wakeup_s20_zhihuijingling_20230103.bin"); //商务定制版唤醒资源的路径。如果开发者对唤醒率有更高的要求，请联系商务申请定制唤醒资源。
        config.addConfig(DDSConfig.K_AEC_MODE, "internal");//AEC模式，HAL层未集成AEC算法时，选择"internal"。HAL已经集成AEC算法时，选择"external"

        config.addConfig(DDSConfig.K_AUDIO_CHANNEL_COUNT, "2");
        config.addConfig(DDSConfig.K_AUDIO_CHANNEL_CONF, AudioFormat.CHANNEL_IN_MONO);
        config.addConfig(DDSConfig.K_AUDIO_SAMPLERATE, "32000");
        config.addConfig(DDSConfig.K_AUDIO_SOURCE, MediaRecorder.AudioSource.VOICE_RECOGNITION);


        /////////////////////////////////////////////////////////////////////////////////
        //config.addConfig(DDSConfig.K_WAKEUP_BIN, "wakeup_s20_zhihuijingling_20230103.bin"); //商务定制版唤醒资源的路径。如果开发者对唤醒率有更高的要求，请联系商务申请定制唤醒资源。
        if (BuildConfig.DEBUG) {
            config.addConfig(DDSConfig.K_CACHE_PATH, "/sdcard/cache"); // 调试信息保存路径,如果不设置则保存在默认路径"/sdcard/Android/data/包名/cache"
            config.addConfig(DDSConfig.K_CACHE_SIZE, "1024"); // 调试信息保存路径,如果不设置则保存在默认路径"/sdcard/Android/data/包名/cache"
            config.addConfig(DDSConfig.K_WAKEUP_DEBUG, "true"); // 用于唤醒音频调试, 开启后在 "/sdcard/Android/data/包名/cache" 目录下会生成唤醒音频
            config.addConfig(DDSConfig.K_VAD_DEBUG, "true"); // 用于过vad的音频调试, 开启后在 "/sdcard/Android/data/包名/cache" 目录下会生成过vad的音频
            config.addConfig(DDSConfig.K_ASR_DEBUG, "true"); // 用于识别音频调试, 开启后在 "/sdcard/Android/data/包名/cache" 目录下会生成识别音频
            config.addConfig(DDSConfig.K_TTS_DEBUG, "true");  // 用于tts音频调试, 开启后在 "/sdcard/Android/data/包名/cache/tts/" 目录下会自动生成tts音频
        }
//        https://iot-sz.aispeech.com/doc/cicada-doc/#/ProjectDocking/?id=%e5%8d%8a%e5%8f%8c%e5%b7%a5%e6%a8%a1%e5%bc%8f%e4%b8%8b%ef%bc%8c%e6%92%ad%e6%8a%a5%e5%94%a4%e9%86%92%e5%9b%9e%e5%a4%8d%e8%af%ad%e7%9a%84%e5%90%8c%e6%97%b6%ef%bc%8c%e8%bf%9b%e8%a1%8c%e8%af%ad%e9%9f%b3%e8%af%86%e5%88%ab%e7%9a%84%e6%96%b9%e6%a1%88

        GeeUILogUtils.logd("RhjAudioManager", "config->" + config.toString());
        return config;
    }

    private String getDeviceName(String deviceId) {
        
        return hashMap.get(deviceId);
    }

    private String getDeviceId(Context context) {
        String deviceid = AppUtils.getMacReadSystemFile(context);
        GeeUILogUtils.logd("RhjAudioManager", "getDeviceId: 上传给sdk 的值：" + deviceid);
        return deviceid;
    }

    public void setMessageCallback(MessageCallback messageCallback) {
        this.messageCallbackList.add(messageCallback);
    }

    public void setWakeupResultCallback(WakeupResultCallback wakeupResultCallback) {
        this.wakeupResultCallback = wakeupResultCallback;
    }

    public void setWakeupStateChangeCallback(WakeupStateChangeCallback wakeupStateChangeCallback) {
        this.wakeupStateChangeCallbackList.add(wakeupStateChangeCallback);
    }

    public void setVadBeginCallbackList(VadBeginCallback vadBeginCallback) {
        this.vadBeginCallbackList.add(vadBeginCallback);
    }

    public void setWakeupDoaCallback(WakeupDoaCallback wakeupDoaCallback) {
        this.wakeupDoaCallbackList.add(wakeupDoaCallback);
    }

    public void setTtsStateChangeCallback(TtsStateChangeCallback ttsStateChangeCallback) {
        this.ttsStateChangeCallbackList.add(ttsStateChangeCallback);
    }

    public void setCommandCallback(CommandCallback commandCallback) {
        this.commandCallbackList.add(commandCallback);
    }

    public void setRhjDMTaskCallback(RhjDMTaskCallback rhjDMTaskCallback) {
        this.rhjDMTaskCallback = rhjDMTaskCallback;
    }

    public void setFmodStateChangeCallback(FmodStateChangeCallback fmodStateChangeCallback) {
        this.fmodStateChangeCallback = fmodStateChangeCallback;
    }

    public void setAuthStatusCallback(AuthStatusCallback authStatusCallback) {
        this.authStatusCallback = authStatusCallback;
    }

    public void setInitCallback(InitCallback initCallback) {
        this.initCallback = initCallback;
    }

    public DDS getDDS() {
        return DDS.getInstance();
    }

    /**
     * String text, int priority, String ttsId, int audioFocus, String type
     *
     * @param text
     */
    public void speak(String text) {
        speak(text, 1);
    }

    /**
     * @param text
     * @param priority 建议是用 0 ，否则影响播放的同时，语音唤醒
     */
    public void speak(String text, int priority) {
        speak(text, priority, UUID.randomUUID().toString(), android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, "0");
    }

    public void speak(String text, String ttsId) {
        speak(text, 1, ttsId, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, "0");
    }

    public void speak(String text, String ttsId, int priority) {
        speak(text, priority, ttsId, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE, "0");
    }


    /**
     * @param text       播报文本，支持SSML。
     * @param priority   优先级
     *                   ①优先级0：保留，与DDS语音交互同级，仅限内部使用；
     *                   ②优先级1：正常，默认选项，同级按序播放；
     *                   ③优先级2：重要，可以插话<优先级1>，同级按序播放，播报完毕后继续播报刚才被插话的<优先级1>；
     *                   ④优先级3：紧急，可以打断当前正在播放的<优先级1|优先级2>，同级按序播放，播报完毕后不再继续播报刚才被打断的<优先级1｜优先级2>。
     * @param ttsId      用于追踪该次播报的id，建议使用UUID。
     * @param audioFocus 该次播报的音频焦点，默认值:
     *                   ①优先级0：android.media.AudioManager#AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
     *                   ②优先级非0：android.media.AudioManager#AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
     * @param type       语音合成格式 云端合成，建议是mp3，本地合成，可以用wav
     */
    public void speak(String text, int priority, String ttsId, int audioFocus, String type) {
        GeeUILogUtils.logd("RhjAudioManager", "speak: " + text);
        try {
            DDS.getInstance().getAgent().getTTSEngine().speak(text, priority, ttsId, audioFocus, type);
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public List<CustomAudioBean> getSpeakList() {
        try {
            return DDS.getInstance().getAgent().getTTSEngine().getCustomAudio();
        } catch (DDSNotInitCompleteException e) {
            throw new RuntimeException(e);
        }
    }

    public void speakAndStartDialog(String text) {
        GeeUILogUtils.logd("RhjAudioManager", "speakAndStartDialog  " + text);
        JSONObject jsonObject = new JSONObject();
        try {
//            DDS.getInstance().getAgent().stopDialog();
            GeeUILogUtils.logd("RhjAudioManager", "stopDialog  " + text);
            jsonObject.put("speakText", text);
            DDS.getInstance().getAgent().startDialog(jsonObject);
            GeeUILogUtils.logd("RhjAudioManager", "startDialog  " + jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public void stopDialogWithText(String text) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("speakText", text);
            DDS.getInstance().getAgent().stopDialog(jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public void startDialog() {
        try {
            DDS.getInstance().getAgent().startDialog();
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    public void stopDialog() {
        try {
            DDS.getInstance().getAgent().stopDialog();
        } catch (DDSNotInitCompleteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取最近的十条处理结果
     *
     * @return
     */
    public List<DmTaskResultBean> getResultHistoryList() {
        return resultHistoryList;
    }

    /**
     * 获取是否初始化成功
     *
     * @return
     */
    public boolean isInitComplete() {
        return initComplete;
    }

    /**
     * 获取是否授权成功
     *
     * @return
     */
    public boolean isAuthSuccess() {
        return authSuccess;
    }

    private void copyAssets() {
        FileUtils.copyFilesFassets(context, "greet", "/sdcard/video");
    }


}
