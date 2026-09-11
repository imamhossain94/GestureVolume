package com.newagedevs.gesturevolume.ui.screens.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.ui.theme.Surface
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.newagedevs.gesturevolume.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import com.newagedevs.gesturevolume.ui.screens.upgrade.ProGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToAppearance: (String?) -> Unit,
    onNavigateToActions: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToDeck: () -> Unit,
    onNavigateToQuickPanel: () -> Unit,
    onNavigateToLongPressMenu: () -> Unit,
    onNavigateToFaq: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Resolved in composable scope so it follows a locale change.
    val handlerShownMsg = stringResource(R.string.handler_shown_toast)

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                isProActivated = state.isProActivated,
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
                        // The way to Pro, where the close button used to be. Gold, because it is
                        // the one thing on this bar that is an offer rather than a control.
                        IconButton(onClick = onNavigateToUpgrade) {
                            Icon(
                                painter = painterResource(R.drawable.ic_crown_2),
                                contentDescription = stringResource(R.string.upgrade_to_pro),
                                tint = ProGold,
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // Service Control & Navigation Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Service Control Card
                    ServiceControlCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isRunning = state.isRunning,
                        onToggle = { viewModel.onEvent(MainEvent.ToggleService(it, context)) }
                    )

                    // Navigation Cards Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Appearance Card
                        NavigationCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            title = stringResource(R.string.appearance),
                            subtitle = stringResource(R.string.appearance_desc),
                            icon = R.drawable.ic_color_palette,
                            gradientColors = listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6)
                            ),
                            onClick = { onNavigateToAppearance(null) }
                        )

                        // Actions Card
                        NavigationCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            title = stringResource(R.string.actions),
                            subtitle = stringResource(R.string.actions_desc),
                            icon = R.drawable.ic_app_open,
                            gradientColors = listOf(
                                Color(0xFF10B981),
                                Color(0xFF06B6D4)
                            ),
                            onClick = onNavigateToActions
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // The Deck: the slide-out panel of shortcuts and tools beside the bar.
                NavigationCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    title = stringResource(R.string.deck_title),
                    subtitle = stringResource(R.string.deck_card_subtitle),
                    icon = R.drawable.ic_layer,
                    gradientColors = listOf(
                        Color(0xFFF59E0B),
                        Color(0xFFEF4444)
                    ),
                    onClick = onNavigateToDeck
                )

                Spacer(modifier = Modifier.height(12.dp))

                // The Quick panel: the track the bar opens into. Beside the Deck rather than
                // buried in Actions, because the two are the bar's two panels and a user looking
                // for one will look wherever they found the other.
                NavigationCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    title = stringResource(R.string.quick_slider_title),
                    subtitle = stringResource(R.string.quick_panel_card_subtitle),
                    icon = R.drawable.ic_brightness_up,
                    gradientColors = listOf(
                        Color(0xFF06B6D4),
                        Color(0xFF3B82F6)
                    ),
                    onClick = onNavigateToQuickPanel
                )

                Spacer(modifier = Modifier.height(12.dp))

                // The long-press menu, beside the two panels it sits alongside in use. It was
                // reachable only from a row buried in Actions, which is where you look for what a
                // gesture *does* — not for what is inside the thing one of them opens.
                NavigationCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    title = stringResource(R.string.context_menu_title),
                    subtitle = stringResource(R.string.long_press_menu_card_subtitle),
                    icon = R.drawable.ic_move,
                    gradientColors = listOf(
                        Color(0xFF8B5CF6),
                        Color(0xFFEC4899)
                    ),
                    onClick = onNavigateToLongPressMenu
                )

                Spacer(modifier = Modifier.height(12.dp))

                // The route back from "Hide handler". Only while there is something to undo.
                if (state.isRunning && state.isHandlerHidden) {
                    HandlerHiddenCard(
                        onShowHandler = {
                            viewModel.onEvent(MainEvent.SetHandlerHidden(false, context))
                            viewModel.showToast(handlerShownMsg)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Permissions Card with Status
                PermissionsStatusCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    hasOverlayPermission = state.hasOverlayPermission,
                    missingPermissionCount = state.missingPermissionCount,
                    onClick = onNavigateToPermissions
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!state.isProActivated) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    ) {
                        viewModel.adsManager?.NativeAdWidget(
                            modifier = Modifier.wrapContentHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Presets Section
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
                    onNavigateToAppearance = { presetId ->
                        scope.launch { drawerState.close() }
                        onNavigateToAppearance(presetId)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}