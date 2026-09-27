package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.newagedevs.gesturevolume.ui.motion.TextButton
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

/**
 * One answer from a short list, each with its consequence underneath: the pattern
 * volume stream choice on the Actions screen set, for the bar's other single-choice settings.
 *
 * The choice is held here until Apply, so the live overlay — which reads these at the start of each
 * gesture — is not re-pointed on every tap of a user still making up their mind.
 */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onConfirm: (T) -> Unit,
) {
    var working by remember(selected) { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                options.forEach { option ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = working == option,
                                role = Role.RadioButton,
                                onClick = { working = option },
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The whole row is the target; a second one inside it would let a
                            // screen reader announce the same choice twice.
                            RadioButton(selected = working == option, onClick = null)
                            Spacer(modifier = Modifier.fillMaxWidth(0.03f))
                            Text(text = label(option), style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description(option),
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
