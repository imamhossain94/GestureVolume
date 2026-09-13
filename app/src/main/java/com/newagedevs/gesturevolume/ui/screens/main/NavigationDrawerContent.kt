package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import androidx.compose.ui.res.stringResource

@Composable
fun NavigationDrawerContent(
    isProActivated: Boolean,
    onMenuItemClick: (String) -> Unit
) {
    val context = LocalContext.current

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // === APP HEADER ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isProActivated) stringResource(R.string.pro_version) else stringResource(R.string.free_version),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )

            // Menu Items - Scrollable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Pro is the crown on the home screen's bar now, not a card here.
                // === GENERAL SECTION ===
                Text(
                    text = stringResource(R.string.general),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
                    letterSpacing = 0.5.sp
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_translate,
                    label = stringResource(R.string.language),
                    onClick = { onMenuItemClick("Language") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_color_swatch,
                    label = stringResource(R.string.theme),
                    onClick = { onMenuItemClick("Theme") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_share,
                    label = stringResource(R.string.share_app),
                    onClick = { onMenuItemClick("Share") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_star,
                    label = stringResource(R.string.rate_on_play_store),
                    onClick = { onMenuItemClick("Rate us") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_feedback,
                    label = stringResource(R.string.send_feedback),
                    onClick = { onMenuItemClick("Feedback") }
                )
                
                // Above Troubleshoot, because it is the wider net: most of what sends someone
                // to this section is a question rather than a fault, and Troubleshoot only helps
                // with the handful of problems that have a button to press.
                NavigationDrawerItem(
                    icon = R.drawable.ic_help,
                    label = stringResource(R.string.faq),
                    onClick = { onMenuItemClick("FAQ") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_bug,
                    label = stringResource(R.string.troubleshoot),
                    onClick = { onMenuItemClick("Troubleshoot") }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // === MORE SECTION ===
                Text(
                    text = stringResource(R.string.more),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
                    letterSpacing = 0.5.sp
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_playstore,
                    label = stringResource(R.string.other_apps),
                    onClick = { onMenuItemClick("Other apps") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_nothing,
                    label = stringResource(R.string.about),
                    onClick = { onMenuItemClick("About") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_reset,
                    label = stringResource(R.string.reset_app),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onMenuItemClick("Reset") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NavigationDrawerItem(
    icon: Int,
    label: String,
    badge: String? = null,
    /** Overrides the icon and label colour. Used by the one destructive row. */
    tint: Color? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = badge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}