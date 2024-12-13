package com.newagedevs.gesturevolume.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat.startActivity
import com.newagedevs.gesturevolume.BuildConfig


fun getApplicationVersion(): String {
    return BuildConfig.VERSION_NAME
}

fun Context.toast(message:String){
    Toast.makeText(this, message , Toast.LENGTH_SHORT).show()
}

fun shareTheApp(context: Context) {
    ShareCompat.IntentBuilder.from((context as Activity)).setType("text/plain")
        .setChooserTitle("Chooser title")
        .setText("http://play.google.com/store/apps/details?id=" + context.packageName)
        .startChooser()
}

fun openMailApp(context: Context, subject: String, mail: Array<String>, content: String = "") {

    // Get app information
    val packageManager = context.packageManager
    val packageName = context.packageName
    var appName: String
    var appVersion: String
    var appBuild: String

    try {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
        appName = packageManager.getApplicationLabel(applicationInfo).toString()
        appVersion = packageInfo.versionName.toString()
        appBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        appName = "Unknown"
        appVersion = "Unknown"
        appBuild = "Unknown"
    }

    // Get device information
    val deviceName = Build.MODEL
    val androidVersion = Build.VERSION.SDK_INT

    // Build the email body
    val template = """
Q: What problem did you encounter?
A: 
$content

--------------
APP: $appName
Version: $appVersion
Build: $appBuild
Device: $deviceName
System: $androidVersion
""".trimIndent()


    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, mail)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, template)
        }
        startActivity(context, intent, null)
    } catch (ex: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "There are no email apps installed on your device",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun openAppStore(context: Context, link: String, error: (String?) -> Unit) {
    try {
        startActivity(context, Intent(Intent.ACTION_VIEW, Uri.parse(link)), null)
    } catch (e:Exception) {
        error(e.message)
    }
}

fun openWebPage(context: Context, url: String?, error: (String?) -> Unit) {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(context, browserIntent, null)
    } catch (e:Exception) {
        error(e.message)
    }
}

fun isUriEmpty(uri: Uri?):Boolean{
    return uri == null || uri == Uri.EMPTY
}