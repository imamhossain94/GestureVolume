package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.view.Gravity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.ui.view.HandlerView

private data class AppearanceState(
    val gravity: Int,
    val width: Float,
    val height: Float,
    val bgColor: Int,
    val bgAlpha: Int,
    val strokeColor: Int,
    val strokeWidth: Float,
    val strokeAlpha: Int,
    val cornerTL: Float,
    val cornerTR: Float,
    val cornerBL: Float,
    val cornerBR: Float,
    val iconRes: Int,
    val iconSize: Float,
    val iconColor: Int,
    val showIcon: Boolean,
    val vibrate: Boolean,
    val lockPosition: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerAppearanceScreen(
    viewModel: MainViewModel = hiltViewModel(),
    presetId: String?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preference = remember { viewModel.preference }

    var handlerViewRef by remember { mutableStateOf<HandlerView?>(null) }
    var isExpandedPreview by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    fun getSavedState() = AppearanceState(
        gravity = if (preference.getHandlerPosition() == "Left") Gravity.START else Gravity.END,
        width = preference.getHandlerWidthDp(),
        height = preference.getHandlerHeightDp(),
        bgColor = preference.getHandlerColor(),
        bgAlpha = preference.getHandlerBackgroundAlpha(),
        strokeColor = preference.getHandlerStrokeColor(),
        strokeWidth = preference.getHandlerStrokeWidth(),
        strokeAlpha = preference.getHandlerStrokeAlpha(),
        cornerTL = preference.getHandlerCornerRadiusTL(),
        cornerTR = preference.getHandlerCornerRadiusTR(),
        cornerBL = preference.getHandlerCornerRadiusBL(),
        cornerBR = preference.getHandlerCornerRadiusBR(),
        iconRes = preference.getHandlerIconRes(),
        iconSize = preference.getHandlerIconSize(),
        iconColor = preference.getHandlerIconColor(),
        showIcon = preference.getHandlerShowIcon(),
        vibrate = preference.getHandlerVibrateOnClick(),
        lockPosition = preference.getHandlerLockPosition()
    )

    var savedState by remember { mutableStateOf(getSavedState()) }

    var handlerGravity by remember { mutableStateOf(savedState.gravity) }
    var bgImage by remember { mutableStateOf(viewModel.getNextBackground()) }
    var handlerWidth by remember { mutableStateOf(savedState.width) }
    var handlerHeight by remember { mutableStateOf(savedState.height) }
    var backgroundColor by remember { mutableStateOf(Color(savedState.bgColor)) }
    var backgroundAlpha by remember { mutableStateOf(savedState.bgAlpha) }
    var strokeColor by remember { mutableStateOf(Color(savedState.strokeColor)) }
    var strokeWidth by remember { mutableStateOf(savedState.strokeWidth) }
    var strokeAlpha by remember { mutableStateOf(savedState.strokeAlpha) }
    
    var cornerRadiusAll by remember { mutableStateOf(savedState.cornerTL) }
    var cornerRadiusTL by remember { mutableStateOf(savedState.cornerTL) }
    var cornerRadiusTR by remember { mutableStateOf(savedState.cornerTR) }
    var cornerRadiusBL by remember { mutableStateOf(savedState.cornerBL) }
    var cornerRadiusBR by remember { mutableStateOf(savedState.cornerBR) }
    var selectedIconRes by remember { mutableStateOf(savedState.iconRes) }
    var iconSize by remember { mutableStateOf(savedState.iconSize) }
    var iconColor by remember { mutableStateOf(Color(savedState.iconColor)) }
    var showIcon by remember { mutableStateOf(savedState.showIcon) }
    var enableVibration by remember { mutableStateOf(savedState.vibrate) }
    var lockPosition by remember { mutableStateOf(savedState.lockPosition) }
    var showIconPicker by remember { mutableStateOf(false) }

    val currentState = AppearanceState(
        gravity = handlerGravity,
        width = handlerWidth,
        height = handlerHeight,
        bgColor = backgroundColor.toArgb(),
        bgAlpha = backgroundAlpha,
        strokeColor = strokeColor.toArgb(),
        strokeWidth = strokeWidth,
        strokeAlpha = strokeAlpha,
        cornerTL = cornerRadiusTL,
        cornerTR = cornerRadiusTR,
        cornerBL = cornerRadiusBL,
        cornerBR = cornerRadiusBR,
        iconRes = selectedIconRes,
        iconSize = iconSize,
        iconColor = iconColor.toArgb(),
        showIcon = showIcon,
        vibrate = enableVibration,
        lockPosition = lockPosition
    )

    val hasUnsavedChanges = currentState != savedState

    LaunchedEffect(presetId) {
        if (presetId != null) {
            when (presetId) {
                "Default" -> {
                    handlerGravity = Gravity.END
                    lockPosition = true
                    handlerWidth = 30f
                    handlerHeight = 100f
                    backgroundColor = Color.White
                    backgroundAlpha = 50
                    strokeColor = Color.White
                    strokeWidth = 1f
                    strokeAlpha = 200
                    cornerRadiusAll = 15f
                    cornerRadiusTL = 15f
                    cornerRadiusTR = 15f
                    cornerRadiusBL = 15f
                    cornerRadiusBR = 15f
                    selectedIconRes = R.drawable.ic_vol_increase
                    iconSize = 18f
                    iconColor = Color.White
                    showIcon = true
                    enableVibration = false
                }
                "Minimal" -> {
                    handlerGravity = Gravity.END
                    lockPosition = true
                    handlerWidth = 10f
                    handlerHeight = 100f
                    backgroundColor = Color.White
                    backgroundAlpha = 50
                    strokeColor = Color.White
                    strokeWidth = 1f
                    strokeAlpha = 200
                    cornerRadiusAll = 5f
                    cornerRadiusTL = 5f
                    cornerRadiusTR = 5f
                    cornerRadiusBL = 5f
                    cornerRadiusBR = 5f
                    selectedIconRes = R.drawable.ic_vol_increase
                    iconSize = 18f
                    iconColor = Color.White
                    showIcon = false
                    enableVibration = false
                }
                "Bold" -> {
                    handlerGravity = Gravity.END
                    lockPosition = true
                    handlerWidth = 40f
                    handlerHeight = 100f
                    backgroundColor = Color.White
                    backgroundAlpha = 50
                    strokeColor = Color.White
                    strokeWidth = 1f
                    strokeAlpha = 255
                    cornerRadiusAll = 15f
                    cornerRadiusTL = 15f
                    cornerRadiusTR = 15f
                    cornerRadiusBL = 15f
                    cornerRadiusBR = 15f
                    selectedIconRes = R.drawable.ic_move
                    iconSize = 32f
                    iconColor = Color.White
                    showIcon = true
                    enableVibration = true
                }
                "Night" -> {
                    handlerGravity = Gravity.END
                    lockPosition = true
                    handlerWidth = 30f
                    handlerHeight = 100f
                    backgroundColor = Color(0xFF1F2937)
                    backgroundAlpha = 230
                    strokeColor = Color(0xFF374151)
                    strokeWidth = 1f
                    strokeAlpha = 200
                    cornerRadiusAll = 15f
                    cornerRadiusTL = 15f
                    cornerRadiusTR = 15f
                    cornerRadiusBL = 15f
                    cornerRadiusBR = 15f
                    selectedIconRes = R.drawable.ic_vol_increase
                    iconSize = 22f
                    iconColor = Color(0xFF9CA3AF)
                    showIcon = true
                    enableVibration = true
                }
                "Ghost" -> {
                    handlerGravity = Gravity.END
                    lockPosition = true
                    handlerWidth = 20f
                    handlerHeight = 100f
                    backgroundColor = Color.White
                    backgroundAlpha = 5
                    strokeColor = Color.White
                    strokeWidth = 0.5f
                    strokeAlpha = 5
                    cornerRadiusAll = 12f
                    cornerRadiusTL = 12f
                    cornerRadiusTR = 12f
                    cornerRadiusBL = 12f
                    cornerRadiusBR = 12f
                    selectedIconRes = R.drawable.ic_vol_increase
                    iconSize = 16f
                    iconColor = Color.White
                    showIcon = false
                    enableVibration = false
                }
            }
        }
    }

    LaunchedEffect(currentState) {
        handlerViewRef?.let { handler ->
            handler.setViewGravity(currentState.gravity)
            handler.setViewDimensionsDp(currentState.width, currentState.height)
            handler.setViewBackgroundColor(currentState.bgColor, currentState.bgAlpha)
            handler.setStrokeProperties(currentState.strokeColor, currentState.strokeWidth, currentState.strokeAlpha)
            handler.setCornerRadiiDp(currentState.cornerTL, currentState.cornerTR, currentState.cornerBL, currentState.cornerBR)
            handler.setCenterIcon(currentState.iconRes, currentState.iconSize, currentState.iconColor)
            handler.setCenterIconColor(currentState.iconColor)
            handler.setCenterIconVisible(currentState.showIcon)
            handler.setVibrateOnClick(currentState.vibrate)
            handler.setHandlerPositionLocked(currentState.lockPosition)
        }
    }

    fun saveChanges() {
        preference.setHandlerPosition(if (handlerGravity == Gravity.START) "Left" else "Right")
        preference.setHandlerWidthDp(handlerWidth)
        preference.setHandlerHeightDp(handlerHeight)
        preference.setHandlerColor(backgroundColor.toArgb())
        preference.setHandlerBackgroundAlpha(backgroundAlpha)
        preference.setHandlerStrokeColor(strokeColor.toArgb())
        preference.setHandlerStrokeWidth(strokeWidth)
        preference.setHandlerStrokeAlpha(strokeAlpha)
        preference.setHandlerCornerRadiusTL(cornerRadiusTL)
        preference.setHandlerCornerRadiusTR(cornerRadiusTR)
        preference.setHandlerCornerRadiusBL(cornerRadiusBL)
        preference.setHandlerCornerRadiusBR(cornerRadiusBR)
        preference.setHandlerIconRes(selectedIconRes)
        preference.setHandlerIconSize(iconSize)
        preference.setHandlerIconColor(iconColor.toArgb())
        preference.setHandlerShowIcon(showIcon)
        preference.setHandlerVibrateOnClick(enableVibration)
        preference.setHandlerLockPosition(lockPosition)

        if (cornerRadiusTL == cornerRadiusTR && cornerRadiusTR == cornerRadiusBL && cornerRadiusBL == cornerRadiusBR) {
            preference.setAllCornerRadii(cornerRadiusTL)
        }

        savedState = currentState
        viewModel.sendUpdateToService(context)
        viewModel.showToast("Appearance saved")
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = "Unsaved Changes",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = "You have unsaved changes. Do you want to apply them before leaving?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveChanges()
                        showDiscardDialog = false
                        onNavigateBack()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Discard")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    text = "Apply Changes?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to apply these appearance settings to your active handler?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveChanges()
                        showSaveDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSaveDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Handler Appearance") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasUnsavedChanges) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Check, contentDescription = "Save Changes", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { isExpandedPreview = true }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Expand Preview")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
                onHandlerCreated = { handlerViewRef = it }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Position Section
                SectionTitle("POSITION", Color(0xFF8B5CF6))
                CustomizationCard(borderColor = Color(0xFF8B5CF6)) {
                    LabeledControl(label = "Gravity") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SelectableButton(
                                text = "Left",
                                selected = handlerGravity == Gravity.START,
                                borderColor = Color(0xFF8B5CF6),
                                onClick = { handlerGravity = Gravity.START },
                                modifier = Modifier.weight(1f)
                            )
                            SelectableButton(
                                text = "Right",
                                selected = handlerGravity == Gravity.END,
                                borderColor = Color(0xFF8B5CF6),
                                onClick = { handlerGravity = Gravity.END },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SwitchControl(
                        label = "Lock position",
                        checked = lockPosition,
                        borderColor = Color(0xFF8B5CF6),
                        onCheckedChange = { lockPosition = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dimensions Section
                SectionTitle("DIMENSIONS", Color(0xFF3B82F6))
                CustomizationCard(borderColor = Color(0xFF3B82F6)) {
                    SliderControl(
                        label = "Width",
                        value = handlerWidth,
                        valueRange = 10f..60f,
                        valueDisplay = "${handlerWidth.toInt()}dp",
                        borderColor = Color(0xFF3B82F6),
                        onValueChange = { handlerWidth = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Height",
                        value = handlerHeight,
                        valueRange = 30f..200f,
                        valueDisplay = "${handlerHeight.toInt()}dp",
                        borderColor = Color(0xFF3B82F6),
                        onValueChange = { handlerHeight = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Background Section
                SectionTitle("BACKGROUND", Color(0xFF10B981))
                CustomizationCard(borderColor = Color(0xFF10B981)) {
                    ColorPickerControl(
                        label = "Color",
                        color = backgroundColor,
                        borderColor = Color(0xFF10B981),
                        onColorChange = { backgroundColor = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Opacity",
                        value = backgroundAlpha.toFloat(),
                        valueRange = 0f..255f,
                        valueDisplay = "${((backgroundAlpha / 255f) * 100).toInt()}%",
                        borderColor = Color(0xFF10B981),
                        onValueChange = { backgroundAlpha = it.toInt() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stroke Section
                SectionTitle("STROKE", Color(0xFFF59E0B))
                CustomizationCard(borderColor = Color(0xFFF59E0B)) {
                    ColorPickerControl(
                        label = "Color",
                        color = strokeColor,
                        borderColor = Color(0xFFF59E0B),
                        onColorChange = { strokeColor = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Width",
                        value = strokeWidth,
                        valueRange = 0f..8f,
                        valueDisplay = "${strokeWidth.toInt()}dp",
                        borderColor = Color(0xFFF59E0B),
                        onValueChange = { strokeWidth = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Opacity",
                        value = strokeAlpha.toFloat(),
                        valueRange = 0f..255f,
                        valueDisplay = "${((strokeAlpha / 255f) * 100).toInt()}%",
                        borderColor = Color(0xFFF59E0B),
                        onValueChange = { strokeAlpha = it.toInt() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Corner Radius Section
                SectionTitle("CORNER RADIUS", Color(0xFFEC4899))
                CustomizationCard(borderColor = Color(0xFFEC4899)) {
                    SliderControl(
                        label = "All Corners",
                        value = cornerRadiusAll,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusAll.toInt()}dp",
                        borderColor = Color(0xFFEC4899),
                        onValueChange = { value ->
                            cornerRadiusAll = value
                            cornerRadiusTL = value
                            cornerRadiusTR = value
                            cornerRadiusBL = value
                            cornerRadiusBR = value
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Top Left",
                        value = cornerRadiusTL,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusTL.toInt()}dp",
                        borderColor = Color(0xFFEC4899),
                        onValueChange = { cornerRadiusTL = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Top Right",
                        value = cornerRadiusTR,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusTR.toInt()}dp",
                        borderColor = Color(0xFFEC4899),
                        onValueChange = { cornerRadiusTR = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Bottom Left",
                        value = cornerRadiusBL,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusBL.toInt()}dp",
                        borderColor = Color(0xFFEC4899),
                        onValueChange = { cornerRadiusBL = it }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    SliderControl(
                        label = "Bottom Right",
                        value = cornerRadiusBR,
                        valueRange = 0f..50f,
                        valueDisplay = "${cornerRadiusBR.toInt()}dp",
                        borderColor = Color(0xFFEC4899),
                        onValueChange = { cornerRadiusBR = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Icon Section
                SectionTitle("ICON", Color(0xFF06B6D4))
                CustomizationCard(borderColor = Color(0xFF06B6D4)) {
                    SwitchControl(
                        label = "Show icon",
                        checked = showIcon,
                        borderColor = Color(0xFF06B6D4),
                        onCheckedChange = { showIcon = it }
                    )

                    if (showIcon) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        IconPickerControl(
                            label = "Icon",
                            selectedIconRes = selectedIconRes,
                            borderColor = Color(0xFF06B6D4),
                            onClick = { showIconPicker = true }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        ColorPickerControl(
                            label = "Icon Color",
                            color = iconColor,
                            borderColor = Color(0xFF06B6D4),
                            onColorChange = { iconColor = it }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        SliderControl(
                            label = "Icon Size",
                            value = iconSize,
                            valueRange = 16f..48f,
                            valueDisplay = "${iconSize.toInt()}dp",
                            borderColor = Color(0xFF06B6D4),
                            onValueChange = { iconSize = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Behavior Section
                SectionTitle("BEHAVIOR", Color(0xFFEF4444))
                CustomizationCard(borderColor = Color(0xFFEF4444)) {
                    SwitchControl(
                        label = "Vibrate on click",
                        checked = enableVibration,
                        borderColor = Color(0xFFEF4444),
                        onCheckedChange = { enableVibration = it }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

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