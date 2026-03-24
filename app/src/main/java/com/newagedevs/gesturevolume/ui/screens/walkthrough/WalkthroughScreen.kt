package com.newagedevs.gesturevolume.ui.screens.walkthrough

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.NotificationUtil

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WalkthroughScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(0) }

    val hasOverlayPermission = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val hasNotificationPermission = remember { 
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationUtil(context).isPermissionGranted()
            } else {
                true
            }
        ) 
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission.value = Settings.canDrawOverlays(context)
        if (hasOverlayPermission.value) {
            currentPage++
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission.value = isGranted
        currentPage++
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(targetState = currentPage, label = "walkthrough") { page ->
                when (page) {
                    0 -> WalkthroughPage(
                        title = "Welcome to Gesture Volume",
                        description = "Control your device's volume with simple on-screen gestures. No need to reach for the physical buttons.",
                        iconRes = R.drawable.ic_vol_increase // Assuming this exists based on MainViewModel
                    )
                    1 -> WalkthroughPage(
                        title = "Overlay Permission",
                        description = "To show the volume handler on top of other apps, we need the 'Draw over other apps' permission.",
                        iconRes = R.drawable.ic_align_right,
                        isGranted = hasOverlayPermission.value
                    )
                    2 -> WalkthroughPage(
                        title = "Notifications",
                        description = "Opt-in to notifications so we can keep the gesture service running smoothly in the background.",
                        iconRes = R.drawable.ic_lock, // Fallback icon
                        isGranted = hasNotificationPermission.value
                    )
                    else -> WalkthroughPage(
                        title = "You're all set!",
                        description = "Gesture Volume is ready to use. Customize the appearance and actions in the main screen.",
                        iconRes = R.drawable.ic_app_open
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Page indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                for (i in 0..3) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == currentPage) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            // Bottom button
            Button(
                onClick = {
                    when (currentPage) {
                        0 -> currentPage++
                        1 -> {
                            if (!hasOverlayPermission.value) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:${context.packageName}".toUri()
                                )
                                overlayPermissionLauncher.launch(intent)
                            } else {
                                currentPage++
                            }
                        }
                        2 -> {
                            if (!hasNotificationPermission.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                currentPage++
                            }
                        }
                        else -> {
                            viewModel.preference.setFirstLaunchCompleted()
                            onComplete()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = when (currentPage) {
                        0 -> "Get Started"
                        1 -> if (hasOverlayPermission.value) "Next" else "Grant Permission"
                        2 -> if (hasNotificationPermission.value) "Next" else "Grant Permission"
                        else -> "Finish"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Skip button for permissions
            if (currentPage in 1..2) {
                TextButton(
                    onClick = { currentPage++ },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(modifier = Modifier.height(56.dp)) // Maintain spacing
            }
        }
    }
}

@Composable
fun WalkthroughPage(
    title: String,
    description: String,
    iconRes: Int,
    isGranted: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (isGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Permission Granted ✓",
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
