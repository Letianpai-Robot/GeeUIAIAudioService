package com.geeui.aiui.observer;

public interface TtsStateChangeCallback {
    void onSpeakBegin();

    void onSpeakEnd(String ttsId,int errorCode);

    void onSpeakProgress(int current, int total);

    void error(String s);
}
