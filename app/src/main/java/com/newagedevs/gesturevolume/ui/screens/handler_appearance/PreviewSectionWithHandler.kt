package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.newagedevs.gesturevolume.service.HandlerGeometry
import com.newagedevs.gesturevolume.ui.view.HandlerView


@Composable
fun PreviewSectionWithHandler(
    handlerGravity: Int,
    handlerWidth: Float,
    handlerHeight: Float,
    backgroundColor: Color,
    backgroundAlpha: Int,
    strokeColor: Color,
    strokeWidth: Float,
    strokeAlpha: Int,
    cornerRadiusTL: Float,
    cornerRadiusTR: Float,
    cornerRadiusBL: Float,
    cornerRadiusBR: Float,
    iconRes: Int,
    iconSize: Float,
    iconColor: Color,
    showIcon: Boolean,
    enableVibration: Boolean,
    edgeMargin: Float,
    backgroundImageURL: String,
    onHandlerCreated: (HandlerView) -> Unit
) {
    val handlerViewRef = remember { androidx.compose.runtime.mutableStateOf<HandlerView?>(null) }

    /**
     * This card is a *style* preview, so the bar sits centred no matter where it lives on screen.
     * A 220dp card is nowhere near the shape of a phone, so honouring the stored position only
     * pushed the bar into a corner and cropped it. The full-screen preview is where position is
     * shown — and edited — to scale.
     */
    val centreOfCard = 0.5f

    // Re-centre when the bar's height changes; its own translation does not follow a resize.
    LaunchedEffect(handlerHeight) {
        handlerViewRef.value?.post {
            val handler = handlerViewRef.value ?: return@post
            val parentHeight = (handler.parent as? ViewGroup)?.height ?: 0
            if (parentHeight > 0 && handler.height > 0) {
                handler.setTranslationYPosition(
                    HandlerGeometry.fractionToY(
                        centreOfCard, parentHeight, handler.height
                    ).toFloat()
                )
            }
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 0.dp, bottom = 16.dp)
            .background(
                Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Background image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backgroundImageURL)
                        .crossfade(500)
                        .build(),
                    placeholder = ColorPainter(Color.Black),
                    contentDescription = "Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                val composition by rememberLottieComposition(
                    LottieCompositionSpec.Asset("mobile_setting.json")
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Preview handler
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                            }
                        }
                    }
            ) {
                AndroidView(
                    factory = { context ->
                        FrameLayout(context).apply {
                            val handler = HandlerView(context).apply {
                                setViewDimensionsDp(handlerWidth, handlerHeight)
                                setViewGravity(handlerGravity)
                                setViewBackgroundColor(backgroundColor.toArgb(), backgroundAlpha)
                                setCornerRadiiDp(
                                    cornerRadiusTL,
                                    cornerRadiusTR,
                                    cornerRadiusBL,
                                    cornerRadiusBR
                                )
                                setStrokeProperties(strokeColor.toArgb(), strokeWidth, strokeAlpha)
                                setCenterIcon(iconRes, iconSize, iconColor.toArgb())
                                setCenterIconColor(iconColor.toArgb())
                                setCenterIconVisible(showIcon)
                                setVibrateOnClick(enableVibration)
                                setEdgeMarginDp(edgeMargin)
                                // No gesture detector: this small card is a static rendering of the
                                // bar's appearance, not something the user interacts with.

                                post {
                                    val parentHeight = (parent as? ViewGroup)?.height ?: 0
                                    setTranslationYPosition(
                                        HandlerGeometry.fractionToY(
                                            centreOfCard, parentHeight, height
                                        ).toFloat()
                                    )
                                }
                            }

                            addView(handler)
                            handlerViewRef.value = handler
                            onHandlerCreated(handler)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

        }
    }
}