package com.newagedevs.gesturevolume.ui.screens.handler_appearance

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * @param color ignored — kept so the many existing call sites still compile while the screen
 *   moves to a single themed accent.
 * @param modifier defaults to exactly the padding this composable has always applied, so every
 *   call site that does not pass one renders byte-identically. [AppearanceSection] passes a
 *   `weight` so the title shares its header row with a summary and a chevron.
 */
@Composable
fun SectionTitle(
    text: String,
    color: Color,
    modifier: Modifier = Modifier.padding(bottom = 16.dp, start = 4.dp),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.2.sp
        )
    }
}