package com.rhj.speech.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import com.letianpai.robot.components.utils.GeeUILogUtils

/**
 * Wi-Fi 自动连接广播
 */
class WIFIStateReceiver : BroadcastReceiver() {
    private var onWifiConnectedChange: OnWifiConnectedChange? = null
    override fun onReceive(context: Context, intent: Intent) {
        GeeUILogUtils.logd("WIFIStateReceiver", "onReceive---: " + intent.action)
        if (intent.action == WifiManager.NETWORK_STATE_CHANGED_ACTION || intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            if (onWifiConnectedChange != null) {
                onWifiConnectedChange!!.onConnect(isWifiConnected(context))
            }
        }
    }

    fun setOnWifiConnectedChange(onWifiConnectedChange: OnWifiConnectedChange?) {
        this.onWifiConnectedChange = onWifiConnectedChange
    }

    interface OnWifiConnectedChange {
        fun onConnect(isConnected: Boolean)
    }

    companion object {
        private val TAG = WIFIStateReceiver::class.java.name
        fun isWifiConnected(context: Context?): Boolean {
            if (context != null) {
                val mConnectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val mWiFiNetworkInfo = mConnectivityManager.activeNetworkInfo
                if (mWiFiNetworkInfo != null) {
                    GeeUILogUtils.logd(
                        "WIFIStateReceiver",
                        "isWifiConnected: " + (mWiFiNetworkInfo != null) + "  " + mWiFiNetworkInfo.isAvailable + "  " + mWiFiNetworkInfo.isConnected
                    )
                    return mWiFiNetworkInfo.isAvailable && mWiFiNetworkInfo.isConnected
                }
            }
            GeeUILogUtils.logd("WIFIStateReceiver", "isWifiConnected: mWiFiNetworkInfo null")
            return false
        }
    }
}