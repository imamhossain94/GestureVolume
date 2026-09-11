package com.newagedevs.gesturevolume.ui.screens.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.helper.extensions.openAppStore
import com.newagedevs.gesturevolume.helper.extensions.openWebPage
import com.newagedevs.gesturevolume.helper.extensions.shareApp
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.utils.Constants.Companion.PUBLISHER_URL
import java.util.Calendar
import androidx.compose.ui.res.stringResource
import com.newagedevs.gesturevolume.R
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Star,
                    text = stringResource(R.string.rate_us),
                    borderColor = Color(0xFFF59E0B),
                    onClick = {
                        openAppStore(context, Constants.APP_STORE_ID) {

                        }
                    }
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Share,
                    text = stringResource(R.string.share),
                    borderColor = Color(0xFF10B981),
                    onClick = {
                        shareApp(context)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Build,
                    text = stringResource(R.string.source),
                    borderColor = Color(0xFF8B5CF6),
                    onClick = {
                        openAppStore(context, Constants.SOURCE_CODE_URL) {

                        }
                    }
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ShoppingCart,
                    text = stringResource(R.string.more_apps),
                    borderColor = Color(0xFFEC4899),
                    onClick = {
                        openAppStore(context, PUBLISHER_URL) {

                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Developer Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.developed_by),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.newagedevs),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "© ${Calendar.getInstance().get(Calendar.YEAR)} All rights reserved",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thanks for the ideas rather than the code: much of what is new in this app was his
            // suggestion first, and the suggestions keep coming.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.about_ideas_by),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(R.string.about_ideas_name),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.about_ideas_desc),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Links Section
            Text(
                text = stringResource(R.string.legal),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 4.dp)
            )

            LinkCard(
                icon = Icons.Default.Info,
                text = stringResource(R.string.privacy_policy),
                onClick = {
                    openWebPage(context, Constants.PRIVACY_POLICY_URL) {

                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}