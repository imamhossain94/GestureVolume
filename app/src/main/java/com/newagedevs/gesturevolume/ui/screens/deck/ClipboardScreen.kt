package com.newagedevs.gesturevolume.ui.screens.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import java.text.DateFormat
import java.util.Date

/**
 * The clipboard history, and the two accessibility-backed switches that extend it.
 *
 * Both switches are off by default and both say what they need. Capture is the only thing the
 * accessibility service does that reads anything, and paste-on-tap types into other apps'
 * fields; neither is something to switch on without being told so in plain words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preference = viewModel.preference
    var version by remember { mutableIntStateOf(0) }
    val entries = remember(version) { preference.getClipboardEntries() }
    var capture by remember { mutableStateOf(preference.getClipboardCaptureEnabled()) }
    var autoPaste by remember { mutableStateOf(preference.getClipboardAutoPaste()) }
    var maxItems by remember { mutableIntStateOf(preference.getClipboardMaxItems()) }
    var accessibilityOn by remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }
    var showDisclosure by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = OverlayRuntime.isAccessibilityEnabled(context)
                preference.setAppOpenAdPaused(false)
                version++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showDisclosure = false
                preference.setAcceptedAccessibilityDisclosure(true)
                preference.setAppOpenAdPaused(true)
                try {
                    context.startActivity(OverlayRuntime.accessibilitySettingsIntent())
                } catch (_: Exception) {
                    preference.setAppOpenAdPaused(false)
                }
            },
            onDismiss = { showDisclosure = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clipboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.clipboard_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchItem(
                        title = stringResource(R.string.clipboard_capture_title),
                        description = stringResource(R.string.clipboard_capture_desc),
                        checked = capture,
                        onCheckedChange = { on ->
                            capture = on
                            preference.setClipboardCaptureEnabled(on)
                            // The service subscribes to events only while this is on.
                            OverlayRuntime.accessibilityService?.applyEventSubscription()
                            if (on && !accessibilityOn) showDisclosure = true
                        }
                    )
                    if (capture && !accessibilityOn) NeedsAccessibility { showDisclosure = true }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    SettingSwitchItem(
                        title = stringResource(R.string.clipboard_auto_paste_title),
                        description = stringResource(R.string.clipboard_auto_paste_desc),
                        checked = autoPaste,
                        onCheckedChange = { on ->
                            autoPaste = on
                            preference.setClipboardAutoPaste(on)
                            if (on && !accessibilityOn) showDisclosure = true
                        }
                    )
                    if (autoPaste && !accessibilityOn) NeedsAccessibility { showDisclosure = true }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    SliderControl(
                        label = stringResource(R.string.clipboard_max_items, maxItems),
                        value = maxItems.toFloat(),
                        valueRange = 5f..100f,
                        valueDisplay = maxItems.toString(),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { maxItems = it.toInt(); preference.setClipboardMaxItems(maxItems) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    if (entries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.clipboard_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
                    entries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { copyToClipboard(context, entry.text) }
                                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 4)
                                Text(format.format(Date(entry.timeMillis)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { preference.removeClipboardEntry(entry.id); version++ }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                }
            }
            if (entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { preference.clearClipboardEntries(); version++ },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.clipboard_clear_all)) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NeedsAccessibility(onClick: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    ) {
        Text(
            text = stringResource(R.string.clipboard_needs_accessibility),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
