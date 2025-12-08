package com.newagedevs.gesturevolume.ui.screens.handler_action

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerActionsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var showClickActionDialog by remember { mutableStateOf(false) }
    var showDoubleClickActionDialog by remember { mutableStateOf(false) }
    var showLongClickActionDialog by remember { mutableStateOf(false) }
    var showSwipeUpDialog by remember { mutableStateOf(false) }
    var showSwipeDownDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Handler Actions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Tap Actions Section
            SectionTitle("TAP ACTIONS", Color(0xFF8B5CF6))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, Color(0xFF8B5CF6))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ActionSettingItem(
                        label = "Single tap action",
                        description = "What happens on single tap",
                        value = state.clickAction,
                        icon = state.clickActionIcon,
                        borderColor = Color(0xFF8B5CF6),
                        showProBadge = false,
                        onClick = { showClickActionDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF8B5CF6).copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ActionSettingItem(
                        label = "Double tap action",
                        description = "What happens on double tap",
                        value = state.doubleClickAction,
                        icon = state.doubleClickActionIcon,
                        borderColor = Color(0xFF8B5CF6),
                        showProBadge = false,
                        onClick = {
                            showDoubleClickActionDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF8B5CF6).copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ActionSettingItem(
                        label = "Long press action",
                        description = "What happens on long press",
                        value = state.longClickAction,
                        icon = state.longClickActionIcon,
                        borderColor = Color(0xFF8B5CF6),
                        showProBadge = !state.isProActivated,
                        onClick = {
                            if (state.isProActivated) {
                                showLongClickActionDialog = true
                            } else {
                                viewModel.purchasePro(context as Activity)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Gesture Actions Section
            SectionTitle("GESTURE ACTIONS", Color(0xFF10B981))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, Color(0xFF10B981))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ActionSettingItem(
                        label = "Swipe up action",
                        description = "What happens on swipe up",
                        value = state.swipeUpAction,
                        icon = state.swipeUpActionIcon,
                        borderColor = Color(0xFF10B981),
                        showProBadge = false,
                        onClick = { showSwipeUpDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF10B981).copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    ActionSettingItem(
                        label = "Swipe down action",
                        description = "What happens on swipe down",
                        value = state.swipeDownAction,
                        icon = state.swipeDownActionIcon,
                        borderColor = Color(0xFF10B981),
                        showProBadge = false,
                        onClick = { showSwipeDownDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, Color(0xFF3B82F6))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color(0xFF3B82F6)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Customize how you interact with the volume handler through taps and gestures",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialogs
    if (showClickActionDialog) {
        TapActionDialog(
            title = "Single Tap Action",
            currentAction = state.clickAction,
            onDismiss = { showClickActionDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetClickAction(action, context))
                showClickActionDialog = false
            }
        )
    }

    if (showDoubleClickActionDialog) {
        TapActionDialog(
            title = "Double Tap Action",
            currentAction = state.doubleClickAction,
            onDismiss = { showDoubleClickActionDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetDoubleClickAction(action, context))
                showDoubleClickActionDialog = false
            }
        )
    }

    if (showLongClickActionDialog) {
        TapActionDialog(
            title = "Long Press Action",
            currentAction = state.longClickAction,
            onDismiss = { showLongClickActionDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetLongClickAction(action, context))
                showLongClickActionDialog = false
            }
        )
    }

    if (showSwipeUpDialog) {
        SwipeActionDialog(
            title = "Swipe Up Action",
            currentAction = state.swipeUpAction,
            isSwipeUp = true,
            onDismiss = { showSwipeUpDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetSwipeUpAction(action))
                showSwipeUpDialog = false
            }
        )
    }

    if (showSwipeDownDialog) {
        SwipeActionDialog(
            title = "Swipe Down Action",
            currentAction = state.swipeDownAction,
            isSwipeUp = false,
            onDismiss = { showSwipeDownDialog = false },
            onSelect = { action ->
                viewModel.onEvent(MainEvent.SetSwipeDownAction(action))
                showSwipeDownDialog = false
            }
        )
    }
}