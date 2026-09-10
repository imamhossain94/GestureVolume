package com.newagedevs.gesturevolume.utils

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent

/**
 * The device-level toggles the bar and the Deck can flip: the torch, Do Not Disturb, auto-rotate,
 * and the media transport keys.
 *
 * One object rather than four, for the reason [VolumeController] is one object: every one of
 * these is an OEM-modified corner of Android that is documented to throw on some devices, and the
 * overlay must degrade rather than crash. Every framework call here is inside `runCatching`, and
 * every toggle returns the new state or `null` for "could not", so callers can say which.
 *
 * Nothing here needs a runtime permission prompt: the torch needs none at all, media keys need
 * none, Do Not Disturb wants the notification-policy access screen, and auto-rotate the same
 * WRITE_SETTINGS toggle the brightness actions already use.
 */
class DeviceToggles(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val camera: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val notifications: NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val audio: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Kept warm by the torch callback, because the framework offers no getter. */
    @Volatile
    private var torchOn: Boolean = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchOn = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) torchOn = false
        }
    }

    /** The first back-facing camera with a flash unit, or any camera with one. */
    private val torchCameraId: String? by lazy {
        val manager = camera ?: return@lazy null
        runCatching {
            val ids = manager.cameraIdList
            ids.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    /** Starts watching the torch. Pair with [stop]; the callback is held by the camera service. */
    fun start() {
        val manager = camera ?: return
        runCatching { manager.registerTorchCallback(torchCallback, mainHandler) }
    }

    fun stop() {
        val manager = camera ?: return
        runCatching { manager.unregisterTorchCallback(torchCallback) }
    }

    // ---- flashlight ----------------------------------------------------------------------------

    fun hasFlashlight(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) &&
            torchCameraId != null

    fun isFlashlightOn(): Boolean = torchOn

    /** @return the torch's new state, or null when the device has none or refused. */
    fun toggleFlashlight(): Boolean? = setFlashlight(!torchOn)

    fun setFlashlight(on: Boolean): Boolean? {
        val manager = camera ?: return null
        val id = torchCameraId ?: return null
        // The camera may be mid-use by another app, in which case setTorchMode throws
        // CameraAccessException; a torch the user cannot have is reported, not crashed on.
        return runCatching {
            manager.setTorchMode(id, on)
            torchOn = on
            on
        }.getOrNull()
    }

    // ---- Do Not Disturb ------------------------------------------------------------------------

    fun canToggleDnd(): Boolean =
        runCatching { notifications?.isNotificationPolicyAccessGranted == true }.getOrDefault(false)

    fun isDndOn(): Boolean = runCatching {
        val filter = notifications?.currentInterruptionFilter
            ?: NotificationManager.INTERRUPTION_FILTER_ALL
        filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(false)

    /** @return the new state, or null without notification-policy access. */
    fun toggleDnd(): Boolean? {
        if (!canToggleDnd()) return null
        val manager = notifications ?: return null
        val turnOn = !isDndOn()
        return runCatching {
            manager.setInterruptionFilter(
                if (turnOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            turnOn
        }.getOrNull()
    }

    /** The system screen where the user grants Do Not Disturb access. */
    fun dndAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    // ---- auto-rotate ---------------------------------------------------------------------------

    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun isAutoRotateOn(): Boolean = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    }.getOrDefault(true)

    /** @return the new state, or null when WRITE_SETTINGS has not been granted. */
    fun toggleAutoRotate(): Boolean? {
        if (!canWriteSettings()) return null
        val turnOn = !isAutoRotateOn()
        return runCatching {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (turnOn) 1 else 0
            )
            turnOn
        }.getOrNull()
    }

    // ---- media keys ----------------------------------------------------------------------------

    fun isMusicActive(): Boolean = runCatching { audio?.isMusicActive == true }.getOrDefault(false)

    /**
     * Sends one media key to whatever session is playing, exactly as a headset button would.
     *
     * The transport keys need no permission and no notification listener: the platform routes
     * them to the active media session itself. Only the *display* of what is playing needs more.
     */
    fun mediaKey(keyCode: Int): Boolean {
        val manager = audio ?: return false
        return runCatching {
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        }.getOrDefault(false)
    }

    fun mediaPlayPause(): Boolean = mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun mediaNext(): Boolean = mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun mediaPrevious(): Boolean = mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    // ---- system panels -------------------------------------------------------------------------

    /**
     * The quick Wi-Fi panel on Android 10+, the full settings screen before that.
     *
     * Toggling Wi-Fi directly has been closed to apps since Android 10, so a panel the user flips
     * themselves is the honest version of a Wi-Fi tile.
     */
    fun wifiPanelIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }

    fun bluetoothSettingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    /** The system calculator, via its app category; null when the device has none. */
    fun systemCalculatorIntent(): Intent? {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_CALCULATOR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
