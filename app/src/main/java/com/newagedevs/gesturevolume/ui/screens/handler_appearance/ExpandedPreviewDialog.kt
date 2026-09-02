package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.newagedevs.gesturevolume.service.HandlerGeometry
import com.newagedevs.gesturevolume.ui.view.HandlerGestureDetector
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.BrightnessController
import com.newagedevs.gesturevolume.utils.HandlerActions
import kotlin.math.roundToInt
import com.newagedevs.gesturevolume.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPreviewDialog(
    state: AppearanceStateHolder,
    viewModel: MainViewModel = hiltViewModel(),
    backgroundImageURL: String,
    onShowIconPicker: () -> Unit,
    /** A drag in the preview updates the draft state, so Apply/Discard still governs it. */
    onPositionChanged: (Float) -> Unit,
    /** The horizontal half of the same thing. The bar is placed freely here, exactly as it is live. */
    onHorizontalPositionChanged: (Float) -> Unit,
    /** Which side the bar's flat edge faces — decided by where it ended up, not chosen. */
    onGravityChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preference = remember { viewModel.preference }

    var handlerViewRef by remember { mutableStateOf<HandlerView?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetOffsetY = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    // Slide in when shown
    LaunchedEffect(showBottomSheet) {
        if (showBottomSheet) {
            sheetOffsetY.snapTo(sheetHeightPx.coerceAtLeast(1f))
            sheetOffsetY.animateTo(0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))
        }
    }

    fun dismissSheet() {
        coroutineScope.launch {
            sheetOffsetY.animateTo(sheetHeightPx, animationSpec = spring(dampingRatio = 1f, stiffness = 300f))
            showBottomSheet = false
            sheetOffsetY.snapTo(0f)
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val vibratorService = remember { context.getSystemService(Vibrator::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val brightness = remember { BrightnessController(context) }
    val previousVolume = remember { mutableIntStateOf(1) }

    val gestureDetectorRef = remember { mutableStateOf<HandlerGestureDetector?>(null) }
    val adjustIsBrightness = remember { mutableStateOf(false) }
    val adjustEnabled = remember { mutableStateOf(false) }
    val adjustDirection = remember { mutableIntStateOf(0) }

    // Whether a long press moves the bar, for the on-screen hints. Read once per composition —
    // the gesture host reads it live, so a change made while this dialog is open still takes effect.
    val repositionOnLongPress = remember {
        preference.getHandlerLongTapAction() == HandlerActions.REPOSITION
    }

    // Keep the preview in step with a position changed from elsewhere — Reset position, mostly.
    LaunchedEffect(state.positionFraction, state.posXFraction, state.height, state.width) {
        val handler = handlerViewRef ?: return@LaunchedEffect
        handler.post {
            val parent = handler.parent as? android.view.ViewGroup ?: return@post
            if (parent.height <= 0 || handler.height <= 0) return@post
            handler.setTranslationYPosition(
                HandlerGeometry.fractionToY(
                    state.positionFraction, parent.height, handler.height
                ).toFloat()
            )
            handler.setFreeTranslationX(
                HandlerGeometry.fractionToX(
                    state.posXFraction, parent.width, handler.width
                ).toFloat()
            )
        }
    }

    // Reactively update handler view as state properties change
    LaunchedEffect(state.gravity, state.width, state.height, state.bgColor, state.bgAlpha,
        state.strokeColor, state.strokeWidth, state.strokeAlpha, state.cornerTL,
        state.cornerTR, state.cornerBL, state.cornerBR, state.iconRes, state.iconSize,
        state.iconColor, state.showIcon, state.vibrate, state.edgeMargin) {
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
            HandlerActions.LOCK,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent, shape = RoundedCornerShape(0.dp))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFfdd2c2),
                                Color(0xFFfcc463),
                                Color(0xFFfe4485)
                            )
                        ),
                        shape = RoundedCornerShape(0.dp)
                    ),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
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
                // Removed LottieAnimation to make it cleaner, the AsyncImage is sufficient
            }

            // Handler View — driven by exactly the same gesture engine as the live overlay, so
            // what the user tries out here is what they get on their home screen.
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
                                val x = handler.freeTranslationX().roundToInt()
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

            // Top Control Panel
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Settings Button
                    Card(
                        modifier = Modifier.clickable { showBottomSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.1f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = Color(0xFF1F2937),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.settings),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1F2937),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Minimize Button
                    Card(
                        modifier = Modifier.clickable(onClick = onDismiss),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.1f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = stringResource(R.string.minimize),
                                tint = Color(0xFF1F2937),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.minimize),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1F2937),
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Reposition hint. There is no lock toggle any more: whether the bar can be moved
                // is decided by the long-press action alone, over on the Handler actions screen.
                if (repositionOnLongPress) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.1f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenWith,
                                contentDescription = null,
                                tint = Color(0xFF1F2937),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.long_press_to_reposition),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1F2937),
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }

            // Bottom Info Card
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.1f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.test_mode),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (repositionOnLongPress) {
                                stringResource(R.string.reposition_mode_desc)
                            } else {
                                stringResource(R.string.test_mode_desc)
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            lineHeight = 16.sp
                        )
                    }
                }
            // End of Bottom Info Card
            }
        
        // Draggable Bottom Sheet
        if (showBottomSheet) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                    .offset { IntOffset(0, sheetOffsetY.value.toInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (sheetOffsetY.value > sheetHeightPx * 0.3f) {
                                        dismissSheet()
                                    } else {
                                        sheetOffsetY.animateTo(
                                            0f,
                                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f)
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    sheetOffsetY.animateTo(0f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f))
                                }
                            }
                        ) { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (sheetOffsetY.value + dragAmount).coerceAtLeast(0f)
                                sheetOffsetY.snapTo(newOffset)
                            }
                        }
                    },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Drag handle pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(Color.Gray.copy(alpha = 0.4f), CircleShape)
                        )
                    }

                    // Settings Content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        HandlerAppearanceSettingsContent(
                            state = state,
                            onShowIconPicker = onShowIconPicker,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        }
    }
}