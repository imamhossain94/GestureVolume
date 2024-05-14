package com.newagedevs.gesturevolume.livedata

class LiveDataManager {
    companion object {
        private val communicator = CommunicatorLiveData()

        fun sendCommand(message: String) {
            communicator.setCommand(message)
        }

        fun communicator() = communicator

    }
}