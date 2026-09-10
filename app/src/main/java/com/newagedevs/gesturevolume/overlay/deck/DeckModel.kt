package com.newagedevs.gesturevolume.overlay.deck

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.newagedevs.gesturevolume.data.local.QuickDialEntry
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.utils.BrightnessController
import com.newagedevs.gesturevolume.utils.DeviceToggles
import com.newagedevs.gesturevolume.utils.VolumeController

/** How the Deck looks, read from [com.newagedevs.gesturevolume.data.local.DeckStore] at open. */
data class DeckConfig(
    val widthDp: Float,
    val heightFraction: Float,
    val cornerDp: Float,
    val background: Color,
    val accent: Color,
    val autoCloseSeconds: Int,
    val utilitiesFirst: Boolean
)

/** A pinned app, resolved to what the strip draws. */
data class AppShortcut(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Everything the Deck composable needs, captured once per opening.
 *
 * A snapshot rather than live preference reads: the strip must not reshuffle under a thumb
 * because a setting changed in the app behind it, and reading forty preferences per frame is
 * not a thing a composable should do anyway.
 *
 * @param anchor the bar's window rectangle, in pixels relative to the usable frame.
 * @param frame the usable frame, in pixels.
 * @param isLeft whether the bar — and therefore the strip — sits on the left edge.
 * @param initialTile a card to open straight away, from a gesture bound to that tile.
 */
data class DeckModel(
    val config: DeckConfig,
    val tiles: List<DeckTile>,
    val apps: List<AppShortcut>,
    val quickDial: List<QuickDialEntry>,
    val anchor: IntRect,
    val frame: IntSize,
    val isLeft: Boolean,
    val initialTile: String?,
    /** How the panel is dressed. See [com.newagedevs.gesturevolume.utils.PanelTheme]. */
    val panelTheme: String = com.newagedevs.gesturevolume.utils.PanelTheme.SOLID,
)

/**
 * The live objects the cards act through. Owned by the overlay controller and shared with the
 * bar, so the Deck's volume slider and the bar's volume swipe cannot disagree.
 */
class DeckEnvironment(
    val context: Context,
    val preference: SharedPref,
    val toggles: DeviceToggles,
    val volume: VolumeController,
    val brightness: BrightnessController,
    val state: DeckState
)

/** What the Deck asks its host to do. Every method is safe to call from a click handler. */
interface DeckActions {
    val env: DeckEnvironment

    /** Runs a [com.newagedevs.gesturevolume.utils.HandlerActions] identifier, as the bar would. */
    fun runAction(action: String)

    fun launchApp(packageName: String)
    fun dial(entry: QuickDialEntry)

    /** Starts an Activity from the overlay. Returns false when nothing resolves. */
    fun launch(intent: Intent): Boolean

    /** Opens one of the app's own screens, by navigation route. */
    fun openAppScreen(route: String)

    /** Copies [text]; with [paste] also pastes it into the field under the Deck, when possible. */
    fun copy(text: String, paste: Boolean)

    /**
     * Reads whatever is on the clipboard into the history.
     *
     * Called only when the clipboard card is opened, never on every Deck opening: Android 12+
     * shows the system's own "pasted from your clipboard" notice each time an app reads the
     * clipboard, and a notice on every flick of the bar would be intolerable. Opening the
     * clipboard card is the one moment the user has actually asked for the clipboard.
     */
    fun captureClipboard()

    fun message(text: String)

    /** Starts, or restarts, the countdown for [seconds]. */
    fun startTimer(seconds: Int)
    fun stopTimer()

    /** Any touch or keystroke: pushes the auto-close back. */
    fun onInteraction()

    fun close()
}
