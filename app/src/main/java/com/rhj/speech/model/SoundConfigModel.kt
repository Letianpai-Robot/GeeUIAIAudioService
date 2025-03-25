package com.rhj.speech.model
import com.google.gson.annotations.SerializedName


data class SoundConfigModel(
    @SerializedName("name")
    val name: String?,
    @SerializedName("face")
    val face: String?,
    @SerializedName("motion")
    val motion: Int?,
    @SerializedName("motionSpeed")
    val motionSpeed: Int?,
    @SerializedName("motionStep")
    val motionStep: Int?,
    @SerializedName("sound")
    val sound: String?,
    @SerializedName("time")
    val time: Int?
)