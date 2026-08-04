package com.newagedevs.gesturevolume.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The two OEM-level settings that actually decide whether a foreground service survives.
 *
 * Neither is something the app can grant itself. All it can do is take the user straight to the
 * right screen, which is the difference between a fix a user will apply and one they will not.
 */
object DeviceCareSettings {

    // ---- battery optimisation ---------------------------------------------------------------

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system list of battery-optimised apps.
     *
     * Deliberately the *settings list* rather than
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which pops a direct allow/deny dialog. Play's
     * policy only permits the direct request for a narrow set of app types, and an app that gets
     * flagged for it is worse off than one that asks the user to flip the switch themselves.
     */
    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    // ---- OEM auto-start ---------------------------------------------------------------------

    /**
     * Manufacturer auto-start / protected-app screens.
     *
     * These are private OEM activities, so they are matched against the package manager before
     * being offered and every launch is guarded — component names get renamed between ROM versions
     * without notice, and a stale entry would otherwise crash the app.
     */
    private val AUTO_START_COMPONENTS = listOf(
        // Xiaomi / Redmi / POCO (MIUI, HyperOS)
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // Oppo / Realme (ColorOS)
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        // Vivo (Funtouch / OriginOS)
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // Huawei / Honor (EMUI, MagicOS)
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        // Samsung (One UI)
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        // Letv, Asus, Nokia and other smaller skins
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        "com.evenwell.powersaving.g3" to "com.evenwell.powersaving.g3.exception.PowerSaverExceptionActivity"
    )

    /** The first auto-start intent this device actually resolves, or null when it has none. */
    fun autoStartIntent(context: Context): Intent? {
        val pm = context.packageManager
        for ((pkg, cls) in AUTO_START_COMPONENTS) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            val resolved = try {
                pm.queryIntentActivities(intent, 0)
            } catch (_: Exception) {
                emptyList()
            }
            if (resolved.isNotEmpty()) {
                return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return null
    }

    fun hasAutoStartScreen(context: Context): Boolean = autoStartIntent(context) != null

    // ---- app info ----------------------------------------------------------------------------

    /** Falls back to this app's own settings page, which every device has. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when the OS build is new enough for the restrictions users are hitting to apply. */
    val isModernAndroid: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
