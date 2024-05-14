package com.newagedevs.gesturevolume.livedata

import androidx.lifecycle.LiveData

class CommunicatorLiveData : LiveData<String>() {
    fun setCommand(message: String){
        postValue(message)
    }
}