package com.newagedevs.gesturevolume.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.PermissionNeeds

/**
 * The line under a choice that cannot work yet, and the way to the screen that fixes it.
 *
 * Shown where the choice is made rather than only counted on the home screen, because that is
 * where the user is when it matters: a swipe set to brightness without Modify system settings
 * saves, and then does nothing out on the bar but flash a message, and the reason used to live
 * two screens away on a page nobody had a reason to open.
 *
 * Tapping it hands [missing] back, so the Permissions screen can open on that permission's card
 * and flash it rather than leaving the user to find it in the list.
 */
@Composable
fun PermissionNote(
    missing: PermissionNeeds.Permission,
    onOpenPermissions: (PermissionNeeds.Permission) -> Unit,
    modifier: Modifier = Modifier,
) {
    PermissionNote(
        text = stringResource(permissionNoteText(missing)),
        onClick = { onOpenPermissions(missing) },
        modifier = modifier,
    )
}

/** The note's wording for each permission. */
@StringRes
fun permissionNoteText(permission: PermissionNeeds.Permission): Int = when (permission) {
    PermissionNeeds.Permission.OVERLAY -> R.string.permission_note_overlay
    PermissionNeeds.Permission.WRITE_SETTINGS -> R.string.permission_note_write_settings
    PermissionNeeds.Permission.ACCESSIBILITY -> R.string.permission_note_accessibility
    PermissionNeeds.Permission.NOTIFICATION_POLICY -> R.string.permission_note_dnd
    PermissionNeeds.Permission.NOTIFICATIONS -> R.string.permission_note_notifications
    PermissionNeeds.Permission.CONTACTS -> R.string.permission_note_contacts
    PermissionNeeds.Permission.PHONE -> R.string.permission_note_phone
}

/** The same note with words of the caller's own, for a setting rather than an action. */
@Composable
fun PermissionNote(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
