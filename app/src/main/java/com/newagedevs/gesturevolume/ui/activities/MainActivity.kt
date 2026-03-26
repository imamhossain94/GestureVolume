package com.newagedevs.gesturevolume.ui.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val DEVICE_ADMIN_REQUEST_CODE = 3
    }

    private val viewModel: MainViewModel by viewModels()

    // Bumped in onConfigurationChanged to trigger recomposition with new locale strings
    private val configVersion = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // Read configVersion so Compose recomposes when locale changes
            val version = configVersion.intValue
            val state by viewModel.state.collectAsState()
            val isDarkTheme = when (state.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            GestureVolumeTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }

        // Initialize IAP
        viewModel.initializeIAP(this)

        // Observe pro user status and initialize ads if needed
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                if (!state.isProActivated) {
                    viewModel.initializeAdsManager(this@MainActivity)
                }
            }
        }

        // Observe LiveData communicator
        viewModel.observeCommunicator(this)

        // Increment launch count and check for review
        viewModel.preference.incrementAppLaunchCount()
        showInAppReviewIfNeeded()

        // Check for App Updates
        checkForAppUpdate()
    }

    override fun onStart() {
        super.onStart()
        // Re-bind to running service if it exists (handles recents-clear scenario)
        viewModel.rebindToServiceIfRunning(this)
    }

    override fun onResume() {
        super.onResume()
        // Hide handler when app is in foreground — use Intent (works even if not bound)
        sendServiceCommand("hide")

        // Resume App Update if needed
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    1001
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Show handler when app goes to background — use Intent (works even if not bound)
        sendServiceCommand("show")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Bump version to force Compose to recompose with updated locale resources
        configVersion.intValue++
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.removeObserver()
    }

    private fun sendServiceCommand(action: String) {
        if (viewModel.preference.isRunning()) {
            val intent = Intent(this, OverlayService::class.java).apply {
                this.action = action
            }
            try {
                startService(intent)
            } catch (_: Exception) {
                // Service might not be running, ignore
            }
        }
    }

    private fun checkForAppUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    1001
                )
            }
        }
    }

    private fun showInAppReviewIfNeeded() {
        if (viewModel.preference.hasShownReview()) return

        val launchCount = viewModel.preference.getAppLaunchCount()
        
        // Show review on 3rd, 10th, 20th launch etc if not shown
        if (launchCount == 3 || launchCount == 10 || launchCount == 20) {
            val manager = ReviewManagerFactory.create(this)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(this, reviewInfo)
                    flow.addOnCompleteListener {
                        viewModel.preference.setHasShownReview(true)
                    }
                }
            }
        }
    }
}