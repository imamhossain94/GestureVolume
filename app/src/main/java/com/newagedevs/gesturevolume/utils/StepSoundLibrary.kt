package com.newagedevs.gesturevolume.utils

/**
 * Which sound the Quick panel's steps make: one of the app's own, or one the user picked from the
 * phone.
 *
 * Stored as a single string, so one preference holds either: an id from [IDS] for the app's own,
 * or [DEVICE_PREFIX] and a content uri for a sound from the phone. Nothing here touches Android, so
 * the rules for reading a stored value back are tested on their own; [StepSound] turns the ids into
 * the files in res/raw.
 *
 * The app's sounds are made by tool/sounds/step_sounds.py: synthesised from code, all but the cat's
 * meow, a recording dedicated to the public domain (CC0; see tool/sounds/sources) — so there is
 * nothing in them to license, in the store or anywhere else.
 */
object StepSoundLibrary {

    /** The tick the Quick panel has always made, and still makes until another is chosen. */
    const val TICK = "tick"

    /** The app's own sounds, in the order they are offered: the crisp ones first, the ringing ones last. */
    val IDS: List<String> = listOf(
        TICK, "tap", "dial", "keys", "wood", "pop", "drop", "bubble", "snap", "thud", "blip",
        "marimba", "glass", "chime",
        // The button animations' own sounds, there to choose for the Quick panel too.
        "twinkle", "firework", "rumble", "launch", "swish", "crack",
        "whoosh", "spray", "bubbles", "meow", "monkey", "ufo", "buzz", "splash", "ribbit", "zip", "balloon", "burst",
        "cosmic", "boing",
    )

    const val DEFAULT = TICK

    /**
     * Sounds an animation plays of its own accord rather than at a press — the balloon's hiss as it
     * fills — not offered to choose: too long for a step.
     */
    val CUES: List<String> = listOf("inflate")

    /** The start of a stored value that names a sound on the phone rather than one of the app's. */
    const val DEVICE_PREFIX = "device:"

    /** The stored value for a sound on the phone at [uri]. */
    fun device(uri: String): String = DEVICE_PREFIX + uri

    fun isDevice(source: String?): Boolean = source != null && source.startsWith(DEVICE_PREFIX)

    /** The content uri a stored value names, or null when it names one of the app's own. */
    fun deviceUri(source: String?): String? =
        if (isDevice(source)) source!!.substring(DEVICE_PREFIX.length).takeIf { it.isNotBlank() } else null

    /**
     * A stored value made safe to use: an id this build has, or a sound on the phone, and the
     * default for anything else — a value from a newer build, restored from a backup, or garbage.
     */
    fun sanitize(source: String?): String = when {
        source == null -> DEFAULT
        isDevice(source) -> if (deviceUri(source) != null) source else DEFAULT
        source in IDS || source in CUES -> source
        else -> DEFAULT
    }
}
