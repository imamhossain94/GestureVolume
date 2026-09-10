package com.newagedevs.gesturevolume.overlay.deck

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.HandlerActions

/** What a tile does when tapped. */
enum class DeckTileKind {
    /** Flips a device setting and stays in the strip, lit while the setting is on. */
    TOGGLE,

    /** Runs an action — usually one that leaves the Deck — and closes. */
    LAUNCH,

    /** Opens a card beside the strip. */
    PANEL
}

/**
 * One utility tile.
 *
 * @param id the persistence key, never renamed. [HandlerActions.deckTileFor] maps gesture actions
 *   onto these, so the two lists agree by construction.
 * @param action for [DeckTileKind.TOGGLE] and [DeckTileKind.LAUNCH], the [HandlerActions]
 *   identifier the tile runs; the overlay's action runner is the one place that knows how.
 */
data class DeckTile(
    val id: String,
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val kind: DeckTileKind,
    val action: String? = null,
    val defaultOn: Boolean = false,
    /** Only the accessibility service can perform it; shown with a badge in settings. */
    val needsAccessibility: Boolean = false
)

/**
 * Every tile the Deck can hold, in canonical order.
 *
 * The order here is the order a fresh install shows and the order new tiles are appended in when
 * an upgrading install already has a saved order. Defaults are deliberately a short strip: seven
 * tiles a thumb can reach without scrolling, and the rest one switch away in settings.
 */
object DeckTiles {

    const val SEARCH = "search"
    const val FLASHLIGHT = "flashlight"
    const val DND = "dnd"
    const val ROTATION = "rotation"
    const val WIFI = "wifi"
    const val BLUETOOTH = "bluetooth"
    const val VOLUME = "volume"
    const val BRIGHTNESS = "brightness"
    const val MEDIA = "media"
    const val TIMER = "timer"
    const val CALCULATOR = "calculator"
    const val NOTES = "notes"
    const val CLIPBOARD = "clipboard"
    const val CHECKLIST = "checklist"
    const val COIN = "coin"
    const val DICE = "dice"
    const val SCREENSHOT = "screenshot"
    const val LOCK = "lock"
    const val QR = "qr"
    const val SONG = "song"
    const val WEATHER = "weather"

    val ALL: List<DeckTile> = listOf(
        DeckTile(SEARCH, R.string.tile_search, R.string.tile_search_desc, Icons.Filled.Search, DeckTileKind.PANEL, defaultOn = true),
        DeckTile(FLASHLIGHT, R.string.tile_flashlight, R.string.tile_flashlight_desc, Icons.Filled.FlashlightOn, DeckTileKind.TOGGLE, HandlerActions.TOGGLE_FLASHLIGHT, defaultOn = true),
        DeckTile(DND, R.string.tile_dnd, R.string.tile_dnd_desc, Icons.Filled.DoNotDisturbOn, DeckTileKind.TOGGLE, HandlerActions.TOGGLE_DND, defaultOn = true),
        DeckTile(ROTATION, R.string.tile_rotation, R.string.tile_rotation_desc, Icons.Filled.ScreenRotation, DeckTileKind.TOGGLE, HandlerActions.TOGGLE_AUTO_ROTATE),
        DeckTile(WIFI, R.string.tile_wifi, R.string.tile_wifi_desc, Icons.Filled.Wifi, DeckTileKind.LAUNCH),
        DeckTile(BLUETOOTH, R.string.tile_bluetooth, R.string.tile_bluetooth_desc, Icons.Filled.Bluetooth, DeckTileKind.LAUNCH),
        DeckTile(VOLUME, R.string.tile_volume, R.string.tile_volume_desc, Icons.Filled.VolumeUp, DeckTileKind.PANEL, defaultOn = true),
        DeckTile(BRIGHTNESS, R.string.tile_brightness, R.string.tile_brightness_desc, Icons.Filled.Brightness6, DeckTileKind.PANEL),
        DeckTile(MEDIA, R.string.tile_media, R.string.tile_media_desc, Icons.Filled.MusicNote, DeckTileKind.PANEL, defaultOn = true),
        DeckTile(TIMER, R.string.tile_timer, R.string.tile_timer_desc, Icons.Filled.Timer, DeckTileKind.PANEL, defaultOn = true),
        DeckTile(CALCULATOR, R.string.tile_calculator, R.string.tile_calculator_desc, Icons.Filled.Calculate, DeckTileKind.PANEL),
        DeckTile(NOTES, R.string.tile_notes, R.string.tile_notes_desc, Icons.Filled.EditNote, DeckTileKind.PANEL, defaultOn = true),
        DeckTile(CLIPBOARD, R.string.tile_clipboard, R.string.tile_clipboard_desc, Icons.Filled.ContentPaste, DeckTileKind.PANEL, defaultOn = true),
        DeckTile(CHECKLIST, R.string.tile_checklist, R.string.tile_checklist_desc, Icons.Filled.Checklist, DeckTileKind.PANEL),
        DeckTile(COIN, R.string.tile_coin, R.string.tile_coin_desc, Icons.Filled.Paid, DeckTileKind.PANEL),
        DeckTile(DICE, R.string.tile_dice, R.string.tile_dice_desc, Icons.Filled.Casino, DeckTileKind.PANEL),
        DeckTile(SCREENSHOT, R.string.tile_screenshot, R.string.tile_screenshot_desc, Icons.Filled.Screenshot, DeckTileKind.LAUNCH, HandlerActions.SCREENSHOT, needsAccessibility = true),
        DeckTile(LOCK, R.string.tile_lock, R.string.tile_lock_desc, Icons.Filled.Lock, DeckTileKind.LAUNCH, HandlerActions.LOCK, needsAccessibility = true),
        DeckTile(QR, R.string.tile_qr, R.string.tile_qr_desc, Icons.Filled.QrCodeScanner, DeckTileKind.LAUNCH, HandlerActions.SCAN_QR),
        DeckTile(SONG, R.string.tile_song, R.string.tile_song_desc, Icons.Filled.Audiotrack, DeckTileKind.LAUNCH, HandlerActions.SONG_SEARCH),
        DeckTile(WEATHER, R.string.tile_weather, R.string.tile_weather_desc, Icons.Filled.WbSunny, DeckTileKind.PANEL)
    )

    private val byId: Map<String, DeckTile> = ALL.associateBy { it.id }

    fun byId(id: String): DeckTile? = byId[id]

    /**
     * The tiles the strip shows, in the user's order, with the user's switches applied.
     *
     * A saved order that predates a tile gets it appended; a saved order naming a tile this build
     * no longer has drops it. Both are what "the user's order" means across an update.
     */
    fun visible(order: List<String>?, enabled: Set<String>?): List<DeckTile> {
        val ordered = ordered(order)
        val on = enabled ?: ALL.filter { it.defaultOn }.map { it.id }.toSet()
        return ordered.filter { it.id in on }
    }

    /** Every tile in the user's order, switched on or not — for the settings list. */
    fun ordered(order: List<String>?): List<DeckTile> {
        if (order == null) return ALL
        val known = order.mapNotNull { byId[it] }
        val missing = ALL.filterNot { it.id in order }
        return known + missing
    }

    fun defaultEnabled(): Set<String> = ALL.filter { it.defaultOn }.map { it.id }.toSet()
}
