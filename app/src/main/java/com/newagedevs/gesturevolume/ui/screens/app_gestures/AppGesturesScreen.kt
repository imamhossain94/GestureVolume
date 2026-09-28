package com.newagedevs.gesturevolume.ui.screens.app_gestures

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import com.newagedevs.gesturevolume.ui.motion.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.newagedevs.gesturevolume.ui.motion.IconButton
import androidx.compose.material3.MaterialTheme
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.AppGestureStore
import com.newagedevs.gesturevolume.data.local.AppGestureStore.Slot
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.actionDisplayName
import com.newagedevs.gesturevolume.ui.screens.deck.InstalledApp
import com.newagedevs.gesturevolume.ui.screens.deck.loadLaunchableApps
import com.newagedevs.gesturevolume.ui.screens.handler_action.ActionPickerScreen
import com.newagedevs.gesturevolume.ui.screens.handler_action.GestureRow
import com.newagedevs.gesturevolume.ui.screens.handler_action.segmentShape
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What [slot] does everywhere, which an app's own gesture replaces while that app is in front. */
private fun everywhereAction(preference: SharedPref, slot: Slot): String = when (slot) {
    Slot.SINGLE_TAP -> preference.getHandlerSingleTapAction()
    Slot.DOUBLE_TAP -> preference.getHandlerDoubleTapAction()
    Slot.TRIPLE_TAP -> preference.getHandlerTripleTapAction()
    Slot.LONG_PRESS -> preference.getHandlerLongTapAction()
    Slot.SWIPE_UP -> preference.getHandlerSwipeUpAction()
    Slot.SWIPE_DOWN -> preference.getHandlerSwipeDownAction()
    Slot.SWIPE_IN -> preference.getHandlerSwipeInAction()
    Slot.SWIPE_OUT -> preference.getHandlerSwipeOutAction()
}

/**
 * Gestures that do something else in the apps the user chooses.
 *
 * Three views in one screen, because each is a step of one task: the apps that have gestures of
 * their own, a list to add one from, and one app's eight gestures. Back walks back through them
 * before it leaves.
 *
 * An app's gesture is either its own action or "as everywhere", which follows the Actions screen —
 * so a gesture left alone here keeps up with changes made there. The pickers are the Actions
 * screen's own, with one more button that puts a gesture back to that.
 *
 * Knowing which app is in front takes the accessibility service, as hiding the bar in chosen apps
 * does, and the screen says so the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppGesturesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit = {},
) {
    val context = LocalContext.current
    val preference = viewModel.preference
    val store = preference.appGestures
    val lifecycleOwner = LocalLifecycleOwner.current

    var profiles by remember { mutableStateOf(store.getProfiles()) }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    /** The app whose gestures are open, or null for the list. */
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    /** The gesture whose action is being chosen, for the app being edited: a screen of its own. */
    var pickingSlot by rememberSaveable { mutableStateOf<Slot?>(null) }
    var accessibilityOn by remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }
    // Bumped on every return, so the notes under the gestures re-read what has been granted since.
    var permissionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = OverlayRuntime.isAccessibilityEnabled(context)
                permissionTick++
                preference.setAppOpenAdPaused(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refresh() {
        profiles = store.getProfiles()
        // The service watches for the app in front only while some app has a gesture of its own.
        OverlayRuntime.accessibilityService?.applyEventSubscription()
    }

    BackHandler(enabled = editing != null || picking) {
        if (picking) picking = false else editing = null
    }

    val appsByPackage = remember(apps) { apps.orEmpty().associateBy { it.packageName } }
    val editingApp = editing?.let { appsByPackage[it] }

    // Choosing a gesture's action takes the screen, as it does for the bar's own gestures.
    val choosing = pickingSlot
    val choosingFor = editing
    if (choosing != null && choosingFor != null) {
        val own = profiles[choosingFor].orEmpty()[choosing]
        val everywhere = everywhereAction(preference, choosing)
        ActionPickerScreen(
            slot = choosing,
            currentAction = own ?: everywhere,
            onLeft = remember { preference.getHandlerPosition() == "Left" },
            subtitle = editingApp?.label ?: choosingFor,
            everywhereAction = everywhere,
            followsEverywhere = own == null,
            onBack = { pickingSlot = null },
            onSelect = { action ->
                store.setAction(choosingFor, choosing, action)
                refresh()
                pickingSlot = null
            },
            onUseEverywhere = {
                store.setAction(choosingFor, choosing, null)
                refresh()
                pickingSlot = null
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            picking -> stringResource(R.string.app_gestures_pick_app)
                            editing != null -> editingApp?.label ?: editing.orEmpty()
                            else -> stringResource(R.string.app_gestures_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            picking -> picking = false
                            editing != null -> editing = null
                            else -> onNavigateBack()
                        }
                    }) {
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
        val accessibilityNote: @Composable () -> Unit = {
            if (!accessibilityOn) {
                PermissionNote(
                    text = stringResource(R.string.visibility_apps_needs_accessibility),
                    onClick = { onOpenPermissions(PermissionNeeds.Permission.ACCESSIBILITY) },
                )
            }
        }
        val current = editing
        when {
            picking -> AppPicker(
                apps = apps,
                exclude = profiles.keys,
                modifier = Modifier.padding(padding),
                onPick = { pkg ->
                    store.addApp(pkg)
                    refresh()
                    picking = false
                    editing = pkg
                }
            )
            current != null -> ProfileEditor(
                app = editingApp,
                profile = profiles[current].orEmpty(),
                preference = preference,
                permissionTick = permissionTick,
                accessibilityNote = accessibilityNote,
                onOpenPermissions = onOpenPermissions,
                modifier = Modifier.padding(padding),
                onPick = { pickingSlot = it },
                onRemove = {
                    store.removeApp(current)
                    refresh()
                    editing = null
                }
            )
            else -> ProfileList(
                profiles = profiles,
                appsByPackage = appsByPackage,
                appsLoaded = apps != null,
                accessibilityNote = accessibilityNote,
                modifier = Modifier.padding(padding),
                onOpen = { editing = it },
                onAdd = { picking = true }
            )
        }
    }
}

/** The apps that have gestures of their own, and the way to add another. */
@Composable
private fun ProfileList(
    profiles: Map<String, Map<Slot, String>>,
    appsByPackage: Map<String, InstalledApp>,
    appsLoaded: Boolean,
    accessibilityNote: @Composable () -> Unit,
    modifier: Modifier,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_gestures_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        accessibilityNote()
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.app_gestures_add))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                if (profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.app_gestures_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                // Alphabetical by what the user sees, once the labels are in; by package until then.
                val ordered = profiles.keys.sortedBy { (appsByPackage[it]?.label ?: it).lowercase() }
                ordered.forEachIndexed { index, pkg ->
                    val changed = profiles[pkg].orEmpty().size
                    AppLine(
                        app = appsByPackage[pkg],
                        fallbackLabel = pkg,
                        subtitle = if (changed == 0) {
                            stringResource(R.string.app_gestures_unchanged)
                        } else {
                            pluralStringResource(R.plurals.app_gestures_changed_count, changed, changed)
                        },
                        onClick = { onOpen(pkg) }
                    )
                    if (index < ordered.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }
                }
                if (!appsLoaded && profiles.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** One app's eight gestures, each its own or as everywhere, and the way to remove the app. */
@Composable
private fun ProfileEditor(
    app: InstalledApp?,
    profile: Map<Slot, String>,
    preference: SharedPref,
    permissionTick: Int,
    accessibilityNote: @Composable () -> Unit,
    onOpenPermissions: (PermissionNeeds.Permission?) -> Unit,
    modifier: Modifier,
    onPick: (Slot) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (app != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app)
                Spacer(modifier = Modifier.width(12.dp))
                Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        accessibilityNote()
        Spacer(modifier = Modifier.height(8.dp))
        val onLeft = remember { preference.getHandlerPosition() == "Left" }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Slot.entries.forEachIndexed { index, slot ->
                val own = profile[slot]
                val effective = own ?: everywhereAction(preference, slot)
                val entry = HandlerActionCatalog.displayEntryFor(effective)
                val actionName = if (entry != null) actionDisplayName(effective) else stringResource(R.string.app_gestures_none)
                GestureRow(
                    slot = slot,
                    onLeft = onLeft,
                    actionIcon = entry?.icon ?: ActionIcon.Res(R.drawable.ic_nothing),
                    // An app's own gesture in full colour; one following everywhere, faded.
                    actionText = if (own != null) actionName else stringResource(R.string.app_gestures_as_everywhere, actionName),
                    dimmed = own == null,
                    shape = segmentShape(index, Slot.entries.size),
                    onClick = { onPick(slot) },
                ) {
                    if (own != null) {
                        @Suppress("UNUSED_VARIABLE") val tick = permissionTick
                        PermissionNeeds.missingFor(context, preference, own)?.let {
                            PermissionNote(
                                missing = it,
                                onOpenPermissions = onOpenPermissions,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRemove, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.app_gestures_remove))
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Every launchable app not already listed, searchable, to add one from. */
@Composable
private fun AppPicker(
    apps: List<InstalledApp>?,
    exclude: Set<String>,
    modifier: Modifier,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val choices = remember(apps, exclude, query) {
        val q = query.trim()
        apps.orEmpty()
            .filter { it.packageName !in exclude }
            .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_apps_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (apps == null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            }
        } else {
            items(choices, key = { it.packageName }) { app ->
                AppLine(app = app, fallbackLabel = app.packageName, subtitle = null, onClick = { onPick(app.packageName) })
            }
        }
    }
}

@Composable
private fun AppLine(app: InstalledApp?, fallbackLabel: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app != null) AppIcon(app) else Spacer(modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app?.label ?: fallbackLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp) {
    val icon = app.icon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/** How many apps have gestures of their own, for the row on the Actions screen. */
@Composable
fun appGesturesSummary(store: AppGestureStore): String {
    val count = store.getProfiles().size
    return if (count == 0) {
        stringResource(R.string.app_gestures_none)
    } else {
        pluralStringResource(R.plurals.app_gestures_app_count, count, count)
    }
}
