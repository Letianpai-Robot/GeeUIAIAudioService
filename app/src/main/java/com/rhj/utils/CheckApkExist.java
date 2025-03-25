package com.rhj.utils;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.letianpai.robot.components.utils.GeeUILogUtils;

/**
 * Created by lijingnan on 26/04/2017.
 */
public class CheckApkExist {
    private static String aiPkgName = "com.rhj.ai";

    public static boolean checkApkExist(Context context, String packageName){
        if (TextUtils.isEmpty(packageName))
            return false;
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(packageName,
                            PackageManager.GET_UNINSTALLED_PACKAGES);
            GeeUILogUtils.logd("CheckApkExist", "checkApkExist: "+info.toString()); // Timber 是我打印 log 用的工具，这里只是打印一下 log
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            GeeUILogUtils.logd("CheckApkExist", "checkApkExist: "+e.toString()); // Timber 是我打印 log 用的工具，这里只是打印一下 log
            return false;
        }
    }

    public static boolean checkAIExist(Context context){
        return checkApkExist(context, aiPkgName);
    }
    // 剩余的可以自行扩展，下边会给出一些常用的包名
}