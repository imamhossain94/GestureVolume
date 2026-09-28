package com.newagedevs.gesturevolume.ui.screens.whats_new

import com.newagedevs.gesturevolume.ui.screens.handler_appearance.AppearanceSection
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.newagedevs.gesturevolume.ui.motion.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.TourCard
import com.newagedevs.gesturevolume.utils.ReleaseNotes
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Every release, newest first, with the install milestones between them.
 *
 * The version installed on this phone is marked, so "what did the update I just got change" is the
 * first card rather than a search. Dates are written the way the app's language writes them; the
 * notes themselves stay in English, as Google Play published them.
 *
 * Opening it is what clears the dot on the home screen's icon: see [onSeen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(
    onNavigateBack: () -> Unit,
    /** Called once when the screen opens, so the "new" marker on the home screen goes. */
    onSeen: () -> Unit,
    /** Opens the tour of the app, offered at the top: the new things shown rather than listed. */
    onTakeTour: (() -> Unit)? = null,
) {
    LaunchedEffect(Unit) { onSeen() }

    val installed = remember { ReleaseNotes.normalize(BuildConfig.VERSION_NAME) }
    // The activity's own locale, which follows the language chosen in the app, not the system's.
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    val dates = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whats_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
        // Kept to a readable column on a tablet or a phone on its side, centred like the home screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = MAX_WIDTH)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onTakeTour != null) {
                    item(key = "tour") { TourCard(onTakeTour = onTakeTour) }
                }
                itemsIndexed(
                    ReleaseNotes.HISTORY,
                    key = { _, entry ->
                        when (entry) {
                            is ReleaseNotes.Release -> "release:" + entry.version
                            is ReleaseNotes.Milestone -> "milestone:" + entry.installs
                        }
                    },
                ) { index, entry ->
                    when (entry) {
                        is ReleaseNotes.Release -> ReleaseCard(
                            release = entry,
                            isInstalled = entry.version == installed,
                            date = entry.date?.format(dates),
                            // The newest, and the one on this phone, open; the rest folded to a row.
                            open = index == 0 || entry.version == installed,
                        )
                        is ReleaseNotes.Milestone -> MilestoneRow(
                            text = stringResource(R.string.whats_new_milestone, numbers.format(entry.installs)),
                            date = entry.date.format(dates),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A release, as the settings screens' groups are: a row with its picture, its version and its
 * date, and its notes under it as the second half of the same card, folded away for the older
 * ones. The one on this phone is marked on its tile and its line.
 */
@Composable
private fun ReleaseCard(release: ReleaseNotes.Release, isInstalled: Boolean, date: String?, open: Boolean) {
    val installedLabel = stringResource(R.string.whats_new_installed)
    AppearanceSection(
        title = stringResource(R.string.whats_new_version, release.version),
        summary = listOfNotNull(date, installedLabel.takeIf { isInstalled }).joinToString(" · ").ifEmpty { null },
        initiallyExpanded = open,
        icon = if (isInstalled) Icons.Filled.NewReleases else Icons.Filled.History,
    ) {
        if (release.firstRelease) {
            Text(
                text = stringResource(R.string.whats_new_first_release),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        release.notes.forEach { note -> NoteLine(note) }
    }
}

/** One line of notes, its emoji in a column of its own so the words line up. */
@Composable
private fun NoteLine(note: String) {
    val (emoji, words) = remember(note) { ReleaseNotes.splitEmoji(note) }
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = emoji ?: "•",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = words,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** A milestone between two releases: small and centred, so it reads as a marker and not a release. */
@Composable
private fun MilestoneRow(text: String, date: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                text = "🎉 $text · $date",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/** A reading width: notes run on at the home screen's full 920dp. */
private val MAX_WIDTH = 720.dp
