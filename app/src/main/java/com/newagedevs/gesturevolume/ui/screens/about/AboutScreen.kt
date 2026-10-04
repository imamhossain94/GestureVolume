package com.newagedevs.gesturevolume.ui.screens.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.helper.PrivacyChoices
import com.newagedevs.gesturevolume.helper.extensions.openAppStore
import com.newagedevs.gesturevolume.helper.extensions.openWebPage
import com.newagedevs.gesturevolume.helper.extensions.shareApp
import com.newagedevs.gesturevolume.ui.components.GroupHeading
import com.newagedevs.gesturevolume.ui.components.HeroCard
import com.newagedevs.gesturevolume.ui.components.IconTile
import com.newagedevs.gesturevolume.ui.components.KitRow
import com.newagedevs.gesturevolume.ui.components.RowGroup
import com.newagedevs.gesturevolume.ui.components.cardShape
import com.newagedevs.gesturevolume.ui.components.rowColor
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.utils.Constants.Companion.PUBLISHER_URL
import java.util.Calendar

/**
 * The app itself: which version this is, the ways to help it along, who made it and whose ideas
 * it grew from, and the small print.
 *
 * Laid out as the Actions screen is: a card at the top, then groups of rows, each a picture on a
 * tile and what it is — the whole row the thing to tap where it leads somewhere.
 *
 * @param isProActivated hides the Privacy choices entry; Pro never initialises the ad SDK.
 * @param onOpenPrivacyChoices reopens the ad consent form. See [PrivacyChoices].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    isProActivated: Boolean,
    onOpenPrivacyChoices: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPrivacyChoices by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, isProActivated) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showPrivacyChoices = PrivacyChoices.isAvailable(context, isProActivated)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // The launcher's own icon, as the phone draws it: it is adaptive, which a painter cannot load.
    val appIcon = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap()
        }.getOrNull()
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                HeroCard(
                    title = stringResource(R.string.gesture_volume),
                    text = stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                    leading = {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                            )
                        } else {
                            IconTile(Icons.Filled.Apps, lit = true, size = 56.dp)
                        }
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ---- the ways to help it along ------------------------------------------------
                GroupHeading(stringResource(R.string.about_group_support))
                RowGroup {
                    KitRow(
                        title = stringResource(R.string.rate_us),
                        shape = cardShape(0, 4),
                        leading = { IconTile(Icons.Filled.Star) },
                        onClick = { openAppStore(context, Constants.APP_STORE_ID) {} },
                    )
                    KitRow(
                        title = stringResource(R.string.share),
                        shape = cardShape(1, 4),
                        leading = { IconTile(Icons.Filled.Share) },
                        onClick = { shareApp(context) },
                    )
                    KitRow(
                        title = stringResource(R.string.source),
                        shape = cardShape(2, 4),
                        leading = { IconTile(Icons.Filled.Code) },
                        onClick = { openAppStore(context, Constants.SOURCE_CODE_URL) {} },
                    )
                    KitRow(
                        title = stringResource(R.string.more_apps),
                        shape = cardShape(3, 4),
                        leading = { IconTile(Icons.Filled.Apps) },
                        onClick = { openAppStore(context, PUBLISHER_URL) {} },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---- who: the people who built it, and whose ideas much of it was --------------
                GroupHeading(stringResource(R.string.about_group_credits))
                RowGroup {
                    CreditRow(
                        icon = Icons.Filled.Person,
                        label = stringResource(R.string.about_developed_in),
                        name = stringResource(R.string.newagedevs),
                        detail = stringResource(R.string.about_developer_mission),
                        footnote = stringResource(
                            R.string.about_copyright,
                            Calendar.getInstance().get(Calendar.YEAR).toString()
                        ),
                        shape = cardShape(0, 2),
                    )
                    // Thanks for the ideas rather than the code: much of what is new in this app
                    // was his suggestion first, and the suggestions keep coming.
                    CreditRow(
                        icon = Icons.Filled.Lightbulb,
                        label = stringResource(R.string.about_ideas_by),
                        name = stringResource(R.string.about_ideas_name),
                        detail = stringResource(R.string.about_ideas_desc),
                        shape = cardShape(1, 2),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ---- the small print -------------------------------------------------------------
                // Privacy choices reopens the ad consent form, for users who were asked for consent
                // in the first place. Re-checked on every resume: the ad SDK may finish initialising
                // after this screen is first drawn.
                val legalRows = if (showPrivacyChoices) 3 else 2
                GroupHeading(stringResource(R.string.group_legal))
                RowGroup {
                    KitRow(
                        title = stringResource(R.string.privacy_policy),
                        shape = cardShape(0, legalRows),
                        leading = { IconTile(Icons.Filled.Policy) },
                        onClick = { openWebPage(context, Constants.PRIVACY_POLICY_URL) {} },
                    )
                    KitRow(
                        title = stringResource(R.string.terms_of_service),
                        shape = cardShape(1, legalRows),
                        leading = { IconTile(Icons.Filled.Gavel) },
                        onClick = { openWebPage(context, Constants.TERMS_OF_SERVICE_URL) {} },
                    )
                    if (showPrivacyChoices) {
                        KitRow(
                            title = stringResource(R.string.privacy_choices),
                            shape = cardShape(2, legalRows),
                            leading = { IconTile(Icons.Filled.PrivacyTip) },
                            onClick = onOpenPrivacyChoices,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/** One credit: what they did, their name, a line more, and a quieter footnote, beside a tile. */
@Composable
private fun CreditRow(
    icon: ImageVector,
    label: String,
    name: String,
    detail: String,
    shape: Shape,
    footnote: String? = null,
) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowColor())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        // The tile heads the row rather than floating beside its middle: the credit runs to
        // several lines, and centred it drifted down level with the small print.
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = colours.onSurfaceVariant)
            Text(
                text = name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colours.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = colours.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (footnote != null) {
                // A short rule in the accent, so the small print reads as small print rather than
                // as one more line of the sentence above it.
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 7.dp)
                        .size(width = 28.dp, height = 1.dp)
                        .background(colours.primary.copy(alpha = 0.45f))
                )
                Text(
                    text = footnote,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp,
                    color = colours.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** The home screen's widest column. */
private val CONTENT_MAX_WIDTH = 920.dp
