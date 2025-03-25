package com.geeui.aiui.observer;

/**
 * 唤醒角度回掉
 * {"doa":355,"wakeupWord":"嗨，小乐","wakeupType":"major"}
 */
public interface WakeupDoaCallback {
    void onDoa(String doaData);
}
