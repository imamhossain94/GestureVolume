package com.newagedevs.gesturevolume.ui.screens.visibility

import com.newagedevs.gesturevolume.utils.PermissionNeeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
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
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.isLandscape
import com.newagedevs.gesturevolume.ui.screens.deck.AppRow
import com.newagedevs.gesturevolume.ui.screens.deck.InstalledApp
import com.newagedevs.gesturevolume.ui.screens.deck.loadLaunchableApps
import com.newagedevs.gesturevolume.ui.screens.handler_action.SectionTitle
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * When the bar steps aside: out of screenshots taken with the buttons, and over the apps the user
 * picks.
 *
 * Both take the accessibility service — it hears the Volume down press, and it knows which app is
 * in front — so the screen says so plainly beside each and offers the way to turn it on, rather
 * than letting a switch quietly do nothing.
 *
 * Upright, the two sections share one scrolling list. On its side that list is a strip a few rows
 * tall under the top bar, and the screenshot card alone fills it, so there the screenshot switch
 * takes two fifths of the width and the app list the other three fifths beside it — the same split
 * the preview screens use — each scrolling on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisibilityScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {},
) {
    val context = LocalContext.current
    val preference = viewModel.preference
    val lifecycleOwner = LocalLifecycleOwner.current

    var hiddenApps by remember { mutableStateOf(preference.getHandlerHiddenApps()) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    var accessibilityOn by remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }
    var hideInScreenshots by remember { mutableStateOf(preference.getHideInScreenshots()) }

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

        val screenshots: @Composable () -> Unit = {
            ScreenshotsSection(
                hideInScreenshots = hideInScreenshots,
                accessibilityOn = accessibilityOn,
                onHideInScreenshotsChange = {
                    hideInScreenshots = it
                    preference.setHideInScreenshots(it)
                    // The volume keys are asked for only while something needs them.
                    OverlayRuntime.accessibilityService?.applyEventSubscription()
                },
                onOpenPermissions = onOpenPermissions,
            )
        }
        val appsHeader: @Composable (Modifier) -> Unit = { modifier ->
            AppsHeader(
                accessibilityOn = accessibilityOn,
                query = query,
                onQueryChange = { query = it },
                onTurnOnAccessibility = { onOpenPermissions(PermissionNeeds.Permission.ACCESSIBILITY) },
                modifier = modifier,
            )
        }

        if (isLandscape()) {
            // Inside the home screen's widest column, like the preview screens beside this one.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = LANDSCAPE_MAX_WIDTH)
                        .fillMaxHeight()
                        .padding(start = SIDE_MARGIN)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(SCREENSHOTS_SHARE)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp)
                    ) {
                        screenshots()
                    }
                    // The app rows pad themselves by the side margin, so the list runs edge to edge
                    // of its column and only what makes up the gap is added here: the rows' own
                    // inset plus this is the same 20dp the preview screens leave between panes.
                    Spacer(modifier = Modifier.width(PANE_GAP - SIDE_MARGIN))
                    // Its own LazyColumn, not a scrolling Column around one: a lazy list inside a
                    // vertical scroll has no height to measure against and throws.
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f - SCREENSHOTS_SHARE)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item { appsHeader(Modifier.padding(horizontal = SIDE_MARGIN)) }
                        appRows(list, chosen, others, ::toggle)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SIDE_MARGIN)
                    ) {
                        screenshots()
                        Spacer(modifier = Modifier.height(24.dp))
                        appsHeader(Modifier)
                    }
                }
                appRows(list, chosen, others, ::toggle)
            }
        }
    }
}

/** Hide in screenshots: the switch, what it needs, and what it cannot do. */
@Composable
private fun ScreenshotsSection(
    hideInScreenshots: Boolean,
    accessibilityOn: Boolean,
    onHideInScreenshotsChange: (Boolean) -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit,
) {
    Column {
        SectionTitle(stringResource(R.string.visibility_screenshots_title), MaterialTheme.colorScheme.primary)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchItem(
                    title = stringResource(R.string.hide_in_screenshots_title),
                    description = stringResource(R.string.hide_in_screenshots_desc),
                    checked = hideInScreenshots,
                    onCheckedChange = onHideInScreenshotsChange,
                )
                if (hideInScreenshots && !accessibilityOn) {
                    PermissionNote(
                        text = stringResource(R.string.hide_in_screenshots_needs_accessibility),
                        onClick = { onOpenPermissions(PermissionNeeds.Permission.ACCESSIBILITY) },
                    )
                }
                Text(
                    text = stringResource(R.string.hide_in_screenshots_limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** Everything above the app list: its heading, what it needs, and the search field. */
@Composable
private fun AppsHeader(
    accessibilityOn: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onTurnOnAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionTitle(stringResource(R.string.visibility_apps_title), MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.visibility_apps_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!accessibilityOn) {
            // The same note as beside every other setting that waits on a permission, and the
            // same way to the Permissions screen.
            PermissionNote(
                text = stringResource(R.string.visibility_apps_needs_accessibility),
                onClick = onTurnOnAccessibility,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

/** The apps already hidden in, then all the rest; a spinner until the list has loaded. */
private fun LazyListScope.appRows(
    list: List<InstalledApp>?,
    chosen: List<InstalledApp>,
    others: List<InstalledApp>,
    onToggle: (String) -> Unit,
) {
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
                AppRow(app, checked = true) { onToggle(app.packageName) }
            }
        }
        item { ListLabel(stringResource(R.string.deck_apps_all)) }
        items(others, key = { it.packageName }) { app ->
            AppRow(app, checked = false) { onToggle(app.packageName) }
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

/** The home screen's side margin, and the one [AppRow] pads itself by. */
private val SIDE_MARGIN = 16.dp

/** The home screen's widest column. */
private val LANDSCAPE_MAX_WIDTH = 920.dp

/** Between the two panes on its side, as on the preview screens. */
private val PANE_GAP = 20.dp

/** How much of the width the screenshot switch takes on its side. */
private const val SCREENSHOTS_SHARE = 0.4f
