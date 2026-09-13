package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * The card every "here is what it will look like" preview sits in.
 *
 * One composable rather than two nearly-identical ones, because the handler's preview and the
 * Quick panel's preview are answering the same question about two things the user sets up on
 * consecutive screens, and the moment they stop looking alike the app stops looking like one app.
 *
 * **Why a photograph.** Both subjects are translucent by default and both have an opacity slider.
 * Judged against a flat colour, opacity is a number; judged against a photograph, it is the thing
 * the user is actually choosing — how much of their wallpaper shows through the bar on their home
 * screen. The surface colour underneath is not a fallback so much as the answer for the seconds
 * before the image arrives, and for a device that is offline when it does not.
 */
@Composable
fun PreviewStage(
    backgroundImageURL: String,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_STAGE_HEIGHT),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Box(contentAlignment = contentAlignment) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backgroundImageURL)
                    .crossfade(400)
                    .build(),
                // Transparent rather than a colour, so what shows through before the image lands
                // is the Surface above — one backdrop that fades into another, not two.
                placeholder = ColorPainter(androidx.compose.ui.graphics.Color.Transparent),
                error = ColorPainter(androidx.compose.ui.graphics.Color.Transparent),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            content()
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
 * ends of the wallpaper it is being judged against — a track bleeding off both edges reads as a
 * cropped photograph rather than as an object standing on one.
 */
val PREVIEW_STAGE_INSET = 26.dp

/** What is left for the subject once [PREVIEW_STAGE_INSET] is taken off each end. */
val PREVIEW_SUBJECT_MAX_HEIGHT = PREVIEW_STAGE_HEIGHT - PREVIEW_STAGE_INSET * 2
