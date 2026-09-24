package com.newagedevs.gesturevolume.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.newagedevs.gesturevolume.data.local.SearchStore

/**
 * Where a phone number goes: the dialer or straight into a call, an SMS, or a WhatsApp or Telegram
 * chat.
 *
 * One place for it because two surfaces open numbers — the Deck's search and its quick-dial
 * buttons — and they share the settings that decide how: Call directly, and the country code. Two
 * copies of this had already drifted once, one encoding the number and one stripping it.
 *
 * WhatsApp and Telegram take a `wa.me` / `t.me` link rather than a package-specific intent, so a
 * user without the app gets a web page rather than a crash — and the number needs its country
 * code, which is why the prefix setting exists.
 */
object NumberIntents {

    /**
     * @param via one of [SearchStore.ALL_NUMBER_ACTIONS]; anything else is a call.
     */
    fun intentFor(context: Context, search: SearchStore, number: String, via: String): Intent =
        when (via) {
            SearchStore.NUMBER_SMS ->
                Intent(Intent.ACTION_SENDTO, "smsto:${SearchRouter.normalizeNumber(number)}".toUri())
            SearchStore.NUMBER_WHATSAPP ->
                Intent(
                    Intent.ACTION_VIEW,
                    "https://wa.me/${SearchRouter.toInternational(number, search.getDialPrefix())}".toUri()
                )
            SearchStore.NUMBER_TELEGRAM ->
                Intent(
                    Intent.ACTION_VIEW,
                    "https://t.me/+${SearchRouter.toInternational(number, search.getDialPrefix())}".toUri()
                )
            else -> {
                val direct = search.getDirectCall() &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED
                // The number as written, encoded, rather than stripped to its digits: a quick-dial
                // entry can be a service code such as *121#, and stripping it dials 121.
                Intent(
                    if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                    Uri.fromParts("tel", number.trim(), null)
                )
            }
        }
}
