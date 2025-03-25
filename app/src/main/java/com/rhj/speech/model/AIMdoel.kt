package com.rhj.speech.model

import com.google.gson.annotations.SerializedName
import com.squareup.moshi.JsonClass

import com.squareup.moshi.Json

@JsonClass(generateAdapter = true)
data class AIModel(
    @Json(name = "api_key")
    val apiKey: String?,
    @Json(name = "api_secret")
    val apiSecret: String?,
    @Json(name = "app_id")
    val appId: String?,
    @Json(name = "client_id")
    val clientId: String?,
    @Json(name = "create_time")
    val createTime: Int?,
    @Json(name = "id")
    val id: Int?,
    @Json(name = "is_aide")
    val isAide: Int?,
    @Json(name = "update_time")
    val updateTime: Int?,
    @Json(name = "user_id")
    val userId: Int?,
    @Json(name = "voice_type")
    val voiceType: String?
)


