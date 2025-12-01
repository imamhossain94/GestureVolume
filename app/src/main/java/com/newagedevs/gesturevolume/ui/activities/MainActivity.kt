package com.newagedevs.gesturevolume.ui.activities

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

    override fun onResume() {
        super.onResume()
        viewModel.overlayService?.hide()
    }

    override fun onPause() {
        super.onPause()
        viewModel.overlayService?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.removeObserver()
    }
}