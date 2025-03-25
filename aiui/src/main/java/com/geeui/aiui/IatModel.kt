package com.geeui.aiui
import com.google.gson.annotations.SerializedName


data class IatModel(
    @SerializedName("text")
    var text: Text
)

data class Text(
    @SerializedName("bg")
    var bg: Int,
    @SerializedName("ed")
    var ed: Int,
    @SerializedName("ls")
    var ls: Boolean,
    @SerializedName("pgs")
    var pgs: String,
    @SerializedName("rg")
    var rg: List<Int>,
    @SerializedName("sn")
    var sn: Int,
    @SerializedName("ws")
    var ws: List<W>
)

data class W(
    @SerializedName("bg")
    var bg: Int,
    @SerializedName("cw")
    var cw: List<Cw>
)

data class Cw(
    @SerializedName("sc")
    var sc: Int,
    @SerializedName("w")
    var w: String
)