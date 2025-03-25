package com.rhj.audio.observer;

import com.aispeech.dui.dds.DDS;
import com.aispeech.dui.dsk.duiwidget.CommandObserver;
import com.letianpai.robot.components.utils.GeeUILogUtils;
import com.rhj.audio.Const;

/**
 * 客户端CommandObserver, 用于处理客户端动作的执行以及快捷唤醒中的命令响应.
 * 例如在平台配置客户端动作： command://call?phone=$phone$&name=#name#,
 * 那么在CommandObserver的onCall方法中会回调topic为"call", data为
 */
public class RhjCommandObserver implements CommandObserver {
    //    private static final String command_dance = "rhj.controller.dance";
//    private static final String command_navigation = "rhj.controller.navigation";
    private CommandCallback callback;
    private static final String TAG = "RhjCommandObserver";

    public static RhjCommandObserver getInstance() {
        return new RhjCommandObserver();
    }

    private RhjCommandObserver() {
    }

    // 注册当前更新消息
    public void register(CommandCallback callback) {
        GeeUILogUtils.logd("RhjCommandObserver", "register: 注册命令");
        this.callback = callback;
        DDS.getInstance().getAgent().subscribe(new String[]{Const.Remind.Insert, Const.Remind.Remove, Const.RhjController.move, Const.RhjController.turn, Const.RhjController.motion, Const.RhjController.show, Const.RhjController.earmotion, Const.RhjController.earlightcolor, Const.RhjController.earlightcolorOff, Const.RhjController.takePhoto, Const.DUIController.ShutDown, Const.DUIController.Reboot, Const.DUIController.OpenBluetooth, Const.DUIController.OpenSettings, Const.DUIController.SetVolumeWithNumber, Const.DUIController.CloseSettings, Const.DUIController.SoundsOpenMode, Const.DUIController.SoundsOpenMode, Const.DUIController.SoundsCloseMode, Const.DUIController.CloseBluetooth, Const.MediaController.Play, Const.MediaController.Pause, Const.MediaController.Stop, Const.MediaController.Switch, Const.MediaController.Next, Const.MediaController.Prev, Const.RhjController.congraturationBirthday, Const.RhjController.motionHappy, Const.RhjController.motionSad, Const.RhjController.motionAiApp,Const.RhjController.motionAiAppClose,Const.RhjController.motionHandEnter, Const.RhjController.motionhandExit, Const.RhjController.open, Const.RhjController.close, Const.RhjController.fingerGuessEnter, Const.RhjController.fingerGuessExit, Const.RhjController.bodyEnter, Const.RhjController.bodyExit, Const.RhjController.searchPeople, Const.RhjController.motionThinking, Const.RhjController.motionWho, Const.RhjController.dance, Const.RhjController.urgentcall, Const.RhjController.videocall,Const.RhjController.remind}, this);
    }

    public void addSubscribe(String[] strings) {
        DDS.getInstance().getAgent().subscribe(strings, this);
    }

    // 注销当前更新消息
    public void unregister() {
        GeeUILogUtils.logd("RhjCommandObserver", "unregister: 注销命令");
//        DDS.getInstance().getAgent().unSubscribe(this);
    }

    @Override
    public void onCall(String command, String data) {
        GeeUILogUtils.logd("DuiCommandObserver", "onCall: " + data);
        if (callback != null) {
            callback.onCommand(command, data);
        }
    }

}
