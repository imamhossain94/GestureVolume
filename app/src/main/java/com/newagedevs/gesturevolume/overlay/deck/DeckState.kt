package com.newagedevs.gesturevolume.overlay.deck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the Deck remembers between one opening and the next, for as long as the host lives.
 *
 * The composition is thrown away every time the Deck closes — its window is removed — so anything
 * that has to outlive a close lives here instead: the countdown, the last coin and dice results,
 * the calculator's half-typed sum. Compose state objects, so the cards observe them directly.
 */
class DeckState {

    /** The tile whose card is open beside the strip, or null for the bare strip. */
    var expandedTile by mutableStateOf<String?>(null)

    // ---- timer ----------------------------------------------------------------------------------

    /** When the running countdown ends, in elapsed-realtime millis; 0 while idle. */
    var timerEndAt by mutableLongStateOf(0L)

    /** The duration the user has dialled in, in seconds. */
    var timerSeconds by mutableIntStateOf(5 * 60)

    /** When the last countdown finished, so the card can say so briefly; 0 when it has not. */
    var timerFinishedAt by mutableLongStateOf(0L)

    val timerRunning: Boolean get() = timerEndAt > 0L

    // ---- coin and dice --------------------------------------------------------------------------

    var coinHeads by mutableStateOf<Boolean?>(null)
    var coinTosses by mutableIntStateOf(0)
    var dice by mutableStateOf<Pair<Int, Int>?>(null)
    var diceRolls by mutableIntStateOf(0)

    // ---- calculator -----------------------------------------------------------------------------

    var calculatorInput by mutableStateOf("")

    /**
     * Bumped after every device toggle so the strip re-reads the torch, Do Not Disturb and
     * rotation states. Cheaper and more honest than mirroring three booleans that other apps
     * can change behind the Deck's back.
     */
    var toggleVersion by mutableIntStateOf(0)
}
