package com.newagedevs.gesturevolume.repository

import com.newagedevs.gesturevolume.model.AppHandler
import com.newagedevs.gesturevolume.persistence.HandlerDao
import timber.log.Timber


class MainRepository constructor(
    private val handlerDao: HandlerDao
) : Repository {


    fun getHandler(): AppHandler? {
        return handlerDao.getHandler()
    }

//    fun getHandlerFlow(): Flow<AppHandler> {
//        return handlerDao.getHandlerFlow()
//    }

    fun setHandler(handler: AppHandler) {
        handlerDao.deleteHandler()
        handlerDao.insertHandler(handler)
    }

    init {
        Timber.d("Injection MainRepository")
    }

}