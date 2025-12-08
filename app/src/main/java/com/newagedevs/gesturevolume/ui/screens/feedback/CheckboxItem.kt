package com.newagedevs.gesturevolume.ui.screens.feedback

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun CheckboxItem(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    color: Color
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (checked) 1.05f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "scale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (checked) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )

    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (checked) color.copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onCheckedChange(!checked)
            },
        shape = RoundedCornerShape(12.dp),
        color = animatedBackgroundColor,
        border = BorderStroke(1.5.dp, animatedBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (checked) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
                color = if (checked) color else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Custom Checkbox
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = if (checked) color else Color.Transparent,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (checked) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Checked",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}