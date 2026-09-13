package com.newagedevs.gesturevolume.ui.screens.main

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
 * Built the way the cards above it are — the same surface, corner, icon chip, title and
 * description, in the same order — only the full width of the grid, so it reads as the grid's last
 * row rather than as a banner under it.
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
    onClick: () -> Unit
) {
    val allGranted = missingPermissionCount == 0 && overlay != PermissionChipState.MISSING
    val tone = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 12.dp else 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 32.dp else 38.dp)
                        .clip(RoundedCornerShape(if (compact) 10.dp else 12.dp))
                        .background(tone.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (allGranted) Icons.Default.Shield else Icons.Default.PrivacyTip,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                        tint = tone
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.permissions),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (compact) 14.sp else 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            allGranted -> stringResource(R.string.all_permissions_granted)
                            // Naming the count is the difference between a card the user opens and
                            // one they learn to ignore.
                            missingPermissionCount == 1 ->
                                stringResource(R.string.permission_action_required_one)
                            missingPermissionCount > 1 ->
                                stringResource(R.string.permission_action_required_many, missingPermissionCount)
                            else -> stringResource(R.string.action_required)
                        },
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 10.dp))
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

            Spacer(modifier = Modifier.height(10.dp))
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
        }
    }
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
