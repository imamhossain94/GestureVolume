package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.view.Gravity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.ui.view.HandlerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerAppearanceScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preference = remember { viewModel.preference }

    var handlerViewRef by remember { mutableStateOf<HandlerView?>(null) }
    var isExpandedPreview by remember { mutableStateOf(false) }

    // Load saved settings from SharedPreferences
    var handlerGravity by remember {
        mutableStateOf(
            if (preference.getHandlerPosition() == "Left") Gravity.START else Gravity.END
        )
    }

    var bgImage by remember { mutableStateOf(viewModel.getNextBackground()) }
    var handlerWidth by remember { mutableStateOf(preference.getHandlerWidthDp()) }
    var handlerHeight by remember { mutableStateOf(preference.getHandlerHeightDp()) }
    var backgroundColor by remember { mutableStateOf(Color(preference.getHandlerColor())) }
    var backgroundAlpha by remember { mutableStateOf(preference.getHandlerBackgroundAlpha()) }
    var strokeColor by remember { mutableStateOf(Color(preference.getHandlerStrokeColor())) }
    var strokeWidth by remember { mutableStateOf(preference.getHandlerStrokeWidth()) }
    var strokeAlpha by remember { mutableStateOf(preference.getHandlerStrokeAlpha()) }
    var cornerRadiusAll by remember { mutableStateOf(preference.getHandlerCornerRadiusTL()) }
    var cornerRadiusTL by remember { mutableStateOf(preference.getHandlerCornerRadiusTL()) }
    var cornerRadiusTR by remember { mutableStateOf(preference.getHandlerCornerRadiusTR()) }
    var cornerRadiusBL by remember { mutableStateOf(preference.getHandlerCornerRadiusBL()) }
    var cornerRadiusBR by remember { mutableStateOf(preference.getHandlerCornerRadiusBR()) }
    var selectedIconRes by remember { mutableStateOf(preference.getHandlerIconRes()) }
    var iconSize by remember { mutableStateOf(preference.getHandlerIconSize()) }
    var iconColor by remember { mutableStateOf(Color(preference.getHandlerIconColor())) }
    var showIcon by remember { mutableStateOf(preference.getHandlerShowIcon()) }
    var enableVibration by remember { mutableStateOf(preference.getHandlerVibrateOnClick()) }
    var lockPosition by remember { mutableStateOf(preference.getHandlerLockPosition()) }
    var showIconPicker by remember { mutableStateOf(false) }

    // Save settings to SharedPreferences whenever they change
    LaunchedEffect(handlerGravity) {
        preference.setHandlerPosition(if (handlerGravity == Gravity.START) "Left" else "Right")
    }

    LaunchedEffect(handlerWidth) {
        preference.setHandlerWidthDp(handlerWidth)
    }

    LaunchedEffect(handlerHeight) {
        preference.setHandlerHeightDp(handlerHeight)
    }

    LaunchedEffect(backgroundColor.toArgb()) {
        preference.setHandlerColor(backgroundColor.toArgb())
    }

    LaunchedEffect(backgroundAlpha) {
        preference.setHandlerBackgroundAlpha(backgroundAlpha)
    }

    LaunchedEffect(strokeColor.toArgb()) {
        preference.setHandlerStrokeColor(strokeColor.toArgb())
    }

    LaunchedEffect(strokeWidth) {
        preference.setHandlerStrokeWidth(strokeWidth)
    }

    LaunchedEffect(strokeAlpha) {
        preference.setHandlerStrokeAlpha(strokeAlpha)
    }

    LaunchedEffect(cornerRadiusTL) {
        preference.setHandlerCornerRadiusTL(cornerRadiusTL)
    }

    LaunchedEffect(cornerRadiusTR) {
        preference.setHandlerCornerRadiusTR(cornerRadiusTR)
    }

    LaunchedEffect(cornerRadiusBL) {
        preference.setHandlerCornerRadiusBL(cornerRadiusBL)
    }

    LaunchedEffect(cornerRadiusBR) {
        preference.setHandlerCornerRadiusBR(cornerRadiusBR)
    }

    LaunchedEffect(selectedIconRes) {
        preference.setHandlerIconRes(selectedIconRes)
    }

    LaunchedEffect(iconSize) {
        preference.setHandlerIconSize(iconSize)
    }

    LaunchedEffect(iconColor.toArgb()) {
        preference.setHandlerIconColor(iconColor.toArgb())
    }

    LaunchedEffect(showIcon) {
        preference.setHandlerShowIcon(showIcon)
    }

    LaunchedEffect(enableVibration) {
        preference.setHandlerVibrateOnClick(enableVibration)
    }

    LaunchedEffect(lockPosition) {
        preference.setHandlerLockPosition(lockPosition)
    }

    // Apply changes to HandlerView
    LaunchedEffect(
        handlerGravity, handlerWidth, handlerHeight, backgroundColor, backgroundAlpha,
        strokeColor, strokeWidth, strokeAlpha, cornerRadiusTL, cornerRadiusTR,
        cornerRadiusBL, cornerRadiusBR, selectedIconRes, iconSize, iconColor, showIcon,
        enableVibration, lockPosition
    ) {
        handlerViewRef?.let { handler ->
            handler.setViewGravity(handlerGravity)
            handler.setViewDimensionsDp(handlerWidth, handlerHeight)
            handler.setViewBackgroundColor(backgroundColor.toArgb(), backgroundAlpha)
            handler.setStrokeProperties(strokeColor.toArgb(), strokeWidth, strokeAlpha)
            handler.setCornerRadiiDp(cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)
            handler.setCenterIcon(selectedIconRes, iconSize, iconColor.toArgb())
            handler.setCenterIconColor(iconColor.toArgb())
            handler.setCenterIconVisible(showIcon)
            handler.setVibrateOnClick(enableVibration)
            handler.setHandlerPositionLocked(lockPosition)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Handler Appearance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isExpandedPreview = true }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand Preview")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    // containerColor = MaterialTheme.colorScheme.primaryContainer,
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Fixed Live Preview Section at top
            PreviewSectionWithHandler(
                handlerGravity = handlerGravity,
                handlerWidth = handlerWidth,
                handlerHeight = handlerHeight,
                backgroundColor = backgroundColor,
                backgroundAlpha = backgroundAlpha,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                strokeAlpha = strokeAlpha,
                cornerRadiusTL = cornerRadiusTL,
                cornerRadiusTR = cornerRadiusTR,
                cornerRadiusBL = cornerRadiusBL,
                cornerRadiusBR = cornerRadiusBR,
                iconRes = selectedIconRes,
                iconSize = iconSize,
                iconColor = iconColor,
                showIcon = showIcon,
                enableVibration = enableVibration,
                backgroundImageURL = bgImage,
                onHandlerCreated = {
                    handlerViewRef = it
                }
            )

            // Scrollable Customization Controls
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Position Section
                SectionTitle("POSITION", Color(0xFF8B5CF6))
                CustomizationCard(
                    gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))
                ) {
                    LabeledControl(label = "Gravity") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectableButton(
                                text = "Left",
                                selected = handlerGravity == Gravity.START,
                                onClick = { handlerGravity = Gravity.START },
                                modifier = Modifier.weight(1f)
                            )
                            SelectableButton(
                                text = "Right",
                                selected = handlerGravity == Gravity.END,
                                onClick = { handlerGravity = Gravity.END },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SwitchControl(
                        label = "Lock position",
                        checked = lockPosition,
                        onCheckedChange = { lockPosition = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dimensions Section
                SectionTitle("DIMENSIONS", Color(0xFF3B82F6))
                CustomizationCard(
                    gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
                ) {
                    SliderControl(
                        label = "Width",
                        value = handlerWidth,
                        valueRange = 10f..60f,
                        valueDisplay = "${handlerWidth.toInt()}dp",
                        onValueChange = { handlerWidth = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Height",
                        value = handlerHeight,
                        valueRange = 30f..200f,
                        valueDisplay = "${handlerHeight.toInt()}dp",
                        onValueChange = { handlerHeight = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Section
                SectionTitle("BACKGROUND", Color(0xFF10B981))
                CustomizationCard(
                    gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669))
                ) {
                    ColorPickerControl(
                        label = "Color",
                        color = backgroundColor,
                        onColorChange = { backgroundColor = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Opacity",
                        value = backgroundAlpha.toFloat(),
                        valueRange = 0f..255f,
                        valueDisplay = "${((backgroundAlpha / 255f) * 100).toInt()}%",
                        onValueChange = { backgroundAlpha = it.toInt() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stroke Section
                SectionTitle("STROKE", Color(0xFFF59E0B))
                CustomizationCard(
                    gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                ) {
                    ColorPickerControl(
                        label = "Color",
                        color = strokeColor,
                        onColorChange = { strokeColor = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Width",
                        value = strokeWidth,
                        valueRange = 0f..8f,
                        valueDisplay = "${strokeWidth.toInt()}dp",
                        onValueChange = { strokeWidth = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Opacity",
                        value = strokeAlpha.toFloat(),
                        valueRange = 0f..255f,
                        valueDisplay = "${((strokeAlpha / 255f) * 100).toInt()}%",
                        onValueChange = { strokeAlpha = it.toInt() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Corner Radius Section
                SectionTitle("CORNER RADIUS", Color(0xFFEC4899))
                CustomizationCard(
                    gradientColors = listOf(Color(0xFFEC4899), Color(0xFFDB2777))
                ) {
                    // NEW: All Corners slider
                    SliderControl(
                        label = "All Corners",
                        value = cornerRadiusAll,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusAll.toInt()}dp",
                        onValueChange = { value ->
                            cornerRadiusAll = value
                            cornerRadiusTL = value
                            cornerRadiusTR = value
                            cornerRadiusBL = value
                            cornerRadiusBR = value
                            preference.setAllCornerRadii(value)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Top Left",
                        value = cornerRadiusTL,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusTL.toInt()}dp",
                        onValueChange = { cornerRadiusTL = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Top Right",
                        value = cornerRadiusTR,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusTR.toInt()}dp",
                        onValueChange = { cornerRadiusTR = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Bottom Left",
                        value = cornerRadiusBL,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusBL.toInt()}dp",
                        onValueChange = { cornerRadiusBL = it }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    )

                    SliderControl(
                        label = "Bottom Right",
                        value = cornerRadiusBR,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusBR.toInt()}dp",
                        onValueChange = { cornerRadiusBR = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Section
                SectionTitle("ICON", Color(0xFF06B6D4))
                CustomizationCard(
                    gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
                ) {
                    SwitchControl(
                        label = "Show icon",
                        checked = showIcon,
                        onCheckedChange = { showIcon = it }
                    )

                    if (showIcon) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )

                        IconPickerControl(
                            label = "Icon",
                            selectedIconRes = selectedIconRes,
                            onClick = { showIconPicker = true }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )

                        ColorPickerControl(
                            label = "Icon Color",
                            color = iconColor,
                            onColorChange = { iconColor = it }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )

                        SliderControl(
                            label = "Icon Size",
                            value = iconSize,
                            valueRange = 16f..48f,
                            valueDisplay = "${iconSize.toInt()}dp",
                            onValueChange = { iconSize = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Behavior Section
                SectionTitle("BEHAVIOR", Color(0xFFEF4444))
                CustomizationCard(
                    gradientColors = listOf(Color(0xFFEF4444), Color(0xFFDC2626))
                ) {
                    SwitchControl(
                        label = "Vibrate on click",
                        checked = enableVibration,
                        onCheckedChange = { enableVibration = it }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Expanded Preview Dialog
    if (isExpandedPreview) {
        ExpandedPreviewDialog(
            handlerGravity = handlerGravity,
            handlerWidth = handlerWidth,
            handlerHeight = handlerHeight,
            backgroundColor = backgroundColor,
            backgroundAlpha = backgroundAlpha,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            strokeAlpha = strokeAlpha,
            cornerRadiusTL = cornerRadiusTL,
            cornerRadiusTR = cornerRadiusTR,
            cornerRadiusBL = cornerRadiusBL,
            cornerRadiusBR = cornerRadiusBR,
            iconRes = selectedIconRes,
            iconSize = iconSize,
            iconColor = iconColor,
            showIcon = showIcon,
            enableVibration = enableVibration,
            lockPosition = lockPosition,
            translationY = preference.getHandlerTranslationY(),
            backgroundImageURL = bgImage,
            onDismiss = { isExpandedPreview = false }
        )
    }

    // Icon Picker Dialog
    if (showIconPicker) {
        IconPickerDialog(
            selectedIconRes = selectedIconRes,
            onIconSelected = {
                selectedIconRes = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
}
