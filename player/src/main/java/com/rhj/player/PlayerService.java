package com.rhj.player;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.Gson;
import com.rhj.message.MessageMediaListBean;
import com.rhj.message.MessageMusicBean;

import org.json.JSONObject;

import java.util.List;

/**
 * 播放音乐技能
 */
public class PlayerService extends Service {
    private static final String TAG = "PlayerService";
    public static final String INTENT_EXTRA_DATA = "data";
    private Player player;
    private Gson mGson;
    private List<MessageMusicBean> musicList;
    private AudioManager am;
    private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "PlayerService onCreate: ");
        mGson = new Gson();
        init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onNewDataLoad(intent.getStringExtra(INTENT_EXTRA_DATA));
        return super.onStartCommand(intent, flags, startId);
    }

    private void init() {
        Log.i(TAG, "PlayerService init: ");
        player = Player.getInstance();
        player.setOnPreparedListener(mp -> {
            Log.i("PlayerService", "init: " + "com.rhj.speech.MainActivity".equals(getTopActivityName(this)));
            if (mp != null && !playerWordId.isEmpty() && "com.rhj.speech.MainActivity".equals(getTopActivityName(this))) {
                mp.start();
            }
        });
        registerFocus();
    }

    private String getTopActivityName(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(1);
        if (runningTasks != null && runningTasks.size() > 0) {
            ActivityManager.RunningTaskInfo taskInfo = runningTasks.get(0);
            ComponentName componentName = taskInfo.topActivity;
            if (componentName != null && componentName.getClassName() != null) {
                return componentName.getClassName();
            }
        }
        return null;
    }

    private void registerFocus() {
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        onAudioFocusChangeListener = focusChange -> {
            Log.i(TAG, "PlayerService registerFocus: " + focusChange);
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    player.pause();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    //TODO  获取音频焦点之后不响应
                    Log.i(TAG, "PlayerService registerFocus:音频播放器获取音频焦点 ");

//                    player.start();
                    break;
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            am.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    public void unRegisterFocus() {
        am.abandonAudioFocus(onAudioFocusChangeListener);
    }

    private void onNewDataLoad(String json) {
        musicList = loadMusic(json);
        player.init(musicList);
    }

    public void setNewMusic(String json) {
        onNewDataLoad(json);
    }

    private List<MessageMusicBean> loadMusic(String data) {
        try {
            Log.d(TAG, "loadMusic: " + data);
            JSONObject object = new JSONObject(data);
            MessageMediaListBean musics = mGson.fromJson(data, MessageMediaListBean.class);
            return musics.getList();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void play() {
        if (player != null) {
            registerFocus();
            player.start();
        }
    }

    public void prev() {
        if (player != null) {
            player.prev();
        }
    }

    public void next() {
        if (player != null) {
            player.next();
        }
    }

    public void pause() {
        if (player != null) {
            unRegisterFocus();
            player.pause();
        }
    }

    public void stop() {
        if (player != null) {
            unRegisterFocus();
            player.stop();
        }
    }

    public boolean isPlaying() {
        if (player != null) {
            return player.isPlaying();
        }
        return false;
    }

    public void setOnFinishListener(Player.OnFinishListener listener) {

        player.setOnFinishListener(new Player.OnFinishListener() {
            @Override
            public void onFinish() {
                if (listener != null) {
                    listener.onFinish();
                }
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "PlayerService onBind: ");
        onNewDataLoad(intent.getStringExtra(INTENT_EXTRA_DATA));
        if (myBinder == null) {
            myBinder = new MyBinder();
        }
        return myBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "PlayerService onUnbind: ");
//        pause();
        stopSelf();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "PlayerService onDestroy: ");
        unRegisterFocus();
    }

    MyBinder myBinder;


    public class MyBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    public static String playerWordId = "";
}