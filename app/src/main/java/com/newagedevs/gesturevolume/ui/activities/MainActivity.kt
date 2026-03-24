package com.newagedevs.gesturevolume.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val DEVICE_ADMIN_REQUEST_CODE = 3
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            GestureVolumeTheme {
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
    }

    override fun onPause() {
        super.onPause()
        // Show handler when app goes to background — use Intent (works even if not bound)
        sendServiceCommand("show")
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.removeObserver()
    }

    /**
     * Send a command to OverlayService via Intent.
     * This works regardless of whether we're bound to the service or not.
     * Only sends if the service should be running (preference check).
     */
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
}