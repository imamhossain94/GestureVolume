package com.newagedevs.gesturevolume.ui.screens.faq

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R

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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            ENTRIES.forEach { entry ->
                FaqItem(
                    question = stringResource(entry.question),
                    answer = stringResource(entry.answer),
                    // Only one entry has somewhere to go, and it is the one whose answer is "there
                    // is a screen for this" rather than a paragraph.
                    actionLabel = entry.actionLabel?.let { stringResource(it) },
                    onAction = onNavigateToTroubleshoot,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class FaqEntry(
    @param:StringRes val question: Int,
    @param:StringRes val answer: Int,
    @param:StringRes val actionLabel: Int? = null,
)

/**
 * Ordered by how early someone hits the question, not by topic.
 *
 * The first three are what a new install runs into in its first minute; the permission questions
 * come next because they are what stops the app working at all; the rest is the tour.
 */
private val ENTRIES = listOf(
    FaqEntry(R.string.faq_q_where_is_bar, R.string.faq_a_where_is_bar),
    FaqEntry(R.string.faq_q_move_bar, R.string.faq_a_move_bar),
    FaqEntry(R.string.faq_q_hide_bar, R.string.faq_a_hide_bar),
    FaqEntry(
        R.string.faq_q_bar_disappears,
        R.string.faq_a_bar_disappears,
        R.string.troubleshoot,
    ),
    FaqEntry(R.string.faq_q_brightness, R.string.faq_a_brightness),
    FaqEntry(R.string.faq_q_accessibility, R.string.faq_a_accessibility),
    FaqEntry(R.string.faq_q_notification, R.string.faq_a_notification),
    FaqEntry(R.string.faq_q_quick_panel, R.string.faq_a_quick_panel),
    FaqEntry(R.string.faq_q_deck, R.string.faq_a_deck),
    FaqEntry(R.string.faq_q_panel_vs_deck, R.string.faq_a_panel_vs_deck),
    FaqEntry(R.string.faq_q_change_gestures, R.string.faq_a_change_gestures),
    FaqEntry(R.string.faq_q_menu, R.string.faq_a_menu),
    FaqEntry(R.string.faq_q_volume_stream, R.string.faq_a_volume_stream),
    FaqEntry(R.string.faq_q_landscape, R.string.faq_a_landscape),
    FaqEntry(R.string.faq_q_battery, R.string.faq_a_battery),
    FaqEntry(R.string.faq_q_games, R.string.faq_a_games),
)

/**
 * One question, collapsed to its title until asked for.
 *
 * All sixteen open at once would be a wall of prose; the point of a FAQ is that the questions are
 * the index. Expansion is `rememberSaveable` so an answer survives a rotation mid-read.
 */
@Composable
private fun FaqItem(
    question: String,
    answer: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "faqChevron",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 14.dp)
                    .semantics {
                        this.role = Role.Button
                        heading()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    // The row already announces itself and its state; naming the chevron too would
                    // make a screen reader say the same thing twice.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(chevron),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5,
                    )
                    if (actionLabel != null) {
                        TextButton(
                            onClick = onAction,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(actionLabel)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
