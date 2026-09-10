package com.newagedevs.gesturevolume.utils

/**
 * How the app's floating panels are dressed — the long-press menu, the Quick panel, the Deck.
 *
 * One setting for all three rather than one each. They are three faces of the same thing: surfaces
 * this app puts over somebody else's screen. A user who frosts the Deck and then finds the menu
 * still opaque has not configured anything, they have found an inconsistency.
 *
 * The identifiers are a persistence format, like [HandlerActions]: written verbatim into
 * preferences, never renamed.
 */
object PanelTheme {

    /** An almost-opaque dark card. What every panel looked like before this setting existed. */
    const val SOLID = "solid"

    /** Translucent, with the screen behind it blurred. Frosted glass. */
    const val FROSTED = "frosted"

    /** More translucent still, more blur, and a lit edge. */
    const val GLASS = "glass"

    val ALL = listOf(SOLID, FROSTED, GLASS)

    fun sanitize(value: String?): String = if (value in ALL) value!! else SOLID

    /**
     * How hard to blur what is behind the panel, in dp. Zero means no blur at all.
     *
     * Only reaches the screen on Android 12 and up, and only where the device has cross-window
     * blur switched on — it is expensive, and the system turns it off in battery saver and on
     * hardware that cannot afford it. See `OverlayController.applyPanelBlur`, which is also why
     * the alphas below are chosen to look deliberate *without* it: a translucent card over an
     * unblurred screen has to still read as a card, not as a bug.
     */
    fun blurRadiusDp(theme: String): Int = when (theme) {
        FROSTED -> 32
        // Heavier than frosted by enough to be a different material rather than more of the same.
        // Glass gives up most of its own colour, so the blur is what stops the panel dissolving
        // into whatever is behind it — it is doing the work the surface no longer does.
        GLASS -> 64
        else -> 0
    }

    /**
     * How much of the panel's own colour survives, 0..1.
     *
     * Frosted keeps enough to stay legible against a bright app; glass gives up more, which is
     * what makes it glass, and pays for it with the lit edge below.
     */
    fun surfaceAlpha(theme: String): Float = when (theme) {
        FROSTED -> 0.72f
        // Low enough that the surface is barely a surface. What holds the panel together at this
        // alpha is not the fill, it is the lit rim and the sheen — which is the whole idea the
        // material is built on, and why raising this "so you can see it" makes it look worse.
        GLASS -> 0.34f
        else -> 1f
    }

    /** Whether the panel draws the bright gradient edge that reads as a lit rim of glass. */
    fun hasLitEdge(theme: String): Boolean = theme == GLASS
}
