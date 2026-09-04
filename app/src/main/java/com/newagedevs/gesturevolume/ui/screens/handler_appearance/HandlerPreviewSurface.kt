package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.service.HandlerGeometry
import com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.BrightnessController
import com.newagedevs.gesturevolume.utils.HandlerActions
import kotlin.math.roundToInt

/**
 * The handler, on a wallpaper, driven by the real gesture engine.
 *
 * This is the appearance screen's only preview, and it is deliberately full-bleed: placement is
 * stored as a fraction of the usable screen, so anything smaller than the screen would misreport
 * where the bar is going to sit. Dragging here moves the bar exactly as it moves live - the same
 * [HandlerGestureDetector], the same edge clamp, the same snap - and every change is routed back
 * through the appearance state, so Apply/Discard still governs it.
 *
 * Gestures that would act on the live overlay or leave the app (Hide, Stop, Open app, the
 * music overlay) report that they are unavailable rather than firing: this is a rehearsal, and a
 * rehearsal that locks the phone is not one.
 */
@Composable
fun HandlerPreviewSurface(
    state: AppearanceStateHolder,
    backgroundImageURL: String,
    /** A drag in the preview updates the draft state, so Apply/Discard still governs it. */
    onPositionChanged: (Float) -> Unit,
    /** The horizontal half of the same thing. The bar is placed freely here, exactly as it is live. */
    onHorizontalPositionChanged: (Float) -> Unit,
    /** Which side the bar's flat edge faces - decided by where it ended up, not chosen. */
    onGravityChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val preference = remember { viewModel.preference }

    var handlerViewRef by remember { mutableStateOf<HandlerView?>(null) }


    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val vibratorService = remember { context.getSystemService(Vibrator::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val brightness = remember { BrightnessController(context) }
    val previousVolume = remember { mutableIntStateOf(1) }

    val gestureDetectorRef = remember { mutableStateOf<HandlerGestureDetector?>(null) }
    val adjustIsBrightness = remember { mutableStateOf(false) }
    val adjustEnabled = remember { mutableStateOf(false) }
    val adjustDirection = remember { mutableIntStateOf(0) }

    /**
     * Puts the bar where the current draft says it goes.
     *
     * Keyed on the edge distance and the snap switch as well as the position itself, which is what
     * makes the Edge distance slider live. It could not be before: the preview places the bar with
     * [HandlerView.setFreeTranslationX], and once a free x is set, `HandlerView.applyEdgeMargin`
     * returns early and the margin it was handed is never read again. So dragging the slider
     * changed a number, redrew nothing, and looked broken.
     *
     * Running the same clamp and snap the live overlay runs — rather than teaching the view to
     * combine a free position with a margin — is also what keeps the two from disagreeing about
     * where "28dp from the edge" is.
     */
    LaunchedEffect(
        state.positionFraction, state.posXFraction, state.height, state.width,
        state.edgeMargin, state.snapToEdge
    ) {
        val handler = handlerViewRef ?: return@LaunchedEffect
        handler.post {
            val parent = handler.parent as? android.view.ViewGroup ?: return@post
            if (parent.height <= 0 || handler.height <= 0) return@post
            handler.setTranslationYPosition(
                HandlerGeometry.fractionToY(
                    state.positionFraction, parent.height, handler.height
                ).toFloat()
            )
            val marginPx =
                (state.edgeMargin * handler.resources.displayMetrics.density).roundToInt()
            val storedX = HandlerGeometry.fractionToX(
                state.posXFraction, parent.width, handler.width
            )
            val x = if (state.snapToEdge) {
                HandlerGeometry.snapX(storedX, parent.width, handler.width, marginPx)
            } else {
                HandlerGeometry.clampX(storedX, parent.width, handler.width, marginPx)
            }
            handler.setFreeTranslationX(x.toFloat())
        }
    }

    // Reactively update handler view as state properties change
    LaunchedEffect(state.gravity, state.width, state.height, state.bgColor, state.bgAlpha,
        state.strokeColor, state.strokeWidth, state.strokeAlpha, state.cornerTL,
        state.cornerTR, state.cornerBL, state.cornerBR, state.iconRes, state.iconSize,
        state.iconColor, state.showIcon, state.vibrate, state.edgeMargin, state.snapToEdge) {
        handlerViewRef?.apply {
            setViewDimensionsDp(state.width, state.height)
            setViewGravity(state.gravity)
            setEdgeMarginDp(state.edgeMargin)
            setViewBackgroundColor(state.bgColor.toArgb(), state.bgAlpha)
            setCornerRadiiDp(state.cornerTL, state.cornerTR, state.cornerBL, state.cornerBR)
            setStrokeProperties(state.strokeColor.toArgb(), state.strokeWidth, state.strokeAlpha)
            setCenterIcon(state.iconRes, state.iconSize, state.iconColor.toArgb())
            setCenterIconColor(state.iconColor.toArgb())
            setCenterIconVisible(state.showIcon)
            setVibrateOnClick(state.vibrate)
        }
    }

    // Resolved up here, in composable scope, rather than through context.getString() inside the
    // callbacks below — a Context captured from LocalContext does not follow a configuration change.
    val autoBrightnessOnMsg = stringResource(R.string.auto_brightness_on)
    val autoBrightnessOffMsg = stringResource(R.string.auto_brightness_off)
    val brightnessPermissionMsg = stringResource(R.string.brightness_needs_permission_short)
    val actionNotAvailableMsg = stringResource(R.string.action_not_available_msg)
    val unknownActionMsg = stringResource(R.string.unknown_action_msg)

    fun handlerTapActions(action: String) {
        if (preference.getHandlerVibrateOnClick()) {
            vibratorService?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        when (action) {
            // Reposition is armed by the gesture engine, never run as an action.
            HandlerActions.NONE, HandlerActions.REPOSITION -> {}
            HandlerActions.OPEN_VOLUME_UI -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            HandlerActions.MUTE -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            HandlerActions.MUTE_OR_UNMUTE -> {
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)

                if (volume > 0) {
                    previousVolume.intValue = volume
                    audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume.intValue, 0)
                }
            }
            HandlerActions.TOGGLE_AUTO_BRIGHTNESS -> {
                if (brightness.canWrite()) {
                    val turningOn = !brightness.isAutoBrightnessOn()
                    brightness.setAutoBrightness(turningOn)
                    if (turningOn) preference.setBrightnessAutoWasOn(false)
                    viewModel.showToast(
                        if (turningOn) autoBrightnessOnMsg else autoBrightnessOffMsg
                    )
                } else {
                    viewModel.showToast(brightnessPermissionMsg)
                }
            }
            // Everything that acts on the live overlay or leaves the app. Listed rather than left
            // to the else branch, which reports an unrecognised identifier — a real bug worth
            // seeing, and not what a perfectly valid action outside the preview's remit is.
            HandlerActions.ACTIVE_MUSIC_OVERLAY,
            HandlerActions.HIDE_HANDLER,
            HandlerActions.STOP_SERVICE,
            HandlerActions.OPEN_APP -> {
                viewModel.showToast(actionNotAvailableMsg)
            }
            else -> {
                viewModel.showToast(unknownActionMsg)
            }
        }
    }

    fun adjustVolume(direction: Int): Boolean {
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (volume + direction).coerceIn(0, maxVolume)
        if (newVolume == volume) return false
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI)
        return true
    }

    Box(modifier = modifier.fillMaxSize()) {
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

            // The handler itself, driven by exactly the same gesture engine as the live overlay,
            // so what the user tries out here is what they get on their home screen.
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        val container = this
                        val handler = HandlerView(ctx).apply {
                            setViewDimensionsDp(state.width, state.height)
                            setViewGravity(state.gravity)
                            setViewBackgroundColor(state.bgColor.toArgb(), state.bgAlpha)
                            setCornerRadiiDp(state.cornerTL, state.cornerTR, state.cornerBL, state.cornerBR)
                            setStrokeProperties(state.strokeColor.toArgb(), state.strokeWidth, state.strokeAlpha)
                            setCenterIcon(state.iconRes, state.iconSize, state.iconColor.toArgb())
                            setCenterIconColor(state.iconColor.toArgb())
                            setCenterIconVisible(state.showIcon)
                            setVibrateOnClick(state.vibrate)
                            setEdgeMarginDp(state.edgeMargin)
                        }

                        val host = object : HandlerGestureDetector.Host {
                            private var dragStartY = 0f
                            private var dragStartX = 0f

                            /**
                             * Which side the bar is dressed for, so the dressing is only redone
                             * when it changes. `setViewGravity` rebuilds the background drawable
                             * and requests a layout — not something to do on every move frame.
                             */
                            private var dressedLeft: Boolean? = null

                            override fun isLongPressReposition(): Boolean =
                                preference.getHandlerLongTapAction() == HandlerActions.REPOSITION

                            override fun isDoubleTapArmed(): Boolean =
                                preference.getHandlerDoubleTapAction() != HandlerActions.NONE

                            override fun onTap() =
                                handlerTapActions(preference.getHandlerSingleTapAction())

                            override fun onDoubleTap() =
                                handlerTapActions(preference.getHandlerDoubleTapAction())

                            override fun onLongPress() =
                                handlerTapActions(preference.getHandlerLongTapAction())

                            /** Mirrors `OverlayService.resolveAdjustAction` — see the note there. */
                            private fun resolveAdjustAction(direction: Int) {
                                if (direction == adjustDirection.intValue || direction == 0) return
                                adjustDirection.intValue = direction

                                val action = if (direction > 0) {
                                    preference.getHandlerSwipeUpAction()
                                } else {
                                    preference.getHandlerSwipeDownAction()
                                }
                                adjustIsBrightness.value = HandlerActions.isBrightnessSwipe(action)
                                adjustEnabled.value = !HandlerActions.isDisabled(action)
                                gestureDetectorRef.value?.setStepCount(
                                    if (adjustIsBrightness.value) brightness.stepCount else maxVolume
                                )
                            }

                            override fun onAdjustBegin(initialDirection: Int) {
                                adjustDirection.intValue = 0
                                resolveAdjustAction(initialDirection)
                            }

                            override fun onAdjustStep(direction: Int): Boolean {
                                resolveAdjustAction(direction)
                                if (!adjustEnabled.value) return false
                                if (adjustIsBrightness.value) {
                                    if (!brightness.canWrite()) return false
                                    if (brightness.disableAutoBrightnessIfNeeded()) {
                                        preference.setBrightnessAutoWasOn(true)
                                    }
                                    return brightness.step(direction) != null
                                }
                                return adjustVolume(direction)
                            }

                            override fun onAdjustEnd() {
                                adjustEnabled.value = false
                                adjustDirection.intValue = 0
                            }

                            override fun onDragCue(active: Boolean) {
                                handler.setDragCue(active)
                                if (active && state.vibrate) {
                                    vibratorService?.vibrate(
                                        VibrationEffect.createOneShot(
                                            40,
                                            VibrationEffect.DEFAULT_AMPLITUDE
                                        )
                                    )
                                }
                            }

                            /**
                             * No menu in the preview.
                             *
                             * The preview exists to show what the bar *looks* like while the user
                             * tunes its appearance, and it lives inside a dialog that already owns
                             * the screen. Popping a second floating menu on top of that would sit
                             * over the very controls being adjusted. Holding here still highlights
                             * the bar and still drags it, which is the part worth previewing.
                             */
                            override fun onContextMenuOpen() = Unit

                            override fun onContextMenuDismiss() = Unit

                            override fun onDragBegin() {
                                dragStartY = handler.translationY
                                dragStartX = handler.freeTranslationX()
                                dressedLeft = state.gravity == Gravity.START
                            }

                            override fun onDragUpdate(offsetXPx: Float, offsetYPx: Float) {
                                val maxY = (container.height - handler.height).coerceAtLeast(0)
                                handler.translationY =
                                    (dragStartY + offsetYPx).coerceIn(0f, maxY.toFloat())
                                // The same edge-offset clamp the live overlay uses, so what the
                                // preview shows is what the bar will do.
                                val x = HandlerGeometry.clampX(
                                    (dragStartX + offsetXPx).roundToInt(),
                                    container.width,
                                    handler.width,
                                    (state.edgeMargin * ctx.resources.displayMetrics.density)
                                        .roundToInt()
                                )
                                handler.setFreeTranslationX(x.toFloat())
                                // Dressed for the nearer side during the drag, matching the live
                                // bar.
                                val isLeft =
                                    HandlerGeometry.xToIsLeft(x, container.width, handler.width)
                                if (dressedLeft != isLeft) {
                                    dressedLeft = isLeft
                                    handler.setViewGravity(
                                        if (isLeft) Gravity.START else Gravity.END
                                    )
                                }
                            }

                            override fun onDragEnd(moved: Boolean) {
                                if (!moved) return
                                val released = handler.freeTranslationX().roundToInt()
                                // The same choice the live overlay makes, from the same setting,
                                // so what the preview does on release is what the bar will do.
                                val x = if (state.snapToEdge) {
                                    HandlerGeometry.snapX(
                                        released,
                                        container.width,
                                        handler.width,
                                        (state.edgeMargin * ctx.resources.displayMetrics.density)
                                            .roundToInt()
                                    )
                                } else {
                                    released
                                }
                                if (x != released) {
                                    ValueAnimator.ofFloat(released.toFloat(), x.toFloat()).apply {
                                        duration = 180L
                                        interpolator = DecelerateInterpolator()
                                        addUpdateListener {
                                            handler.setFreeTranslationX(it.animatedValue as Float)
                                        }
                                        start()
                                    }
                                }
                                // Routed through the appearance state holder rather than written
                                // straight to preferences, so a drag obeys the same Apply/Discard
                                // contract as every other setting on this screen.
                                onGravityChanged(
                                    if (HandlerGeometry.xToIsLeft(x, container.width, handler.width)) {
                                        Gravity.START
                                    } else {
                                        Gravity.END
                                    }
                                )
                                onHorizontalPositionChanged(
                                    HandlerGeometry.xToFraction(x, container.width, handler.width)
                                )
                                onPositionChanged(
                                    HandlerGeometry.yToFraction(
                                        handler.translationY.roundToInt(),
                                        container.height,
                                        handler.height
                                    )
                                )
                            }
                        }

                        val detector = HandlerGestureDetector(ctx, host)
                        handler.setGestureDetector(detector)
                        gestureDetectorRef.value = detector

                        handler.post {
                            handler.setTranslationYPosition(
                                HandlerGeometry.fractionToY(
                                    state.positionFraction, container.height, handler.height
                                ).toFloat()
                            )
                            handler.setFreeTranslationX(
                                HandlerGeometry.fractionToX(
                                    state.posXFraction, container.width, handler.width
                                ).toFloat()
                            )
                        }

                        handlerViewRef = handler
                        addView(handler)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
    }
}
