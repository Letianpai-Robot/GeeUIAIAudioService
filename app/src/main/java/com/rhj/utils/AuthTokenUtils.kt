package com.rhj.utils

import android.content.Context
import android.net.wifi.WifiManager
import com.letianpai.robot.components.utils.GeeUILogUtils
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object AuthTokenUtils {
    val partSecretKey = ""
    fun getMacSign(sn: String?, ts: Long): String {
        val hardcode = SystemProperties.get("persist.sys.hardcode")
        val deviceSecretKey = md5(sn + hardcode + ts + partSecretKey)
        val macSign = sha256(sn + hardcode + ts + deviceSecretKey)
        GeeUILogUtils.logd(
            "AuthTokenUtils",
            "getMacSign: sn:$sn ts:$ts  hardcode:$hardcode  partSecretKey:$partSecretKey  deviceSecretKey:$deviceSecretKey getMacSign: $macSign "
        )
        return macSign
    }

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun md5(params: String): String {
        try {
            val md = MessageDigest.getInstance("MD5")

            val string = params
            md.update(string.toByteArray())
            var tem: Int
            val buffer = StringBuffer()
            for (it in md.digest()) {
                tem = it.toInt()
                if (tem < 0) {
                    tem += 256
                }
                if (tem < 16) {
                    buffer.append("0")
                }
                buffer.append(Integer.toHexString(tem))
            }
            return buffer.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return ""
    }

    fun getWifiMac(context: Context?): String? {
        val wifiManager = context!!.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val sWifiMac = wifiInfo.macAddress
        return sWifiMac
    }
}
