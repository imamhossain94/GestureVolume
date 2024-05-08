package com.newagedevs.gesturevolume.persistence

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.newagedevs.gesturevolume.utils.Constants2

class SharedPrefRepository2(private val context: Context) {

    // Shared preferences Constants2
    private val sharedPrefName = "MyPrefs"

    // Click count properties
    private val clickCountKey = "clickCount"
    private val openCountKey = "openCount"

    // New properties
    private val isRunningKey = "isRunning"
    private val unlockConditionKey = "unlockCondition"
    private val alwaysOnDisplayKey = "alwaysOnDisplay"
    private val clockStyleIndexKey = "clockStyleIndex"
    private val clockColorKey = "clockColor"
    private val clockTextTransparencyKey = "clockTextTransparency"
    private val clockFrameTransparencyKey = "clockFrameTransparency"
    private val screenLockPrivacyKey = "screenLockPrivacy"
    private val overlayStyleIndexKey = "overlayStyleIndex"
    private val overlayColorKey = "overlayColor"
    private val overlayTransparencyKey = "overlayTransparency"
    private val gestureIncreaseVolumeKey = "gestureIncreaseVolume"
    private val gestureDecreaseVolumeKey = "gestureDecreaseVolume"
    private val colorPickerRecentColorsKey = "colorPickerRecentColors"

    // Handler properties
    private val handlerPositionKey = "handlerPosition"
    private val lockHandlerPositionKey = "lockHandlerPosition"
    private val handlerColorKey = "handlerColor"
    private val handlerTransparencyKey = "handlerTransparency"
    private val handlerSizeKey = "handlerSize"
    private val handlerWidthKey = "handlerWidth"
    private val vibrateHandlerOnTouchKey = "vibrateHandlerOnTouch"
    private val handlerTranslationYKey = "handlerTranslationY"


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
        } else if (clickCount < Constants2.showAdsOnEveryClick) {
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
        } else if (clickCount < Constants2.showAdsOnEveryOpen) {
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

    // Get the unlock condition
    fun getUnlockCondition(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(unlockConditionKey, Constants2.defaultUnlockCondition) ?: Constants2.defaultUnlockCondition
    }

    // Set the unlock condition
    fun setUnlockCondition(unlockCondition: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(unlockConditionKey, unlockCondition)
        editor.apply()
    }

    // Get the always-on display state
    fun isAlwaysOnDisplay(): Boolean {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(alwaysOnDisplayKey, false)
    }

    // Set the always-on display state
    fun setAlwaysOnDisplay(alwaysOnDisplay: Boolean) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(alwaysOnDisplayKey, alwaysOnDisplay)
        editor.apply()
    }

    // Get the clock style index
    fun getClockStyleIndex(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(clockStyleIndexKey, 0)
    }

    // Set the clock style index
    fun setClockStyleIndex(clockStyleIndex: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(clockStyleIndexKey, clockStyleIndex)
        editor.apply()
    }

    // Get the clock color
    fun getClockColor(): Int? {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(clockColorKey, Constants2.defaultClockColor)
    }

    // Set the clock color
    fun setClockColor(clockColor: Int?) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(clockColorKey, clockColor ?: Constants2.defaultClockColor)
        editor.apply()
    }

    // Get the text clock transparency
    fun getTextClockTransparency(): Float {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getFloat(clockTextTransparencyKey, Constants2.defaultTextClockTransparency)
    }

    // Set the clock transparency
    fun setTextClockTransparency(clockTransparency: Float) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putFloat(clockTextTransparencyKey, clockTransparency)
        editor.apply()
    }

    // Get the frame clock transparency
    fun getFrameClockTransparency(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(clockFrameTransparencyKey, Constants2.defaultFrameClockTransparency)
    }

    // Set the clock transparency
    fun setFrameClockTransparency(clockTransparency: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(clockFrameTransparencyKey, clockTransparency)
        editor.apply()
    }

    // Get the screen lock privacy state
    fun isScreenLockPrivacyEnabled(): Boolean {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(screenLockPrivacyKey, false)
    }

    // Set the screen lock privacy state
    fun setScreenLockPrivacy(screenLockPrivacy: Boolean) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(screenLockPrivacyKey, screenLockPrivacy)
        editor.apply()
    }

    // Get the overlay style index
    fun getOverlayStyleIndex(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(overlayStyleIndexKey, 0)
    }

    // Set the overlay style index
    fun setOverlayStyleIndex(overlayStyleIndex: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(overlayStyleIndexKey, overlayStyleIndex)
        editor.apply()
    }

    // Get the overlay color
    fun getOverlayColor(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(overlayColorKey, Constants2.defaultOverlayColor)
    }

    // Set the overlay color
    fun setOverlayColor(overlayColor: Int?) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(overlayColorKey, overlayColor ?: Constants2.defaultOverlayColor)
        editor.apply()
    }

    // Get the overlay transparency
    fun getOverlayTransparency(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(overlayTransparencyKey, Constants2.defaultOverlayTransparency)
    }

    // Set the overlay transparency
    fun setOverlayTransparency(overlayTransparency: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(overlayTransparencyKey, overlayTransparency)
        editor.apply()
    }

    // Get the gesture increase volume state
    fun isGestureIncreaseVolumeEnabled(): Boolean {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(gestureIncreaseVolumeKey, false)
    }

    // Set the gesture increase volume state
    fun setGestureIncreaseVolumeEnabled(gestureIncreaseVolume: Boolean) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(gestureIncreaseVolumeKey, gestureIncreaseVolume)
        editor.apply()
    }

    // Get the gesture decrease volume state
    fun isGestureDecreaseVolumeEnabled(): Boolean {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(gestureDecreaseVolumeKey, false)
    }

    // Set the gesture decrease volume state
    fun setGestureDecreaseVolumeEnabled(gestureDecreaseVolume: Boolean) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(gestureDecreaseVolumeKey, gestureDecreaseVolume)
        editor.apply()
    }

    // Get recent colors
    fun getRecentColors(): ArrayList<Int> {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPref.getString(colorPickerRecentColorsKey, "")
        val type = object : TypeToken<ArrayList<Int>>() {}.type
        return gson.fromJson(json, type) ?: ArrayList()
    }

    // Set recent colors
    fun setRecentColors(recentColors: ArrayList<Int>) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val gson = Gson()
        val json = gson.toJson(recentColors)
        editor.putString(colorPickerRecentColorsKey, json)
        editor.apply()
    }


    // Handler properties
// Get the handler position
    fun getHandlerPosition(): String {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getString(handlerPositionKey, Constants2.defaultHandlerPosition) ?: Constants2.defaultHandlerPosition
    }

    // Set the handler position
    fun setHandlerPosition(handlerPosition: String) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString(handlerPositionKey, handlerPosition)
        editor.apply()
    }

    // Get the lock handler position state
    fun isLockHandlerPositionEnabled(): Boolean {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(lockHandlerPositionKey, false)
    }

    // Set the lock handler position state
    fun setLockHandlerPositionEnabled(lockHandlerPosition: Boolean) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(lockHandlerPositionKey, lockHandlerPosition)
        editor.apply()
    }

    // Get the handler color
    fun getHandlerColor(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(handlerColorKey, Constants2.defaultHandlerColor)
    }

    // Set the handler color
    fun setHandlerColor(handlerColor: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(handlerColorKey, handlerColor)
        editor.apply()
    }

    // Get the handler transparency
    fun getHandlerTransparency(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(handlerTransparencyKey, Constants2.defaultHandlerTransparency)
    }

    // Set the handler transparency
    fun setHandlerTransparency(handlerTransparency: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(handlerTransparencyKey, handlerTransparency)
        editor.apply()
    }

    // Get the handler size
    fun getHandlerSize(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(handlerSizeKey, Constants2.defaultHandlerSize)
    }

    // Set the handler size
    fun setHandlerSize(handlerSize: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(handlerSizeKey, handlerSize)
        editor.apply()
    }

    // Get the handler width
    fun getHandlerWidth(): Int {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getInt(handlerWidthKey, Constants2.defaultHandlerWidth)
    }

    // Set the handler width
    fun setHandlerWidth(handlerWidth: Int) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(handlerWidthKey, handlerWidth)
        editor.apply()
    }

    // Get the vibrate handler on touch state
    fun isVibrateHandlerOnTouchEnabled(): Boolean {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(vibrateHandlerOnTouchKey, false)
    }

    // Set the vibrate handler on touch state
    fun setVibrateHandlerOnTouchEnabled(vibrateHandlerOnTouch: Boolean) {
        val sharedPref = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putBoolean(vibrateHandlerOnTouchKey, vibrateHandlerOnTouch)
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

}
