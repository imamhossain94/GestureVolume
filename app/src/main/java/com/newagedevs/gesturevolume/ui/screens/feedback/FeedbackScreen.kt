package com.newagedevs.gesturevolume.ui.screens.feedback

import com.newagedevs.gesturevolume.ui.components.rowColor
import com.newagedevs.gesturevolume.ui.components.cardShape
import com.newagedevs.gesturevolume.ui.components.RowGroup
import com.newagedevs.gesturevolume.ui.components.KitRow
import com.newagedevs.gesturevolume.ui.components.IconTile
import com.newagedevs.gesturevolume.ui.components.HeroCard
import com.newagedevs.gesturevolume.ui.components.GroupHeading
import androidx.compose.ui.graphics.Shape
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.TipsAndUpdates
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import com.newagedevs.gesturevolume.ui.motion.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.newagedevs.gesturevolume.ui.motion.IconButton
import androidx.compose.material3.MaterialTheme
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.helper.extensions.openMailApp
import com.newagedevs.gesturevolume.ui.components.isLandscape
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.AppearanceSection
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SectionTitle
import com.newagedevs.gesturevolume.utils.Constants

/**
 * Tell the developer what is wrong, or what would be better, by email.
 *
 * The four common issues are ticked on a grid, anything else goes in the note, and Send opens the
 * user's email app with both written out along with the app and device details, exactly as it
 * always has. Nothing is sent from here.
 *
 * Upright it is one column inside the home screen's margins, with Cancel and Send pinned under it.
 * On its side the header and what gets sent take two fifths of the width, and the issues, the note
 * and the buttons the other three fifths — the split every two-pane screen in the app uses. Both
 * layouts pad for the keyboard, so the note and the Send button stay above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // Saveable: a rotation swaps the layout, and should not throw away what was typed.
    var appCrashesChecked by rememberSaveable { mutableStateOf(false) }
    var tooManyAdsChecked by rememberSaveable { mutableStateOf(false) }
    var appFreezesChecked by rememberSaveable { mutableStateOf(false) }
    var notUserFriendlyChecked by rememberSaveable { mutableStateOf(false) }
    var otherIssueText by rememberSaveable { mutableStateOf("") }

    val issues = listOf(
        IssueOption(R.string.issue_crash, Icons.Default.BugReport, appCrashesChecked) { appCrashesChecked = it },
        IssueOption(R.string.issue_ads, Icons.Default.AdsClick, tooManyAdsChecked) { tooManyAdsChecked = it },
        IssueOption(R.string.issue_unresponsive, Icons.Default.HourglassEmpty, appFreezesChecked) { appFreezesChecked = it },
        IssueOption(R.string.issue_not_user_friendly, Icons.Default.SentimentDissatisfied, notUserFriendlyChecked) { notUserFriendlyChecked = it },
    )

    val send = {
        val answers = buildFeedbackMessage(
            context,
            appCrashesChecked,
            tooManyAdsChecked,
            appFreezesChecked,
            notUserFriendlyChecked,
            otherIssueText
        )
        openMailApp(context, "App Feedback", Constants.feedbackEmails, answers)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback)) },
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // The Scaffold's padding on every side, as on the home screen; consumed, so the keyboard
        // padding below adds only the part of the keyboard that rises above the navigation bar.
        val framed = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()

        val message: @Composable () -> Unit = {
            MessageCard(value = otherIssueText, onValueChange = { otherIssueText = it })
        }
        val actions: @Composable () -> Unit = {
            ActionButtons(onCancel = onNavigateBack, onSend = send)
        }

        if (isLandscape()) {
            Box(modifier = framed, contentAlignment = Alignment.TopCenter) {
                Row(
                    modifier = Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxHeight()
                        .padding(horizontal = SIDE_MARGIN),
                    horizontalArrangement = Arrangement.spacedBy(PANE_GAP)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(SIDE_SHARE)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(GAP)
                    ) {
                        HeaderCard()
                        HowItIsSentCard()
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f - SIDE_SHARE)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(GAP)
                        ) {
                            IssuesSection(issues)
                            message()
                        }
                        Box(modifier = Modifier.padding(bottom = 12.dp)) { actions() }
                    }
                }
            }
        } else {
            Column(modifier = framed) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = CONTENT_MAX_WIDTH)
                            .padding(horizontal = SIDE_MARGIN)
                            .padding(top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(GAP)
                    ) {
                        HeaderCard()
                        IssuesSection(issues)
                        message()
                        HowItIsSentCard()
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SIDE_MARGIN, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH)) { actions() }
                }
            }
        }
    }
}

/** One of the common issues, and how to tick it. */
private class IssueOption(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

@Composable
private fun HeaderCard() {
    HeroCard(
        title = stringResource(R.string.we_d_love_to_hear_from_you),
        text = stringResource(R.string.help_us_improve_the_app),
        leading = { IconTile(Icons.Default.Email, lit = true, size = 56.dp) },
    )
}

/** The four common issues, one row each, ticked on their own, as the Visibility screen's apps are. */
@Composable
private fun IssuesSection(issues: List<IssueOption>) {
    Column {
        GroupHeading(
            title = stringResource(R.string.section_common_issues),
            hint = stringResource(R.string.feedback_pick_any),
        )
        RowGroup {
            issues.forEachIndexed { index, issue -> IssueRow(issue, cardShape(index, issues.size)) }
        }
    }
}

/** One issue: its picture, its name, and a tick once chosen — the whole row the tick. */
@Composable
private fun IssueRow(issue: IssueOption, shape: Shape) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (issue.checked) colours.primaryContainer else rowColor())
            .toggleable(value = issue.checked, role = Role.Checkbox, onValueChange = issue.onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(issue.icon, lit = issue.checked)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(issue.label),
            fontSize = 16.sp,
            fontWeight = if (issue.checked) FontWeight.SemiBold else FontWeight.Medium,
            color = if (issue.checked) colours.onPrimaryContainer else colours.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = if (issue.checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (issue.checked) colours.primary else colours.outline,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Anything the four issues do not cover. */
@Composable
private fun MessageCard(value: String, onValueChange: (String) -> Unit) {
    Column {
        GroupHeading(stringResource(R.string.group_other_feedback))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.describe_the_issue_or_suggestion)) },
            placeholder = { Text(stringResource(R.string.tell_us_more_about_your_experience)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = rowColor(),
                unfocusedContainerColor = rowColor(),
            )
        )
    }
}

/** What Send does and what goes with it, so nobody wonders whether it has already gone. */
@Composable
private fun HowItIsSentCard() {
    Column {
        GroupHeading(stringResource(R.string.group_how_sent))
        RowGroup {
            KitRow(
                title = stringResource(R.string.feedback_via_email_title),
                description = stringResource(R.string.feedback_via_email_desc),
                shape = cardShape(0, 2),
                leading = { IconTile(Icons.Default.Email) },
            )
            KitRow(
                title = stringResource(R.string.feedback_details_title),
                description = stringResource(R.string.feedback_details_desc),
                shape = cardShape(1, 2),
                leading = { IconTile(Icons.Default.PhoneAndroid) },
            )
        }
    }
}

/** Cancel beside a Send that takes the rest of the row. */
@Composable
private fun ActionButtons(onCancel: () -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            contentPadding = PaddingValues(horizontal = 18.dp)
        ) {
            Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onSend,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.send_feedback), fontWeight = FontWeight.Bold)
        }
    }
}

private fun buildFeedbackMessage(
    context: Context,
    appCrashes: Boolean,
    tooManyAds: Boolean,
    appFreezes: Boolean,
    notUserFriendly: Boolean,
    otherIssue: String
): String {
    val selectedProblems = mutableListOf<String>()

    if (appCrashes) selectedProblems.add("• ${context.getString(R.string.issue_crash)}")
    if (tooManyAds) selectedProblems.add("• ${context.getString(R.string.issue_ads)}")
    if (appFreezes) selectedProblems.add("• ${context.getString(R.string.issue_unresponsive)}")
    if (notUserFriendly) selectedProblems.add("• ${context.getString(R.string.issue_not_user_friendly)}")

    var message = ""

    if (selectedProblems.isNotEmpty()) {
        message = "${context.getString(R.string.selected_issues)}\n" + selectedProblems.joinToString("\n")
    }

    if (otherIssue.isNotEmpty()) {
        if (message.isNotEmpty()) message += "\n\n"
        message += "${context.getString(R.string.additional_feedback)}\n$otherIssue"
    }

    return message.ifEmpty { context.getString(R.string.no_feedback_provided) }
}

/** The home screen's side margin. */
private val SIDE_MARGIN = 16.dp

/** The home screen's widest column. */
private val CONTENT_MAX_WIDTH = 920.dp

/** Between the two panes on its side, as on the preview screens. */
private val PANE_GAP = 20.dp

/** Between cards in a column. */
private val GAP = 24.dp

/** How much of the width the header and the notes take on its side. */
private const val SIDE_SHARE = 0.4f
