package com.rhj.message;

public class MessageBean {
    public static final int TYPE_OUTPUT = 7;
    public static final int TYPE_INPUT = 1;
    public static final int TYPE_WIDGET_CONTENT = 2;
    public static final int TYPE_WIDGET_LIST = 3;
    public static final int TYPE_WIDGET_WEB = 4;
    /**
     * 音乐类的消息回调，如歌曲信息
     */
    public static final int TYPE_WIDGET_MEDIA = 5;
    public static final int TYPE_WIDGET_WEATHER = 6;


    public static final int TYPE_WAKEUP_RESULT = 8;
    /**
     * asr 检测超时，声音为空
     */
    public static final int TYPE_VAD_TIMEOUT = 9;
    /**
     * 对话开始
     */
    public static final int TYPE_DIALOG_START = 10;
    /**
     * 对话结束
     */
    public static final int TYPE_DIALOG_END = 11;

    private int message_type;

    public int getMessage_type() {
        return message_type;
    }

    public void setMessage_type(int message_type) {
        this.message_type = message_type;
    }

}
