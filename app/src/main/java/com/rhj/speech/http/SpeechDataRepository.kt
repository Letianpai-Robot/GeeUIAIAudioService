package com.rhj.speech.http

import android.content.Context
import com.letianpai.network.net.Resource
import com.letianpai.network.net.ServiceGenerator
import com.letianpai.network.template.repository.NetDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.RequestBody
import kotlin.coroutines.CoroutineContext

class SpeechDataRepository(context: Context) : NetDataRepository(context) {
    private var remoteData: SpeechRemoteData
    private var ioDispatcher: CoroutineContext
    protected var serviceGenerator: ServiceGenerator = ServiceGenerator.getInstance()
    private var newApiService: Api = serviceGenerator.createService(Api::class.java)

    init {
        remoteData = SpeechRemoteData(context)
        ioDispatcher = Dispatchers.IO
    }

    suspend fun uploadLog(
        sn: String, ts: String, list: RequestBody
    ): Flow<Resource<Any?>> {
        return flow {
            emit(remoteData.uploadLog(sn, ts, list))
        }.flowOn(ioDispatcher)
    }

    suspend fun uploadCall(
        sn: String, ts: String, map:RequestBody
    ): Flow<Resource<Any?>> {
        return flow {
            emit(remoteData.uploadCall(sn, ts, map))
        }.flowOn(ioDispatcher)
    }

    suspend fun getConfigData(
        sn: String,
        ts: String,
        map: Map<String, String>
    ): Flow<Resource<Any>> {
        return flow {
            emit(remoteData.getConfigData(sn, ts, map))
        }.flowOn(ioDispatcher)
    }

    suspend fun getWakeConfig(
        sn: String,
        ts: String
    ): Flow<Resource<*>> {

        return flow {
            emit(remoteData.getWakeConfig(sn, ts))
        }
    }

    suspend fun getAiConfig(
        sn: String,
        ts: String,
    ): Flow<Resource<Any>> {
        return flow {
            emit(remoteData.getAiConfig(sn, ts))
        }.flowOn(ioDispatcher)
    }


}