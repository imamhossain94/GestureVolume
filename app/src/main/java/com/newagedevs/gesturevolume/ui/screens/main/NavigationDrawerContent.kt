package com.newagedevs.gesturevolume.ui.screens.main

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFAA7BFF),
                                        Color(0xFF2C209A)
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(512f, 512f)
                                )
                            ),
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
                            text = "Gesture Volume",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isProActivated) "Pro Version" else "Free Version",
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
                        text = "UPGRADE",
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
                    text = "GENERAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
                    letterSpacing = 0.5.sp
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_share,
                    label = "Share App",
                    accentColor = Color(0xFF10B981),
                    onClick = { onMenuItemClick("Share") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_feedback,
                    label = "Send Feedback",
                    accentColor = Color(0xFF3B82F6),
                    onClick = { onMenuItemClick("Feedback") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_star,
                    label = "Rate on Play Store",
                    accentColor = Color(0xFFFBBF24),
                    onClick = { onMenuItemClick("Rate us") }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // === MORE SECTION ===
                Text(
                    text = "MORE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp),
                    letterSpacing = 0.5.sp
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_playstore,
                    label = "Other Apps",
                    accentColor = Color(0xFF06B6D4),
                    onClick = { onMenuItemClick("Other apps") }
                )

                NavigationDrawerItem(
                    icon = R.drawable.ic_nothing,
                    label = "About",
                    accentColor = Color(0xFFEC4899),
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
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.02f)
        ),
        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // === ICON (48dp, left aligned) ===
            Box(
                modifier = Modifier
                    .size(48.dp)   // ⬅️ updated to 48dp
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFFAA7BFF),
                                Color(0xFF2C209A)
                            ),
                            start = Offset.Zero,
                            end = Offset(400f, 400f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_crown_2),
                    contentDescription = "Crown",
                    modifier = Modifier.size(26.dp),   // proportional to 48dp container
                    colorFilter = ColorFilter.tint(Color.White)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // === TEXT ===
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Upgrade to Pro",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Unlock all features",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // === PRICE TAG ===
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF8B5CF6)
            ) {
                Text(
                    text = "$0.99",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF8B5CF6)
            )
        }
    }
}


@Composable
private fun NavigationDrawerItem(
    icon: Int,
    label: String,
    accentColor: Color,
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = label,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

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
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = badge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}