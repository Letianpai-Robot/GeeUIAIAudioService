package com.rhj.audio.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

@Entity(tableName = "dmResult")
data class DMResultModel(
    @SerializedName("display")
    val display: String?,
    @SerializedName("dmInput")
    val dmInput: String?,
    @SerializedName("endSessionReason")
    val endSessionReason: String?,
//    @SerializedName("error")
//    val error: String?,
    @SerializedName("from")
    val from: String?,
    @SerializedName("intentName")
    val intentName: String?,
    @SerializedName("nlg")
    val nlg: String?,
    @SerializedName("recordId")
    val recordId: String?,
    @SerializedName("sessionId")
    val sessionId: String?,
    @SerializedName("shouldEndSession")
    val shouldEndSession: Boolean?,
    @SerializedName("skillId")
    val skillId: String?,
    @SerializedName("skillName")
    val skillName: String?,
    @SerializedName("speakUrl")
    val speakUrl: String?,
    @SerializedName("ssml")
    val ssml: String?,
    @SerializedName("task")
    val task: String?,
    @SerializedName("taskId")
    val taskId: String?,
    @SerializedName("watchId")
    val watchId: String?,
    @PrimaryKey(autoGenerate = true)
    val id: Long? = System.currentTimeMillis(),
    val createTime: String? = SimpleDateFormat("yyyy年MM月dd日HH:mm:ss").format(Date())
)
