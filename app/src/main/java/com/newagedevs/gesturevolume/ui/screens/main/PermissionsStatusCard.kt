package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PermissionsStatusCard(
    modifier: Modifier = Modifier,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onClick: () -> Unit
) {
    val allPermissionsGranted = hasOverlayPermission && hasNotificationPermission

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allPermissionsGranted) MaterialTheme.colorScheme.surfaceVariant 
            else MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (allPermissionsGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (allPermissionsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permissions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (allPermissionsGranted) MaterialTheme.colorScheme.onSurfaceVariant 
                    else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (allPermissionsGranted) "All permissions granted" else "Action required",
                    fontSize = 13.sp,
                    color = if (allPermissionsGranted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f)
                    else MaterialTheme.colorScheme.onErrorContainer.copy(alpha=0.8f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (allPermissionsGranted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f)
                else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
            )
        }
    }
}