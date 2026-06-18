package com.newagedevs.gesturevolume.utils

import android.content.Context
import com.newagedevs.gesturevolume.R

/**
 * Resolves a drawable resource id safely.
 *
 * A previously stored icon id can dangle after an app update: `R.drawable.*` values are
 * NOT stable across builds, so an Int persisted by an older version may point at nothing
 * in the current build. Handing such an id to `painterResource()` / `getDrawable()` throws
 * `Resources$NotFoundException` and crashes the screen (see the Handler Appearance crash).
 *
 * This returns [default] for any id that is missing or not a drawable in the current build,
 * so callers can render without guarding every call site themselves.
 */
fun Context.safeDrawableIdOrDefault(id: Int, default: Int = R.drawable.ic_vol_increase): Int =
    try {
        if (resources.getResourceTypeName(id) == "drawable") id else default
    } catch (_: Exception) {
        default
    }
