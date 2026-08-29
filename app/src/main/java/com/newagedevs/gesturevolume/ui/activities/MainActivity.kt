package com.newagedevs.gesturevolume.ui.activities

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
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
import androidx.lifecycle.lifecycleScope
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.utils.ReviewPrompter
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

        /** Let the screen settle — and any app-open ad finish — before considering a prompt. */
        private const val REVIEW_SETTLE_DELAY_MS = 2_500L
    }

    private val viewModel: MainViewModel by viewModels()

    /**
     * True from the moment a Play update is known to be pending until this Activity goes away.
     *
     * Both Play dialogs are system-owned and neither yields to the other, so the only way to stop
     * them overlapping is to not ask for the second one.
     */
    private var updateFlowActive = false

    private val reviewRunnable = Runnable { showInAppReviewIfNeeded() }
    private val reviewHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Bumped in onConfigurationChanged to trigger recomposition with new locale strings
    private val configVersion = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

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

        // Launch count feeds the review pacing; the old boolean is folded in once.
        viewModel.preference.incrementAppLaunchCount()
        viewModel.preference.migrateReviewState()

        // Check for App Updates
        checkForAppUpdate()
    }

    override fun onStart() {
        super.onStart()
        // Restart the service if the system killed it while the user still wants it running.
        // Doing this from the foreground sidesteps the Android 12+ background-start restrictions.
        viewModel.repairServiceIfNeeded(this)
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
                updateFlowActive = true
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    1001
                )
            }
        }

        // Deliberately delayed and deliberately not in onCreate. At launch this would land on top
        // of the splash screen, the update check, and the app-open ad — which is what made the
        // old prompt feel like it came out of nowhere. Waiting for a settled, still-open screen
        // costs nothing and is the whole difference between a prompt and an ambush.
        reviewHandler.removeCallbacks(reviewRunnable)
        reviewHandler.postDelayed(reviewRunnable, REVIEW_SETTLE_DELAY_MS)
    }

    override fun onPause() {
        super.onPause()
        // Show handler when app goes to background — use Intent (works even if not bound)
        sendServiceCommand("show")
        // Nothing half-scheduled outlives the foreground: a prompt that fires as the user is
        // leaving lands on whatever they switched to.
        reviewHandler.removeCallbacks(reviewRunnable)
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
                updateFlowActive = true
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
        if (isFinishing || isDestroyed) return

        val versionCode = BuildConfig.VERSION_CODE
        if (!ReviewPrompter.shouldAsk(
                preference = viewModel.preference,
                serviceRunning = viewModel.preference.isRunning(),
                updateFlowActive = updateFlowActive,
                versionCode = versionCode
            )
        ) return

        val manager = ReviewManagerFactory.create(this)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            // Re-checked: requestReviewFlow is asynchronous, and the user may have left in the
            // meantime. launchReviewFlow on a dead Activity is the other way this looks broken.
            if (isFinishing || isDestroyed) return@addOnCompleteListener

            manager.launchReviewFlow(this, task.result).addOnCompleteListener {
                // Recorded whichever way it went. Play does not report whether the sheet was
                // actually shown — success here covers "displayed", "suppressed by quota" and
                // "already rated" alike — so this counts attempts, not impressions. That is
                // precisely why the budget is three and not one.
                viewModel.preference.recordReviewAsk(versionCode)
            }
        }
    }
}