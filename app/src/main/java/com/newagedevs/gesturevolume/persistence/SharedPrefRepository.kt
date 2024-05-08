package com.newagedevs.gesturevolume.persistence

import android.content.Context
import com.newagedevs.gesturevolume.utils.Constants

class SharedPrefRepository(private val context: Context) {

    private val sharedPrefName = "MyPrefs"
    private val clickCountKey = "clickCount"
    private val openCountKey = "openCount"

    private fun incrementClickCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt(clickCountKey, 0)
        val editor = sharedPref.edit()
        editor.putInt(clickCountKey, currentCount + 1)
        editor.apply()
    }

    private fun getClickCount(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(clickCountKey, 0)
    }

    private fun resetClickCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(clickCountKey, 0)
        editor.apply()
    }


    private fun incrementOpenCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt(openCountKey, 0)
        val editor = sharedPref.edit()
        editor.putInt(openCountKey, currentCount + 1)
        editor.apply()
    }

    private fun getOpenCount(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(openCountKey, 0)
    }

    private fun resetOpenCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(openCountKey, 0)
        editor.apply()
    }

    fun shouldShowInterstitialAds(): Boolean {
        val clickCount = getClickCount()
        return if (clickCount == 0) {
            true
        } else if (clickCount < Constants.showAdsOnEveryClick) {
            incrementClickCount()
            false
        } else {
            resetClickCount()
            true
        }
    }

    fun shouldShowAppOpenAds(): Boolean {
        val clickCount = getOpenCount()
        return if (clickCount == 0) {
            true
        } else if (clickCount < Constants.showAdsOnEveryOpen) {
            incrementOpenCount()
            false
        } else {
            resetOpenCount()
            true
        }
    }

}
