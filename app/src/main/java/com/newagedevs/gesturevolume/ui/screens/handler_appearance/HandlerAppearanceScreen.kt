package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import com.newagedevs.gesturevolume.ui.components.screenWash
import android.content.res.Configuration
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.newagedevs.gesturevolume.ui.motion.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.newagedevs.gesturevolume.ui.motion.IconButton
import androidx.compose.material3.MaterialTheme
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
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
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.HandlerPresets
import com.newagedevs.gesturevolume.utils.AdPacing
import com.newagedevs.gesturevolume.ui.components.PreviewSettingsLayout
import com.newagedevs.gesturevolume.ui.components.DemoGesture
import com.newagedevs.gesturevolume.ui.components.GestureDemoOverlay
import com.newagedevs.gesturevolume.ui.components.rememberGestureDemoState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay

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
 * real — and, for the place the bar starts in, [HandlerPlacementEditor], which gives the bar the
 * whole screen rather than a model of it, opened from the Position section. So
 * [HandlerPreviewSurface] is now a shallow dock that shows the handler at its true size on a
 * wallpaper, and it fits at the top of an ordinary scrolling page.
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
    var showPlacement by remember { mutableStateOf(false) }

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
                shape = preference.getHandlerShape(),
                flare = preference.getHandlerShapeFlare(),
                iconRes = preference.getHandlerIconRes(),
                iconSize = preference.getHandlerIconSize(),
                iconColor = preference.getHandlerIconColor(),
                showIcon = preference.getHandlerShowIcon(),
                vibrate = preference.getHandlerVibrateOnClick(),
                edgeMargin = preference.getHandlerEdgeMarginDp(),
                snapToEdge = preference.getHandlerSnapToEdge(),
                positionFraction = preference.getHandlerPosYFraction(isPortrait),
                posXFraction = preference.getHandlerPosXFraction(isPortrait),
                samePosition = preference.getHandlerSamePositionBothOrientations(),
                otherPositionFraction = preference.getHandlerPosYFraction(!isPortrait),
                otherPosXFraction = preference.getHandlerPosXFraction(!isPortrait),
                dynamicPosition = preference.getHandlerDynamicPosition(),
                showVolumePercent = preference.getShowVolumePercent(),
            )
        )
    }

    // A finger acting out what the bar is for — a tap, a swipe up, a swipe down — twice on arrival
    // and on request after that. The dock can show what the bar looks like but not what it does,
    // and this is the one screen everyone sees before the bar is any use to them.
    val gestureDemo = rememberGestureDemoState(APPEARANCE_DEMO_STEPS)

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
            initialShape = savedState.value.shape,
            initialFlare = savedState.value.flare,
            initialIconRes = savedState.value.iconRes,
            initialIconSize = savedState.value.iconSize,
            initialIconColor = Color(savedState.value.iconColor),
            initialShowIcon = savedState.value.showIcon,
            initialVibrate = savedState.value.vibrate,
            initialEdgeMargin = savedState.value.edgeMargin,
            initialSnapToEdge = savedState.value.snapToEdge,
            initialPositionFraction = savedState.value.positionFraction,
            initialPosXFraction = savedState.value.posXFraction,
            initialSamePosition = savedState.value.samePosition,
            initialOtherPositionFraction = savedState.value.otherPositionFraction,
            initialOtherPosXFraction = savedState.value.otherPosXFraction,
            initialDynamicPosition = savedState.value.dynamicPosition,
            initialShowVolumePercent = savedState.value.showVolumePercent,
        )
    }

    // What the finger's gestures are set to do, so the preview acts out the user's own rather than a
    // generic volume bar: the Actions screen's, or those of a preset chosen here, which Apply saves
    // along with its look.
    val previewGestures = remember(state.appliedPresetId) {
        val preset = HandlerPresets.byId(state.appliedPresetId)?.behaviour
        PreviewGestures(
            tap = preset?.singleTap ?: preference.getHandlerSingleTapAction(),
            swipeUp = preset?.swipeUp ?: preference.getHandlerSwipeUpAction(),
            swipeDown = preset?.swipeDown ?: preference.getHandlerSwipeDownAction(),
            stepPercent = preference.getSwipeStepPercent(),
        )
    }
    val previewEffects = remember(previewGestures) { PreviewEffects(gestureDemo, previewGestures) }

    // A preset chosen here can change what the gestures do, so the finger shows the new ones at
    // once — unless it is already on its way, in which case it is showing them.
    var shownGestures by remember { mutableStateOf(previewGestures) }
    LaunchedEffect(previewGestures) {
        if (previewGestures == shownGestures) return@LaunchedEffect
        shownGestures = previewGestures
        if (!gestureDemo.playing) gestureDemo.replay()
    }

    // Switched on, the number shows on the bar for a moment, so the switch is seen to do something:
    // the bar only ever shows it mid-swipe, which is not when anyone is looking at a settings page.
    var percentWasOn by remember { mutableStateOf(state.showVolumePercent) }
    LaunchedEffect(state.showVolumePercent) {
        val turnedOn = state.showVolumePercent && !percentWasOn
        percentWasOn = state.showVolumePercent
        if (!turnedOn || gestureDemo.playing) return@LaunchedEffect
        try {
            previewEffects.flashPercent = true
            delay(PERCENT_FLASH_MS)
        } finally {
            previewEffects.flashPercent = false
        }
    }

    // Resolved in composable scope so it follows a configuration change.
    val appearanceSavedMsg = stringResource(R.string.appearance_saved)

    // Automatically recomputes as nested properties change
    val currentState = state.toState()
    val hasUnsavedChanges = currentState != savedState.value


    LaunchedEffect(presetId) {
        // Values come from HandlerPresets rather than a when-block here, so the cards that offer
        // these presets on the main screen can preview exactly what applying one will do. Applied
        // to the holder only, so the deep link is still an offer: Apply writes the preset, look
        // and behaviour both, and Back can still discard it. An id this build does not know — a
        // retired preset, say — applies nothing.
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
        preference.setHandlerShape(state.shape)
        preference.setHandlerShapeFlare(state.flare)
        preference.setHandlerIconRes(state.iconRes)
        preference.setHandlerIconSize(state.iconSize)
        preference.setHandlerIconColor(state.iconColor.toArgb())
        preference.setHandlerShowIcon(state.showIcon)
        preference.setHandlerVibrateOnClick(state.vibrate)
        preference.setHandlerEdgeMarginDp(state.edgeMargin)
        preference.setHandlerSnapToEdge(state.snapToEdge)
        preference.setShowVolumePercent(state.showVolumePercent)

        // Position is written only when this screen actually changed it — a preview drag or Reset
        // position. Two reasons. It goes to the per-orientation pair the overlay really reads,
        // which is why dragging the bar in the preview never used to reach the live overlay: the
        // legacy single fraction below is read once, at migration, and moves nothing on its own.
        // And writing it unconditionally would let a colour change undo a drag: the bar is
        // reachable while this screen sits in the background, so the values loaded when it opened
        // can be stale by the time Apply is pressed.
        //
        // The switch first, because it decides which keys the writes below land in: with one
        // position for both orientations, landscape's pair is portrait's. The other orientation is
        // written before this one, so where the two keys are the same key, the pair the user was
        // looking at is the one that stays.
        val saved = savedState.value
        // A preset applied in this session. It sets the position in both orientations, so the
        // position is written as if the switches had changed: the values this screen loaded can be
        // stale, and a preset landing on the height already stored must still reach both keys.
        val appliedPreset = HandlerPresets.byId(state.appliedPresetId)
        // Dynamic position before anything else: while it is on, both orientations read and write
        // the portrait pair, which is the side and the place along it that the bar carries round.
        val dynamicChanged = state.dynamicPosition != saved.dynamicPosition
        preference.setHandlerDynamicPosition(state.dynamicPosition)
        val sameChanged = state.samePosition != saved.samePosition || dynamicChanged || appliedPreset != null
        preference.setHandlerSamePositionBothOrientations(state.samePosition)
        // Written whenever the switch changed as well: turning it off hands landscape its own keys
        // back, and they must hold what the sliders showed rather than whatever was there before.
        if (state.dynamicPosition) {
            // The upright place is portrait's pair, whichever way the phone is held right now.
            val uprightY = if (isPortrait) state.positionFraction else state.otherPositionFraction
            preference.setHandlerPosYFraction(true, uprightY)
            preference.setHandlerPosXFraction(true, if (state.gravity == Gravity.START) 0f else 1f)
        } else if (!state.samePosition) {
            if (sameChanged || state.otherPositionFraction != saved.otherPositionFraction) {
                preference.setHandlerPosYFraction(!isPortrait, state.otherPositionFraction)
            }
            if (sameChanged || state.otherPosXFraction != saved.otherPosXFraction) {
                preference.setHandlerPosXFraction(!isPortrait, state.otherPosXFraction)
            }
        }
        if (!state.dynamicPosition && (sameChanged || state.positionFraction != saved.positionFraction)) {
            preference.setHandlerPosYFraction(isPortrait, state.positionFraction)
            preference.setHandlerPositionFraction(state.positionFraction)
        }
        if (!state.dynamicPosition && (sameChanged || state.posXFraction != saved.posXFraction)) {
            preference.setHandlerPosXFraction(isPortrait, state.posXFraction)
        }

        if (state.cornerTL == state.cornerTR && state.cornerTR == state.cornerBL && state.cornerBL == state.cornerBR) {
            preference.setAllCornerRadii(state.cornerTL)
        }

        // The rest of the preset — actions, Quick panel, menu, animation — only when one was
        // applied since the screen opened or was last saved. Saving a colour change on its own
        // must not put back actions the user chose on the Actions screen.
        if (appliedPreset != null) {
            preference.writePresetBehaviour(appliedPreset.behaviour)
            // The key filter is asked for only while something needs it, and the volume keys may
            // just have been set to Instant.
            OverlayRuntime.accessibilityService?.applyEventSubscription()
        }
        state.appliedPresetId = null

        savedState.value = state.toState()
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

    // A Box so the placement editor can cover the whole screen, top bar and all, from here: it
    // edits this screen's draft, so it lives with it rather than on a route of its own.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // The walkthrough's cool wash at the top, behind the transparent bar: see screenWash.
            modifier = Modifier.screenWash(),
            containerColor = Color.Transparent,
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
                            onClick = {
                                saveChanges()
                                // A break point: the user finished and saved, and stays on this screen.
                                // Only here, not in the discard dialog's Apply, which Back opens and
                                // which leaves the screen.
                                viewModel.onHappyMoment(AdPacing.Trigger.SETTINGS_APPLIED)
                            },
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
                    // Transparent, as every other screen's: a surface-coloured bar stood out as a band
                    // against the page in the dark theme.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { innerPadding ->
            // The dock pinned, and outside the scroll on purpose: the point of the rework is that a
            // control and the thing it changes are on screen together. Above the settings upright,
            // beside them on its side; see PreviewSettingsLayout.
            PreviewSettingsLayout(
                contentPadding = innerPadding,
                // On the preview's card, as the Quick panel's screen has it. The dock shows what the bar
                // will look like and cannot show what it will do, so the one thing worth saying here
                // is where the rest of it lives.
                hint = stringResource(R.string.appearance_preview_hint),
                preview = { modifier, fillHeight ->
                    val barAtStart = state.gravity == Gravity.START
                    HandlerPreviewSurface(
                        state = state,
                        fillHeight = fillHeight,
                        modifier = modifier,
                        barLabel = { previewEffects.barLabel(state.showVolumePercent) },
                        // The hand reaches in over the frame, as the walkthrough's does.
                        overGlass = {
                            GestureDemoOverlay(
                                state = gestureDemo,
                                barAtStart = barAtStart,
                                barInset = (state.edgeMargin.toFloat() + state.width.toFloat() / 2f).dp,
                                modifier = Modifier.matchParentSize(),
                            )
                        },
                        // The demo it plays, and under the phone while it plays, what each gesture is set to do.
                        demo = gestureDemo,
                        caption = { gesture -> GestureCaption(gesture, previewEffects.actionOf(gesture)) },
                    ) {
                        // On the phone's screen with the bar, so what is placed against its edge
                        // lines up with it. What the finger's taps and swipes do, with the user's
                        // own settings: under the finger, over the bar.
                        HandlerPreviewEffects(
                            effects = previewEffects,
                            barAtStart = barAtStart,
                            barReach = (state.edgeMargin.toFloat() + state.width.toFloat()).dp,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                },
            ) {
                HandlerAppearanceSettingsContent(
                    state = state,
                    isPortrait = isPortrait,
                    onShowIconPicker = { showIconPicker = true },
                    onSetInitialPosition = { showPlacement = true },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        AnimatedVisibility(
            visible = showPlacement,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            HandlerPlacementEditor(
                state = state,
                isPortrait = isPortrait,
                onDismiss = { showPlacement = false },
            )
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

/** What the bar is for, in the order people meet it: a tap, then a swipe each way. */
private val APPEARANCE_DEMO_STEPS = listOf(DemoGesture.TAP, DemoGesture.SWIPE_UP, DemoGesture.SWIPE_DOWN)

/** How long the number stays on the preview's bar when the percentage is switched on. */
private const val PERCENT_FLASH_MS = 1600L
