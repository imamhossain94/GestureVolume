package com.newagedevs.gesturevolume.utils

/**
 * How the app's floating panels are dressed — the long-press menu, the Quick panel, the Deck.
 *
 * One setting for all three rather than one each. They are three faces of the same thing: surfaces
 * this app puts over somebody else's screen. A user who frosts the Deck and then finds the menu
 * still opaque has not configured anything, they have found an inconsistency.
 *
 * The identifiers are a persistence format, like [HandlerActions]: written verbatim into
 * preferences, never renamed. The labels shown for them are in `strings.xml` and are free to
 * change.
 *
 * **What a material is.** Four things, and a panel that picks up only some of them does not look
 * like glass however transparent it is:
 *
 *  1. **Blur**, clipped to the panel's own outline. Not the whole screen — see [blurRadiusDp].
 *  2. **A fill** that is weak enough to let the blur through and strong enough to keep text legible.
 *  3. **A rim**, because the edge of a piece of glass catches light and that edge is what tells the
 *     eye where the material stops.
 *  4. **A sheen** on the upper half, because light falls from above.
 */
object PanelTheme {

    /** An almost-opaque dark card. What every panel looked like before this setting existed. */
    const val SOLID = "solid"

    /** Translucent dark, with the screen behind it blurred. Frosted glass. */
    const val FROSTED = "frosted"

    /** More translucent still, more blur, and a lit edge. */
    const val GLASS = "glass"

    /**
     * Light, glossy, faintly blue: the Aero look.
     *
     * The one material here that is *lit* rather than tinted. Its fill is brighter than most of
     * what it will ever sit on, so it reads as a pane held in front of the screen rather than as a
     * shadow cast on it, and the sheen down its upper half is doing as much of that work as the
     * blur is.
     */
    const val AERO = "aero"

    /**
     * Light, neutral and heavily blurred: the look a phone's own share sheet wears.
     *
     * The most transparent material of the five. What holds it together is the amount of blur —
     * enough that whatever is behind becomes colour rather than content — plus dark text and
     * hairline dividers, which is the whole recipe.
     */
    const val VIBRANT = "vibrant"

    val ALL = listOf(SOLID, FROSTED, GLASS, AERO, VIBRANT)

    fun sanitize(value: String?): String = if (value in ALL) value!! else SOLID

    /**
     * How hard to blur what is behind the panel, in dp. Zero means no blur at all.
     *
     * Only reaches the screen on Android 12 and up, and only where the device has cross-window
     * blur switched on — it is expensive, and the system turns it off in battery saver and on
     * hardware that cannot afford it. See `OverlayController.panelBackdropFor`, which is also why
     * the alphas below are chosen to look deliberate *without* it: a translucent card over an
     * unblurred screen has to still read as a card, not as a bug.
     *
     * Kept in the tens rather than the hundreds. The blur is applied at device pixels, so 40dp is
     * already north of a hundred pixels on a modern phone; past that the picture behind stops
     * being recognisable as anything and the panel may as well have been drawn on grey.
     */
    fun blurRadiusDp(theme: String): Int = when (theme) {
        FROSTED -> 28
        GLASS -> 40
        AERO -> 36
        // The heaviest, because this material gives up more of its own fill than any other and
        // the blur is the only thing left holding it together.
        VIBRANT -> 44
        else -> 0
    }

    /**
     * How much of a panel's own colour survives, 0..1.
     *
     * This one is a *multiplier*, because the Deck and the Quick panel carry a colour the user
     * picked and it is not this object's business to overrule it. The long-press menu has no such
     * colour of its own and takes the full palette below instead.
     */
    fun surfaceAlpha(theme: String): Float = when (theme) {
        FROSTED -> 0.66f
        // Low enough that the surface is barely a surface. What holds the panel together at this
        // alpha is not the fill, it is the lit rim and the sheen — which is the whole idea the
        // material is built on, and why raising this "so you can see it" makes it look worse.
        GLASS -> 0.34f
        AERO -> 0.42f
        VIBRANT -> 0.30f
        else -> 1f
    }

    /** Whether the panel draws the bright gradient edge that reads as a lit rim of glass. */
    fun hasLitEdge(theme: String): Boolean = theme == GLASS || theme == AERO

    /**
     * Whether this material is a pale one, and so wants dark content on it.
     *
     * The overlays otherwise force a dark palette on purpose — see `OverlayTheme` — because a
     * light card over a dark app is a glare. These two materials are the deliberate exception:
     * they are pale *because* the reference look is, and they carry their own dark text with them
     * rather than inheriting white and disappearing.
     */
    fun isLight(theme: String): Boolean = theme == AERO || theme == VIBRANT

    /**
     * The surface a pale material insists on, or null where the panel keeps the user's own colour.
     *
     * [surfaceAlpha] is a multiplier because the Deck and the Quick panel carry a colour somebody
     * chose, and thinning it is a change this object is entitled to make. Replacing it is not — so
     * only the two materials that are *defined* by being pale return anything here. Fading a dark
     * bar toward transparent gives grey; it never gives a pane of glass, and a Deck that stayed
     * charcoal while the menu beside it turned to frost was the whole complaint.
     *
     * Where this is non-null the alpha is already in it, and [surfaceAlpha] must not be applied on
     * top or the surface is thinned twice.
     */
    fun panelSurface(theme: String): Long? = when (theme) {
        AERO -> 0x8CD6E6F4
        VIBRANT -> 0xA6F7F7FA
        else -> null
    }

    /**
     * The long-press menu's full palette, as `0xAARRGGBB` literals.
     *
     * Longs rather than `androidx.compose.ui.graphics.Color` so that this file stays where it is —
     * in `utils`, readable by the classic-View panels as much as by the Compose ones. Compose takes
     * a `Long` straight; a View calls `.toInt()`.
     */
    data class MenuPalette(
        /** The card's fill, over the blur. */
        val surface: Long,
        /** Labels and glyphs. */
        val onSurface: Long,
        /** The word under a grid tile: present, but not competing with its icon. */
        val onSurfaceDim: Long,
        /** The filled shape behind each icon. */
        val chip: Long,
        /** The hairline between rows in the list layout. */
        val divider: Long,
        /** The plain 1dp border, for the materials that do not draw a lit rim instead. */
        val border: Long,
    )

    /**
     * The menu's palette with a colour of the user's own in place of the material's.
     *
     * The menu is the one panel that never had a colour setting — it takes a full palette from the
     * material because it has no surface of its own to tint. Given one, everything else follows
     * from its brightness: ink, the chip behind each icon, the hairline between rows and the rim
     * all flip together, because a palette where half the entries assumed a dark pane is how you
     * get white text on white glass.
     *
     * @param surface `0xAARRGGBB`, or null to use the material's own.
     */
    fun menuPalette(theme: String, surface: Long?): MenuPalette {
        val base = menuPalette(theme)
        if (surface == null) return base
        val light = luminanceOf(surface) > 0.5
        return MenuPalette(
            surface = surface,
            onSurface = if (light) 0xF0101014 else 0xF5FFFFFF,
            onSurfaceDim = if (light) 0x9E101014 else 0xC2FFFFFF,
            chip = if (light) 0x14000000 else 0x24FFFFFF,
            divider = if (light) 0x1A000000 else 0x1AFFFFFF,
            border = if (light) 0x33000000 else 0x2EFFFFFF,
        )
    }

    /**
     * Perceived brightness of an `0xAARRGGBB` colour, 0..1, ignoring its alpha.
     *
     * Alpha is left out deliberately: a pale pane at a third opacity is still a pale pane, because
     * what shows through it has been blurred to a wash of roughly its own brightness. The weights
     * are the usual ones — the eye is far more sensitive to green than to blue, and a palette
     * chosen on unweighted averages puts dark text on saturated blue.
     */
    fun luminanceOf(colour: Long): Double {
        val r = ((colour shr 16) and 0xFF) / 255.0
        val g = ((colour shr 8) and 0xFF) / 255.0
        val b = (colour and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun menuPalette(theme: String): MenuPalette = when (theme) {
        FROSTED -> MenuPalette(
            surface = 0xA815161B,
            onSurface = 0xF5FFFFFF,
            onSurfaceDim = 0xC2FFFFFF,
            chip = 0x24FFFFFF,
            divider = 0x1AFFFFFF,
            border = 0x2EFFFFFF,
        )
        GLASS -> MenuPalette(
            surface = 0x5E14141A,
            onSurface = 0xFFFFFFFF,
            onSurfaceDim = 0xCCFFFFFF,
            chip = 0x2EFFFFFF,
            divider = 0x24FFFFFF,
            border = 0x33FFFFFF,
        )
        AERO -> MenuPalette(
            // A cool white rather than a neutral one. Aero's panes were tinted with the desktop
            // colour and the blue is the half of that anybody remembers.
            surface = 0x8CD6E6F4,
            onSurface = 0xF2101820,
            onSurfaceDim = 0xA6101820,
            // White, not black: on a pale glossy pane a dark chip reads as a hole punched in it,
            // where a brighter one reads as a raised key.
            chip = 0x59FFFFFF,
            divider = 0x1F000000,
            border = 0x8CFFFFFF,
        )
        VIBRANT -> MenuPalette(
            surface = 0xA6F7F7FA,
            onSurface = 0xF01B1B1F,
            onSurfaceDim = 0x9E1B1B1F,
            chip = 0x14000000,
            divider = 0x1A000000,
            border = 0x66FFFFFF,
        )
        else -> MenuPalette(
            surface = 0xF41C1C20,
            onSurface = 0xF2FFFFFF,
            onSurfaceDim = 0xB8FFFFFF,
            chip = 0x1AFFFFFF,
            divider = 0x1FFFFFFF,
            border = 0x1FFFFFFF,
        )
    }
}
