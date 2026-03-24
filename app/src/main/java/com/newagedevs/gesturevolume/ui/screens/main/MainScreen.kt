package com.newagedevs.gesturevolume.ui.screens.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.newagedevs.gesturevolume.ui.theme.Surface
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToAppearance: () -> Unit,
    onNavigateToActions: () -> Unit,
    onNavigateToPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Update permissions status when screen appears
    LaunchedEffect(Unit) {
        viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
        viewModel.onEvent(MainEvent.SyncServiceState(context))
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            viewModel.onBackPressed {
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
                            "Gesture Volume",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { (context as? Activity)?.finish() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
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
                            title = "Appearance",
                            subtitle = "Customize handler",
                            icon = com.newagedevs.gesturevolume.R.drawable.ic_color_palette,
                            gradientColors = listOf(
                                Color(0xFF6366F1),
                                Color(0xFF8B5CF6)
                            ),
                            onClick = onNavigateToAppearance
                        )

                        // Actions Card
                        NavigationCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            title = "Actions",
                            subtitle = "Tap & gesture settings",
                            icon = com.newagedevs.gesturevolume.R.drawable.ic_app_open,
                            gradientColors = listOf(
                                Color(0xFF10B981),
                                Color(0xFF06B6D4)
                            ),
                            onClick = onNavigateToActions
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Permissions Card with Status
                PermissionsStatusCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    hasOverlayPermission = state.hasOverlayPermission,
                    hasNotificationPermission = state.hasNotificationPermission,
                    onClick = onNavigateToPermissions
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!state.isProActivated) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        viewModel.adsManager?.NativeAdWidget(
                            modifier = Modifier.wrapContentHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Presets Section
                Text(
                    text = "QUICK PRESETS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 10.dp),
                    letterSpacing = 0.5.sp
                )

                PresetCardsGrid(
                    viewModel = viewModel,
                    context = context,
                    onNavigateToAppearance = {
                        scope.launch { drawerState.close() }
                        onNavigateToAppearance()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}