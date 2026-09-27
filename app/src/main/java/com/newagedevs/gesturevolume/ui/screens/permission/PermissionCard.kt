package com.newagedevs.gesturevolume.ui.screens.permission

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.components.IconTile
import com.newagedevs.gesturevolume.ui.components.rowColor
import com.newagedevs.gesturevolume.ui.motion.Button
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton

/**
 * One permission, as a row of the Actions and Visibility screens: its picture on a tile — lit once
 * it is granted — what it is and what it is for, and a tick; or, until it is granted, why this user
 * needs it now and the button that grants it.
 *
 * @param shape where it sits in its group: see `cardShape`.
 * @param isOptional no longer drawn: the group it sits in says so.
 * @param borderColor no longer drawn; kept so the callers read as they did.
 */
@Composable
fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    borderColor: Color,
    isOptional: Boolean = false,
    /**
     * Why this ungranted permission matters *to this user right now* — set when their own
     * configuration has made an optional permission necessary. Null when there is nothing
     * outstanding, and ignored once the permission is granted.
     */
    warning: String? = null,
    onRequestPermission: () -> Unit,
    onDisablePermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * 0 to 1: how strongly the card is marked as the one the user was sent here for. Drawn as a
     * primary border and a primary-container tint, both scaled by it, so an animated value pulses.
     */
    highlight: Float = 0f,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    val colours = MaterialTheme.colorScheme
    val amount = highlight.coerceIn(0f, 1f)
    val base = rowColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (amount > 0f) lerp(base, colours.primaryContainer, amount) else base)
            .then(
                if (amount > 0f) {
                    Modifier.border(BorderStroke(2.dp, colours.primary.copy(alpha = amount)), shape)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, lit = isGranted)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colours.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colours.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (isGranted) {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.granted),
                    tint = colours.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // Above the button, not below it: the reason to press something belongs before the
        // thing to press.
        if (!isGranted && warning != null) {
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colours.errorContainer.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colours.onErrorContainer,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = warning,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colours.onErrorContainer,
                )
            }
        }

        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.grant_permission),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (onDisablePermission != null) {
            OutlinedButton(
                onClick = onDisablePermission,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colours.primary,
                ),
                border = BorderStroke(1.dp, colours.primary.copy(alpha = 0.6f)),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.revoke_permission),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
