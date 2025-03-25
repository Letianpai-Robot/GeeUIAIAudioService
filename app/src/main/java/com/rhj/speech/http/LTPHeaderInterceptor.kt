package com.rhj.speech.http


import android.util.Log
import com.rhj.utils.AuthTokenUtils
import com.rhj.utils.SystemProperties
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * created by yujianbin on 2019/3/21
 *
 */
private const val contentType = "Content-Type"
private const val contentTypeValue = "application/json"

class YHeaderInterceptor() : Interceptor {
    private val partSecretKey = ""
    override fun intercept(chain: Interceptor.Chain): Response {
        Log.d("<<<", "result--chain=${chain}")
        val original = chain.request()
        //如果Mac地址不为空
        val macAddress = original.url.queryParameter("mac")
        val sn = original.url.queryParameter("sn")
        if (sn != null){
            return chain.proceed(getSnSignBuild(original))
        }
        if (macAddress != null) {
            return chain.proceed(getMacSignBuild(original))
        }
        return chain.proceed(original.newBuilder().build())
    }

    private fun getMacSignBuild(original: Request): Request {
        val macAddress = original.url.queryParameter("mac")
        val timestamp = original.url.queryParameter("ts")
        val deviceSecretKey = AuthTokenUtils.md5(macAddress + timestamp + partSecretKey)
        val macSign = sha256(macAddress + timestamp + deviceSecretKey)
        Log.d("<<<", "result--macSign=${macSign}")
        Log.d("<<<", "result--deviceSecretKey=${deviceSecretKey}")

        return original.newBuilder()
            .header(contentType, contentTypeValue)
            .header("Authorization", "Bearer $macSign")
            .method(original.method, original.body)
            .build()
    }

    private fun getSnSignBuild(original: Request): Request {
        val sn = original.url.queryParameter("sn")
        val ts = original.url.queryParameter("ts")
        val hardcode = SystemProperties.get("persist.sys.hardcode")
        val deviceSecretKey = AuthTokenUtils.md5(sn+ hardcode + ts + partSecretKey)
        val snSign = AuthTokenUtils.sha256(sn + hardcode + ts + deviceSecretKey)
        Log.d("<<<", "--hardcode=${hardcode}")
        Log.d("<<<", "--snSign=${snSign}")
        Log.d("<<<", "--deviceSecretKey=${deviceSecretKey}")
        return original.newBuilder()
            .header(contentType, contentTypeValue)
            .header("Authorization", "Bearer $snSign")
            .method(original.method, original.body)
            .build()

    }


    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
