package com.newagedevs.gesturevolume.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

/**
 * How the app's screens arrive and leave: on a spring, sliding a short way and fading, forward
 * from the end the reading runs toward and back again on Back.
 *
 * A short slide rather than the whole width. A full slide that overshoots opens a gap at the
 * trailing edge for a frame; a fifth of the width overshooting by a few pixels only lands.
 */
object ScreenTransitions {

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(SlideDirection.Start, Springs.ScreenOffset) { it / 5 } +
            fadeIn(Springs.ScreenFade)
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(SlideDirection.Start, Springs.ScreenOffset) { it / 10 } +
            fadeOut(Springs.ScreenFade)
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(SlideDirection.End, Springs.ScreenOffset) { it / 10 } +
            fadeIn(Springs.ScreenFade)
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(SlideDirection.End, Springs.ScreenOffset) { it / 5 } +
            fadeOut(Springs.ScreenFade)
    }
}
