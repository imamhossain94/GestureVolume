package com.newagedevs.gesturevolume.utils

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore

/**
 * The handler presets, defined once: Dock (the default), Edge and Bold.
 *
 * A preset is a look for the bar plus a [Behaviour] — its gestures, the Quick panel, the menu and
 * the panel animation — and every one of them puts the bar 21% of the way down the screen.
 *
 * These used to live as a `when (presetId)` block inside the appearance screen's LaunchedEffect.
 * Moving them here gives the screen one place to read from, and keeps the identifiers that select
 * a preset separate from the names shown for it.
 *
 * The bar colours are taken from what the preset cards on the main screen preview, so choosing a
 * preset produces the handler the card showed. Only the fill colour and its alpha are shared;
 * width, corner radius, stroke and icon stay tuned for the handler itself, since the card's
 * swatch is a small decorative strip rather than a scale drawing of the bar.
 */
object HandlerPresets {

    /**
     * The two colours the preset cards preview with, as literals.
     *
     * These are `MaterialTheme.colorScheme.primary` and `onSurface` from the app's light palette
     * (see ui/theme/Color.kt). Copied rather than read from the theme because the handler is not
     * inside the app: it is drawn over whatever is on screen, by a Service with no Compose theme
     * to resolve against, and it must keep the same appearance whether or not the app is running
     * or which theme the app is set to.
     *
     * Alphas below are the cards' 0..1 preview values scaled to the 0..255 the handler stores:
     * 0.5 -> 128, 0.4 -> 102, 0.85 -> 217, 0.7 -> 179, 0.1 -> 26.
     */
    private val PREVIEW_PRIMARY = Color(0xFF4F46E5)
    private val PREVIEW_ON_SURFACE = Color(0xFF1F2937)

    /**
     * Which side a preset puts the bar against, for the ones that have to touch an edge.
     *
     * Every preset sets the bar's height on screen — see [Preset.positionFraction] — but only a
     * shape that runs into the edge also needs a side and a snap: a tab parked mid-screen curves
     * away into nothing. The bubble carries none, so applying it keeps whichever side the bar is on.
     */
    data class Placement(
        val gravity: Int,
        val posXFraction: Float,
        val snapToEdge: Boolean
    )

    /**
     * What the open Quick panel is, as a preset sets it.
     *
     * Plain values with [com.newagedevs.gesturevolume.data.local.QuickSliderStore]'s identifiers,
     * so a preset can be compared and tested without preferences to write it to.
     */
    data class SliderBehaviour(
        /** One of `QuickSliderStore.ALL_TARGETS`. */
        val target: String,
        val showValue: Boolean,
        val showIcon: Boolean,
        val lengthDp: Float,
        val thicknessDp: Float,
        val edgeOffsetDp: Float,
        /** How far the number sits from the panel's top. See `QuickSliderStore.getValueMarginDp`. */
        val valueMarginDp: Float,
        /** How far the icon sits from the panel's bottom. */
        val iconMarginDp: Float,
        /** Whether the panel is cut to the bar's own outline. See `QuickSliderStore.getFollowHandlerShape`. */
        val followHandlerShape: Boolean,
        /** One of `QuickSliderStore.ALL_VOLUME_KEY_MODES`. */
        val volumeKeys: String,
    )

    /**
     * Everything a preset sets beyond the look of the bar: what its gestures do, the Quick panel,
     * the long-press menu, how panels arrive, and whether the bar follows the phone round its edges.
     *
     * Data only. It is written to preferences by the Appearance screen when a preset applied there
     * is saved, and never merely because the screen was saved — see `HandlerAppearanceScreen`.
     * The Default preset's behaviour is also what a fresh install reads for each of these settings.
     */
    data class Behaviour(
        /** See `SharedPref.getHandlerDynamicPosition`. */
        val dynamicPosition: Boolean,
        /** [HandlerActions] identifiers, one per gesture slot. */
        val singleTap: String,
        val doubleTap: String,
        val tripleTap: String,
        val longPress: String,
        val swipeUp: String,
        val swipeDown: String,
        val swipeIn: String,
        val swipeOut: String,
        val slider: SliderBehaviour,
        /** One of [PanelAnimation.ALL]; shared by the menu, the Quick panel and the Deck. */
        val panelAnimation: String,
        /** [ContextMenuLayout.GRID] or [ContextMenuLayout.LIST]. */
        val menuLayout: String,
        /**
         * Entries on one page of the grid menu, one of [ContextMenuLayout.PER_PAGE_CHOICES].
         * Nine at the default width's three columns is a 3×3 page.
         */
        val menuPerPage: Int,
    )

    /**
     * Where every preset puts the bar's centre, the Classic included: 21% of the way down the
     * usable height. Also where a fresh install starts it, since the default is a preset.
     */
    const val POSITION_FRACTION = 0.21f

    /** The behaviour the edge presets share. Bold differs only where it says so. */
    private val EDGE_BEHAVIOUR = Behaviour(
        dynamicPosition = true,
        singleTap = HandlerActions.OPEN_VOLUME_UI,
        doubleTap = HandlerActions.NONE,
        tripleTap = HandlerActions.NONE,
        longPress = HandlerActions.REPOSITION,
        swipeUp = HandlerActions.OPEN_QUICK_SLIDER,
        swipeDown = HandlerActions.OPEN_QUICK_SLIDER,
        swipeIn = HandlerActions.OPEN_DECK,
        swipeOut = HandlerActions.NONE,
        slider = SliderBehaviour(
            target = QuickSliderStore.TARGET_ADAPTIVE,
            showValue = true,
            showIcon = true,
            lengthDp = 220f,
            thicknessDp = PANEL_THICKNESS,
            edgeOffsetDp = 0f,
            valueMarginDp = 26f,
            iconMarginDp = 26f,
            followHandlerShape = true,
            volumeKeys = QuickSliderStore.VOLUME_KEYS_INSTANT,
        ),
        panelAnimation = PanelAnimation.SLIDE,
        menuLayout = ContextMenuLayout.GRID,
        menuPerPage = 9,
    )

    /**
     * How wide the Quick panel opens, in dp, for the presets whose panel is not their bar's own
     * width — the Dock and the Edge — and so for a fresh install. The Classic's and the Bold's are
     * their bars' widths.
     */
    const val PANEL_THICKNESS = 32f

    /**
     * The Dock's: the edge behaviour with the number and the icon further in from the panel's ends.
     * The tab's sweeps curve the panel's ends away, and at the Edge's 26dp the number and icon sat
     * on the curve rather than on the flat.
     */
    private val DOCK_BEHAVIOUR = EDGE_BEHAVIOUR.copy(
        slider = EDGE_BEHAVIOUR.slider.copy(valueMarginDp = 35f, iconMarginDp = 35f),
    )

    /**
     * The Classic's: the app as it was before the Quick panel and the Deck. Swipe up and down change
     * the volume and show it, a tap opens the volume panel, a hold moves the bar — and nothing opens
     * a panel, the volume keys included. What the users who asked to turn the Quick slider off
     * wanted back, and what a regular user starts with. See [UserMode].
     */
    private val CLASSIC_BEHAVIOUR = EDGE_BEHAVIOUR.copy(
        dynamicPosition = false,
        singleTap = HandlerActions.OPEN_VOLUME_UI,
        doubleTap = HandlerActions.NONE,
        tripleTap = HandlerActions.NONE,
        longPress = HandlerActions.REPOSITION,
        swipeUp = HandlerActions.INCREASE_VOLUME_UI,
        swipeDown = HandlerActions.DECREASE_VOLUME_UI,
        swipeIn = HandlerActions.NONE,
        swipeOut = HandlerActions.NONE,
        // A few dp off the edge: the Classic's panel is a rounded pill, and flush against the edge
        // a pill reads as cut off by it. The tab presets' panels stay flush, where a tab belongs.
        // As wide as the Classic's bar, so the panel opens to the width of the pill it grows out of.
        slider = EDGE_BEHAVIOUR.slider.copy(
            thicknessDp = CLASSIC_WIDTH,
            edgeOffsetDp = ROUNDED_PANEL_EDGE_OFFSET,
            volumeKeys = QuickSliderStore.VOLUME_KEYS_OFF,
        ),
        panelAnimation = PanelAnimation.POP,
    )

    /**
     * How far the Classic's and the Bold's rounded panels stand off the edge, in dp: enough to read
     * as a pill on the screen rather than one cut off by its edge, and little enough to still open
     * out of the bar beside it.
     */
    const val ROUNDED_PANEL_EDGE_OFFSET = 4f

    /** The Classic's bar's width, and its Quick panel's. */
    private const val CLASSIC_WIDTH = 30f

    /** The Bold's bubble's width, and its Quick panel's. */
    private const val BOLD_WIDTH = 46f

    data class Preset(
        val id: String,
        val nameRes: Int,
        val subtitleRes: Int,
        val gravity: Int,
        val width: Float,
        val height: Float,
        val bgColor: Color,
        val bgAlpha: Int,
        val strokeColor: Color,
        val strokeWidth: Float,
        val strokeAlpha: Int,
        /**
         * The radius every corner takes unless one of the four below overrides it.
         *
         * Kept as the single source for the symmetric presets, which is most of them, so that
         * "this shape is a pill" stays one number rather than four that have to agree.
         */
        val cornerRadius: Float,
        @param:DrawableRes val iconRes: Int,
        val iconSize: Float,
        val iconColor: Color,
        val showIcon: Boolean,
        val vibrate: Boolean,
        val edgeMargin: Float,
        /** Vertical position of the bar's centre, 0..1 of the usable height, in both orientations. */
        val positionFraction: Float,
        /** What the preset sets beyond the bar's look. See [Behaviour]. */
        val behaviour: Behaviour,
        /** Non-null only for a preset that has to sit against an edge. See [Placement]. */
        val placement: Placement? = null,
        /**
         * Per-corner overrides, for the shapes that are not symmetric.
         *
         * A bar flush against a screen edge wants its outer corners square and its inner ones
         * rounded — that is what makes it read as something attached to the edge rather than
         * floating near it. Expressed as four nullable overrides rather than four required values
         * so that the symmetric presets stay one number.
         */
        val cornerTopLeft: Float? = null,
        val cornerTopRight: Float? = null,
        val cornerBottomLeft: Float? = null,
        val cornerBottomRight: Float? = null,
        /**
         * The outline the bar is cut to. See [HandlerShape].
         *
         * Defaulted rather than required because every preset that predates shapes is a rounded
         * rectangle, and saying so five times would be five chances to say it differently.
         */
        val shape: String = HandlerShape.ROUNDED,
        /** How far a [HandlerShape.TAB]'s ends sweep, as a fraction of the bar's height. */
        val flare: Float = HandlerShape.DEFAULT_FLARE,
    ) {
        val topLeft: Float get() = cornerTopLeft ?: cornerRadius
        val topRight: Float get() = cornerTopRight ?: cornerRadius
        val bottomLeft: Float get() = cornerBottomLeft ?: cornerRadius
        val bottomRight: Float get() = cornerBottomRight ?: cornerRadius
    }

    val ALL: List<Preset> = listOf(
        Preset(
            /**
             * The round button: the out-of-the-box handler from before the Dock, brought back as
             * it was — a 30 by 100 pill in half-transparent indigo with a thin white outline, no
             * icon, on the right a little below the top.
             *
             * A regular user's default (see [UserMode]), and the look the users who missed it
             * described as sufficient. Its behaviour is the app's original too: [CLASSIC_BEHAVIOUR].
             */
            id = "Classic",
            nameRes = R.string.preset_classic_title,
            subtitleRes = R.string.preset_classic_subtitle,
            gravity = Gravity.END,
            width = CLASSIC_WIDTH, height = 100f,
            bgColor = PREVIEW_PRIMARY, bgAlpha = 128,
            strokeColor = Color.White, strokeWidth = 1f, strokeAlpha = 200,
            cornerRadius = 15f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 18f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = POSITION_FRACTION,
            behaviour = CLASSIC_BEHAVIOUR,
            placement = Placement(
                gravity = Gravity.END,
                posXFraction = 1f,
                snapToEdge = true
            )
        ),
        Preset(
            /**
             * The dock tab: a bar whose ends sweep back into the side of the phone.
             *
             * The one preset here that is not a rounded rectangle, and the reason [HandlerShape]
             * exists. A pill sits *next to* the screen edge — however flush you push it, the two
             * corners facing the glass tell you it is a separate object resting against it. A tab
             * has no corners there at all: its outline runs off the edge and back, so it reads as
             * part of the phone's own frame, which is the whole effect this shape is for.
             *
             * Taller than the pill presets because the sweeps eat into both ends — at 120dp with
             * a 0.29 flare the straight section is only about 50dp, and a tab needs a flat middle
             * to read as a handle rather than as a leaf. Opaque black for
             * the same reason the Edge is: the shape is the whole idea, and a translucent tab
             * over a busy app is a shape you cannot make out.
             *
             * Carries a [Placement] because a tab that is not touching the edge is not a tab. The
             * sweeps run to where the glass is, and parked in mid-screen they curve away into
             * nothing.
             *
             * The out-of-the-box handle, so changing these values changes what an install with
             * unset appearance preferences looks like:
             * [com.newagedevs.gesturevolume.data.local.SharedPref] falls back to this preset for each
             * of them. Installs from before it became the default keep the bar they had — see
             * `SharedPref.pinEdgeAppearanceDefaults`.
             */
            id = "Dock",
            nameRes = R.string.preset_dock_title,
            subtitleRes = R.string.preset_dock_subtitle,
            gravity = Gravity.END,
            width = 14f, height = 120f,
            bgColor = Color.Black, bgAlpha = 255,
            strokeColor = Color.White, strokeWidth = 0f, strokeAlpha = 200,
            // Unused by a tab, which has no corners — carried so that switching this preset back
            // to a rounded shape lands on something sane rather than on four zeroes.
            cornerRadius = 8f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 16f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = POSITION_FRACTION,
            behaviour = DOCK_BEHAVIOUR,
            placement = Placement(
                gravity = Gravity.END,
                posXFraction = 1f,
                snapToEdge = true
            ),
            shape = HandlerShape.TAB,
            flare = 0.29f
        ),
        Preset(
            /**
             * The Edge: a slim black pill, flush to the right edge.
             *
             * These numbers are a deliberate copy of the shape the edge-launcher category has
             * settled on — 10dp of width, a little under a hundred tall, fully opaque black, ends
             * rounded to a half-width radius. It is the shape that reads as "grab here" while disappearing into
             * a dark app's chrome, which is why every app in this category converges on it.
             *
             * A geometric proportion is not anyone's property, and nothing here is copied from
             * another app's assets or code.
             *
             * It was the out-of-the-box handle, under the name Default, until the Dock tab took
             * that over, and an install from those days keeps it: see
             * `SharedPref.pinEdgeAppearanceDefaults`.
             */
            id = "Edge",
            nameRes = R.string.preset_edge_title,
            subtitleRes = R.string.preset_edge_subtitle,
            gravity = Gravity.END,
            width = 12f, height = 95f,
            bgColor = Color.Black, bgAlpha = 255,
            strokeColor = Color.White, strokeWidth = 0f, strokeAlpha = 200,
            cornerRadius = 10f,
            iconRes = R.drawable.ic_vol_increase, iconSize = 18f, iconColor = Color.White,
            showIcon = false, vibrate = false, edgeMargin = 0f, positionFraction = POSITION_FRACTION,
            behaviour = EDGE_BEHAVIOUR,
            placement = Placement(
                gravity = Gravity.END,
                posXFraction = 1f,
                snapToEdge = true
            ),
            // Square where it meets the screen edge, rounded where it faces the app. The two
            // radii are given as left/right rather than inner/outer because the preset is written
            // for the right-hand edge it ships against; carried to the left edge by a drag, the
            // bar keeps these corners and the rounding ends up on the outside. Living with that
            // is the cost of corners being four plain numbers the user can also edit by hand.
            cornerTopLeft = 10f, cornerTopRight = 1f,
            cornerBottomLeft = 10f, cornerBottomRight = 1f
        ),
        Preset(
            /**
             * The floating bubble: a circle that sits near the edge rather than against it.
             *
             * The odd one out on purpose. Every other preset here is a bar — a tall thin thing
             * welded to the side of the screen — and this is the shape people reach for when they
             * want the opposite: something round, obviously draggable, and clearly *on top of* the
             * app rather than part of its frame. A circle is what an assistive on-screen button
             * has looked like on every platform that has one.
             *
             * Three numbers make it a bubble rather than a wide bar. Width and height are equal,
             * the radius is exactly half of them — anything less is a rounded square — and the
             * edge margin lifts it off the side, because a circle flush to the edge is a circle
             * with a slice missing. The alpha is low enough to see the app through it and high
             * enough to find it on a white screen.
             */
            id = "Bold",
            nameRes = R.string.preset_bold_title,
            subtitleRes = R.string.preset_bold_subtitle,
            gravity = Gravity.END,
            width = BOLD_WIDTH, height = BOLD_WIDTH,
            bgColor = PREVIEW_ON_SURFACE, bgAlpha = 140,
            strokeColor = Color.White, strokeWidth = 1.5f, strokeAlpha = 90,
            cornerRadius = 23f,
            // The volume glyph rather than the move one. A bubble is round and obviously
            // draggable already; what it cannot say for itself is what it is *for*.
            iconRes = R.drawable.ic_vol_increase, iconSize = 24f, iconColor = Color.White,
            showIcon = true, vibrate = true, edgeMargin = 8f, positionFraction = POSITION_FRACTION,
            // Held still rather than carried round the phone, and a Quick panel as thick as the
            // bubble, standing a few dp off the edge as the Classic's does. It stood off by the
            // bubble's own 8dp, which left it floating well clear of the edge it opens from.
            behaviour = EDGE_BEHAVIOUR.copy(
                dynamicPosition = false,
                slider = EDGE_BEHAVIOUR.slider.copy(thicknessDp = BOLD_WIDTH, edgeOffsetDp = ROUNDED_PANEL_EDGE_OFFSET),
            )
        )
    )

    /**
     * The preset with this id, or null.
     *
     * Null for anything unknown, which includes the retired Minimal, Night and Ghost: a deep link
     * or back-stack entry still naming one of those simply applies nothing.
     */
    fun byId(id: String?): Preset? = ALL.firstOrNull { it.id == id }

    /**
     * The out-of-the-box handler: the Dock tab.
     *
     * [com.newagedevs.gesturevolume.data.local.SharedPref] falls back to these values for every
     * unset appearance preference, and for the behaviour settings its [Behaviour] names — gestures,
     * dynamic position, the Quick panel, the menu's page and the panel animation — so a fresh
     * install already *is* this preset rather than merely resembling it. Installs from before these
     * behaviour defaults keep what they had: see `SharedPref.pinPreDockBehaviourDefaults`.
     */
    val DEFAULT: Preset = ALL.first { it.id == "Dock" }

    /** The slim pill that was the default before the Dock. See `SharedPref.pinEdgeAppearanceDefaults`. */
    val EDGE: Preset = ALL.first { it.id == "Edge" }

    /** The round button the app began with, and a regular user's default. See [UserMode]. */
    val CLASSIC: Preset = ALL.first { it.id == "Classic" }
}
