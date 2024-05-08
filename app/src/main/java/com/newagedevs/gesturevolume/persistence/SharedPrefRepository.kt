package com.newagedevs.gesturevolume.persistence

import android.content.Context
import com.newagedevs.gesturevolume.utils.Constants

class SharedPrefRepository(private val context: Context) {

    // Shared preferences Constants2
    private val sharedPrefName = "MyPrefs"

    // Click count properties
    private val clickCountKey = "clickCount"
    private val openCountKey = "openCount"

    // New properties
    private val isRunningKey = "isRunning"

    // Handler properties
    private val handlerPositionKey = "handlerPosition"
    private val handlerColorKey = "handlerColor"
    private val handlerSizeKey = "handlerSize"
    private val handlerWidthKey = "handlerWidth"
    private val handlerTranslationYKey = "handlerTranslationY"

    // Handler tap action properties
    private val handlerSingleTapKey = "handlerSingleTap"
    private val handlerDoubleTapKey = "handlerDoubleTap"
    private val handlerLongTapKey = "handlerLongTap"

    // Handler gesture action properties
    private val handlerSwipeUpKey = "handlerSwipeUp"
    private val handlerSwipeDownKey = "handlerSwipeDown"

    // Increment click count
    private fun incrementClickCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt(clickCountKey, 0)
        val editor = sharedPref.edit()
        editor.putInt(clickCountKey, currentCount + 1)
        editor.apply()
    }

    // Get click count
    private fun getClickCount(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(clickCountKey, 0)
    }

    // Reset click count
    private fun resetClickCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(clickCountKey, 0)
        editor.apply()
    }

    // Increment open count
    private fun incrementOpenCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val currentCount = sharedPref.getInt(openCountKey, 0)
        val editor = sharedPref.edit()
        editor.putInt(openCountKey, currentCount + 1)
        editor.apply()
    }

    // Get open count
    private fun getOpenCount(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(openCountKey, 0)
    }

    // Reset open count
    private fun resetOpenCount() {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(openCountKey, 0)
        editor.apply()
    }

    // Check if interstitial ads should be shown
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

    // Check if app open ads should be shown
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

    // New properties

    // Check if the app is running
    fun isRunning(): Boolean {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(isRunningKey, false)
    }

    // Set the app running state
    fun setRunning(isRunning: Boolean) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(isRunningKey, isRunning)
        editor.apply()
    }


    // Handler properties
    // Get the handler position
    fun getHandlerPosition(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerPositionKey, Constants.gravityTitles.last()) ?: Constants.gravityTitles.last()
    }

    // Set the handler position
    fun setHandlerPosition(handlerPosition: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerPositionKey, handlerPosition)
        editor.apply()
    }


    // Get the handler color
    fun getHandlerColor(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(handlerColorKey, Constants.defaultHandlerColor)
    }

    // Set the handler color
    fun setHandlerColor(handlerColor: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(handlerColorKey, handlerColor)
        editor.apply()
    }

    // Get the handler size
    // Set the handler size
    fun getHandlerSize(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerSizeKey, Constants.sizeTitles[1]) ?: Constants.sizeTitles[1]
    }

    // Set the handler position
    fun setHandlerSize(handlerSize: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerSizeKey, handlerSize)
        editor.apply()
    }


    // Get the handler width
    fun getHandlerWidth(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerWidthKey, Constants.widthTitles.first()) ?: Constants.widthTitles.first()
    }

    // Set the handler width
    fun setHandlerWidth(handlerWidth: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerWidthKey, handlerWidth)
        editor.apply()
    }


    // Save handler translationY
    fun setHandlerTranslationY(translationY: Float) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putFloat(handlerTranslationYKey, translationY)
        editor.apply()
    }

    // Retrieve handler translationY
    fun getHandlerTranslationY(): Float {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getFloat(handlerTranslationYKey, 260f)
    }



    // Get the handler single tap action
    fun getHandlerSingleTapAction(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerSingleTapKey, Constants.tapActionTitles[1]) ?: Constants.tapActionTitles[1]
    }

    // Set the handler width
    fun setHandlerSingleTapAction(value: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerSingleTapKey, value)
        editor.apply()
    }

    // Get the handler double tap action
    fun getHandlerDoubleTapAction(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerDoubleTapKey, Constants.tapActionTitles.first()) ?: Constants.tapActionTitles.first()
    }

    // Set the handler width
    fun setHandlerDoubleTapAction(value: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerDoubleTapKey, value)
        editor.apply()
    }

    // Get the handler long tap action
    fun getHandlerLongTapAction(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerLongTapKey, Constants.tapActionTitles.first()) ?: Constants.tapActionTitles.first()
    }

    // Set the handler width
    fun setHandlerLongTapAction(value: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerLongTapKey, value)
        editor.apply()
    }


    // Get the handler swipe up action
    fun getHandlerSwipeUpAction(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerSwipeUpKey, Constants.swipeUpTitles[1]) ?: Constants.swipeUpTitles[1]
    }

    // Set the handler swipe up action
    fun setHandlerSwipeUpAction(value: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerSwipeUpKey, value)
        editor.apply()
    }

    // Get the handler swipe down action
    fun getHandlerSwipeDownAction(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerSwipeDownKey, Constants.swipeDownTitles[1]) ?: Constants.swipeDownTitles[1]
    }

    // Set the handler swipe down action
    fun setHandlerSwipeDownAction(value: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerSwipeDownKey, value)
        editor.apply()
    }

}
