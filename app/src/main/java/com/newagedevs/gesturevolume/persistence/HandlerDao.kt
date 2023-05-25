package com.newagedevs.gesturevolume.persistence

import androidx.room.*
import com.newagedevs.gesturevolume.model.AppHandler
import kotlinx.coroutines.flow.Flow

@Dao
interface HandlerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHandler(shape: AppHandler): Long

    // GET Handler
    @Query("SELECT * FROM AppHandler LIMIT 1")
    fun getHandler(): AppHandler?

    @Query("SELECT * FROM AppHandler LIMIT 1")
    fun getHandlerFlow(): Flow<AppHandler>

    // DELETE Handler
    @Query("DELETE FROM AppHandler")
    fun deleteHandler()

}
