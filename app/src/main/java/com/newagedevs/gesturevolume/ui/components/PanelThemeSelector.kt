package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.PanelTheme

/**
 * Picks how the floating panels are dressed, from any of the three screens that own one.
 *
 * The same composable on the Deck, the Quick panel and the long-press menu, writing the same
 * preference — so wherever the user goes looking, the setting is there and says the same thing.
 * Repeating one control across three screens is usually a smell; here it is the point, because
 * the thing being set is not a property of any one of them.
 */
@Composable
fun PanelThemeSelector(
    theme: String,
    onThemeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.panel_theme),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.panel_theme_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        // Two rows rather than five segments abreast. Five labels across a phone leaves about
        // sixty pixels each, which is not a word — it is an ellipsis. The short row is padded out
        // so its segments stay the same width as the ones above rather than stretching to fill.
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PanelTheme.ALL.chunked(SEGMENTS_PER_ROW).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(3.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    row.forEach { id ->
                        Segment(
                            label = stringResource(labelFor(id)),
                            selected = theme == id,
                            onClick = { onThemeChange(id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(SEGMENTS_PER_ROW - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private const val SEGMENTS_PER_ROW = 3

private fun labelFor(id: String): Int = when (id) {
    PanelTheme.FROSTED -> R.string.panel_theme_frosted
    PanelTheme.GLASS -> R.string.panel_theme_glass
    PanelTheme.AERO -> R.string.panel_theme_aero
    PanelTheme.VIBRANT -> R.string.panel_theme_vibrant
    else -> R.string.panel_theme_solid
}

@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
