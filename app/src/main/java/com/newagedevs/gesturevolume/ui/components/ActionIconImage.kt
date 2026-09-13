package com.newagedevs.gesturevolume.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.content.Context
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.data.local.PREFERENCES_FILE
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.QuickSliderIcons
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
        ActionIcon.QuickPanel -> {
            // Read as it is drawn, from the panel's own setting, so the picker, the Actions rows
            // and the long-press menu all say what the panel will actually change.
            val context = LocalContext.current
            val target = QuickSliderStore.targetOf(
                context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
            )
            Icon(
                painter = painterResource(QuickSliderIcons.automatic(target)),
                contentDescription = contentDescription,
                modifier = modifier,
                tint = tint
            )
        }
    }
}
