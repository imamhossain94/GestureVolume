package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import com.newagedevs.gesturevolume.ui.screens.handler_action.segmentShape
import androidx.compose.material.icons.filled.PowerSettingsNew
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.newagedevs.gesturevolume.ui.motion.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.helper.PrivacyChoices
import com.newagedevs.gesturevolume.ui.components.TourCard
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToAppearance: (String?) -> Unit,
    onNavigateToActions: () -> Unit,
    onNavigateToPermissions: (PermissionNeeds.Permission?) -> Unit,
    onNavigateToDeck: () -> Unit,
    onNavigateToQuickPanel: () -> Unit,
    onNavigateToLongPressMenu: () -> Unit,
    onNavigateToFaq: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
    onNavigateToVisibility: () -> Unit,
    onNavigateToWhatsNew: () -> Unit = {},
    onNavigateToTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Resolved in composable scope so it follows a locale change.
    val handlerShownMsg = stringResource(R.string.handler_shown_toast)

    // Wide enough for the cards to sit in two columns rather than one long list: a landscape phone,
    // or a tablet either way up.
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_DP

    // On every return, not only on first composition. The bar can be hidden from its own
    // long-press menu or from the notification while this screen sits in the background, and the
    // "Handler is hidden" card is only useful if it is there when the user comes back looking for
    // their missing bar.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
                viewModel.onEvent(MainEvent.SyncServiceState(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            viewModel.onBackPressed(context) {
                (context as? Activity)?.finish()
            }
        }
    }

    // Recomputed each time the drawer opens or closes, so an ad SDK that finished initialising
    // after this screen was drawn still gets its entry.
    val showPrivacyChoices = remember(state.isProActivated, drawerState.currentValue) {
        PrivacyChoices.isAvailable(context, state.isProActivated)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                isProActivated = state.isProActivated,
                showPrivacyChoices = showPrivacyChoices,
                whatsNewUnseen = state.hasUnseenWhatsNew,
                onMenuItemClick = { option ->
                    viewModel.handleMenuOption(option, context)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                    actions = {
                        // What's new, with a dot after an update until it has been opened.
                        IconButton(onClick = onNavigateToWhatsNew) {
                            BadgedBox(badge = { if (state.hasUnseenWhatsNew) Badge() }) {
                                Icon(
                                    imageVector = Icons.Outlined.NewReleases,
                                    contentDescription = stringResource(R.string.whats_new_title),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        // The way to Pro, where the close button used to be. In the app's own
                        // colour: Pro is part of this app, not an advert laid over it.
                        IconButton(onClick = onNavigateToUpgrade) {
                            Icon(
                                painter = painterResource(R.drawable.ic_crown_2),
                                contentDescription = stringResource(R.string.upgrade_to_pro),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .padding(horizontal = 16.dp)
                ) {
                    // The tour, offered once after an update: first on the page, where an update's
                    // user looks when the app opens. Answered either way, it goes for good.
                    AnimatedVisibility(
                        visible = state.showTourOffer,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        TourCard(
                            onTakeTour = {
                                viewModel.dismissTourOffer()
                                onNavigateToTour()
                            },
                            onWhatsNew = {
                                viewModel.dismissTourOffer()
                                onNavigateToWhatsNew()
                            },
                            onDismiss = { viewModel.dismissTourOffer() },
                            modifier = Modifier.padding(bottom = GAP),
                        )
                    }

                    // The route back from "Hide handler", before everything else: while it shows,
                    // the bar the rest of the screen is about is not on screen.
                    if (state.isRunning && state.isHandlerHidden) {
                        HandlerHiddenCard(
                            onShowHandler = {
                                viewModel.onEvent(MainEvent.SetHandlerHidden(false, context))
                                viewModel.showToast(handlerShownMsg)
                            }
                        )
                        Spacer(modifier = Modifier.height(GAP))
                    }

                    // What the view model re-read on the last return here. Only the Permissions row
                    // warns: a badge on every row whose screen held a waiting setting put the same
                    // warning in three or four places at once, and read as each of them being broken.
                    val permissionNeeds = state.permissionNeeds
                    // The count, the chips and the reason all come from the same list the notes on
                    // the other screens read, so the row and the screens can never disagree.
                    val firstNeed = permissionNeeds.first
                    val featureCount = permissionNeeds.all.map { it.feature }.distinct().size
                    val permissionReason = firstNeed?.let { need ->
                        val name = stringResource(need.feature.labelRes)
                        if (featureCount > 1) {
                            stringResource(R.string.permission_needed_by_more, name, featureCount - 1)
                        } else {
                            stringResource(R.string.permission_needed_by, name)
                        }
                    }

                    // The screen as the Actions and Visibility screens are: each group's rows joined
                    // into one card. First the bar itself, with no heading over it — the switch says
                    // what the group is — then how it looks, what it does, and what it still needs.
                    val barGroup: @Composable (Modifier) -> Unit = { groupModifier ->
                        Column(modifier = groupModifier) {
                            HomeSegments {
                                HomeToggleRow(
                                    icon = Icons.Filled.PowerSettingsNew,
                                    title = stringResource(if (state.isRunning) R.string.service_active else R.string.service_inactive),
                                    // Off, where to begin; on, where it is.
                                    summary = stringResource(if (state.isRunning) R.string.home_service_on_desc else R.string.home_service_off_desc),
                                    checked = state.isRunning,
                                    shape = segmentShape(0, 4),
                                    onCheckedChange = { viewModel.onEvent(MainEvent.ToggleService(it, context)) },
                                    highlight = true,
                                )
                                HomeLinkRow(
                                    icon = { HomeIconTile(painterResource(R.drawable.ic_color_palette)) },
                                    title = stringResource(R.string.appearance),
                                    summary = stringResource(R.string.appearance_desc),
                                    shape = segmentShape(1, 4),
                                    onClick = { onNavigateToAppearance(null) },
                                )
                                HomeLinkRow(
                                    icon = { HomeIconTile(painterResource(R.drawable.ic_app_open)) },
                                    title = stringResource(R.string.actions),
                                    summary = stringResource(R.string.actions_desc),
                                    shape = segmentShape(2, 4),
                                    onClick = onNavigateToActions,
                                )
                                PermissionsStatusCard(
                                    hasOverlayPermission = state.hasOverlayPermission,
                                    missingPermissionCount = permissionNeeds.missingCount,
                                    overlay = if (permissionNeeds.overlayMissing) {
                                        PermissionChipState.MISSING
                                    } else {
                                        PermissionChipState.GRANTED
                                    },
                                    accessibility = when {
                                        state.isAccessibilityEnabled -> PermissionChipState.GRANTED
                                        permissionNeeds.accessibilityMissing -> PermissionChipState.MISSING
                                        else -> PermissionChipState.OPTIONAL
                                    },
                                    writeSettings = when {
                                        state.hasWriteSettingsPermission -> PermissionChipState.GRANTED
                                        permissionNeeds.writeSettingsMissing -> PermissionChipState.MISSING
                                        else -> PermissionChipState.OPTIONAL
                                    },
                                    otherMissing = (permissionNeeds.missingCount - listOf(
                                        permissionNeeds.overlayMissing,
                                        permissionNeeds.accessibilityMissing,
                                        permissionNeeds.writeSettingsMissing,
                                    ).count { it }).coerceAtLeast(0),
                                    reason = permissionReason,
                                    shape = segmentShape(3, 4),
                                    // Straight to the first thing to fix, flashed there so it is found at once.
                                    onClick = { onNavigateToPermissions(firstNeed?.permission) }
                                )
                            }
                        }
                    }
                    // Then the parts of an advanced setup, folded away for a regular user. See
                    // AdvancedFeaturesGroup. The Deck and the Quick slider together, the bar's two
                    // panels; the long-press menu and Visibility together, the settings about the
                    // bar itself.
                    // Whether the native ad has loaded: until then its slot in the group takes no room.
                    var adShown by remember { mutableStateOf(false) }
                    val advancedGroup: @Composable (Modifier) -> Unit = { groupModifier ->
                        AdvancedFeaturesGroup(
                            userMode = state.userMode,
                            onChangeMode = { viewModel.chooseUserMode(it, context) },
                            modifier = groupModifier,
                            rows = listOf(
                                { shape ->
                                    HomeLinkRow(
                                        icon = { HomeIconTile(painterResource(R.drawable.ic_layer)) },
                                        title = stringResource(R.string.deck_title),
                                        summary = stringResource(R.string.deck_card_subtitle),
                                        shape = shape,
                                        onClick = onNavigateToDeck,
                                    )
                                },
                                { shape ->
                                    HomeLinkRow(
                                        icon = { HomeIconTile(painterResource(R.drawable.ic_brightness_up)) },
                                        title = stringResource(R.string.quick_slider_title),
                                        summary = stringResource(R.string.quick_panel_card_subtitle),
                                        shape = shape,
                                        onClick = onNavigateToQuickPanel,
                                    )
                                },
                                { shape ->
                                    HomeLinkRow(
                                        icon = { HomeIconTile(painterResource(R.drawable.ic_move)) },
                                        title = stringResource(R.string.context_menu_title),
                                        summary = stringResource(R.string.long_press_menu_card_subtitle),
                                        shape = shape,
                                        onClick = onNavigateToLongPressMenu,
                                    )
                                },
                                { shape ->
                                    HomeLinkRow(
                                        icon = { HomeIconTile(painterResource(R.drawable.ic_visibility_hide)) },
                                        title = stringResource(R.string.visibility_title),
                                        summary = stringResource(R.string.visibility_card_subtitle),
                                        shape = shape,
                                        onClick = onNavigateToVisibility,
                                    )
                                },
                            ),
                            // The native ad, as a row of the group: under the mode switch, where it
                            // sits among the rows rather than on top of them. Its surface is the
                            // rows'; what is inside takes the rows' colours too — see
                            // ApplovinAdsManager.styleNativeAdView.
                            ad = if (!state.isProActivated && !com.newagedevs.gesturevolume.BuildConfig.ADS_DISABLED) {
                                { shape ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    ) {
                                        viewModel.adsManager?.NativeAdWidget(
                                            modifier = Modifier.wrapContentHeight(),
                                            onShownChange = { adShown = it },
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            adShown = adShown,
                        )
                    }

                    // Last, the presets, as a list like the advanced features.
                    val presetsGroup: @Composable (Modifier) -> Unit = { groupModifier ->
                        Column(modifier = groupModifier) {
                            HomeHeading(
                                title = stringResource(R.string.home_presets_title),
                                hint = stringResource(R.string.home_presets_desc),
                            )
                            PresetCardsGrid(
                                viewModel = viewModel,
                                context = context,
                                onNavigateToAppearance = { presetId ->
                                    scope.launch { drawerState.close() }
                                    onNavigateToAppearance(presetId)
                                }
                            )
                        }
                    }

                    if (wide) {
                        // Two columns on a wide screen: the bar on its own on the left, where the
                        // switch is always in reach, and everything else on the right.
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            barGroup(Modifier.weight(1f))
                            Column(modifier = Modifier.weight(1f)) {
                                advancedGroup(Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(24.dp))
                                presetsGroup(Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        barGroup(Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(24.dp))
                        advancedGroup(Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(24.dp))
                        presetsGroup(Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private val GAP: Dp = 12.dp

/** The widest the home screen's column grows before it is centred instead. */
private val CONTENT_MAX_WIDTH: Dp = 920.dp

/** From this width the groups sit in two columns. */
private const val WIDE_LAYOUT_DP = 600
