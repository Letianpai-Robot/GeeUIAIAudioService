package com.rhj.audio.observer;


import com.aispeech.dui.dds.DDS;
import com.aispeech.dui.dds.agent.MessageObserver;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.letianpai.robot.components.utils.GeeUILogUtils;
import com.rhj.message.MessageBean;
import com.rhj.message.MessageMusicBean;
import com.rhj.message.MessageMediaListBean;
import com.rhj.message.MessageOutputTextBean;
import com.rhj.message.MessageResultInputBean;
import com.rhj.message.MessageVadBean;
import com.rhj.message.MessageWidgetBean;
import com.rhj.message.MessageWidgetContentBean;
import com.rhj.message.MessageWidgetListBean;
import com.rhj.message.MessageWidgetWebBean;
import com.rhj.message.MessageWeatherBean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 客户端MessageObserver, 用于处理客户端动作的消息响应.
 * SDK 支持的消息
 */
public class RhjMessageObserver implements MessageObserver {
    private WakeupResultCallback wakeupResultCallback;

    public static RhjMessageObserver getInstance() {
        return new RhjMessageObserver();
    }

    private RhjMessageObserver() {
        mGson = new Gson();
    }


    private MessageCallback messageCallback;
    private WakeupStateChangeCallback wakeupStateChangeCallback;
    private WakeupDoaCallback wakeupDoaCallback;
    private VadBeginCallback vadBeginCallback;
    private Gson mGson;
    /**
     * https://iot-sz.aispeech.com/doc/cicada-doc/#/FAQ/dds_sdk_android?id=q%ef%bc%9asdk%e6%94%af%e6%8c%81%e7%9a%84%e6%b6%88%e6%81%af%e6%9c%89%e5%93%aa%e4%ba%9b%ef%bc%9f
     */
    private String[] mSubscribeKeys = new String[]{"sys.dialog.state", "sys.dialog.start", "sys.dialog.end", "context.output.text", "context.input.text", "context.widget.content", "context.widget.list", "context.widget.web", "context.widget.media", "context.widget.custom", "sys.wakeup.doa", "sys.wakeup.result", "sys.vad.begin", "sys.vad.timeout",//asr 没有检测到人声
            "sys.recorder.end"};

    // 注册当前更新消息
    public void register(MessageCallback messageCallback, WakeupStateChangeCallback wakeupStateChangeCallback, WakeupDoaCallback wakeupDoaCallback, WakeupResultCallback wakeupResultCallback, VadBeginCallback vadBeginCallback) {
        GeeUILogUtils.logd("RhjMessageObserver", "register: 注册消息");
        this.messageCallback = messageCallback;
        this.wakeupStateChangeCallback = wakeupStateChangeCallback;
        this.wakeupDoaCallback = wakeupDoaCallback;
        this.wakeupResultCallback = wakeupResultCallback;
        this.vadBeginCallback = vadBeginCallback;
        DDS.getInstance().getAgent().subscribe(mSubscribeKeys, this);
    }

    public void addSubscribe(String[] strings) {
        DDS.getInstance().getAgent().subscribe(strings, this);
    }

    // 注销当前更新消息
    public void unregister() {
        GeeUILogUtils.logd("RhjMessageObserver", "unregister: 注销消息");
//        DDS.getInstance().getAgent().unSubscribe(this);
    }

    @Override
    public void onMessage(String message, String data) {
        GeeUILogUtils.logd("DuiMessageObserver", "message : " + message + " data : " + data);

        switch (message) {
            case "context.output.text":
                MessageOutputTextBean messageOutputTextBean = mGson.fromJson(data, MessageOutputTextBean.class);
                messageOutputTextBean.setMessage_type(MessageBean.TYPE_OUTPUT);
                updateMessage(messageOutputTextBean);
                break;
            case "context.input.text":
                MessageResultInputBean messageResultInputBean = mGson.fromJson(data, MessageResultInputBean.class);
                messageResultInputBean.setMessage_type(MessageBean.TYPE_INPUT);
                updateMessage(messageResultInputBean);
                break;
            case "context.widget.content":
                MessageWidgetContentBean messageWidgetContentBean = new MessageWidgetContentBean();
                messageWidgetContentBean.setMessage_type(MessageBean.TYPE_WIDGET_CONTENT);
                try {
                    JSONObject jo = new JSONObject(data);
                    String title = jo.optString("title", "");
                    String subTitle = jo.optString("subTitle", "");
                    String imgUrl = jo.optString("imageUrl", "");
                    messageWidgetContentBean.setTitle(title);
                    messageWidgetContentBean.setSubTitle(subTitle);
                    messageWidgetContentBean.setImgUrl(imgUrl);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                updateMessage(messageWidgetContentBean);
                break;
            case "context.widget.list":
                MessageWidgetListBean messageWidgetListBean = new MessageWidgetListBean();
                messageWidgetListBean.setMessage_type(MessageBean.TYPE_WIDGET_LIST);
                try {
                    JSONObject jo = new JSONObject(data);
                    int currentPage = jo.optInt("currentPage");
                    messageWidgetListBean.setCurrentPage(currentPage);

                    int itemsPerPage = jo.optInt("itemsPerPage");
                    messageWidgetListBean.setItemsPerPage(itemsPerPage);
                    JSONArray array = jo.optJSONArray("content");

                    if (array == null || array.length() == 0) {
                        return;
                    }
                    Type type = new TypeToken<List<MessageWidgetBean>>() {
                    }.getType();
                    List<MessageWidgetBean> list = mGson.fromJson(jo.optJSONArray("content").toString(), type);
                    messageWidgetListBean.setMessageWidgetBean(list);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                updateMessage(messageWidgetListBean);
                break;
            case "context.widget.web":
                MessageWidgetWebBean messageWidgetWebBean = new MessageWidgetWebBean();
                messageWidgetWebBean.setMessage_type(MessageBean.TYPE_WIDGET_WEB);
                try {
                    JSONObject jo = new JSONObject(data);
                    String url = jo.optString("url");
                    messageWidgetWebBean.setUrl(url);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                updateMessage(messageWidgetWebBean);
                break;
            case "context.widget.custom":
                MessageWeatherBean messageWeatherBean = new MessageWeatherBean();
                messageWeatherBean.setMessage_type(MessageBean.TYPE_WIDGET_WEATHER);
                try {
                    GeeUILogUtils.showLargeLog("RhjMessageObserver", "" + data);
                    JSONObject jo = new JSONObject(data);
                    String name = jo.optString("name");
                    if (name.equals("weather")) {
                        messageWeatherBean = mGson.fromJson(data, MessageWeatherBean.class);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                updateMessage(messageWeatherBean);
                break;
            case "context.widget.media":
                JSONObject jsonObject;
                MessageMediaListBean messageMusicListBean = new MessageMediaListBean();
                messageMusicListBean.setMessage_type(MessageBean.TYPE_WIDGET_MEDIA);
                try {
                    jsonObject = new JSONObject(data);
                    int count = jsonObject.optInt("count");
                    String name = jsonObject.optString("widgetName");
                    messageMusicListBean.setCount(count);
                    messageMusicListBean.setWidgetName(name);
                    JSONObject object = new JSONObject(data);
                    JSONArray array = object.optJSONArray("content");

                    if (array == null || array.length() == 0) {
                        return;
                    }
                    Type type = new TypeToken<List<MessageMusicBean>>() {
                    }.getType();
                    List<MessageMusicBean> messageMusicBeans = mGson.fromJson(object.optJSONArray("content").toString(), type);
                    messageMusicListBean.setList(messageMusicBeans);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                updateMessage(messageMusicListBean);
                break;
            case "sys.wakeup.doa":
                GeeUILogUtils.logd("RhjMessageObserver", "onMessage: 声源定位角度" + data);
                if (wakeupDoaCallback != null) {
                    wakeupDoaCallback.onDoa(data);
                }
                break;
            case "sys.dialog.state":
                if (wakeupStateChangeCallback != null) {
                    wakeupStateChangeCallback.onState(data);
                }
                break;

            case "sys.wakeup.result":
                GeeUILogUtils.logd("sys.wakeup.result", "onMessage: sys.wakeup.result");
                if (wakeupResultCallback != null) {
                    GeeUILogUtils.logd("sys.wakeup.result", "wakeupResultCallback");
                    wakeupResultCallback.getResult(data);
                }
                break;
            case "sys.vad.timeout":
                MessageVadBean messageVadBean = new MessageVadBean();
                messageVadBean.setMessage_type(MessageBean.TYPE_VAD_TIMEOUT);
                updateMessage(messageVadBean);
                break;
            case "sys.dialog.start":
                MessageBean messageBean = new MessageBean();
                messageBean.setMessage_type(MessageBean.TYPE_DIALOG_START);
                updateMessage(messageBean);
                break;

            case "sys.dialog.end":
                MessageBean messageEndBean = new MessageBean();
                messageEndBean.setMessage_type(MessageBean.TYPE_DIALOG_END);
                updateMessage(messageEndBean);
                break;
            case "sys.vad.begin":
                if (vadBeginCallback != null) {
                    vadBeginCallback.speakBegin();
                }
                break;

            default:

        }
    }


    private void updateMessage(MessageBean bean) {
        if (messageCallback != null) {
            messageCallback.onMessage(bean);
        }
    }

}
