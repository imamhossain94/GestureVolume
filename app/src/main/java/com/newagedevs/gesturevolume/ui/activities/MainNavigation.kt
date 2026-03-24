package com.newagedevs.gesturevolume.ui.activities

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.newagedevs.gesturevolume.ui.screens.about.AboutScreen
import com.newagedevs.gesturevolume.ui.screens.feedback.FeedbackScreen
import com.newagedevs.gesturevolume.ui.screens.handler_action.HandlerActionsScreen
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.HandlerAppearanceScreen
import com.newagedevs.gesturevolume.ui.screens.main.MainScreen
import com.newagedevs.gesturevolume.ui.screens.permission.PermissionsScreen
import com.newagedevs.gesturevolume.ui.screens.walkthrough.WalkthroughScreen
import com.newagedevs.gesturevolume.ui.viewmodels.MainEffect
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel

@Composable
fun MainNavigation(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MainEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                is MainEffect.NavigateToAbout ->  navController.navigate("about")
                is MainEffect.NavigateToFeedback -> navController.navigate("feedback")
                is MainEffect.RequestOverlayPermission -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    overlayPermissionLauncher.launch(intent)
                }
                is MainEffect.RequestNotificationPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {}
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .navigationBarsPadding()) {
        // Main content
        Box(modifier = Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = if (viewModel.preference.isFirstLaunch()) "walkthrough" else "main"
            ) {
                composable("walkthrough") {
                    WalkthroughScreen(
                        viewModel = viewModel,
                        onComplete = {
                            navController.navigate("main") {
                                popUpTo("walkthrough") { inclusive = true }
                            }
                        }
                    )
                }

                composable("main") {
                    MainScreen(
                        viewModel = viewModel,
                        onNavigateToAppearance = {
                            navController.navigate("appearance")
                        },
                        onNavigateToActions = {
                            navController.navigate("actions")
                        },
                        onNavigateToPermissions = {
                            navController.navigate("permissions")
                        }
                    )
                }

                composable("appearance") {
                    HandlerAppearanceScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("actions") {
                    HandlerActionsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("permissions") {
                    PermissionsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("about") {
                    AboutScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable("feedback") {
                    FeedbackScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }

        // Banner ad at the bottom (only for non-pro users)
        if (!state.isProActivated) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                viewModel.adsManager?.BannerAdView()
            }
        }
    }
}