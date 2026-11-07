package com.newagedevs.gesturevolume.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.ui.motion.IconButton
import java.util.Locale

/**
 * The video tutorials on the NewAgeDevs YouTube channel, one for each part of the app, in the
 * order the series plays them.
 *
 * Opened in YouTube, or the browser where there is no YouTube app, and never played in the app.
 * An embedded player would bring YouTube's own terms, cookies and ads into an app that loads none
 * of its screens from the web. A plain link brings none of that: no SDK and no permission, and
 * nothing about the user goes with it. The URLs carry no share or tracking parameters for the
 * same reason.
 *
 * [seconds] is the length YouTube reports, shown so someone can see it is a minute, not ten.
 */
enum class Tutorial(
    val videoId: String,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int,
    val seconds: Int,
) {
    GETTING_STARTED("hhnmDjMchnw", R.string.tutorial_getting_started, R.string.tutorial_getting_started_summary, 69),
    SWIPE_VOLUME("gXA0KPfFzP4", R.string.tutorial_swipe_volume, R.string.tutorial_swipe_volume_summary, 61),
    QUICK_SLIDER("iL-AladQ3B4", R.string.tutorial_quick_slider, R.string.tutorial_quick_slider_summary, 94),
    DECK("fImtgUqBtH0", R.string.tutorial_deck, R.string.tutorial_deck_summary, 84),
    SEARCH("meHMyUiXOe8", R.string.tutorial_search, R.string.tutorial_search_summary, 60),
    MENU("oO_s17Gd83k", R.string.tutorial_menu, R.string.tutorial_menu_summary, 79),
    ACTIONS("NENTzFCEbi8", R.string.tutorial_actions, R.string.tutorial_actions_summary, 66),
    APPEARANCE("kQZFzztZQdg", R.string.tutorial_appearance, R.string.tutorial_appearance_summary, 63),
    VISIBILITY("UOHPfXJXeus", R.string.tutorial_visibility, R.string.tutorial_visibility_summary, 72);

    val url: String get() = "https://www.youtube.com/watch?v=$videoId"

    /** "1:03", in the reader's digits. */
    fun duration(locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
}

/**
 * Opens a [Tutorial], from whichever screen calls this.
 *
 * The app-open ad is held back for the trip, as it is for a trip to a system screen. Coming back
 * from a video this app sent the user to is not a fresh open, and an ad then would land on
 * someone part-way through learning the app. The hold is lifted when the calling screen resumes,
 * after the process's ON_START has already decided against the ad.
 */
@Composable
fun rememberTutorialOpener(): (Tutorial) -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preference = remember(context) { SharedPref(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) preference.setAppOpenAdPaused(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(context, preference) {
        { tutorial ->
            preference.setAppOpenAdPaused(true)
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, tutorial.url.toUri()))
            } catch (_: Exception) {
                preference.setAppOpenAdPaused(false)
                Toast.makeText(context, R.string.tutorial_cannot_open, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * A screen's own video, as a ▶ in its top bar beside the ? that replays the demo: the demo shows
 * the gesture, and the video walks through the whole screen for anyone the demo did not help.
 *
 * A plain play symbol, not YouTube's logo, which its brand rules keep for YouTube's own use.
 */
@Composable
fun TutorialAction(tutorial: Tutorial) {
    val open = rememberTutorialOpener()
    IconButton(onClick = { open(tutorial) }) {
        Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = stringResource(R.string.tutorial_watch, stringResource(tutorial.title)),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
