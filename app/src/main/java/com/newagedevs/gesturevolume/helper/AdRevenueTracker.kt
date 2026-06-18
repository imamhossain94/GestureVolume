package com.newagedevs.gesturevolume.helper

import android.content.Context
import android.util.Log
import com.applovin.mediation.MaxAd

/**
 * Impression-level ad-revenue tracking for AppLovin MAX.
 *
 * Wire it via `setRevenueListener { ad -> AdRevenueTracker.logAdRevenue(context, ad) }` on every
 * ad object (interstitial, app open, banner, native loader). This is what lets us see which
 * placement/network actually earns instead of flying blind.
 *
 * Right now this app has no Firebase project wired (no google-services.json), so revenue is
 * logged via Logcat — useful for on-device validation during the 3–5 day rollout.
 *
 * TODO (follow-up, needs a Firebase project): add `google-services.json` + the
 * `com.google.gms.google-services` plugin + `firebase-analytics`, then swap the body below to log
 * the standard `ad_impression` event so ARPDAU breaks down by format/network/placement in Firebase:
 *
 *   val params = Bundle().apply {
 *       putString(FirebaseAnalytics.Param.AD_PLATFORM, "appLovin")
 *       putString(FirebaseAnalytics.Param.AD_SOURCE, ad.networkName)
 *       putString(FirebaseAnalytics.Param.AD_FORMAT, ad.format?.label ?: "unknown")
 *       putString(FirebaseAnalytics.Param.AD_UNIT_NAME, ad.adUnitId)
 *       putDouble(FirebaseAnalytics.Param.VALUE, ad.revenue)
 *       putString(FirebaseAnalytics.Param.CURRENCY, "USD")
 *   }
 *   FirebaseAnalytics.getInstance(context.applicationContext)
 *       .logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params)
 */
object AdRevenueTracker {

    fun logAdRevenue(context: Context, ad: MaxAd) {
        try {
            Log.i(
                "AdRevenue",
                "ad_impression revenue=${ad.revenue} network=${ad.networkName} " +
                    "format=${ad.format?.label ?: "unknown"} unit=${ad.adUnitId} " +
                    "placement=${ad.placement ?: "-"}"
            )
        } catch (e: Exception) {
            Log.e("AdRevenue", "Failed to log ad revenue", e)
        }
    }
}
