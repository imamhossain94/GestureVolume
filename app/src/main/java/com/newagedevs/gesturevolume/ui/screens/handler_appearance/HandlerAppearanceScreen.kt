package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import android.content.res.Configuration
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.HandlerPresets

/**
 * One screen. Preview on top, settings underneath, nothing overlapping.
 *
 * This is the third arrangement of this screen and the first one that is not fighting itself. It
 * began as two screens — an inline strip at the top of a settings list plus a separate full-screen
 * preview dialog with its own copy of the placement maths — which disagreed about how big the
 * screen was and never showed a control and its effect at the same time. That was replaced by a
 * full-bleed preview with the settings in a bottom sheet floating over it, which fixed the
 * disagreement and introduced a new one: the sheet covered the bottom half of the very thing it
 * was editing, so placing the bar low meant swiping the settings away, and adjusting anything
 * meant bringing them back.
 *
 * What settled it was giving up on previewing *placement* here at all. Placement is stored as a
 * fraction of the screen, so any preview smaller than the screen either lies about it or has to be
 * a scale model — and a scale model small enough to leave room for the settings draws a 10dp bar
 * at four dp, which is too small to judge a corner radius on and too small to drag. Placement
 * already has a better home: the live bar, where a long press picks it up and puts it down for
 * real — and the one part of it this screen still owns, which side the bar starts on, is a pair of
 * buttons in the Position section rather than a drag. So [HandlerPreviewSurface] is now a shallow
 * dock that shows the handler at its true size on a wallpaper, and it fits at the top of an
 * ordinary scrolling page.
 *
 * The chrome is two things and no more:
 *
 * ```
 *  ←  Appearance                            ✓
 * ```
 *
 * back · apply. The help button went with the rehearsal it was explaining.
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

    // The stored position is per orientation — see SharedPref.getHandlerPosXFraction — so this
    // screen edits whichever pair matches the way the phone is being held right now.
    val isPortrait = LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE

    var showDiscardDialog by remember { mutableStateOf(false) }
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
                snapToEdge = preference.getHandlerSnapToEdge(),
                positionFraction = preference.getHandlerPosYFraction(isPortrait),
                posXFraction = preference.getHandlerPosXFraction(isPortrait)
            )
        )
    }

    // One wallpaper per visit, not per recomposition: a backdrop that reshuffled while a colour
    // was being chosen would be worse than no backdrop at all.
    val bgImage = remember { viewModel.getNextBackground() }

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

    // Resolved in composable scope so it follows a configuration change.
    val appearanceSavedMsg = stringResource(R.string.appearance_saved)

    // Automatically recomputes as nested properties change
    val currentState = state.toState()
    val hasUnsavedChanges = currentState != savedState.value


    LaunchedEffect(presetId) {
        // Values come from HandlerPresets rather than a when-block here, so the cards that offer
        // these presets on the main screen can preview exactly what applying one will do.
        // A preset is an appearance, not a placement. It deliberately leaves gravity and both
        // position fractions alone: the bar is dragged where the user wants it, and picking
        // "Night" to change the colour should not also throw that away.
        HandlerPresets.byId(presetId)?.let { state.applyPreset(it) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    // Always present, so its place in the bar never moves; live only when there
                    // is something to apply, which is also the whole of the answer to "have I
                    // saved this yet?".
                    // The tick is the screen's one piece of feedback that something is pending,
                    // so it is worth a moment of motion. Scale through graphicsLayer rather than a
                    // size change: the icon sits in a top bar with other buttons beside it, and a
                    // bouncy spring on a real dimension would shove them sideways. `enabled` still
                    // gates the click, so an animating tick is never a mis-tap.
                    val tickScale by animateFloatAsState(
                        targetValue = if (hasUnsavedChanges) 1f else 0.85f,
                        animationSpec = AppearanceMotion.Pop,
                        label = "applyTickScale",
                    )
                    val tickTint by animateColorAsState(
                        targetValue = if (hasUnsavedChanges) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        },
                        animationSpec = AppearanceMotion.Tint,
                        label = "applyTickTint",
                    )
                    IconButton(
                        onClick = { saveChanges() },
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.save_changes),
                            tint = tickTint,
                            modifier = Modifier.graphicsLayer {
                                scaleX = tickScale
                                scaleY = tickScale
                            },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // The dock. Fixed, and outside the scroll on purpose: the point of the rework is
            // that a control and the thing it changes are on screen together, and a preview that
            // scrolls away with the list is only the old two-screen problem with extra steps.
            // It sizes itself — 4:3 of whatever width it is given — so there is no height
            // fraction here to keep in step with it.
            // Above the preview, matching the Quick panel's screen. The dock shows what the bar
            // will look like and cannot show what it will do, so the one thing worth saying here
            // is where the rest of it lives.
            Text(
                text = stringResource(R.string.appearance_preview_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
            HandlerPreviewSurface(
                state = state,
                backgroundImageURL = bgImage,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    // The last control would otherwise sit under the gesture pill.
                    .navigationBarsPadding()
                    .padding(top = 16.dp)
            ) {
                HandlerAppearanceSettingsContent(
                    state = state,
                    onShowIconPicker = { showIconPicker = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
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
