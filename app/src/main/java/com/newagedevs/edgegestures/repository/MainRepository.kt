package com.newagedevs.edgegestures.repository

import com.newagedevs.edgegestures.model.AppHandler
import com.newagedevs.edgegestures.persistence.HandlerDao
import kotlinx.coroutines.flow.Flow
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