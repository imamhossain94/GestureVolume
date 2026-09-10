package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.newagedevs.gesturevolume.ui.components.PREVIEW_SUBJECT_MAX_HEIGHT
import com.newagedevs.gesturevolume.ui.components.PreviewStage
import com.newagedevs.gesturevolume.ui.view.HandlerView

/**
 * The dock: the handler, at its real size, on a surface that shows what it is made of.
 *
 * **What this is not, any more.** It used to be a working rehearsal — a scale model of the whole
 * screen, on a downloaded wallpaper, driven by the live [com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector],
 * where the bar could be dragged into place and its gestures tried out. Every part of that fought
 * the job the screen is actually for. The wallpaper changed on every visit, so the colour being
 * chosen was judged against a different backdrop each time. The model had to be shrunk to about a
 * third of full size to leave room for the settings, which made a 10dp bar four dp of screen — too
 * small to see the corner radius or the icon being adjusted, and too small to hit, which needed a
 * touch delegate to paper over. And rehearsing gestures here duplicated the live bar's behaviour
 * in a second implementation that could only ever drift from it.
 *
 * So the preview does one thing: it shows the handler. Real dp dimensions, real colours, real
 * corners, real icon, flush to the side it is dressed for. Placement stays with the live bar,
 * where it always actually happened; gestures stay with the live bar, which is the only place they
 * can be tried honestly.
 *
 * **Why a plain card rather than an aspect-ratio model.** It is the same shape the Quick panel's
 * own preview uses one screen over — full width, a fixed height, a translucent surface — because
 * the two are doing the same job and looking like each other is most of what makes a settings app
 * feel like one thing. A fixed height is also the only sizing that behaves: a ratio taken off the
 * full width asks for a dock two and a half screens tall in landscape, and pushes every control
 * this preview exists to serve off the page.
 */
@Composable
fun HandlerPreviewSurface(
    state: AppearanceStateHolder,
    backgroundImageURL: String,
    modifier: Modifier = Modifier,
) {
    var handlerViewRef by remember { mutableStateOf<HandlerView?>(null) }

    // Every property, in one effect. There is nothing here that has to happen in a particular
    // order relative to anything else, and one keyed effect is what keeps "the preview shows the
    // draft" a single fact rather than fifteen.
    LaunchedEffect(
        handlerViewRef, state.gravity, state.width, state.height, state.bgColor, state.bgAlpha,
        state.strokeColor, state.strokeWidth, state.strokeAlpha, state.cornerTL,
        state.cornerTR, state.cornerBL, state.cornerBR, state.iconRes, state.iconSize,
        state.iconColor, state.showIcon, state.edgeMargin
    ) {
        handlerViewRef?.apply {
            // Capped to the stage's clear height for the same reason the Quick panel's preview
            // is: a bar drawn at its full 200dp in a 230dp stage touches both ends and stops
            // reading as an object placed on the wallpaper. The width is never capped — width is
            // the dimension the user is actually judging here.
            setViewDimensionsDp(
                state.width,
                minOf(state.height, PREVIEW_SUBJECT_MAX_HEIGHT.value)
            )
            setViewGravity(state.gravity)
            setEdgeMarginDp(state.edgeMargin)
            setViewBackgroundColor(state.bgColor.toArgb(), state.bgAlpha)
            setCornerRadiiDp(state.cornerTL, state.cornerTR, state.cornerBL, state.cornerBR)
            setStrokeProperties(state.strokeColor.toArgb(), state.strokeWidth, state.strokeAlpha)
            setCenterIcon(state.iconRes, state.iconSize, state.iconColor.toArgb())
            setCenterIconColor(state.iconColor.toArgb())
            setCenterIconVisible(state.showIcon)

            // Last, and it has to be last: every setter above that changes a dimension or the
            // side rebuilds the layout params from scratch — see HandlerView.updateLayoutParams —
            // so a vertical centring applied before any of them is simply discarded. Centring is
            // this dock's own business rather than the bar's, because the bar's gravity is a
            // record of which screen edge it faces and must not pick up a second axis.
            (layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.gravity = state.gravity or Gravity.CENTER_VERTICAL
                layoutParams = lp
            }
        }
    }

    PreviewStage(backgroundImageURL = backgroundImageURL, modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    // No gesture detector. The bar here is a picture; `setGestureDetector` is
                    // simply never called, so HandlerView's own onTouchEvent has nothing to hand
                    // a touch to, and the dock is inert by construction rather than by
                    // suppressing behaviours one at a time.
                    val handler = HandlerView(ctx)
                    handlerViewRef = handler
                    addView(handler)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}


