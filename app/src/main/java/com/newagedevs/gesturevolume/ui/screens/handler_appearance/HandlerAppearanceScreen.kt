package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.content.res.Configuration
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.HandlerPresets
import kotlinx.coroutines.launch

/**
 * Where the sheet rests when the screen opens: a little over half, leaving the preview the rest.
 *
 * A fraction rather than a fixed dp, so a short phone gets a proportionally short sheet instead of
 * one that swallows it.
 */
private const val SHEET_PEEK_FRACTION = 0.55f

/**
 * The most of the screen the settings sheet may ever take.
 *
 * Without a cap the sheet expands to whatever its content wants, which for this many sliders is
 * the whole screen — and a settings screen that hides the live preview is the two-screen problem
 * this rework exists to remove. Three quarters still leaves a quarter showing the bar and the
 * wallpaper behind it while any slider is being dragged.
 */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.75f

/** Material's own drag handle: a 4dp indicator inside 22dp of padding, top and bottom. */
private val DRAG_HANDLE_HEIGHT = 48.dp

/**
 * One screen, one preview.
 *
 * This used to be two: a small inline strip at the top of a scrolling settings list, and a
 * separate full-screen "expanded preview" dialog with its own copy of the placement maths and its
 * own floating buttons. Two previews of the same bar is one too many — they disagreed about how
 * big the screen was, only one of them could actually be dragged, and the settings being adjusted
 * were never visible at the same time as the thing they changed.
 *
 * Now the preview is the screen, and the settings live in a sheet that starts partly open over it,
 * so a slider and its effect are on screen together. The chrome is four things and no more:
 *
 * ```
 *  ←  Appearance                    ?   ✓   ⚙
 * ```
 *
 * back · help · apply · settings sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerAppearanceScreen(
    viewModel: MainViewModel = hiltViewModel(),
    presetId: String?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preference = remember { viewModel.preference }
    val scope = rememberCoroutineScope()

    // The stored position is per orientation — see SharedPref.getHandlerPosXFraction — so this
    // screen edits whichever pair matches the way the phone is being held right now.
    val isPortrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

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
                snapToEdge = preference.getHandlerSnapToEdge(),
                positionFraction = preference.getHandlerPosYFraction(isPortrait),
                posXFraction = preference.getHandlerPosXFraction(isPortrait)
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
            initialSnapToEdge = savedState.value.snapToEdge,
            initialPositionFraction = savedState.value.positionFraction,
            initialPosXFraction = savedState.value.posXFraction
        )
    }

    val bgImage = remember { viewModel.getNextBackground() }

    // Resolved in composable scope so it follows a configuration change.
    val appearanceSavedMsg = stringResource(R.string.appearance_saved)

    // Automatically recomputes as nested properties change
    val currentState = state.toState()
    val hasUnsavedChanges = currentState != savedState.value

    // Starts partly open, so a slider and the bar it changes are both on screen. Hidden is
    // allowed — swiping the sheet away is how you reach the bottom of the preview to place the
    // bar there, and the ⚙ button brings it back.
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    // The drag handle sits above this column and counts towards what the sheet covers, so it
    // comes off both budgets rather than being added on top of them.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetContentHeight = screenHeight * SHEET_MAX_HEIGHT_FRACTION - DRAG_HANDLE_HEIGHT
    val sheetPeekHeight = screenHeight * SHEET_PEEK_FRACTION

    LaunchedEffect(presetId) {
        // Values come from HandlerPresets rather than a when-block here, so the cards that offer
        // these presets on the main screen can preview exactly what applying one will do.
        // A preset is an appearance, not a placement. It deliberately leaves gravity and both
        // position fractions alone: the bar is dragged where the user wants it, and picking
        // "Night" to change the colour should not also throw that away.
        HandlerPresets.byId(presetId)?.let { preset ->
            state.width = preset.width
            state.height = preset.height
            state.bgColor = preset.bgColor
            state.bgAlpha = preset.bgAlpha
            state.strokeColor = preset.strokeColor
            state.strokeWidth = preset.strokeWidth
            state.strokeAlpha = preset.strokeAlpha
            state.cornerRadiusAll = preset.cornerRadius
            state.cornerTL = preset.cornerRadius
            state.cornerTR = preset.cornerRadius
            state.cornerBL = preset.cornerRadius
            state.cornerBR = preset.cornerRadius
            state.iconRes = preset.iconRes
            state.iconSize = preset.iconSize
            state.iconColor = preset.iconColor
            state.showIcon = preset.showIcon
            state.vibrate = preset.vibrate
            state.edgeMargin = preset.edgeMargin
        }
    }

    fun saveChanges() {
        // A record of which side the bar is nearer, not a setting: it decides which way the flat
        // edge and the icon face. Where the bar actually sits is the two fractions below.
        preference.setHandlerPosition(if (state.gravity == Gravity.START) "Left" else "Right")
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
        preference.setHandlerSnapToEdge(state.snapToEdge)

        // Position is written only when this screen actually changed it — a preview drag or Reset
        // position. Two reasons. It goes to the per-orientation pair the overlay really reads,
        // which is why dragging the bar in the preview never used to reach the live overlay: the
        // legacy single fraction below is read once, at migration, and moves nothing on its own.
        // And writing it unconditionally would let a colour change undo a drag: the bar is
        // reachable while this screen sits in the background, so the values loaded when it opened
        // can be stale by the time Apply is pressed.
        if (state.positionFraction != savedState.value.positionFraction) {
            preference.setHandlerPosYFraction(isPortrait, state.positionFraction)
            preference.setHandlerPositionFraction(state.positionFraction)
        }
        if (state.posXFraction != savedState.value.posXFraction) {
            preference.setHandlerPosXFraction(isPortrait, state.posXFraction)
        }

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

    if (showHelp) {
        AppearanceHelpDialog(onDismiss = { showHelp = false })
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            // Fixed rather than a maximum. BottomSheetScaffold derives its expanded anchor from
            // the height the sheet reports, and a scrollable child asked for a maximum still
            // reports its full intrinsic height — which for this many sliders is the whole
            // screen, exactly what the cap exists to prevent. Giving the column a height makes
            // the anchor that height, and the scroll happens inside it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetContentHeight)
                    .verticalScroll(rememberScrollState())
                    // The preview runs under the navigation bar, so the sheet is the one thing
                    // here that must not: its last slider would otherwise sit under the gesture
                    // pill.
                    .navigationBarsPadding()
            ) {
                HandlerAppearanceSettingsContent(
                    state = state,
                    onShowIconPicker = { showIconPicker = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) showDiscardDialog = true else onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.help)
                        )
                    }
                    // Always present, so its place in the bar never moves; live only when there
                    // is something to apply, which is also the whole of the answer to "have I
                    // saved this yet?".
                    IconButton(
                        onClick = { saveChanges() },
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.save_changes),
                            tint = if (hasUnsavedChanges) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            }
                        )
                    }
                    // A plain toggle rather than a three-way cycle: the drag handle already owns
                    // "how far open", so this only has to answer "in the way, or not".
                    IconButton(onClick = {
                        scope.launch {
                            if (sheetState.currentValue == SheetValue.Hidden) {
                                sheetState.partialExpand()
                            } else {
                                sheetState.hide()
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.settings_sheet)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { innerPadding ->
        // Only the top inset is consumed, and deliberately so on both counts. The sheet floats
        // over the preview rather than shortening it, because placement is stored as a fraction
        // of the screen — a preview cropped to whatever the sheet leaves behind would put the bar
        // somewhere else entirely once applied. And nothing is subtracted at the bottom either:
        // the live bar is drawn by a window that extends under the navigation bar, so a preview
        // stopping short of it would misreport how low the bar can go.
        HandlerPreviewSurface(
            state = state,
            viewModel = viewModel,
            backgroundImageURL = bgImage,
            onPositionChanged = { state.positionFraction = it },
            onHorizontalPositionChanged = { state.posXFraction = it },
            onGravityChanged = { state.gravity = it },
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
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

/**
 * What the four buttons do, and the one setting the drag gesture depends on.
 *
 * These used to be floating cards pinned over the preview — a "long press to move" chip and a
 * "test mode" card — which covered the wallpaper the preview exists to show and could not be
 * dismissed. Behind the `?` they are available when wanted and absent when not.
 */
@Composable
private fun AppearanceHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.appearance_help_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpEntry(stringResource(R.string.appearance_help_move))
                HelpEntry(stringResource(R.string.appearance_help_test))
                HelpEntry(stringResource(R.string.appearance_help_settings))
                HelpEntry(stringResource(R.string.appearance_help_apply), last = true)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.got_it))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

/**
 * One help entry: the first line is its heading, the rest the explanation.
 *
 * Split here rather than held as two string resources so a translator sees one coherent passage
 * per topic instead of a title stranded from its body.
 */
@Composable
private fun HelpEntry(text: String, last: Boolean = false) {
    val heading = text.substringBefore('\n')
    val body = text.substringAfter('\n', "")
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(6.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {}
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = heading,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (body.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (!last) Spacer(modifier = Modifier.height(14.dp))
    }
}
