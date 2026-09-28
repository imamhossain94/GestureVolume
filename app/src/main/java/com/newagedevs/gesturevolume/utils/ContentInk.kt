package com.newagedevs.gesturevolume.utils

import kotlin.math.pow

/**
 * The colour the Quick panel's number and icon are drawn in when the user has not chosen one: picked
 * for whatever is under them, so they stand out from the track, the fill, and the fill's animation.
 *
 * They used to swap between the track and fill colours, which is right for a white fill on a dark
 * track and wrong for nearly everything else: dark ink on a nebula, on the deep end of the liquid,
 * on a light track the user had picked. Now the swap colour is kept where it reads — it is the
 * user's own, and on the default colours it is what the panel has always looked like — and
 * otherwise the ink is a light or a dark shade of the colour under it, whichever stands out more.
 * A shade rather than plain white or black, so it looks like it belongs to what it is drawn on.
 *
 * Plain arithmetic on `0xAARRGGBB` ints rather than android.graphics.Color, so it can be tested
 * off a device. Alpha is ignored: what is under the panel's translucent track is not known here.
 */
object ContentInk {

    /** Enough contrast to keep the preferred colour: WCAG's for body text, 4.5:1. */
    const val KEEP_CONTRAST = 4.5

    /** How far toward white the light ink goes from the colour under it. */
    private const val LIGHT = 0.9f

    /** How far toward black the dark ink goes from the colour under it. */
    private const val DARK = 0.8f

    /**
     * The ink for something drawn over [background]: [preferred] when it stands out from it enough,
     * or else the light or dark shade of [background] that stands out more.
     */
    fun pick(background: Int, preferred: Int): Int {
        val bg = background or OPAQUE
        val pref = preferred or OPAQUE
        if (contrast(pref, bg) >= KEEP_CONTRAST) return pref
        val light = blend(bg, WHITE, LIGHT)
        val dark = blend(bg, BLACK, DARK)
        return if (contrast(light, bg) >= contrast(dark, bg)) light else dark
    }

    /** The WCAG contrast ratio between two opaque colours, 1..21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** WCAG relative luminance, 0 for black to 1 for white. */
    fun luminance(color: Int): Double =
        0.2126 * channel(color shr 16) + 0.7152 * channel(color shr 8) + 0.0722 * channel(color)

    private fun channel(bits: Int): Double {
        val c = (bits and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** [from] moved [t] of the way to [to], channel by channel, opaque. */
    fun blend(from: Int, to: Int, t: Float): Int {
        fun mix(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t + 0.5f).toInt().coerceIn(0, 255) shl shift
        }
        return OPAQUE or mix(16) or mix(8) or mix(0)
    }

    private const val OPAQUE = 0xFF shl 24
    private const val WHITE = -0x1
    private const val BLACK = OPAQUE
}
