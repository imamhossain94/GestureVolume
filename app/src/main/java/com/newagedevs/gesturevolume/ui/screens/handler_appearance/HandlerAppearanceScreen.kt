package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.ui.view.HandlerView
import androidx.compose.ui.res.stringResource

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
    var showIconPicker by remember { mutableStateOf(false) }

    val savedState = remember {
        mutableStateOf(
            AppearanceState(
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
                edgeMargin = preference.getHandlerEdgeMarginDp(),
                positionFraction = preference.getHandlerPositionFraction()
            )
        )
    }

    val state = remember {
        AppearanceStateHolder(
            initialGravity = savedState.value.gravity,
            initialWidth = savedState.value.width,
            initialHeight = savedState.value.height,
            initialBgColor = Color(savedState.value.bgColor),
            initialBgAlpha = savedState.value.bgAlpha,
            initialStrokeColor = Color(savedState.value.strokeColor),
            initialStrokeWidth = savedState.value.strokeWidth,
            initialStrokeAlpha = savedState.value.strokeAlpha,
            initialCornerTL = savedState.value.cornerTL,
            initialCornerTR = savedState.value.cornerTR,
            initialCornerBL = savedState.value.cornerBL,
            initialCornerBR = savedState.value.cornerBR,
            initialIconRes = savedState.value.iconRes,
            initialIconSize = savedState.value.iconSize,
            initialIconColor = Color(savedState.value.iconColor),
            initialShowIcon = savedState.value.showIcon,
            initialVibrate = savedState.value.vibrate,
            initialEdgeMargin = savedState.value.edgeMargin,
            initialPositionFraction = savedState.value.positionFraction
        )
    }

    var bgImage by remember { mutableStateOf(viewModel.getNextBackground()) }

    // Resolved in composable scope so it follows configuration changes.
    val appearanceSavedMsg = stringResource(R.string.appearance_saved)
    
    // Automatically recomputes as nested properties change
    val currentState = state.toState()
    val hasUnsavedChanges = currentState != savedState.value

    LaunchedEffect(presetId) {
        if (presetId != null) {
            when (presetId) {
                "Default" -> {
                    state.gravity = Gravity.END
                    state.width = 30f
                    state.height = 100f
                    state.bgColor = Color.White
                    state.bgAlpha = 50
                    state.strokeColor = Color.White
                    state.strokeWidth = 1f
                    state.strokeAlpha = 200
                    state.cornerRadiusAll = 15f
                    state.cornerTL = 15f
                    state.cornerTR = 15f
                    state.cornerBL = 15f
                    state.cornerBR = 15f
                    state.iconRes = R.drawable.ic_vol_increase
                    state.iconSize = 18f
                    state.iconColor = Color.White
                    state.showIcon = false
                    state.vibrate = false
                    state.edgeMargin = 0f
                    state.positionFraction = 0.5f
                }
                "Minimal" -> {
                    state.gravity = Gravity.END
                    state.width = 10f
                    state.height = 100f
                    state.bgColor = Color.White
                    state.bgAlpha = 50
                    state.strokeColor = Color.White
                    state.strokeWidth = 1f
                    state.strokeAlpha = 200
                    state.cornerRadiusAll = 5f
                    state.cornerTL = 5f
                    state.cornerTR = 5f
                    state.cornerBL = 5f
                    state.cornerBR = 5f
                    state.iconRes = R.drawable.ic_vol_increase
                    state.iconSize = 18f
                    state.iconColor = Color.White
                    state.showIcon = false
                    state.vibrate = false
                    state.edgeMargin = 0f
                    state.positionFraction = 0.5f
                }
                "Bold" -> {
                    state.gravity = Gravity.END
                    state.width = 40f
                    state.height = 100f
                    state.bgColor = Color.White
                    state.bgAlpha = 50
                    state.strokeColor = Color.White
                    state.strokeWidth = 1f
                    state.strokeAlpha = 255
                    state.cornerRadiusAll = 15f
                    state.cornerTL = 15f
                    state.cornerTR = 15f
                    state.cornerBL = 15f
                    state.cornerBR = 15f
                    state.iconRes = R.drawable.ic_move
                    state.iconSize = 32f
                    state.iconColor = Color.White
                    state.showIcon = true
                    state.vibrate = true
                    state.edgeMargin = 0f
                    state.positionFraction = 0.5f
                }
                "Night" -> {
                    state.gravity = Gravity.END
                    state.width = 30f
                    state.height = 100f
                    state.bgColor = Color(0xFF1F2937)
                    state.bgAlpha = 230
                    state.strokeColor = Color(0xFF374151)
                    state.strokeWidth = 1f
                    state.strokeAlpha = 200
                    state.cornerRadiusAll = 15f
                    state.cornerTL = 15f
                    state.cornerTR = 15f
                    state.cornerBL = 15f
                    state.cornerBR = 15f
                    state.iconRes = R.drawable.ic_vol_increase
                    state.iconSize = 22f
                    state.iconColor = Color(0xFF9CA3AF)
                    state.showIcon = true
                    state.vibrate = true
                    state.edgeMargin = 0f
                    state.positionFraction = 0.5f
                }
                "Ghost" -> {
                    state.gravity = Gravity.END
                    state.width = 20f
                    state.height = 100f
                    state.bgColor = Color.White
                    state.bgAlpha = 5
                    state.strokeColor = Color.White
                    state.strokeWidth = 0.5f
                    state.strokeAlpha = 5
                    state.cornerRadiusAll = 12f
                    state.cornerTL = 12f
                    state.cornerTR = 12f
                    state.cornerBL = 12f
                    state.cornerBR = 12f
                    state.iconRes = R.drawable.ic_vol_increase
                    state.iconSize = 16f
                    state.iconColor = Color.White
                    state.showIcon = false
                    state.vibrate = false
                    state.edgeMargin = 0f
                    state.positionFraction = 0.5f
                }
            }
        }
    }

    // Apply changes to HandlerView instantly (Draft rendering)
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
            handler.setEdgeMarginDp(currentState.edgeMargin)
        }
    }

    fun saveChanges() {
        val newSide = if (state.gravity == Gravity.START) "Left" else "Right"
        // Choosing a side, or changing how far in from it the bar sits, is an explicit instruction
        // about where the bar goes horizontally — so it overrides wherever the bar was last
        // dragged. Only then, though: an unrelated colour change must not yank the bar back to an
        // edge the user had deliberately moved it away from.
        if (newSide != preference.getHandlerPosition() ||
            state.edgeMargin != preference.getHandlerEdgeMarginDp()
        ) {
            preference.clearHandlerPosXFraction()
        }
        preference.setHandlerPosition(newSide)
        preference.setHandlerWidthDp(state.width)
        preference.setHandlerHeightDp(state.height)
        preference.setHandlerColor(state.bgColor.toArgb())
        preference.setHandlerBackgroundAlpha(state.bgAlpha)
        preference.setHandlerStrokeColor(state.strokeColor.toArgb())
        preference.setHandlerStrokeWidth(state.strokeWidth)
        preference.setHandlerStrokeAlpha(state.strokeAlpha)
        preference.setHandlerCornerRadiusTL(state.cornerTL)
        preference.setHandlerCornerRadiusTR(state.cornerTR)
        preference.setHandlerCornerRadiusBL(state.cornerBL)
        preference.setHandlerCornerRadiusBR(state.cornerBR)
        preference.setHandlerIconRes(state.iconRes)
        preference.setHandlerIconSize(state.iconSize)
        preference.setHandlerIconColor(state.iconColor.toArgb())
        preference.setHandlerShowIcon(state.showIcon)
        preference.setHandlerVibrateOnClick(state.vibrate)
        preference.setHandlerEdgeMarginDp(state.edgeMargin)
        preference.setHandlerPositionFraction(state.positionFraction)

        if (state.cornerTL == state.cornerTR && state.cornerTR == state.cornerBL && state.cornerBL == state.cornerBR) {
            preference.setAllCornerRadii(state.cornerTL)
        }

        savedState.value = currentState
        viewModel.sendUpdateToService(context)
        viewModel.showToast(appearanceSavedMsg)
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showDiscardDialog = true
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.unsaved_changes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.you_have_unsaved_changes_do_you_want_to_apply_them_before_leaving),
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
                    Text(stringResource(R.string.apply))
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
                    Text(stringResource(R.string.discard))
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
                    text = stringResource(R.string.apply_changes),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.are_you_sure_you_want_to_apply_these_appearance_settings_to_your_active_handler),
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
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSaveDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.handler_appearance)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (hasUnsavedChanges) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save_changes), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OutlinedButton(
                        onClick = { isExpandedPreview = true },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.preview),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.preview), fontSize = 13.sp)
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
                handlerGravity = currentState.gravity,
                handlerWidth = currentState.width,
                handlerHeight = currentState.height,
                backgroundColor = Color(currentState.bgColor),
                backgroundAlpha = currentState.bgAlpha,
                strokeColor = Color(currentState.strokeColor),
                strokeWidth = currentState.strokeWidth,
                strokeAlpha = currentState.strokeAlpha,
                cornerRadiusTL = currentState.cornerTL,
                cornerRadiusTR = currentState.cornerTR,
                cornerRadiusBL = currentState.cornerBL,
                cornerRadiusBR = currentState.cornerBR,
                iconRes = currentState.iconRes,
                iconSize = currentState.iconSize,
                iconColor = Color(currentState.iconColor),
                showIcon = currentState.showIcon,
                enableVibration = currentState.vibrate,
                edgeMargin = currentState.edgeMargin,
                backgroundImageURL = bgImage,
                onHandlerCreated = { handlerViewRef = it }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                HandlerAppearanceSettingsContent(
                    state = state,
                    onShowIconPicker = { showIconPicker = true },
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    if (isExpandedPreview) {
        ExpandedPreviewDialog(
            state = state,
            viewModel = viewModel,
            backgroundImageURL = bgImage,
            onShowIconPicker = { showIconPicker = true },
            onPositionChanged = { state.positionFraction = it },
            onGravityChanged = { state.gravity = it },
            onDismiss = { isExpandedPreview = false }
        )
    }

    if (showIconPicker) {
        IconPickerDialog(
            selectedIconRes = state.iconRes,
            onIconSelected = {
                state.iconRes = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }
}