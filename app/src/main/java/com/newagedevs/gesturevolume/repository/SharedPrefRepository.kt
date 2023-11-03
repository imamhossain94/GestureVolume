package com.newagedevs.gesturevolume.repository

import android.content.Context

class SharedPrefRepository(private val context: Context) {

    private val sharedPrefName = "MyPrefs"
    private val clickCountKey = "clickCount"

    fun incrementClickCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt(clickCountKey, 0)
        val editor = sharedPref.edit()
        editor.putInt(clickCountKey, currentCount + 1)
        editor.apply()
    }

    fun getClickCount(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(clickCountKey, 0)
    }

    fun resetClickCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(clickCountKey, 0)
        editor.apply()
    }
}
