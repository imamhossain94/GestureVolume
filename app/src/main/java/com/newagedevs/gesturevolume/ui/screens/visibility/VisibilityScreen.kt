package com.newagedevs.gesturevolume.ui.screens.visibility

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.service.HandlerTileService
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.ui.screens.deck.AppRow
import com.newagedevs.gesturevolume.ui.screens.deck.InstalledApp
import com.newagedevs.gesturevolume.ui.screens.deck.loadLaunchableApps
import com.newagedevs.gesturevolume.ui.screens.handler_action.SectionTitle
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * When the handler steps aside: over the apps the user picks, and out of its own screenshots.
 *
 * Screen recording gets an explanation and a tile rather than a switch. A switch would be a promise
 * the platform does not let an app keep: nothing tells an overlay that another app has started
 * recording the screen, or that the phone's buttons are about to take a screenshot. The tile makes
 * stepping aside one tap, from the same panel a recording is started from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisibilityScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val preference = viewModel.preference
    val lifecycleOwner = LocalLifecycleOwner.current
    val accent = MaterialTheme.colorScheme.primary

    var hideInScreenshots by remember { mutableStateOf(preference.getHideInScreenshots()) }
    var hiddenApps by remember { mutableStateOf(preference.getHandlerHiddenApps()) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    var accessibilityOn by remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var tileAdded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    // Back from the system's accessibility screen: show what the user did there.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = OverlayRuntime.isAccessibilityEnabled(context)
                preference.setAppOpenAdPaused(false)
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
                // Paused, so coming back from the system screen is not taken for a fresh launch.
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

    fun toggle(pkg: String) {
        hiddenApps = if (pkg in hiddenApps) hiddenApps - pkg else hiddenApps + pkg
        preference.setHandlerHiddenApps(hiddenApps)
        // The service watches for the app in front only while this list has something in it.
        OverlayRuntime.accessibilityService?.applyEventSubscription()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visibility_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
        val list = apps
        val filtered = remember(list, query) {
            val q = query.trim()
            when {
                list == null -> emptyList()
                q.isEmpty() -> list
                else -> list.filter {
                    it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
                }
            }
        }
        val chosen = remember(filtered, hiddenApps) { filtered.filter { it.packageName in hiddenApps } }
        val others = remember(filtered, hiddenApps) { filtered.filterNot { it.packageName in hiddenApps } }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.visibility_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle(stringResource(R.string.visibility_screenshots_title), accent)
                    Card {
                        SettingSwitchItem(
                            title = stringResource(R.string.visibility_hide_in_screenshots),
                            description = stringResource(R.string.visibility_hide_in_screenshots_desc),
                            checked = hideInScreenshots,
                            onCheckedChange = {
                                hideInScreenshots = it
                                preference.setHideInScreenshots(it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionTitle(stringResource(R.string.visibility_recording_title), accent)
                    Card {
                        Text(
                            text = stringResource(R.string.visibility_recording_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        when {
                            tileAdded -> Text(
                                text = stringResource(R.string.visibility_tile_added),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = accent,
                            )

                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Button(
                                onClick = { requestHandlerTile(context) { added -> tileAdded = added } },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.visibility_add_tile))
                            }

                            else -> Text(
                                text = stringResource(R.string.visibility_tile_manual),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionTitle(stringResource(R.string.visibility_apps_title), accent)
                    Text(
                        text = stringResource(R.string.visibility_apps_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!accessibilityOn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDisclosure = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = stringResource(R.string.visibility_apps_needs_accessibility),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            if (list == null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            } else {
                if (chosen.isNotEmpty()) {
                    item { ListLabel(stringResource(R.string.visibility_apps_chosen)) }
                    items(chosen, key = { "hidden:" + it.packageName }) { app ->
                        AppRow(app, checked = true) { toggle(app.packageName) }
                    }
                }
                item { ListLabel(stringResource(R.string.deck_apps_all)) }
                items(others, key = { it.packageName }) { app ->
                    AppRow(app, checked = false) { toggle(app.packageName) }
                }
            }
        }
    }
}

/** Offers the Handler tile through the system's own add-a-tile prompt. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestHandlerTile(context: Context, onResult: (Boolean) -> Unit) {
    val manager = context.getSystemService(StatusBarManager::class.java) ?: return
    runCatching {
        manager.requestAddTileService(
            ComponentName(context, HandlerTileService::class.java),
            context.getString(R.string.tile_handler),
            android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_gesture),
            context.mainExecutor,
        ) { result ->
            onResult(
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            )
        }
    }
}

@Composable
private fun ListLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
