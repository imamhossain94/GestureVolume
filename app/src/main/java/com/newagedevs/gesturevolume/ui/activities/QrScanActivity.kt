package com.newagedevs.gesturevolume.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Scans a QR code or barcode for the Deck and acts on what it finds.
 *
 * Play services' own scanner rather than a camera pipeline of this app's: it needs no camera
 * permission at all — the scanning UI belongs to Play services, which already has one — and the
 * module is downloaded on demand, so the APK does not carry a vision library for a tile most
 * users will never open.
 *
 * Invisible, like [VoiceSearchActivity], and for the same reason: an overlay window cannot
 * receive an Activity result.
 */
@AndroidEntryPoint
class QrScanActivity : AppCompatActivity() {

    @Inject
    lateinit var preference: SharedPref

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode -> handle(barcode) }
            .addOnFailureListener {
                Toast.makeText(this, R.string.qr_failed, Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnCanceledListener { finish() }
    }

    /**
     * A URL is opened, a phone number dialled, a Wi-Fi or contact code copied.
     *
     * Everything scanned is put on the clipboard as well, and into the app's own history, so a
     * code that turns out to be something this app cannot open is not simply lost.
     */
    private fun handle(barcode: Barcode) {
        val raw = barcode.rawValue?.trim().orEmpty()
        if (raw.isEmpty()) {
            Toast.makeText(this, R.string.qr_empty, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        copy(raw)
        preference.addClipboardEntry(raw)

        val opened = when (barcode.valueType) {
            Barcode.TYPE_URL -> open(Intent(Intent.ACTION_VIEW, (barcode.url?.url ?: raw).toUri()))
            Barcode.TYPE_PHONE -> open(Intent(Intent.ACTION_DIAL, "tel:${barcode.phone?.number ?: raw}".toUri()))
            Barcode.TYPE_SMS -> open(Intent(Intent.ACTION_SENDTO, "smsto:${barcode.sms?.phoneNumber ?: raw}".toUri()))
            Barcode.TYPE_GEO -> {
                val point = barcode.geoPoint
                if (point != null) open(Intent(Intent.ACTION_VIEW, "geo:${point.lat},${point.lng}".toUri())) else false
            }
            else -> if (raw.startsWith("http://") || raw.startsWith("https://")) {
                open(Intent(Intent.ACTION_VIEW, raw.toUri()))
            } else {
                false
            }
        }
        if (!opened) {
            Toast.makeText(this, getString(R.string.qr_copied, raw.take(60)), Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun copy(text: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        runCatching { manager?.setPrimaryClip(ClipData.newPlainText("GestureVolume", text)) }
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
