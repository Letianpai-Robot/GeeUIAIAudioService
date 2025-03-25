package com.rhj.speech.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
/*
data class WakeUpModel(
    @Json(name = "boy_child_voice_switch")
    val boyChildVoiceSwitch: Int?,
    @Json(name = "boy_voice_switch")
    val boyVoiceSwitch: Int?,
    @Json(name = "girl_child_voice_switch")
    val girlChildVoiceSwitch: Int?,
    @Json(name = "girl_voice_switch")
    val girlVoiceSwitch: Int?,
    @Json(name = "letianpai_switch")
    val letianpaiSwitch: Int?,
    @Json(name = "more_modal_switch")
    val moreModalSwitch: Int?,
    @Json(name = "robot_voice_switch")
    val robotVoiceSwitch: Int?,
    @Json(name = "selected_voice_id")
    val selectedVoiceId: String?,
    @Json(name = "selected_voice_name")
    val selectedVoiceName: String?,
    @Json(name = "xiaole_switch")
    val xiaoleSwitch: Int?,@JsonClass(generateAdapter = true)
      @Json(name = "xiaopai_switch")
    val xiaopaiSwitch: Int?,
    @Json(name = "xiaotian_switch")
    val xiaotianSwitch: Int?
)*/
data class WakeUpModel(
    @Json(name = "boy_child_voice_switch")
    val boyChildVoiceSwitch: Int?,
    @Json(name = "boy_voice_switch")
    val boyVoiceSwitch: Int?,
    @Json(name = "custom_pinyin")
    val customPinyin: String?,
    @Json(name = "custom_switch")
    val customSwitch: Int?,
    @Json(name = "custom_title")
    val customTitle: String?,
    @Json(name = "girl_child_voice_switch")
    val girlChildVoiceSwitch: Int?,
    @Json(name = "girl_voice_switch")
    val girlVoiceSwitch: Int?,
    @Json(name = "letianpai_switch")
    val letianpaiSwitch: Int?,
    @Json(name = "more_modal_switch")
    val moreModalSwitch: Int?,
    @Json(name = "robot_voice_switch")
    val robotVoiceSwitch: Int?,
    @Json(name = "selected_voice_id")
    val selectedVoiceId: String?,
    @Json(name = "selected_voice_name")
    val selectedVoiceName: String?,
    @Json(name = "xiaole_switch")
    val xiaoleSwitch: Int?,
    @Json(name = "xiaopai_switch")
    val xiaopaiSwitch: Int?,
    @Json(name = "xiaotian_switch")
    val xiaotianSwitch: Int?
)
