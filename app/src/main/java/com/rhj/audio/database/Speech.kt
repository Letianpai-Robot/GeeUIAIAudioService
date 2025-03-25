package com.rhj.audio.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speech")
data class Speech(
    @ColumnInfo(name = "first_name") val firstName: String?,
    @ColumnInfo(name = "last_name") val lastName: String?,
    @PrimaryKey(autoGenerate = true) val id: Long? = System.currentTimeMillis(),
)