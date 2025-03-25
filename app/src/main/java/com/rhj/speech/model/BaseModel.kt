package com.rhj.speech.model


import com.google.gson.annotations.SerializedName

data class BaseModel<T>(
    @SerializedName("code")
    val code: Int?,
    @SerializedName("data")
    val data: T?,
    @SerializedName("msg")
    val msg: String?
)