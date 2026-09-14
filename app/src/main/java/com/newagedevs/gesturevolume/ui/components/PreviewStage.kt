package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R

/**
 * The card every "here is what it will look like" preview sits in.
 *
 * One composable rather than several nearly-identical ones, because the handler's preview, the
 * Quick panel's, the Deck's and the menu's are answering the same question about things the user
 * sets up on neighbouring screens, and the moment they stop looking alike the app stops looking
 * like one app.
 *
 * **Why a picture behind it.** Every subject here is translucent by default and has an opacity
 * control. Judged against a flat colour, opacity is a number; judged against a picture, it is the
 * thing the user is actually choosing — how much of their screen shows through. The pictures are
 * [PreviewBackdrops]: drawn, quiet, and mid-toned, so the subject stays the subject. The two small
 * buttons in the corner move to the next one, or repaint the one showing in a random colour,
 * because a colour that reads on one screen may not on another, and the only way to know is to look.
 *
 * @param backdrop which of [PreviewBackdrops] to start on.
 * @param fillHeight fill the height it is given instead of taking [PREVIEW_STAGE_HEIGHT] — for the
 *   landscape arrangement, where the preview has a column of its own. See [PreviewSettingsLayout].
 */
@Composable
fun PreviewStage(
    backdrop: Int,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    fillHeight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(backdrop) }
    // The seed of a random colour laid over whichever pattern is showing, kept so rotation redraws
    // the same one; null while the pattern wears its own colour.
    var colorSeed by rememberSaveable { mutableStateOf<Long?>(null) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(PREVIEW_STAGE_HEIGHT)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Box(contentAlignment = contentAlignment) {
            PreviewBackdrop(
                index = index,
                colorSeed = colorSeed,
                modifier = Modifier.fillMaxSize(),
            )
            content()
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                // Two independent controls: the first changes only the pattern, the second only
                // its colour, so a colour that has been found is kept while patterns are tried.
                StageButton(
                    icon = Icons.Default.Wallpaper,
                    description = stringResource(R.string.preview_backdrop_change),
                    onClick = { index = (index + 1) % PreviewBackdrops.COUNT },
                )
                StageButton(
                    icon = Icons.Default.Palette,
                    description = stringResource(R.string.preview_backdrop_random),
                    onClick = { colorSeed = kotlin.random.Random.nextLong() },
                )
            }
        }
    }
}

@Composable
private fun StageButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.28f),
        contentColor = Color.White,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * How tall every preview is.
 *
 * Fixed, and shared, for the reason the class note gives. It also has to hold the subject: the
 * Quick panel's track runs to 320dp and the bar to 200dp, so neither fits whole — but both are
 * long in the axis where being cropped costs nothing, because a track is the same all the way
 * down and what the user is judging is its width, its colour and its ends.
 */
val PREVIEW_STAGE_HEIGHT = 230.dp

/**
 * The breathing room the subject leaves at the top and bottom of the stage.
 *
 * Both subjects here are tall and thin and both are capped against it, so neither runs off the
 * ends of the picture it is being judged against — a track bleeding off both edges reads as a
 * cropped photograph rather than as an object standing on one.
 */
val PREVIEW_STAGE_INSET = 26.dp

/** What is left for the subject once [PREVIEW_STAGE_INSET] is taken off each end. */
val PREVIEW_SUBJECT_MAX_HEIGHT = PREVIEW_STAGE_HEIGHT - PREVIEW_STAGE_INSET * 2
