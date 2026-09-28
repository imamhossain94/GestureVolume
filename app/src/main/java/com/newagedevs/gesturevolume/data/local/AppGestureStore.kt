package com.newagedevs.gesturevolume.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import com.newagedevs.gesturevolume.utils.HandlerActions
import org.json.JSONObject

/**
 * Gestures that do something else in one app: in YouTube, say, swiping the bar up sets the
 * brightness instead of opening the Quick panel, while every other app keeps the usual gestures.
 *
 * One profile per app, and a profile holds only the gestures the user changed for that app. A
 * gesture left out follows the setting everywhere else, so changing a gesture on the Actions screen
 * still changes it in every app that has not been told otherwise.
 *
 * Knowing which app is in front takes the accessibility service, the same way hiding the bar in
 * chosen apps does. See `GestureAccessibilityService`.
 *
 * Stored as one JSON object in the shared preferences file — a handful of apps with a few strings
 * each — so a backup, a restore and the Reset row treat profiles exactly as they treat everything
 * else.
 */
class AppGestureStore(private val prefs: SharedPreferences) {

    /**
     * The eight gesture slots, by the names a profile stores them under. A persistence format:
     * never renamed.
     */
    enum class Slot(val key: String) {
        SINGLE_TAP("singleTap"),
        DOUBLE_TAP("doubleTap"),
        TRIPLE_TAP("tripleTap"),
        LONG_PRESS("longPress"),
        SWIPE_UP("swipeUp"),
        SWIPE_DOWN("swipeDown"),
        SWIPE_IN("swipeIn"),
        SWIPE_OUT("swipeOut");

        companion object {
            fun fromKey(key: String): Slot? = entries.firstOrNull { it.key == key }
        }
    }

    private companion object {
        const val PROFILES = "appGestureProfiles"
    }

    /**
     * The last string parsed and what it parsed to. The overlay asks on every tap and at the start
     * of every swipe, and the answer only changes when a profile is edited.
     */
    private var cache: Pair<String?, Map<String, Map<Slot, String>>>? = null

    /** Every app with a profile, and what each changed gesture is set to there. */
    fun getProfiles(): Map<String, Map<Slot, String>> {
        val raw = prefs.getString(PROFILES, null)
        cache?.let { (cachedRaw, parsed) -> if (cachedRaw == raw) return parsed }
        val parsed = parse(raw)
        cache = raw to parsed
        return parsed
    }

    /**
     * Whether any app has a gesture that differs from everywhere else: the only time the app in
     * front is worth knowing. An app added and not yet changed does not count.
     */
    fun hasOverrides(): Boolean = getProfiles().values.any { it.isNotEmpty() }

    fun getProfile(packageName: String): Map<Slot, String> = getProfiles()[packageName].orEmpty()

    /** What [slot] does in [packageName], or null when it does what it does everywhere. */
    fun actionFor(packageName: String, slot: Slot): String? = getProfiles()[packageName]?.get(slot)

    /**
     * Sets what [slot] does in [packageName], or with null puts it back to what it does everywhere.
     * The app stays listed either way, until it is removed: a user putting one gesture back is
     * usually about to change another.
     */
    fun setAction(packageName: String, slot: Slot, action: String?) {
        val profiles = getProfiles().mapValues { it.value.toMutableMap() }.toMutableMap()
        val profile = profiles.getOrPut(packageName) { mutableMapOf() }
        if (action == null) profile.remove(slot) else profile[slot] = action
        write(profiles)
    }

    /** Lists [packageName] with nothing changed yet, so it can be given its gestures. */
    fun addApp(packageName: String) {
        if (packageName in getProfiles()) return
        write(getProfiles() + (packageName to emptyMap()))
    }

    fun removeApp(packageName: String) = write(getProfiles() - packageName)

    private fun write(profiles: Map<String, Map<Slot, String>>) {
        val json = JSONObject()
        profiles.forEach { (pkg, slots) ->
            val profile = JSONObject()
            slots.forEach { (slot, action) -> profile.put(slot.key, action) }
            json.put(pkg, profile)
        }
        prefs.edit { if (json.length() == 0) remove(PROFILES) else putString(PROFILES, json.toString()) }
    }

    /**
     * Read defensively, like every stored action: a slot this build does not know, or an action it
     * does not understand — a restore from a newer version — is dropped rather than trusted.
     */
    private fun parse(raw: String?): Map<String, Map<Slot, String>> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { pkg ->
                    val profile = json.optJSONObject(pkg) ?: return@forEach
                    val slots = buildMap {
                        profile.keys().forEach { key ->
                            val slot = Slot.fromKey(key) ?: return@forEach
                            val action = profile.optString(key)
                            if (HandlerActions.isKnown(action)) put(slot, action)
                        }
                    }
                    put(pkg, slots)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
