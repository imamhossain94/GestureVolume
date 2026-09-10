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
}
