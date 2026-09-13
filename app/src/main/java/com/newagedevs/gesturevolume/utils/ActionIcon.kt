package com.newagedevs.gesturevolume.utils

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * An icon for an action, in whichever of the two forms the app has it.
 *
 * The original actions ship hand-drawn drawables that match the bar's own icon set; everything
 * added with the Deck uses Material's vector icons, which the app already depends on for its
 * settings screens. One type for both means the action picker, the summary rows and the overlay
 * menu can render any entry without knowing which kind it holds.
 */
sealed interface ActionIcon {
    data class Res(@param:DrawableRes val id: Int) : ActionIcon
    data class Vector(val image: ImageVector) : ActionIcon

    /**
     * The Quick panel's icon, which depends on what the panel is set to drive: a sun for
     * brightness, a speaker for a volume.
     *
     * Its own case rather than a fixed drawable, because the entry is shared by every picker, the
     * Actions rows and the long-press menu, and the panel's target is changed on another screen
     * entirely. A fixed speaker put a volume icon on a gesture that changed the brightness.
     * Resolved where it is drawn — see `ActionIconImage`.
     */
    data object QuickPanel : ActionIcon
}
