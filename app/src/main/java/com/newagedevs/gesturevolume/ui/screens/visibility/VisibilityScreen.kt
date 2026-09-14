package com.newagedevs.gesturevolume.ui.screens.visibility

import com.newagedevs.gesturevolume.utils.PermissionNeeds

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SideSelectorHalf
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The apps the bar steps aside in.
 *
 * Two lists, the chosen apps on the left and every app on the right. On its side there is the width
 * for them to stand side by side, each scrolling on its own; upright there is not, so they are the
 * two halves of one selector over a single list. The chosen apps also appear, ticked, among all of
 * them, so an app can be found and unticked from either list.
 *
 * It takes the accessibility service, which is what knows which app is in front, so the screen says
 * so plainly and offers the way to turn it on, rather than letting a tick quietly do nothing.
 *
 * This screen also had Hide in screenshots, which took the bar out of sight on every Volume down
 * press in case Power followed. That blinked the bar whenever the volume went down, and a panel
 * opened by the key still reached the picture, so it is gone. The Deck's Screenshot tile takes a
 * picture with the bar out of it.
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
    // Opens on the chosen apps when there are any to show, and on all of them when there are not.
    var tab by rememberSaveable { mutableIntStateOf(if (hiddenApps.isEmpty()) TAB_ALL else TAB_CHOSEN) }

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
        val header: @Composable (Modifier) -> Unit = { modifier ->
            AppsHeader(
                accessibilityOn = accessibilityOn,
                query = query,
                onQueryChange = { query = it },
                onTurnOnAccessibility = { onOpenPermissions(PermissionNeeds.Permission.ACCESSIBILITY) },
                modifier = modifier,
            )
        }

        if (isLandscape()) {
            // Inside the home screen's widest column, like the preview screens beside this one. The
            // rows pad themselves by the side margin, so the two lists meet with twice it between.
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
                ) {
                    // Their own LazyColumns, not scrolling Columns around them: a lazy list inside a
                    // vertical scroll has no height to measure against and throws.
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item { header(Modifier.padding(horizontal = SIDE_MARGIN)) }
                        item { ListLabel(stringResource(R.string.visibility_apps_chosen)) }
                        chosenRows(list, chosen, noneChosen = hiddenApps.isEmpty(), onToggle = ::toggle)
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item { ListLabel(stringResource(R.string.deck_apps_all)) }
                        allRows(list, filtered, hiddenApps, ::toggle)
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
                        header(Modifier)
                        Spacer(modifier = Modifier.height(8.dp))
                        ListSelector(tab = tab, chosenCount = hiddenApps.size, onTab = { tab = it })
                    }
                }
                if (tab == TAB_CHOSEN) {
                    chosenRows(list, chosen, noneChosen = hiddenApps.isEmpty(), onToggle = ::toggle)
                } else {
                    allRows(list, filtered, hiddenApps, ::toggle)
                }
            }
        }
    }
}

/** Everything above the lists: the heading, what it needs, and the search field both lists share. */
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

/**
 * The chosen apps on the left, all apps on the right, as two halves of one pill: the same segmented
 * control the Appearance screen picks a side with.
 */
@Composable
private fun ListSelector(tab: Int, chosenCount: Int, onTab: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SideSelectorHalf(
            label = stringResource(R.string.visibility_tab_chosen, chosenCount),
            selected = tab == TAB_CHOSEN,
            onClick = { onTab(TAB_CHOSEN) },
            modifier = Modifier.weight(1f),
        )
        SideSelectorHalf(
            label = stringResource(R.string.visibility_tab_all),
            selected = tab == TAB_ALL,
            onClick = { onTab(TAB_ALL) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** The apps the bar is hidden in, a line saying there are none yet, or a spinner while loading. */
private fun LazyListScope.chosenRows(
    list: List<InstalledApp>?,
    chosen: List<InstalledApp>,
    noneChosen: Boolean,
    onToggle: (String) -> Unit,
) {
    when {
        list == null -> loadingRow()
        // Only when nothing is chosen at all. Chosen apps the search has filtered out leave the
        // list empty without that being news.
        noneChosen -> item {
            Text(
                text = stringResource(R.string.visibility_chosen_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        else -> items(chosen, key = { "chosen:" + it.packageName }) { app ->
            AppRow(app, checked = true) { onToggle(app.packageName) }
        }
    }
}

/** Every app, the chosen ones ticked; a spinner until the list has loaded. */
private fun LazyListScope.allRows(
    list: List<InstalledApp>?,
    filtered: List<InstalledApp>,
    hiddenApps: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (list == null) {
        loadingRow()
        return
    }
    items(filtered, key = { it.packageName }) { app ->
        AppRow(app, checked = app.packageName in hiddenApps) { onToggle(app.packageName) }
    }
}

private fun LazyListScope.loadingRow() {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.Center
        ) { CircularProgressIndicator() }
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

private const val TAB_CHOSEN = 0
private const val TAB_ALL = 1

/** The home screen's side margin, and the one [AppRow] pads itself by. */
private val SIDE_MARGIN = 16.dp

/** The home screen's widest column. */
private val LANDSCAPE_MAX_WIDTH = 920.dp
