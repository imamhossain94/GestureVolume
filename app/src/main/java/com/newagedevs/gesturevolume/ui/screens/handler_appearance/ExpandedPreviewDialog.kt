package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.content.Context
import android.graphics.PointF
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.newagedevs.gesturevolume.ui.view.HandlerView
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import kotlin.math.abs
import kotlin.math.sqrt


@Composable
fun ExpandedPreviewDialog(
    viewModel: MainViewModel = hiltViewModel(),
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
    lockPosition: Boolean,
    translationY: Float,
    backgroundImageURL: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val preference = remember { viewModel.preference }

    var isPositionLocked by remember { mutableStateOf(lockPosition) }
    var handlerViewRef by remember { mutableStateOf<HandlerView?>(null) }

    val touchMoveFactor: Long = 20
    val touchTimeFactor: Long = 300
    val doubleClickTimeDelta: Long = 300
    val longPressTimeThreshold: Long = 500

    // Remember state variables
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val vibratorService = remember { context.getSystemService(Vibrator::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val currentVolume = remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    val minSwipeY = remember { mutableFloatStateOf(0f) }
    val previousVolume = remember { mutableIntStateOf(1) }
    val lastX = remember { mutableFloatStateOf(0f) }
    val lastY = remember { mutableFloatStateOf(0f) }

    val longPressHandler = remember { Handler(Looper.getMainLooper()) }
    val singleClickHandler = remember { Handler(Looper.getMainLooper()) }

    val eventX1 = remember { mutableFloatStateOf(0f) }
    val eventX2 = remember { mutableFloatStateOf(0f) }
    val startY = remember { mutableFloatStateOf(0f) }

    val actionDownPoint = remember { mutableStateOf(PointF(0f, 0f)) }
    val previousPoint = remember { mutableStateOf(PointF(0f, 0f)) }

    val touchDownTime = remember { mutableLongStateOf(0L) }
    val lastClickTime = remember { mutableLongStateOf(0L) }

    val isLongPressHandlerActivated = remember { mutableStateOf(false) }
    val isActionMoveEventStored = remember { mutableStateOf(false) }
    val lastActionMoveEventBeforeUpX = remember { mutableFloatStateOf(0f) }
    val lastActionMoveEventBeforeUpY = remember { mutableFloatStateOf(0f) }

    // Update handler when lock position changes
    LaunchedEffect(isPositionLocked) {
        handlerViewRef?.setHandlerPositionLocked(isPositionLocked)
    }

    fun handlerTapActions(action: String) {
        if (preference.getHandlerVibrateOnClick()) {
            vibratorService?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        when (action) {
            "None" -> {}
            "Open volume UI" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            }
            "Mute" -> {
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
            }
            "Mute or Unmute" -> {
                val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)

                if (volume > 0) {
                    previousVolume.intValue = volume
                    audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                } else {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume.intValue, 0)
                }
            }
            "Active Music Overlay",
            "Lock",
            "Hide Handler",
            "Open App" -> {
                viewModel.showToast("This action is not available here.")
            }
            else -> {
                viewModel.showToast("Unknown action.")
            }
        }
    }

    fun adjustVolume(direction: Int) {
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (volume + direction).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI)
        currentVolume.intValue = newVolume
    }

    fun onLongPress() {

    }

    fun now(): Long = System.currentTimeMillis()

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
                .background(
                    Color.Transparent,
                    shape = RoundedCornerShape(0.dp)
                )
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

                val composition by rememberLottieComposition(
                    LottieCompositionSpec.Asset("mobile_setting.json")
                )
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Handler View
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        val handler = HandlerView(ctx).apply {
                            setViewDimensionsDp(handlerWidth, handlerHeight)
                            setViewGravity(handlerGravity)
                            setViewBackgroundColor(backgroundColor.toArgb(), backgroundAlpha)
                            setCornerRadiiDp(cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)
                            setStrokeProperties(strokeColor.toArgb(), strokeWidth, strokeAlpha)
                            setCenterIcon(iconRes, iconSize, iconColor.toArgb())
                            setCenterIconColor(iconColor.toArgb())
                            setCenterIconVisible(showIcon)
                            setHandlerPositionLocked(isPositionLocked)
                            setVibrateOnClick(enableVibration)

                            setHandlerClickListener(object : HandlerView.HandlerClickListener {
                                override fun onSingleClick() {
                                    if (isPositionLocked) {
                                        handlerTapActions(preference.getHandlerSingleTapAction())
                                    }
                                }

                                override fun onDoubleClick() {
                                    if (isPositionLocked) {
                                        handlerTapActions(preference.getHandlerDoubleTapAction())
                                    }
                                }
                            })

                            setHandlerPositionChangeListener(object : HandlerView.HandlerPositionChangeListener {
                                override fun onVertical(rawY: Float) {
                                    preference.setHandlerTranslationY(rawY)
                                }

                                override fun onVertical(rawY: Int) {
                                    // Optional: handle integer position
                                }
                            })

                            // Custom touch listener for swipe gestures
                            setOnTouchListener { view, event ->
                                if (!isPositionLocked) {
                                    // Let the HandlerView handle dragging when unlocked
                                    return@setOnTouchListener false
                                }

                                // Handle gestures when locked
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        longPressHandler.postDelayed({
                                            onLongPress()
                                            handlerTapActions(preference.getHandlerLongTapAction())
                                            isLongPressHandlerActivated.value = true
                                        }, longPressTimeThreshold)

                                        actionDownPoint.value = PointF(event.x, event.y)
                                        previousPoint.value = PointF(event.x, event.y)
                                        touchDownTime.longValue = now()
                                        eventX1.floatValue = event.x
                                        startY.floatValue = event.y
                                        minSwipeY.floatValue = 0f
                                        lastX.floatValue = event.x
                                        lastY.floatValue = event.y
                                        true
                                    }
                                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                                        if (!isActionMoveEventStored.value) {
                                            isActionMoveEventStored.value = true
                                            lastActionMoveEventBeforeUpX.floatValue = event.x
                                            lastActionMoveEventBeforeUpY.floatValue = event.y
                                        } else {
                                            val currentX = event.x
                                            val currentY = event.y
                                            val firstX = lastActionMoveEventBeforeUpX.floatValue
                                            val firstY = lastActionMoveEventBeforeUpY.floatValue
                                            val distance = sqrt(
                                                ((currentY - firstY) * (currentY - firstY) +
                                                        (currentX - firstX) * (currentX - firstX)).toDouble()
                                            )

                                            if (distance > 20) {
                                                longPressHandler.removeCallbacksAndMessages(null)
                                                eventX2.floatValue = event.x
                                                previousPoint.value = PointF(event.x, event.y)
                                            }

                                            val x = event.x
                                            val y = event.y
                                            val distanceX = x - lastX.floatValue
                                            val distanceY = y - lastY.floatValue

                                            minSwipeY.floatValue += distanceY

                                            val sWidth = dpToPx(handlerWidth)
                                            val sHeight = dpToPx(handlerHeight)

                                            val border = 1
                                            if (event.x < border || event.y < border ||
                                                event.x > sWidth - border || event.y > sHeight - border) {
                                                return@setOnTouchListener false
                                            }

                                            if (abs(distanceX) < abs(distanceY) && abs(minSwipeY.floatValue) > 10) {
                                                if (distanceY > 0) {
                                                    adjustVolume(-1)
                                                } else {
                                                    adjustVolume(1)
                                                }
                                                minSwipeY.floatValue = 0f
                                            }
                                            lastX.floatValue = x
                                            lastY.floatValue = y
                                        }
                                        true
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        isActionMoveEventStored.value = false
                                        longPressHandler.removeCallbacksAndMessages(null)

                                        if (isLongPressHandlerActivated.value) {
                                            isLongPressHandlerActivated.value = false
                                            return@setOnTouchListener false
                                        }

                                        val isTouchDuration = now() - touchDownTime.longValue < touchTimeFactor
                                        val isTouchLength = abs(event.x - actionDownPoint.value.x) +
                                                abs(event.y - actionDownPoint.value.y) < touchMoveFactor
                                        val shouldClick = isTouchLength && isTouchDuration

                                        if (shouldClick) {
                                            view.performClick()
                                            val currentTime = now()
                                            if (currentTime - lastClickTime.longValue < doubleClickTimeDelta) {
                                                singleClickHandler.removeCallbacksAndMessages(null)
                                                handlerTapActions(preference.getHandlerDoubleTapAction())
                                            } else {
                                                singleClickHandler.postDelayed({
                                                    handlerTapActions(preference.getHandlerSingleTapAction())
                                                }, doubleClickTimeDelta)
                                            }
                                            lastClickTime.longValue = currentTime
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }

                            post {
                                setTranslationYPosition(translationY)
                            }
                        }

                        handlerViewRef = handler
                        addView(handler)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { frameLayout ->
                    // Update handler when isPositionLocked changes
                    (frameLayout.getChildAt(0) as? HandlerView)?.apply {
                        setHandlerPositionLocked(isPositionLocked)
                    }
                }
            )

            // Top Control Panel
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                            contentDescription = "Minimize",
                            tint = Color(0xFF1F2937),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MINIMIZE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF1F2937),
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Lock Position Toggle
                Card(
                    modifier = Modifier.clickable {
                        isPositionLocked = !isPositionLocked
                        preference.setHandlerLockPosition(isPositionLocked)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPositionLocked) Color(0xFF10B981) else Color.White
                    ),
                    border = BorderStroke(
                        2.dp,
                        if (isPositionLocked) Color(0xFF10B981) else Color.Black.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isPositionLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isPositionLocked) "Locked" else "Unlocked",
                            tint = if (isPositionLocked) Color.White else Color(0xFF1F2937),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPositionLocked) "POSITION LOCKED" else "UNLOCK TO REPOSITION",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isPositionLocked) Color.White else Color(0xFF1F2937),
                            letterSpacing = 0.8.sp
                        )
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
                            text = if (isPositionLocked) "Test Mode" else "Reposition Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1F2937)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isPositionLocked)
                                "Try swipes and taps"
                            else
                                "Drag handler to new position",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}