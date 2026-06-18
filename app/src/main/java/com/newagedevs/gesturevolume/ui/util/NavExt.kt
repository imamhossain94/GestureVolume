package com.newagedevs.gesturevolume.ui.util

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController

/**
 * Pops the back stack only when the current entry is settled (RESUMED).
 *
 * Bare `popBackStack()` has no debounce, so a fast double-tap on a back button pops twice
 * (e.g. appearance -> main -> empty), leaving the NavHost with nothing to render and the
 * Surface background showing through as a white screen. During a navigation transition the
 * leaving entry drops out of RESUMED, so the second tap here is a no-op and navigation stays
 * on the destination instead of overshooting past the start destination.
 */
fun NavController.navigateBackOnce() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
