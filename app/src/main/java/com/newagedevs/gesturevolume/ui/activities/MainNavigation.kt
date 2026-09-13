package com.newagedevs.gesturevolume.ui.activities

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.newagedevs.gesturevolume.R
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.screens.about.AboutScreen
import com.newagedevs.gesturevolume.ui.screens.faq.FaqScreen
import com.newagedevs.gesturevolume.ui.screens.menu.LongPressMenuScreen
import com.newagedevs.gesturevolume.ui.screens.deck.AppShortcutsScreen
import com.newagedevs.gesturevolume.ui.screens.deck.ClipboardScreen
import com.newagedevs.gesturevolume.ui.screens.deck.DeckScreen
import com.newagedevs.gesturevolume.ui.screens.deck.DeckTilesScreen
import com.newagedevs.gesturevolume.ui.screens.deck.NotesScreen
import com.newagedevs.gesturevolume.ui.screens.deck.QuickDialScreen
import com.newagedevs.gesturevolume.ui.screens.deck.SearchSettingsScreen
import com.newagedevs.gesturevolume.ui.screens.feedback.FeedbackScreen
import com.newagedevs.gesturevolume.ui.screens.handler_action.HandlerActionsScreen
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.HandlerAppearanceScreen
import com.newagedevs.gesturevolume.ui.screens.main.MainScreen
import com.newagedevs.gesturevolume.ui.screens.quick_slider.QuickSliderScreen
import com.newagedevs.gesturevolume.ui.screens.permission.PermissionsScreen
import com.newagedevs.gesturevolume.ui.screens.troubleshoot.TroubleshootScreen
import com.newagedevs.gesturevolume.ui.screens.walkthrough.WalkthroughScreen
import com.newagedevs.gesturevolume.ui.util.navigateBackOnce
import com.newagedevs.gesturevolume.ui.viewmodels.MainEffect
import com.newagedevs.gesturevolume.ui.viewmodels.MainEvent
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.ui.screens.upgrade.UpgradeScreen
import com.newagedevs.gesturevolume.ui.screens.visibility.VisibilityScreen

/** Routes the Deck may ask the app to open. Anything else in the extra is ignored. */
private val DEEP_LINK_ROUTES = setOf("deck", "notes", "clipboard", "deck_search", "deck_apps", "deck_quick_dial", "deck_tiles")

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
    var showResetDialog by remember { mutableStateOf(false) }

    val resetDoneMsg = stringResource(R.string.reset_app_done)

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Resume app open ads now that the user has returned from the overlay permission screen
        viewModel.preference.setAppOpenAdPaused(false)
        viewModel.onEvent(MainEvent.UpdatePermissionsStatus(context))
    }

    // Nothing depends on the answer: granted, the notification's Show/Settings/Stop row appears;
    // declined, the service runs exactly as before and the app is the only route back to a hidden
    // bar. Never gate the overlay on this.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // A route the Deck asked for — "manage notes", "manage clipboard" — arrives on the Activity
    // and is consumed here, once, so a rotation does not navigate a second time.
    val activity = context as? MainActivity
    val pendingRoute = activity?.pendingRoute?.value
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        activity.pendingRoute.value = null
        if (route in DEEP_LINK_ROUTES) navController.navigate(route)
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
                is MainEffect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                }
                is MainEffect.ConfirmResetApp -> showResetDialog = true
                is MainEffect.ShowThemeDialog -> showThemeDialog = true
                is MainEffect.ShowLanguageDialog -> showLanguageDialog = true
                is MainEffect.NavigateToTroubleshoot -> navController.navigate("troubleshoot")
                is MainEffect.NavigateToFaq -> navController.navigate("faq")
                is MainEffect.OpenAccessibilitySettings -> {
                    viewModel.preference.setAppOpenAdPaused(true)
                    try {
                        context.startActivity(OverlayRuntime.accessibilitySettingsIntent())
                    } catch (_: Exception) {
                        viewModel.preference.setAppOpenAdPaused(false)
                    }
                }
                else -> {}
            }
        }
    }

    // The appearance screen is a preview of the whole display, so it draws under the navigation
    // bar and manages that inset itself; every other screen keeps the blanket padding. Applied by
    // route rather than by giving each screen its own insets, which would be seven places to get
    // right instead of one exception.
    val currentRoute by navController.currentBackStackEntryAsState()
    val drawsBehindNavBar =
        currentRoute?.destination?.route?.startsWith("appearance") == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (drawsBehindNavBar) Modifier else Modifier.navigationBarsPadding())
    ) {
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
                        },
                        onNavigateToDeck = {
                            viewModel.maybeShowInterstitialAd()
                            navController.navigate("deck")
                        },
                        onNavigateToQuickPanel = {
                            viewModel.maybeShowInterstitialAd()
                            navController.navigate("quick_slider")
                        },
                        onNavigateToLongPressMenu = {
                            viewModel.maybeShowInterstitialAd()
                            navController.navigate("long_press_menu")
                        },
                        onNavigateToFaq = { navController.navigate("faq") },
                        onNavigateToUpgrade = { navController.navigate("upgrade") },
                        onNavigateToVisibility = {
                            viewModel.maybeShowInterstitialAd()
                            navController.navigate("visibility")
                        }
                    )
                }

                composable("visibility") {
                    VisibilityScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.navigateBackOnce() }
                    )
                }

                composable("upgrade") {
                    UpgradeScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.navigateBackOnce() }
                    )
                }

                composable("deck") {
                    DeckScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.navigateBackOnce() },
                        onNavigate = { route -> navController.navigate(route) }
                    )
                }

                composable("deck_tiles") {
                    DeckTilesScreen(viewModel = viewModel, onNavigateBack = { navController.navigateBackOnce() })
                }

                composable("deck_apps") {
                    AppShortcutsScreen(viewModel = viewModel, onNavigateBack = { navController.navigateBackOnce() })
                }

                composable("deck_quick_dial") {
                    QuickDialScreen(viewModel = viewModel, onNavigateBack = { navController.navigateBackOnce() })
                }

                composable("deck_search") {
                    SearchSettingsScreen(viewModel = viewModel, onNavigateBack = { navController.navigateBackOnce() })
                }

                composable("notes") {
                    NotesScreen(viewModel = viewModel, onNavigateBack = { navController.navigateBackOnce() })
                }

                composable("clipboard") {
                    ClipboardScreen(viewModel = viewModel, onNavigateBack = { navController.navigateBackOnce() })
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
                        },
                        onOpenQuickSlider = {
                            navController.navigate("quick_slider")
                        },
                        onOpenLongPressMenu = {
                            navController.navigate("long_press_menu")
                        }
                    )
                }

                composable("quick_slider") {
                    QuickSliderScreen(
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

                composable("long_press_menu") {
                    LongPressMenuScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("faq") {
                    FaqScreen(
                        onNavigateBack = { navController.popBackStack() },
                        // Replaces this entry rather than stacking on it: the two are alternatives
                        // for the same problem, and Back from Troubleshoot should return to where
                        // the user actually came from.
                        onNavigateToTroubleshoot = {
                            navController.navigate("troubleshoot") {
                                popUpTo("faq") { inclusive = true }
                            }
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

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.reset_app_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.reset_app_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            viewModel.onEvent(MainEvent.ResetAllSettings(context))
                            // The locale is not ours to reset through SharedPref alone: AppCompat
                            // keeps its own copy, and clearing only the preference would leave the
                            // app in a language the settings no longer claim.
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                            viewModel.showToast(resetDoneMsg)
                            // Back to the walkthrough, because that is now genuinely where a
                            // freshly reset install stands - and leaving the user on a settings
                            // screen showing values that were just wiped is its own small lie.
                            navController.navigate("walkthrough") {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.reset_app_confirm))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showResetDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            )
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