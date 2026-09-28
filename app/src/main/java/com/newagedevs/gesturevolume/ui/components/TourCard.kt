package com.newagedevs.gesturevolume.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.motion.Button
import com.newagedevs.gesturevolume.ui.motion.TextButton

/**
 * The offer of a tour of the app: the walkthrough's pages again, for someone who already has it set
 * up. On the home screen once after an update, with What's new and Not now beside it; on the What's
 * new screen, on its own. See `WalkthroughScreen`'s tour.
 *
 * Says that nothing the user has set up will change, because that is what anyone who has spent
 * time on their bar wonders before opening a "walkthrough" — and in a tour it is true.
 *
 * The buttons wrap onto a second line rather than squeeze, for the languages with longer words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TourCard(
    onTakeTour: () -> Unit,
    modifier: Modifier = Modifier,
    onWhatsNew: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colours = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colours.primaryContainer.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = null,
                    tint = colours.primary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tour_offer_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colours.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.tour_offer_body),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colours.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(start = 38.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Button(onClick = onTakeTour, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.tour_take), fontSize = 13.sp)
                }
                if (onWhatsNew != null) {
                    TextButton(onClick = onWhatsNew) {
                        Text(stringResource(R.string.whats_new_title), fontSize = 13.sp)
                    }
                }
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.not_now), fontSize = 13.sp, color = colours.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
