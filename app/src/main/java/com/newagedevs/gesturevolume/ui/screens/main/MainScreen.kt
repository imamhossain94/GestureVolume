package com.newagedevs.gesturevolume.ui.screens.main

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
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
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
                    // What the view model re-read on the last return here. Only the Permissions card
                    // warns: a badge on every card whose screen held a waiting setting put the same
                    // warning in three or four places at once, and read as each of them being broken.
                    val permissionNeeds = state.permissionNeeds
                    val serviceCard: @Composable (Modifier) -> Unit = { modifier ->
                        ServiceControlCard(
                            modifier = modifier,
                            isRunning = state.isRunning,
                            onToggle = { viewModel.onEvent(MainEvent.ToggleService(it, context)) }
                        )
                    }
                    val appearance: @Composable (Modifier) -> Unit = { modifier ->
                        NavigationCard(
                            modifier = modifier,
                            title = stringResource(R.string.appearance),
                            subtitle = stringResource(R.string.appearance_desc),
                            icon = R.drawable.ic_color_palette,
                            gradientColors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
                            stacked = true,
                            compact = !wide,
                            onClick = { onNavigateToAppearance(null) }
                        )
                    }
                    val actions: @Composable (Modifier) -> Unit = { modifier ->
                        NavigationCard(
                            modifier = modifier,
                            title = stringResource(R.string.actions),
                            subtitle = stringResource(R.string.actions_desc),
                            icon = R.drawable.ic_app_open,
                            gradientColors = listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
                            stacked = true,
                            compact = !wide,
                            onClick = onNavigateToActions
                        )
                    }
                    // The Deck and the Quick panel side by side: the bar's two panels, and a user
                    // looking for one will look wherever they found the other.
                    val deck: @Composable (Modifier) -> Unit = { modifier ->
                        NavigationCard(
                            modifier = modifier,
                            title = stringResource(R.string.deck_title),
                            subtitle = stringResource(R.string.deck_card_subtitle),
                            icon = R.drawable.ic_layer,
                            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
                            stacked = true,
                            compact = !wide,
                            onClick = onNavigateToDeck
                        )
                    }
                    val quickPanel: @Composable (Modifier) -> Unit = { modifier ->
                        NavigationCard(
                            modifier = modifier,
                            title = stringResource(R.string.quick_slider_title),
                            subtitle = stringResource(R.string.quick_panel_card_subtitle),
                            icon = R.drawable.ic_brightness_up,
                            gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)),
                            stacked = true,
                            compact = !wide,
                            onClick = onNavigateToQuickPanel
                        )
                    }
                    // The long-press menu beside Visibility: the two settings about the bar itself
                    // rather than about something it opens.
                    val longPressMenu: @Composable (Modifier) -> Unit = { modifier ->
                        NavigationCard(
                            modifier = modifier,
                            title = stringResource(R.string.context_menu_title),
                            subtitle = stringResource(R.string.long_press_menu_card_subtitle),
                            icon = R.drawable.ic_move,
                            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
                            stacked = true,
                            compact = !wide,
                            onClick = onNavigateToLongPressMenu
                        )
                    }
                    val visibility: @Composable (Modifier) -> Unit = { modifier ->
                        NavigationCard(
                            modifier = modifier,
                            title = stringResource(R.string.visibility_title),
                            subtitle = stringResource(R.string.visibility_card_subtitle),
                            icon = R.drawable.ic_visibility_hide,
                            gradientColors = listOf(Color(0xFF14B8A6), Color(0xFF3B82F6)),
                            stacked = true,
                            compact = !wide,
                            onClick = onNavigateToVisibility
                        )
                    }

                    if (wide) {
                        // A true four-column grid. The widths are worked out from the column
                        // rather than shared out by weight: a row whose first card is two columns
                        // wide has one gap fewer than a row of four, and weights would leave its
                        // edges a few dp off the edges of the row beneath it.
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val cell = (maxWidth - GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS
                            val span = { n: Int -> cell * n + GAP * (n - 1) }
                            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                                GridRow {
                                    serviceCard(Modifier.width(span(2)).fillMaxHeight())
                                    appearance(Modifier.width(span(1)).fillMaxHeight())
                                    actions(Modifier.width(span(1)).fillMaxHeight())
                                }
                                GridRow {
                                    deck(Modifier.width(span(1)).fillMaxHeight())
                                    quickPanel(Modifier.width(span(1)).fillMaxHeight())
                                    longPressMenu(Modifier.width(span(1)).fillMaxHeight())
                                    visibility(Modifier.width(span(1)).fillMaxHeight())
                                }
                            }
                        }
                    } else {
                        // The switch beside the two most visited pages, stacked, the switch as tall
                        // as the pair.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(GAP)
                        ) {
                            serviceCard(Modifier.weight(1f).fillMaxHeight())
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(GAP)
                            ) {
                                // Shared evenly: a card fills the height it is given, so without the
                                // weights the first one took the whole column and pushed the second
                                // out of it.
                                appearance(Modifier.fillMaxWidth().weight(1f))
                                actions(Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(GAP))
                        GridRow {
                            deck(Modifier.weight(1f).fillMaxHeight())
                            quickPanel(Modifier.weight(1f).fillMaxHeight())
                        }
                        Spacer(modifier = Modifier.height(GAP))
                        GridRow {
                            longPressMenu(Modifier.weight(1f).fillMaxHeight())
                            visibility(Modifier.weight(1f).fillMaxHeight())
                        }
                    }

                    Spacer(modifier = Modifier.height(GAP))

                    // The route back from "Hide handler". Only while there is something to undo.
                    if (state.isRunning && state.isHandlerHidden) {
                        HandlerHiddenCard(
                            onShowHandler = {
                                viewModel.onEvent(MainEvent.SetHandlerHidden(false, context))
                                viewModel.showToast(handlerShownMsg)
                            }
                        )
                        Spacer(modifier = Modifier.height(GAP))
                    }

                    // The count, the chips and the reason all come from the same list the notes on
                    // the other screens read, so the card and the screens can never disagree.
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
                    PermissionsStatusCard(
                        modifier = Modifier.fillMaxWidth(),
                        hasOverlayPermission = state.hasOverlayPermission,
                        missingPermissionCount = permissionNeeds.missingCount,
                        compact = !wide,
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
                        // Straight to the first thing to fix, flashed there so it is found at once.
                        onClick = { onNavigateToPermissions(firstNeed?.permission) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.quick_presets),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 10.dp),
                        letterSpacing = 0.5.sp
                    )

                    PresetCardsGrid(
                        viewModel = viewModel,
                        context = context,
                        // The same four columns as the cards above, on its side.
                        columns = if (wide) GRID_COLUMNS else 2,
                        onNavigateToAppearance = { presetId ->
                            scope.launch { drawerState.close() }
                            onNavigateToAppearance(presetId)
                        }
                    )

                    // Last, after everything the screen is for. It sat between the Permissions card
                    // and the presets, where it split the settings in two and was the first thing
                    // under the cards a user came here to use.
                    if (!state.isProActivated && !com.newagedevs.gesturevolume.BuildConfig.ADS_DISABLED) {
                        Spacer(modifier = Modifier.height(16.dp))
                        // The same card as every other on this screen, surface and corner and no
                        // border or shadow, so the ad sits in the grid rather than on top of it. What
                        // is inside takes its colours from the theme as well: see
                        // ApplovinAdsManager.styleNativeAdView.
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            viewModel.adsManager?.NativeAdWidget(
                                modifier = Modifier.wrapContentHeight()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/** One row of the grid, every card in it held to the height of the tallest. */
@Composable
private fun GridRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        content = content,
    )
}

private val GAP: Dp = 12.dp

/** The widest the home screen's column grows before it is centred instead. */
private val CONTENT_MAX_WIDTH: Dp = 920.dp

/** How many columns the grid has on its side. */
private const val GRID_COLUMNS = 4

/** From this width the cards go into the four-column grid. */
private const val WIDE_LAYOUT_DP = 600
