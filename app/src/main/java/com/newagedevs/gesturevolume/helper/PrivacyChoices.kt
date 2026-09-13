package com.newagedevs.gesturevolume.helper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref

/**
 * The "Privacy choices" entry: lets a user in a GDPR region reopen the consent form they answered
 * at first launch and change their answer, which the GDPR (and Google's UMP requirements) say must
 * be possible at any time, not only once.
 *
 * The form is AppLovin's own Terms & Privacy Policy flow backed by Google UMP (configured in
 * GestureApplication.initializeAppLovinSdk), reopened through
 * `AppLovinSdk.cmpService.showCmpForExistingUser`.
 */
object PrivacyChoices {

    private const val TAG = "PrivacyChoices"

    /**
     * Whether the entry should be shown at all.
     *
     * Only when there is a consent answer to change: the ad SDK is initialised (it never is for
     * Pro, see GestureApplication.initializeAdsIfNeeded) and the user is in the GDPR geography.
     * Debug builds force that geography, so the entry is always visible there. Pro is checked
     * before touching the SDK so a Pro install never even creates an SDK instance.
     */
    fun isAvailable(context: Context, isPro: Boolean): Boolean {
        if (isPro) return false
        return try {
            val sdk = AppLovinSdk.getInstance(context.applicationContext)
            sdk.isInitialized &&
                sdk.configuration.consentFlowUserGeography ==
                AppLovinSdkConfiguration.ConsentFlowUserGeography.GDPR
        } catch (e: Exception) {
            Log.e(TAG, "Could not read consent geography", e)
            false
        }
    }

    /**
     * Re-shows the consent form over the calling Activity.
     *
     * App-open ads (and, through the same flag, interstitials — see AdPacing) are paused while the
     * form is up, so closing it can never be followed by a full-screen ad. Any failure — no
     * Activity, SDK not ready, or an error from the CMP — ends in a short toast rather than a tap
     * that silently does nothing.
     */
    fun show(context: Context, preference: SharedPref) {
        val activity = context.findActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed ||
            !isAvailable(activity, preference.isProFeatureActivated())
        ) {
            showError(context)
            return
        }

        preference.setAppOpenAdPaused(true)
        try {
            AppLovinSdk.getInstance(activity.applicationContext).cmpService
                .showCmpForExistingUser(activity) { error ->
                    Handler(Looper.getMainLooper()).post {
                        preference.setAppOpenAdPaused(false)
                        if (error != null) {
                            Log.w(TAG, "CMP error: ${error.code} ${error.message}")
                            showError(activity.applicationContext)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the consent form", e)
            preference.setAppOpenAdPaused(false)
            showError(context)
        }
    }

    private fun showError(context: Context) {
        Toast.makeText(
            context.applicationContext,
            R.string.privacy_choices_error,
            Toast.LENGTH_SHORT
        ).show()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
