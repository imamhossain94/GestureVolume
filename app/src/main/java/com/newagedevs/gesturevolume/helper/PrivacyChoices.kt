package com.newagedevs.gesturevolume.helper

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.newagedevs.gesturevolume.GestureApplication
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.helper.extensions.findActivity

/**
 * The "Privacy choices" entry: lets a user in a consent region reopen Google's consent form and
 * change their answer, which the GDPR (and Google's UMP requirements) say must be possible at any
 * time, not only once.
 *
 * The form is Google's User Messaging Platform form, the same one shown at launch
 * (GestureApplication.requestConsentAndStartAds), reopened through
 * `UserMessagingPlatform.showPrivacyOptionsForm`. It is styled in AdMob › Privacy & messaging.
 */
object PrivacyChoices {

    private const val TAG = "PrivacyChoices"

    /**
     * Whether the entry should be shown at all.
     *
     * Only where Google says the user needs a way back to the form: the EEA, the UK and
     * Switzerland, once this install's consent has been checked. Debug builds count as in the EEA
     * (see GestureApplication.consentRequestParameters), so the entry is always there in them.
     * Never for Pro, which shows no ads and so never asks.
     */
    fun isAvailable(context: Context, isPro: Boolean): Boolean {
        if (isPro) return false
        return try {
            UserMessagingPlatform.getConsentInformation(context.applicationContext)
                .privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (e: Exception) {
            Log.e(TAG, "Could not read the consent status", e)
            false
        }
    }

    /**
     * Re-shows the consent form over the calling Activity.
     *
     * App-open ads (and, through the same flag, interstitials — see AdPacing) are paused while the
     * form is up, so closing it can never be followed by a full-screen ad. Any failure — no
     * Activity, or an error from the form — ends in a short toast rather than a tap that silently
     * does nothing. A first answer given here (when the launch form could not load, say) lets the
     * ads start.
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
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                preference.setAppOpenAdPaused(false)
                if (error != null) {
                    Log.w(TAG, "Privacy options form: ${error.errorCode} ${error.message}")
                    showError(activity.applicationContext)
                }
                (activity.application as? GestureApplication)?.startAdsIfAllowed()
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
}
