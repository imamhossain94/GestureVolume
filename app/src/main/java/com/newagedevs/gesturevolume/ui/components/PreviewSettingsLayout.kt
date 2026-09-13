package com.newagedevs.gesturevolume.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/** Whether the screen is wider than it is tall right now. */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/**
 * A pinned preview and the settings that change it, arranged for the way the phone is held.
 *
 * Upright, the preview sits across the top and the settings scroll underneath it, which is the
 * shape all four of these screens had. On its side that shape stops working: a phone in landscape
 * is about 430dp tall, and a 230dp preview under a top bar left the settings a strip a couple of
 * rows high to scroll through. So there the preview takes two fifths of the width, as tall as the
 * screen allows, and the settings take the other three fifths beside it — both on screen at once,
 * which is the whole reason the preview is pinned.
 *
 * On its side the two sit inside the same margins as the home screen: the side insets the
 * [contentPadding] carries, which is where the camera cutout lands in landscape, then the same
 * 16dp, and the same widest column. Without the insets the preview ran in under the cutout while
 * the home screen one tap away stayed clear of it.
 *
 * @param contentPadding the Scaffold's padding. Its top and its sides are applied here; its bottom
 *   belongs to the scrolling settings, which pad for the navigation bar themselves.
 * @param header a line above the preview, when the screen has one. Laid out edge to edge of the
 *   preview column, so it lines up with the preview under it.
 * @param preview told the modifier to place it with, and whether to fill the height it is given.
 */
@Composable
fun PreviewSettingsLayout(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    preview: @Composable (modifier: Modifier, fillHeight: Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val framed = modifier
        .fillMaxSize()
        .padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(direction),
            end = contentPadding.calculateEndPadding(direction),
        )
    if (isLandscape()) {
        Box(modifier = framed, contentAlignment = Alignment.TopCenter) {
            Row(
                modifier = Modifier
                    .widthIn(max = LANDSCAPE_MAX_WIDTH)
                    .fillMaxHeight()
                    .padding(horizontal = SIDE_MARGIN),
                horizontalArrangement = Arrangement.spacedBy(PANE_GAP),
            ) {
                Column(
                    modifier = Modifier
                        .weight(PREVIEW_SHARE)
                        .fillMaxHeight()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                ) {
                    header?.invoke()
                    Spacer(modifier = Modifier.height(8.dp))
                    preview(Modifier.weight(1f), true)
                }
                Column(
                    modifier = Modifier
                        .weight(1f - PREVIEW_SHARE)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 16.dp),
                    content = content,
                )
            }
        }
    } else {
        Column(modifier = framed) {
            header?.let {
                Box(modifier = Modifier.padding(horizontal = SIDE_MARGIN)) { it() }
            }
            preview(Modifier.padding(start = SIDE_MARGIN, end = SIDE_MARGIN, top = 8.dp), false)
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(SIDE_MARGIN),
                content = content,
            )
        }
    }
}

/** The home screen's side margin, so the two screens start and end at the same place. */
private val SIDE_MARGIN = 16.dp

/** The home screen's widest column, for the same reason. */
private val LANDSCAPE_MAX_WIDTH = 920.dp

/** Between the preview and the settings: wider than a card gap, so they read as two panes. */
private val PANE_GAP = 20.dp

/** How much of the width the preview takes on its side. */
private const val PREVIEW_SHARE = 0.4f
