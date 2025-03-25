package com.rhj.audio.utils;

import static android.content.Context.POWER_SERVICE;

import android.content.Context;
import android.os.PowerManager;

import java.lang.reflect.Method;

public class SystemFunctionUtil {
    public static void shutdownRobot(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);
        Class clazz = pm.getClass();
        try {
            Method shutdown = clazz.getMethod("shutdown", boolean.class, String.class, boolean.class);
            shutdown.invoke(pm, false, "shutdown", false);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
