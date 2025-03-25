package com.rhj.audio;

public class Const {
    /**
     * 提醒命令
     * https://www.duiopen.com/docs/tixing_tyty
     */
    public static class Remind {
        public static final String Insert = "ai.dui.dskdm.reminder.insert";
        public static final String Remove = "ai.dui.dskdm.reminder.remove";
    }

    /**
     * 思必驰中控技能
     */
    public static class DUIController {
        public static final String ShutDown = "DUI.System.Shutdown";
        public static final String Reboot = "DUI.System.Reboot";
        /**
         * command" : {
         * "param" : {
         * <p>
         * "volume" : "+"
         * },
         * "api" : "DUI.MediaController.SetVolume"
         * },
         * <p>
         * 声音跳到百分之二十的时候 volume："20"
         * 声音大一点 volume："+"
         * 声音小一点 volume："-"
         */
        public static final String SetVolumeWithNumber = "DUI.MediaController.SetVolume";
        public static final String OpenBluetooth = "DUI.System.Connectivity.OpenBluetooth";
        public static final String CloseBluetooth = "DUI.System.Connectivity.CloseBluetooth";
        public static final String OpenSettings = "DUI.System.OpenSettings";
        public static final String CloseSettings = "DUI.System.CloseSettings";
        /**
         * 静音模式
         */
        public static final String SoundsOpenMode = "DUI.System.Sounds.OpenMode";
        public static final String SoundsCloseMode = "DUI.System.Sounds.CloseMode";
    }

    /**
     * 播放控制
     * https://www.duiopen.com/docs/bofangkongzhi_tytycomm
     */
    public static class MediaController {
        public static final String Play = "DUI.MediaController.Play";
        public static final String Pause = "DUI.MediaController.Pause";
        public static final String Stop = "DUI.MediaController.Stop";
        public static final String Replay = "DUI.MediaController.Replay";
        public static final String Prev = "DUI.MediaController.Prev";
        public static final String Next = "DUI.MediaController.Next";
        public static final String Switch = "DUI.MediaController.Switch";
        public static final String SwitchPlayMode = "DUI.MediaController.SwitchPlayMode";
        public static final String AddCollectionList = "DUI.MediaController.AddCollectionList";
        public static final String RemoveCollectionList = "DUI.MediaController.RemoveCollectionList";
        public static final String PlayCollectionList = "DUI.MediaController.PlayCollectionList";
        public static final String OpenCollectionList = "DUI.MediaController.OpenCollectionList";
        public static final String CloseCollectionList = "DUI.MediaController.CloseCollectionList";
        public static final String SetPlayMode = "DUI.MediaController.SetPlayMode";
        public static final String Progress = "DUI.MediaController.Progress";
    }

    public static class RhjController {
        public static final String GoBack = "DUI.System.GoBack";
        public static final String GoHome = "DUI.System.GoHome";
        public static final String OpenMode = "DUI.System.UserMode.OpenMode";
        //生日快乐祝福
        public static final String congraturationBirthday = "rhj.controller.congraturation";
        //        public static final String congraturationBirthday = "rhj.controller.congraturation";
        //转向
        public static final String move = "rhj.controller.navigation";//?direction=#方向#&number=#数值#
        public static final String turn = "rhj.controller.turn";//?direction=#方向#&number=#数值#
        public static final String motion = "rhj.controller.motion";//动作库
        public static final String earmotion = "com.controller.earmotion";//天线动作
        public static final String show = "rhj.controller.show";//表情
        public static final String earlightcolor = "com.controller.earlightcolor";//天线颜色
        public static final String earlightcolorOff = "com.controller.earlightcolor.off";//天线颜色
        public static final String dance = "com.controller.dance";//天线颜色

        //打开关闭
        public static final String takePhoto = "rhj.controller.takephoto";

        public static final String open = "rhj.controller.openApp"; //打开应用
        public static final String close = "rhj.controller.closeApp"; //关闭应用

        public static final String motionHappy = "rhj.controller.chatgpt.happy";

        public static final String motionSad = "rhj.controller.chatgpt.sad";

        public static final String motionAiApp = "rhj.controller.ai.enter";
        public static final String motionAiAppClose = "rhj.controller.ai.exit";
        //手势控制
        public static final String motionHandEnter = "rhj.controller.handscontroller";
        public static final String motionhandExit = "rhj.controller.cancelhands";


        //猜拳游戏
        public static final String fingerGuessEnter = "rhj.controller.fingerguess";
        public static final String fingerGuessExit = "rhj.controller.fingerguessclose";
        //人体识别
        public static final String bodyEnter = "rhj.controller.body.enter";
        public static final String bodyExit = "rhj.controller.body.exit";

        //找人
        public static final String searchPeople = "rhj.controller.searchPeople";
        public static final String remind = "rhj.controller.remind";

        public static final String motionThinking = "rhj.motion.thinking";
        public static final String motionWho = "rhj.motion.who";
        public static final String videocall = "rhj.controller.videocall";//视频通话
        public static final String urgentcall = "rhj.controller.urgentcall";//紧急求助


    }


}
