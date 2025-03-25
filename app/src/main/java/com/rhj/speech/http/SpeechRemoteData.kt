package com.rhj.speech.http

import android.content.Context
import android.util.Log
import com.letianpai.network.model.BaseResultBean
import com.letianpai.network.net.Resource
import com.letianpai.network.template.datasource.RemoteData
import com.rhj.speech.model.Data
import com.rhj.speech.model.WakeUpModel
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Response

class SpeechRemoteData(context: Context) : RemoteData(context) {
    private var newApiService: Api = serviceGenerator.createService(Api::class.java)

    suspend fun uploadLog(sn: String, ts: String, list: RequestBody): Resource<Any?> {
        val response = processCall3Pramas(newApiService::uploadLog, sn, ts, list)
        Log.i("TAGresponse", "uploadLog: " + response)
        return if (response is String) {
            Resource.Success(data = response)
        } else {
            responseError(response)
        }
    }
 suspend fun uploadCall(sn: String, ts: String,  map: RequestBody): Resource<Any?> {
        val response = processCall3Pramas(newApiService::uploadCall, sn, ts, map)
        Log.i("TAGresponse", "uploadCall: " + response)
        return if (response is Any) {
            Resource.Success(data = response)
        } else {
            responseError(response)
        }
    }

    suspend fun getConfigData(
        sn: String, ts: String, map: Map<String, String>
    ): Resource<Any> {
        val response = processCall3Pramas(newApiService::getConfigData, sn, ts, map)
        return if (response is Data<*>) {
            Resource.Success(data = response)
        } else {
            responseError(response)
        }
    }

    suspend fun getWakeConfig(sn: String, ts: String): Resource<Any> {
        val hashMap = HashMap<String, String>()
        var testWakeConfig = newApiService.getWakeConfig(
            sn, ts, hashMap
        )
        val response = processCallResponse(testWakeConfig)
        return if (response is WakeUpModel) {
            Resource.Success(data = response)
        } else {
            responseError(response)
        }
    }

    suspend fun getAiConfig(
        sn: String, ts: String
    ): Resource<Any> {
        val hashMap = HashMap<String, String>()
        val response = processCall3Pramas(newApiService::getAiConfig, sn, ts, hashMap)
        Log.d("SpeechRemoteData", "getAiConfig:  ${response.toString()}" + " 66");
        return if (response is List<*>) {
            Log.d("SpeechRemoteData", "getAiConfig:  " + " 67");
            Resource.Success(data = response)
        } else {
            Log.d("SpeechRemoteData", "getAiConfig:  " + " 69");
            responseError(response)
        }
    }

}