package com.geeui.aiui.observer;

public interface WakeupResultCallback {
    /**
     * @param stateData avatar.silence 等待唤醒
     *                  avatar.listening 监听中
     *                  avatar.understanding 理解中
     *                  avatar.speaking 播放语音中
     */
    void getResult(String result);
}
