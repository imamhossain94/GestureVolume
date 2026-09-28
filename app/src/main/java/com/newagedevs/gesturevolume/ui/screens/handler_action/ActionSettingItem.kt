package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.utils.ActionIcon

/**
 * A row that leads somewhere — a screen, a dialog — as the Actions screen's rows do: a tile with
 * its picture, its name, what it is set to now, and a line on what it is for, the whole row the
 * thing to tap.
 *
 * It used to be a heading and a line, then a separate box underneath to tap: three things to read
 * before the one to press, and a box that looked like a text field.
 *
 * @param borderColor no longer drawn; kept so the callers read as they did.
 */
@Composable
fun ActionSettingItem(
    label: String,
    description: String,
    value: String,
    icon: ActionIcon,
    borderColor: Color,
    showProBadge: Boolean = false,
    onClick: () -> Unit
) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colours.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colours.primary,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colours.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showProBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = colours.primaryContainer) {
                        Text(
                            text = "PRO",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = colours.onPrimaryContainer,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colours.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colours.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = colours.outline,
        )
    }
}
