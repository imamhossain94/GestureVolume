package com.newagedevs.gesturevolume.data.local

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** One saved note. */
data class DeckNote(val id: Long, val text: String, val timeMillis: Long)

/** One checklist entry. */
data class DeckChecklistItem(val id: Long, val text: String, val done: Boolean)

/** One quick-dial button: a name to show and a number to call. */
data class QuickDialEntry(val id: Long, val name: String, val number: String)

/**
 * Everything the Deck remembers: how it looks, what it holds, and the notes, checklist and
 * quick-dial entries the user has put in it.
 *
 * Its own object rather than more methods on [SharedPref], which already carries the bar's
 * forty-odd settings, but the same `SharedPreferences` file underneath — so a backup, a restore
 * and the Reset row treat the Deck exactly as they treat everything else. Lists are stored as
 * JSON arrays: a few dozen short strings at most, read on each Deck open and written on each
 * edit, which is well inside what a preferences file is for.
 */
class DeckStore(private val prefs: SharedPreferences) {

    private companion object {
        const val WIDTH = "deckWidthDp"
        const val HEIGHT_FRACTION = "deckHeightFraction"
        const val CORNER = "deckCornerRadiusDp"
        const val BG_COLOR = "deckBackgroundColor"
        const val BG_ALPHA = "deckBackgroundAlpha"
        const val ACCENT = "deckAccentColor"
        const val AUTO_CLOSE = "deckAutoCloseSeconds"
        const val UTILITIES_FIRST = "deckUtilitiesFirst"
        const val TILE_ORDER = "deckTileOrder"
        const val TILES_ENABLED = "deckTilesEnabled"
        const val APP_SHORTCUTS = "deckAppShortcuts"
        const val QUICK_DIAL = "deckQuickDial"
        const val NOTES = "deckNotes"
        const val CHECKLIST = "deckChecklist"
        const val TIMER_LAST_SECONDS = "deckTimerLastSeconds"
        const val TIMER_END_AT = "deckTimerEndAt"

        const val DEFAULT_WIDTH = 64f
        const val DEFAULT_HEIGHT_FRACTION = 0.6f
        const val DEFAULT_CORNER = 30f
        /** Near-black with a touch of blue, matching the long-press menu's surface. */
        const val DEFAULT_BG_COLOR = 0xFF15161A.toInt()
        const val DEFAULT_BG_ALPHA = 238
        const val DEFAULT_ACCENT = 0xFFFFFFFF.toInt()
        const val DEFAULT_AUTO_CLOSE = 10
        const val DEFAULT_TIMER_SECONDS = 5 * 60
    }

    // ---- appearance -----------------------------------------------------------------------------

    fun getWidthDp(): Float = prefs.getFloat(WIDTH, DEFAULT_WIDTH).coerceIn(48f, 96f)
    fun setWidthDp(value: Float) = prefs.edit { putFloat(WIDTH, value.coerceIn(48f, 96f)) }

    /** The most of the usable height the strip may take before it scrolls. */
    fun getHeightFraction(): Float =
        prefs.getFloat(HEIGHT_FRACTION, DEFAULT_HEIGHT_FRACTION).coerceIn(0.3f, 0.95f)

    fun setHeightFraction(value: Float) =
        prefs.edit { putFloat(HEIGHT_FRACTION, value.coerceIn(0.3f, 0.95f)) }

    fun getCornerRadiusDp(): Float = prefs.getFloat(CORNER, DEFAULT_CORNER).coerceIn(0f, 48f)
    fun setCornerRadiusDp(value: Float) = prefs.edit { putFloat(CORNER, value.coerceIn(0f, 48f)) }

    fun getBackgroundColor(): Int = prefs.getInt(BG_COLOR, DEFAULT_BG_COLOR)
    fun setBackgroundColor(value: Int) = prefs.edit { putInt(BG_COLOR, value) }

    fun getBackgroundAlpha(): Int = prefs.getInt(BG_ALPHA, DEFAULT_BG_ALPHA).coerceIn(0, 255)
    fun setBackgroundAlpha(value: Int) = prefs.edit { putInt(BG_ALPHA, value.coerceIn(0, 255)) }

    fun getAccentColor(): Int = prefs.getInt(ACCENT, DEFAULT_ACCENT)
    fun setAccentColor(value: Int) = prefs.edit { putInt(ACCENT, value) }

    /** Seconds of inactivity before the Deck closes itself. 0 keeps it open until dismissed. */
    fun getAutoCloseSeconds(): Int = prefs.getInt(AUTO_CLOSE, DEFAULT_AUTO_CLOSE).coerceIn(0, 120)
    fun setAutoCloseSeconds(value: Int) = prefs.edit { putInt(AUTO_CLOSE, value.coerceIn(0, 120)) }

    /** Tiles above the app shortcuts, rather than below them. */
    fun getUtilitiesFirst(): Boolean = prefs.getBoolean(UTILITIES_FIRST, false)
    fun setUtilitiesFirst(value: Boolean) = prefs.edit { putBoolean(UTILITIES_FIRST, value) }

    // ---- tiles ----------------------------------------------------------------------------------

    /**
     * The tile ids in the order the strip shows them, or null when the user has never reordered.
     *
     * Ids this build does not know are dropped on read and ids it has added since are appended
     * by the caller, so an order saved by an older or newer build never loses a tile.
     */
    fun getTileOrder(): List<String>? = readStringList(TILE_ORDER)
    fun setTileOrder(ids: List<String>) = writeStringList(TILE_ORDER, ids)

    /** The tile ids switched on, or null when the user has never touched the switches. */
    fun getEnabledTiles(): Set<String>? = readStringList(TILES_ENABLED)?.toSet()
    fun setEnabledTiles(ids: Set<String>) = writeStringList(TILES_ENABLED, ids.toList())

    // ---- app shortcuts --------------------------------------------------------------------------

    /** Package names of the pinned apps, in the order they were pinned. */
    fun getAppShortcuts(): List<String> = readStringList(APP_SHORTCUTS) ?: emptyList()
    fun setAppShortcuts(packages: List<String>) = writeStringList(APP_SHORTCUTS, packages.distinct())

    // ---- quick dial -----------------------------------------------------------------------------

    fun getQuickDial(): List<QuickDialEntry> = readObjects(QUICK_DIAL) { o ->
        QuickDialEntry(o.getLong("id"), o.getString("name"), o.getString("number"))
    }

    fun setQuickDial(entries: List<QuickDialEntry>) = writeObjects(QUICK_DIAL, entries) { e ->
        JSONObject().put("id", e.id).put("name", e.name).put("number", e.number)
    }

    fun addQuickDial(name: String, number: String): QuickDialEntry {
        val entry = QuickDialEntry(System.currentTimeMillis(), name.trim(), number.trim())
        setQuickDial(getQuickDial() + entry)
        return entry
    }

    fun removeQuickDial(id: Long) = setQuickDial(getQuickDial().filterNot { it.id == id })

    // ---- notes ----------------------------------------------------------------------------------

    /** Newest first. */
    fun getNotes(): List<DeckNote> = readObjects(NOTES) { o ->
        DeckNote(o.getLong("id"), o.getString("text"), o.optLong("time"))
    }

    private fun setNotes(notes: List<DeckNote>) = writeObjects(NOTES, notes) { n ->
        JSONObject().put("id", n.id).put("text", n.text).put("time", n.timeMillis)
    }

    fun addNote(text: String): DeckNote? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val note = DeckNote(now, trimmed, now)
        setNotes(listOf(note) + getNotes())
        return note
    }

    fun updateNote(id: Long, text: String) {
        val trimmed = text.trim()
        setNotes(getNotes().map { if (it.id == id) it.copy(text = trimmed, timeMillis = System.currentTimeMillis()) else it })
    }

    fun removeNote(id: Long) = setNotes(getNotes().filterNot { it.id == id })

    // ---- checklist ------------------------------------------------------------------------------

    fun getChecklist(): List<DeckChecklistItem> = readObjects(CHECKLIST) { o ->
        DeckChecklistItem(o.getLong("id"), o.getString("text"), o.optBoolean("done"))
    }

    fun setChecklist(items: List<DeckChecklistItem>) = writeObjects(CHECKLIST, items) { i ->
        JSONObject().put("id", i.id).put("text", i.text).put("done", i.done)
    }

    fun addChecklistItem(text: String): DeckChecklistItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val item = DeckChecklistItem(System.currentTimeMillis(), trimmed, false)
        setChecklist(getChecklist() + item)
        return item
    }

    fun toggleChecklistItem(id: Long) =
        setChecklist(getChecklist().map { if (it.id == id) it.copy(done = !it.done) else it })

    fun removeChecklistItem(id: Long) = setChecklist(getChecklist().filterNot { it.id == id })

    fun clearDoneChecklistItems() = setChecklist(getChecklist().filterNot { it.done })

    // ---- timer ----------------------------------------------------------------------------------

    fun getTimerLastSeconds(): Int = prefs.getInt(TIMER_LAST_SECONDS, DEFAULT_TIMER_SECONDS).coerceIn(10, 24 * 3600)
    fun setTimerLastSeconds(value: Int) = prefs.edit { putInt(TIMER_LAST_SECONDS, value.coerceIn(10, 24 * 3600)) }

    /**
     * When the running timer ends, as elapsed-realtime millis, or 0 when none is running.
     *
     * Persisted so a countdown survives the host being rebuilt — a settings save, a handover
     * between the two services — rather than silently vanishing mid-count.
     */
    fun getTimerEndAt(): Long = prefs.getLong(TIMER_END_AT, 0L)
    fun setTimerEndAt(value: Long) = prefs.edit { putLong(TIMER_END_AT, value) }

    // ---- JSON helpers ---------------------------------------------------------------------------

    private fun readStringList(key: String): List<String>? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        }.getOrNull()
    }

    private fun writeStringList(key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        prefs.edit { putString(key, array.toString()) }
    }

    private fun <T> readObjects(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { parse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun <T> writeObjects(key: String, values: List<T>, encode: (T) -> JSONObject) {
        val array = JSONArray()
        values.forEach { array.put(encode(it)) }
        prefs.edit { putString(key, array.toString()) }
    }
}
