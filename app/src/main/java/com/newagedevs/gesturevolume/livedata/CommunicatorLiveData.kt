package com.newagedevs.gesturevolume.livedata

import androidx.lifecycle.LiveData

class CommunicatorLiveData : LiveData<String>() {

    /**
     * How many commands have landed, counted on the main thread as each one is set.
     *
     * LiveData hands every new observer the last value it holds, which suits state and not these:
     * they are one-off commands. An observer notes this as it subscribes, and so can tell a command
     * sent since from an old one replayed. See MainViewModel.observeCommunicator.
     */
    var sequence = 0
        private set

    fun setCommand(message: String){
        postValue(message)
    }

    override fun setValue(value: String?) {
        sequence++
        super.setValue(value)
    }
}
