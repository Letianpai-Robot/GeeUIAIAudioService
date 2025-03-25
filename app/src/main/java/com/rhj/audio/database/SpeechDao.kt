package com.rhj.audio.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SpeechDao {
    
    @Query("SELECT * FROM speech ")
    fun loadAll(): List<Speech>

    @Insert
    fun insertAll(vararg users: Speech)

    @Delete
    fun delete(user: Speech)
}
