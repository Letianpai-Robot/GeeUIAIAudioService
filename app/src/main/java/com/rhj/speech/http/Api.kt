package com.rhj.speech.http

import com.letianpai.network.model.BaseResultBean
import com.letianpai.network.template.datasource.NewApi
import com.rhj.speech.model.*
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface Api : NewApi {

    @POST("/your_api")
    suspend fun uploadLog(
        @Query("sn") sn: String, @Query("ts") ts: String, @Body list: RequestBody
    ): Response<BaseResultBean<String>>

    @POST("/your_api")
    suspend fun uploadCall(
        @Query("sn") sn: String, @Query("ts") ts: String, @Body map: RequestBody
    ): Response<BaseResultBean<Any>>

    @GET("/your_api")
    suspend fun getConfigData(
        @Query("sn") sn: String, @Query("ts") ts: String, @QueryMap map: Map<String, String>
    ): Response<BaseResultBean<Data<DanceConfigModel>>>

    @GET("/your_api")
    suspend fun getWakeConfig(
        @Query("sn") sn: String,
        @Query("ts") ts: String,
        @QueryMap map: Map<String, String>
    ): Response<BaseResultBean<WakeUpModel>>

    /**
     * 拿到大模型的配置
     */
    @GET("/your_api")
    suspend fun getAiConfig(
        @Query("sn") sn: String,
        @Query("ts") ts: String,
        @QueryMap map: Map<String, String>
    ): Response<BaseResultBean<List<AIModel>>>

}
