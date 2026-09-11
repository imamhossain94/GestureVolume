package com.newagedevs.gesturevolume.ui.screens.deck

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One launchable app on the device, with its icon already rasterised for the list. */
data class InstalledApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/**
 * Which apps the Deck pins.
 *
 * The whole launcher list is loaded once, off the main thread, with the icons rendered small;
 * the checkbox on each row writes the pinned list straight to the preference, and the pinned
 * rows are shown first so the current selection is never a scroll away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShortcutsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val store = viewModel.preference.deck
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var pinned by remember { mutableStateOf(store.getAppShortcuts()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    fun toggle(pkg: String) {
        pinned = if (pkg in pinned) pinned - pkg else pinned + pkg
        store.setAppShortcuts(pinned)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deck_apps_title)) },
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
        val list = apps
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.deck_apps_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (list == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) { CircularProgressIndicator() }
                return@Column
            }
            val filtered = remember(list, query) {
                if (query.isBlank()) list
                else list.filter { it.label.contains(query.trim(), ignoreCase = true) || it.packageName.contains(query.trim(), ignoreCase = true) }
            }
            val pinnedApps = remember(filtered, pinned) { pinned.mapNotNull { pkg -> filtered.firstOrNull { it.packageName == pkg } } }
            val others = remember(filtered, pinned) { filtered.filterNot { it.packageName in pinned } }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                if (pinnedApps.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.deck_apps_pinned)) }
                    items(pinnedApps, key = { "p" + it.packageName }) { app ->
                        AppRow(app, checked = true) { toggle(app.packageName) }
                    }
                }
                item { SectionLabel(stringResource(R.string.deck_apps_all)) }
                items(others, key = { it.packageName }) { app ->
                    AppRow(app, checked = false) { toggle(app.packageName) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
internal fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** Every app with a launcher entry, labelled and iconed, sorted by name. */
fun loadLaunchableApps(context: android.content.Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val sizePx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
    return resolved
        .map { it.activityInfo.packageName to it }
        .distinctBy { it.first }
        .filter { it.first != context.packageName }
        .mapNotNull { (pkg, info) ->
            runCatching {
                InstalledApp(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}
