package com.newagedevs.gesturevolume.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.newagedevs.gesturevolume.model.AppHandler

@Database(entities = [AppHandler::class], version = 3, exportSchema = true)
@TypeConverters(value = [])
abstract class AppDatabase : RoomDatabase() {

    abstract fun handlerDao(): HandlerDao

}
