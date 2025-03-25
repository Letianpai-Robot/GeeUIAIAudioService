package com.rhj.speech.model


import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json

data class Data<H>(
    @Json(name = "config_data")
    val configData: H,
    @Json(name = "config_key")
    val configKey: String?,
    @Json(name = "config_title")
    val configTitle: String?
)