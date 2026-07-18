package com.newagedevs.gesturevolume.ui.activities

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.newagedevs.gesturevolume.ui.screens.main.LanguageDialog
import com.newagedevs.gesturevolume.ui.screens.main.ThemeDialog
import androidx.compose.ui.platform.LocalContext
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
import com.newagedevs.gesturevolume.ui.screens.troubleshoot.TroubleshootScreen
import com.newagedevs.gesturevolume.ui.screens.walkthrough.WalkthroughScreen
import com.newagedevs.gesturevolume.ui.util.navigateBackOnce
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

    // Banner ads were removed entirely: 1,600 impressions/week for ~$0.01 (a $0.00 eCPM) while
    // being a top driver of "too many ads" complaints. Net-zero revenue loss, less clutter.

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Resume app open ads now that the user has returned from the overlay permission screen
        viewModel.preference.setAppOpenAdPaused(false)
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
                    // Pause ads while the user is in the overlay permission screen
                    viewModel.preference.setAppOpenAdPaused(true)
                    overlayPermissionLauncher.launch(intent)
                }
                is MainEffect.RequestNotificationPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                is MainEffect.ShowThemeDialog -> showThemeDialog = true
                is MainEffect.ShowLanguageDialog -> showLanguageDialog = true
                is MainEffect.NavigateToTroubleshoot -> navController.navigate("troubleshoot")
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
                        onNavigateToAppearance = { presetId ->
                            // Interstitial at a genuine screen transition (capped + cooled down).
                            viewModel.maybeShowInterstitialAd()
                            if (presetId != null) {
                                navController.navigate("appearance?preset=$presetId")
                            } else {
                                navController.navigate("appearance")
                            }
                        },
                        onNavigateToActions = {
                            viewModel.maybeShowInterstitialAd()
                            navController.navigate("actions")
                        },
                        onNavigateToPermissions = {
                            navController.navigate("permissions")
                        }
                    )
                }

                composable(
                    "appearance?preset={preset}",
                    arguments = listOf(androidx.navigation.navArgument("preset") { nullable = true })
                ) { backStackEntry ->
                    val presetId = backStackEntry.arguments?.getString("preset")
                    HandlerAppearanceScreen(
                        viewModel = viewModel,
                        presetId = presetId,
                        onNavigateBack = {
                            navController.navigateBackOnce()
                        }
                    )
                }

                composable("actions") {
                    HandlerActionsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.navigateBackOnce()
                        }
                    )
                }

                composable("permissions") {
                    PermissionsScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            navController.navigateBackOnce()
                        }
                    )
                }

                composable("about") {
                    AboutScreen(
                        onNavigateBack = {
                            navController.navigateBackOnce()
                        }
                    )
                }

                composable("feedback") {
                    FeedbackScreen(
                        onNavigateBack = {
                            navController.navigateBackOnce()
                        }
                    )
                }

                composable("troubleshoot") {
                    TroubleshootScreen(
                        onNavigateBack = {
                            navController.navigateBackOnce()
                        }
                    )
                }
            }
        }

        if (showThemeDialog) {
            ThemeDialog(
                currentTheme = state.theme,
                onDismiss = { showThemeDialog = false },
                onThemeSelect = { theme -> viewModel.setTheme(theme) }
            )
        }

        if (showLanguageDialog) {
            LanguageDialog(
                currentLanguage = state.language,
                onDismiss = { showLanguageDialog = false },
                onLanguageSelect = { code -> 
                    viewModel.setLanguage(code)
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                }
            )
        }
    }
}