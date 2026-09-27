package com.newagedevs.gesturevolume.ui.screens.faq

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.GroupHeading
import com.newagedevs.gesturevolume.ui.components.IconTile
import com.newagedevs.gesturevolume.ui.components.KitRow
import com.newagedevs.gesturevolume.ui.components.RowGroup
import com.newagedevs.gesturevolume.ui.components.cardShape
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.ui.motion.TextButton

/**
 * The questions people actually ask, answered in the app rather than in a store listing.
 *
 * **Why this exists alongside Troubleshoot.** They are not the same thing and neither replaces the
 * other. Troubleshoot is a *tool*: it reads whether battery optimisation is off, finds this OEM's
 * auto-start screen, and takes the user there. It can only cover the handful of problems that have
 * a button to press. This is prose, and it covers everything else — what a gesture does, why a
 * permission is asked for, what the Deck is for. The one place they meet is "the bar keeps
 * vanishing", which is a question here and a fix there, so that entry links across.
 *
 * Laid out as the Actions screen is: the questions in groups by when they come up — starting out,
 * permissions, the features, tips — each a row with its picture, opening to its answer in place,
 * and a search over all of them that keeps only the groups with a match.
 *
 * Every answer is a string resource, and deliberately so: this is the part of the app most likely
 * to be read by someone who does not speak English, and it is the only screen where being
 * untranslated makes the app *less* usable rather than merely less polished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTroubleshoot: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.faq)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
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
        // Every question and answer in the reader's language, to search through.
        val q = query.trim()
        val groups = GROUPS.map { group ->
            group to group.entries.filter { entry ->
                q.isEmpty() ||
                    stringResource(entry.question).contains(q, ignoreCase = true) ||
                    stringResource(entry.answer).contains(q, ignoreCase = true)
            }
        }.filter { (_, entries) -> entries.isNotEmpty() }

        // The home screen's frame: the Scaffold's padding on every side (which carries the camera
        // cutout in landscape), then a column centred at the same widest width with the same 16dp
        // either side, so the cards start and end where the home screen's do.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.faq_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 20.dp),
                )

                if (groups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.faq_empty, q),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                    )
                }

                groups.forEach { (group, entries) ->
                    GroupHeading(stringResource(group.title))
                    RowGroup {
                        entries.forEachIndexed { index, entry ->
                            FaqItem(
                                entry = entry,
                                shape = cardShape(index, entries.size),
                                // Open while searching, so what matched is in sight.
                                open = q.isNotEmpty(),
                                onAction = onNavigateToTroubleshoot,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** The home screen's widest column, so this list is centred the same way on its side. */
private val CONTENT_MAX_WIDTH = 920.dp

private class FaqEntry(
    @param:StringRes val question: Int,
    @param:StringRes val answer: Int,
    val icon: ImageVector,
    @param:StringRes val actionLabel: Int? = null,
)

private class FaqGroup(@param:StringRes val title: Int, val entries: List<FaqEntry>)

/**
 * Ordered by how early someone hits the question, not by topic within a group.
 *
 * The first group is what a new install runs into in its first minute; the permission questions
 * come next because they are what stops the app working at all; the rest is the tour.
 */
private val GROUPS = listOf(
    FaqGroup(
        R.string.faq_group_start,
        listOf(
            FaqEntry(R.string.faq_q_where_is_bar, R.string.faq_a_where_is_bar, Icons.Filled.LocationSearching),
            FaqEntry(R.string.faq_q_move_bar, R.string.faq_a_move_bar, Icons.Filled.OpenWith),
            FaqEntry(R.string.faq_q_hide_bar, R.string.faq_a_hide_bar, Icons.Filled.VisibilityOff),
            FaqEntry(
                R.string.faq_q_bar_disappears,
                R.string.faq_a_bar_disappears,
                Icons.Filled.SyncProblem,
                R.string.troubleshoot,
            ),
        ),
    ),
    FaqGroup(
        R.string.permissions,
        listOf(
            FaqEntry(R.string.faq_q_brightness, R.string.faq_a_brightness, Icons.Filled.BrightnessMedium),
            FaqEntry(R.string.faq_q_accessibility, R.string.faq_a_accessibility, Icons.Filled.Accessibility),
            FaqEntry(R.string.faq_q_notification, R.string.faq_a_notification, Icons.Filled.Notifications),
        ),
    ),
    FaqGroup(
        R.string.faq_group_features,
        listOf(
            FaqEntry(R.string.faq_q_quick_panel, R.string.faq_a_quick_panel, Icons.Filled.Tune),
            FaqEntry(R.string.faq_q_deck, R.string.faq_a_deck, Icons.Filled.ViewSidebar),
            FaqEntry(R.string.faq_q_panel_vs_deck, R.string.faq_a_panel_vs_deck, Icons.Filled.SwapHoriz),
            FaqEntry(R.string.faq_q_change_gestures, R.string.faq_a_change_gestures, Icons.Filled.Swipe),
            FaqEntry(R.string.faq_q_menu, R.string.faq_a_menu, Icons.Filled.Menu),
        ),
    ),
    FaqGroup(
        R.string.faq_group_tips,
        listOf(
            FaqEntry(R.string.faq_q_volume_stream, R.string.faq_a_volume_stream, Icons.Filled.MusicNote),
            FaqEntry(R.string.faq_q_landscape, R.string.faq_a_landscape, Icons.Filled.ScreenRotation),
            FaqEntry(R.string.faq_q_battery, R.string.faq_a_battery, Icons.Filled.BatteryChargingFull),
            FaqEntry(R.string.faq_q_games, R.string.faq_a_games, Icons.Filled.SportsEsports),
        ),
    ),
)

/**
 * One question, collapsed to its title until asked for, with its answer opening under it in the
 * same row. All sixteen open at once would be a wall of prose; the point of a FAQ is that the
 * questions are the index. Expansion is `rememberSaveable` so an answer survives a rotation.
 */
@Composable
private fun FaqItem(entry: FaqEntry, shape: Shape, open: Boolean, onAction: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = expanded || open
    val chevron by animateFloatAsState(targetValue = if (shown) 180f else 0f, label = "faqChevron")

    KitRow(
        title = stringResource(entry.question),
        shape = shape,
        modifier = Modifier.semantics {
            this.role = Role.Button
            heading()
        },
        leading = { IconTile(entry.icon, lit = shown) },
        onClick = { expanded = !shown },
        trailing = {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                // The row already announces itself; naming the chevron too would say it twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.rotate(chevron),
            )
        },
        footer = {
            AnimatedVisibility(
                visible = shown,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(start = 74.dp, end = 16.dp, bottom = 14.dp)) {
                    Text(
                        text = stringResource(entry.answer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5,
                    )
                    // Only one entry has somewhere to go, and it is the one whose answer is
                    // "there is a screen for this" rather than a paragraph.
                    entry.actionLabel?.let { label ->
                        TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                            Text(stringResource(label))
                        }
                    }
                }
            }
        },
    )
}
