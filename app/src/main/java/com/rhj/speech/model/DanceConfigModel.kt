package com.rhj.speech.model


import com.google.gson.annotations.SerializedName
import com.squareup.moshi.Json

data class DanceConfigModel(
    @Json(name = "controlWakeup")
    var controlWakeup: Boolean? = false,
    @Json(name = "jumpGuide")
    val jumpGuide: Boolean? = false,
    @Json(name = "singTapToListen")
    val singTapToListen: Boolean? = true,
    @Json(name = "musicTipsTexts")
    val musicTipsTexts: String? = "",
    @Json(name = "danceTipsTexts")
    val danceTipsTexts: String? = ""
)