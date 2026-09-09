package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.AudioStreamCatalog
import com.newagedevs.gesturevolume.utils.VolumeStreamMode

/**
 * Picks how hard the bar should try to follow whatever the device is actually playing.
 *
 * A single-choice list rather than a switch, because the three answers are not two: "never adapt",
 * "adapt when it is obviously right", and "adapt the way the hardware keys do" are genuinely
 * different intentions, and collapsing the middle one — the default — into either neighbour would
 * mean choosing between a swipe that is wrong during calls and a swipe that changes the ringer when
 * the user did not ask it to.
 *
 * Each option carries its consequence underneath it. The last one says outright that the bar will
 * not silence the ringer completely: that is the one place this feature knowingly falls short of
 * the hardware keys, and it is better disclosed here than discovered.
 */
@Composable
fun VolumeStreamDialog(
    selected: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Held locally so the live overlay is not re-pointed at a new stream on every tick of the
    // user's mind — it reads this preference at the start of each gesture.
    var working by remember(selected) { mutableStateOf(VolumeStreamMode.sanitize(selected)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.volume_stream_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                VolumeStreamMode.ALL.forEach { mode ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = working == mode,
                                role = Role.RadioButton,
                                onClick = { working = mode },
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = working == mode,
                                // The whole row is the target; a second one inside it would let a
                                // screen reader announce the same choice twice.
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.fillMaxWidth(0.03f))
                            Text(
                                text = stringResource(AudioStreamCatalog.labelForMode(mode)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(AudioStreamCatalog.descriptionForMode(mode)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 40.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(working) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
