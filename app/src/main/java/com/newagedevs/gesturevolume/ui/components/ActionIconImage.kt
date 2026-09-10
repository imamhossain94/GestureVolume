package com.newagedevs.gesturevolume.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.safeDrawableIdOrDefault

/**
 * Draws an [ActionIcon], whichever form it takes.
 *
 * Drawable ids go through the safe resolver so a stale stored id — resource ids shift between
 * builds — falls back to the default icon instead of throwing inside `painterResource`.
 */
@Composable
fun ActionIconImage(
    icon: ActionIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    when (icon) {
        is ActionIcon.Vector -> Icon(
            imageVector = icon.image,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
        is ActionIcon.Res -> {
            val context = LocalContext.current
            Icon(
                painter = painterResource(context.safeDrawableIdOrDefault(icon.id)),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint
            )
        }
    }
}
