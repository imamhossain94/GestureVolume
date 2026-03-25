package com.newagedevs.gesturevolume.ui.screens.main

import android.content.Intent
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
import androidx.core.net.toUri
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.helper.InHouseBannerAdsView
import com.newagedevs.gesturevolume.utils.Constants
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
                            modifier = Modifier.size(48.dp),
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
                // === PREMIUM SECTION ===
                if (!isProActivated) {
                    Text(
                        text = stringResource(R.string.upgrade),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
                        letterSpacing = 0.5.sp
                    )

                    PremiumNavigationItem(
                        onClick = { onMenuItemClick("Premium") }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }

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
            }

            // Bottom Ads
            if (!isProActivated) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    InHouseBannerAdsView(
                        bannerAds = Constants.inHouseAdList,
                        onInstallClick = { appLink ->
                            val intent = Intent(Intent.ACTION_VIEW, appLink.toUri())
                            context.startActivity(intent)
                        }
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PremiumNavigationItem(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown_2),
                    contentDescription = stringResource(R.string.crown),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.upgrade_to_pro),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.unlock_all_features),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = stringResource(R.string.pro),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}


@Composable
private fun NavigationDrawerItem(
    icon: Int,
    label: String,
    badge: String? = null,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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