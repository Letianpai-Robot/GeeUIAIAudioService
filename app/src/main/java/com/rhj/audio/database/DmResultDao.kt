package com.rhj.audio.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DmResultDao {
    @Query("SELECT * FROM dmResult")
    fun loadAll(): List<DMResultModel>

    @Query("SELECT * FROM dmResult")
    fun getAll(): LiveData<List<DMResultModel>>

    @Insert
    fun insertAll(vararg dms: DMResultModel)

    @Delete
    fun delete(dm: DMResultModel)

    @Query("delete from dmResult")
    fun deleteAll()
}