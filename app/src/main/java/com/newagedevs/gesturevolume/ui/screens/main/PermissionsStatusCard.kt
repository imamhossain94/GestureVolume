package com.newagedevs.gesturevolume.ui.screens.main

import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R

/** Where one permission stands, for the chips on [PermissionsStatusCard]. */
enum class PermissionChipState {
    /** Granted. */
    GRANTED,

    /** Not granted, and something the user has set up needs it. */
    MISSING,

    /** Not granted, and nothing needs it yet. Not a problem, only not on. */
    OPTIONAL,
}

/**
 * The home screen's way to the Permissions page, and its answer to "is everything the app needs
 * switched on?".
 *
 * A row of the home screen's first group, in the Actions and Visibility screens' design, with what
 * it is waiting for and the chips under it inside the same row.
 *
 * It used to say only "All permissions granted" in a card sized for a paragraph, which read as an
 * empty card. So it says what these permissions are for, and then shows the three that matter most
 * one by one, each with where it stands: granted, needed by something the user has set up, or
 * simply not on yet. Anything else missing is counted in one more chip, so the card never claims
 * more is fine than is.
 *
 * Its warning is carried by colour in a few places only — the chip, the status line, the count and
 * the chips that need attention — and the card itself stays the neutral surface, because a whole
 * red card on the home screen reads as the app being broken when it is usually one optional extra.
 *
 * @param compact the upright size, matching [NavigationCard]'s compact cards beside it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsStatusCard(
    modifier: Modifier = Modifier,
    hasOverlayPermission: Boolean,
    /**
     * Permissions the user's configuration needs but has not granted, the required overlay
     * included. Non-zero turns this card into a warning even when the app is technically running:
     * a brightness action that silently does nothing is not "all permissions granted".
     */
    missingPermissionCount: Int = 0,
    compact: Boolean = false,
    overlay: PermissionChipState = if (hasOverlayPermission) PermissionChipState.GRANTED else PermissionChipState.MISSING,
    accessibility: PermissionChipState = PermissionChipState.OPTIONAL,
    writeSettings: PermissionChipState = PermissionChipState.OPTIONAL,
    /** Missing permissions not shown as a chip of their own: Do Not Disturb, contacts and so on. */
    otherMissing: Int = 0,
    /** What the first missing permission is needed by, shown in place of the description. */
    reason: String? = null,
    /** Where it sits in its group: see segmentShape. */
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: () -> Unit
) {
    val allGranted = missingPermissionCount == 0 && overlay != PermissionChipState.MISSING
    val tone = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    HomeLinkRow(
        // Its tile in the warning colour while something waits, so the row is found at a glance.
        icon = {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tone.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allGranted) Icons.Default.Shield else Icons.Default.PrivacyTip,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = tone
                )
            }
        },
        title = stringResource(R.string.permissions),
        summary = when {
            allGranted -> stringResource(R.string.all_permissions_granted)
            // Naming the count is the difference between a row the user opens and one they
            // learn to ignore.
            missingPermissionCount == 1 -> stringResource(R.string.permission_action_required_one)
            missingPermissionCount > 1 ->
                stringResource(R.string.permission_action_required_many, missingPermissionCount)
            else -> stringResource(R.string.action_required)
        },
        summaryColour = tone,
        shape = shape,
        onClick = onClick,
        trailing = {
            if (!allGranted && missingPermissionCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = missingPermissionCount.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
        },
        footer = {
            Text(
                // When something is waiting, what it is waiting for says more than a description
                // of permissions in general.
                text = if (!allGranted && reason != null) reason else stringResource(R.string.permission_card_desc),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (!allGranted && reason != null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusChip(stringResource(R.string.permission_chip_overlay), overlay)
                StatusChip(stringResource(R.string.permission_chip_accessibility), accessibility)
                StatusChip(stringResource(R.string.permission_chip_settings), writeSettings)
                if (otherMissing > 0) {
                    StatusChip(
                        stringResource(R.string.permission_chip_more, otherMissing),
                        PermissionChipState.MISSING
                    )
                }
            }
        },
    )
}

/** One permission and where it stands, as a small pill. */
@Composable
private fun StatusChip(label: String, state: PermissionChipState) {
    val (ink, fill) = when (state) {
        PermissionChipState.GRANTED ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        PermissionChipState.MISSING ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        PermissionChipState.OPTIONAL ->
            MaterialTheme.colorScheme.onSurfaceVariant to
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .padding(start = 6.dp, end = 9.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (state) {
                PermissionChipState.GRANTED -> Icons.Default.CheckCircle
                PermissionChipState.MISSING -> Icons.Default.Error
                PermissionChipState.OPTIONAL -> Icons.Default.RadioButtonUnchecked
            },
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = ink
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = ink,
            maxLines = 1
        )
    }
}
