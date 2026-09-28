package com.newagedevs.gesturevolume.ui.screens.visibility

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.components.isLandscape
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.ui.screens.deck.InstalledApp
import com.newagedevs.gesturevolume.ui.screens.deck.loadLaunchableApps
import com.newagedevs.gesturevolume.ui.screens.handler_action.segmentShape
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SideSelectorHalf
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.BarBehaviour
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * When the bar shows: only while media plays or a call is on, what it does while the keyboard is
 * open, and the apps it steps aside in.
 *
 * The first two take no permission at all — the overlay hears what is playing and the audio mode on
 * its own, and asks the window manager where the keyboard is. The apps take the accessibility
 * service, which is what knows which app is in front, so the screen says so plainly and offers the
 * way to turn it on, rather than letting a tick quietly do nothing.
 *
 * Laid out as the Actions screen is: each group a card of rows, each row an icon, what it is, and
 * its switch or its tick, the whole row the thing to tap; and the choice of what the bar does while
 * typing on the screen rather than behind a dialog, with its moving animation right under the one
 * choice that moves it. On its side, the settings take two fifths of the width on the left and the
 * apps the rest, as the Actions screen puts its pad beside its gestures.
 *
 * The apps are two lists behind one switch — the apps the bar is hidden in, and all of them — over
 * a search that stays at the top while the list scrolls. The chosen apps also appear, ticked, among
 * all of them, so an app can be found and unticked from either list.
 *
 * This screen also had Hide in screenshots, which took the bar out of sight on every Volume down
 * press in case Power followed. That blinked the bar whenever the volume went down, and a panel
 * opened by the key still reached the picture, so it is gone. The Deck's Screenshot tile takes a
 * picture with the bar out of it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        val landscape = isLandscape()
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
        // Upright the list runs the width of the screen and its rows pad themselves; on its side
        // the pane does.
        val side = if (landscape) 0.dp else SIDE_MARGIN

        // The apps: what they are for, then the search and the switch between the two lists, which
        // stay at the top while the apps scroll under them.
        val appsList: LazyListScope.() -> Unit = {
            item(key = "appsHeader") {
                AppsHeader(
                    accessibilityOn = accessibilityOn,
                    onTurnOnAccessibility = { onOpenPermissions(PermissionNeeds.Permission.ACCESSIBILITY) },
                    modifier = Modifier.padding(start = side, end = side, top = if (landscape) 8.dp else 0.dp),
                )
            }
            stickyHeader(key = "appsSearch") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = side, end = side, top = 4.dp, bottom = 8.dp),
                ) {
                    SearchField(query = query, onQueryChange = { query = it })
                    ListSelector(tab = tab, chosenCount = hiddenApps.size, onTab = { tab = it })
                }
            }
            if (tab == TAB_CHOSEN) {
                chosenRows(list, chosen, noneChosen = hiddenApps.isEmpty(), side = side, onToggle = ::toggle)
            } else {
                allRows(list, filtered, hiddenApps, side = side, onToggle = ::toggle)
            }
        }

        if (landscape) {
            // Inside the home screen's widest column and margins, like the Actions screen beside it.
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
                        .padding(horizontal = SIDE_MARGIN),
                    horizontalArrangement = Arrangement.spacedBy(PANE_GAP),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(SETTINGS_SHARE)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(top = 8.dp, bottom = 24.dp),
                    ) {
                        AutoHideSection(preference)
                    }
                    // A LazyColumn of its own, not a scrolling Column around one: a lazy list inside
                    // a vertical scroll has no height to measure against and throws.
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f - SETTINGS_SHARE)
                            .fillMaxHeight()
                            .imePadding(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        content = appsList,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "settings") {
                    AutoHideSection(
                        preference,
                        modifier = Modifier.padding(start = SIDE_MARGIN, end = SIDE_MARGIN, top = 8.dp, bottom = 24.dp),
                    )
                }
                appsList()
            }
        }
    }
}

/**
 * The bar's two other reasons to step aside, above the apps: nothing it was asked to show for is
 * happening, and the keyboard is open.
 *
 * Both are read live by the overlay, so a change here only has to ask it to look again.
 */
@Composable
private fun AutoHideSection(preference: SharedPref, modifier: Modifier = Modifier) {
    var onlyMedia by remember { mutableStateOf(preference.getShowOnlyWhileMedia()) }
    var onlyCall by remember { mutableStateOf(preference.getShowOnlyWhileCall()) }
    var keyboard by remember { mutableStateOf(preference.getKeyboardBehaviour()) }
    var motion by remember { mutableStateOf(preference.getKeyboardMotion()) }
    // Below Android 11 there is no keyboard to be told about. See ImeProbe.
    val keyboardSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Column(modifier = modifier) {
        // ---- when it shows ------------------------------------------------------------------
        Heading(stringResource(R.string.visibility_auto_title), stringResource(R.string.visibility_auto_desc))
        Segments {
            ToggleRow(
                icon = Icons.Filled.MusicNote,
                title = stringResource(R.string.visibility_media_title),
                description = stringResource(R.string.visibility_media_desc),
                checked = onlyMedia,
                shape = segmentShape(0, 2),
                onCheckedChange = {
                    onlyMedia = it
                    preference.setShowOnlyWhileMedia(it)
                    OverlayRuntime.activeController?.refreshAutoHide()
                },
            )
            ToggleRow(
                icon = Icons.Filled.Call,
                title = stringResource(R.string.visibility_call_title),
                description = stringResource(R.string.visibility_call_desc),
                checked = onlyCall,
                shape = segmentShape(1, 2),
                onCheckedChange = {
                    onlyCall = it
                    preference.setShowOnlyWhileCall(it)
                    OverlayRuntime.activeController?.refreshAutoHide()
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ---- while typing -------------------------------------------------------------------
        // The three answers side by side on the screen, each with what it means, as the Actions
        // screen shows the volume stream's: a dialog hid them until it was opened.
        Heading(stringResource(R.string.visibility_keyboard_title), stringResource(if (keyboardSupported) R.string.visibility_keyboard_desc else R.string.keyboard_needs_android_11))
        if (keyboardSupported) {
            val moving = keyboard == BarBehaviour.KEYBOARD_MOVE
            val count = BarBehaviour.KEYBOARD_BEHAVIOURS.size + if (moving) 1 else 0
            Segments(modifier = Modifier.selectableGroup()) {
                var at = 0
                BarBehaviour.KEYBOARD_BEHAVIOURS.forEach { behaviour ->
                    ChoiceRow(
                        icon = keyboardIcon(behaviour),
                        title = stringResource(keyboardLabel(behaviour)),
                        description = stringResource(keyboardDescription(behaviour)),
                        selected = keyboard == behaviour,
                        shape = segmentShape(at++, count),
                        onClick = {
                            keyboard = behaviour
                            preference.setKeyboardBehaviour(behaviour)
                            OverlayRuntime.activeController?.refreshAutoHide()
                        },
                    )
                    // How it moves, right under the one choice that moves it. Read by the overlay
                    // on every move.
                    if (behaviour == BarBehaviour.KEYBOARD_MOVE) {
                        val shape = segmentShape(if (moving) at else 0, count)
                        if (moving) at++
                        AnimatedVisibility(
                            visible = moving,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            KeyboardMotionSelector(
                                motion = motion,
                                onMotionChange = {
                                    motion = it
                                    preference.setKeyboardMotion(it)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    .padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun keyboardLabel(behaviour: String): Int = when (behaviour) {
    BarBehaviour.KEYBOARD_HIDE -> R.string.keyboard_hide
    BarBehaviour.KEYBOARD_STAY -> R.string.keyboard_stay
    else -> R.string.keyboard_move
}

private fun keyboardDescription(behaviour: String): Int = when (behaviour) {
    BarBehaviour.KEYBOARD_HIDE -> R.string.keyboard_hide_desc
    BarBehaviour.KEYBOARD_STAY -> R.string.keyboard_stay_desc
    else -> R.string.keyboard_move_desc
}

private fun keyboardIcon(behaviour: String): ImageVector = when (behaviour) {
    BarBehaviour.KEYBOARD_HIDE -> Icons.Filled.VisibilityOff
    BarBehaviour.KEYBOARD_STAY -> Icons.Filled.PushPin
    else -> Icons.Filled.KeyboardDoubleArrowUp
}

/** The apps' heading, what they are for, and what they need. */
@Composable
private fun AppsHeader(
    accessibilityOn: Boolean,
    onTurnOnAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Heading(stringResource(R.string.visibility_apps_title), stringResource(R.string.visibility_apps_desc))
        if (!accessibilityOn) {
            // The same note as beside every other setting that waits on a permission, and the
            // same way to the Permissions screen.
            PermissionNote(
                text = stringResource(R.string.visibility_apps_needs_accessibility),
                onClick = onTurnOnAccessibility,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_apps_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The apps the bar is hidden in, and all of them, as two halves of one pill: the same segmented
 * control the Appearance screen picks a side with.
 */
@Composable
private fun ListSelector(tab: Int, chosenCount: Int, onTab: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
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
    side: Dp,
    onToggle: (String) -> Unit,
) {
    when {
        list == null -> loadingRow()
        // Only when nothing is chosen at all. Chosen apps the search has filtered out leave the
        // list empty without that being news.
        noneChosen -> item(key = "none") {
            Text(
                text = stringResource(R.string.visibility_chosen_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = side + 8.dp, vertical = 16.dp),
            )
        }
        else -> itemsIndexed(chosen, key = { _, app -> "chosen:" + app.packageName }) { index, app ->
            AppRow(app, hidden = true, shape = segmentShape(index, chosen.size), side = side) { onToggle(app.packageName) }
        }
    }
}

/** Every app, the chosen ones ticked; a spinner until the list has loaded. */
private fun LazyListScope.allRows(
    list: List<InstalledApp>?,
    filtered: List<InstalledApp>,
    hiddenApps: Set<String>,
    side: Dp,
    onToggle: (String) -> Unit,
) {
    if (list == null) {
        loadingRow()
        return
    }
    itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
        AppRow(
            app,
            hidden = app.packageName in hiddenApps,
            shape = segmentShape(index, filtered.size),
            side = side,
        ) { onToggle(app.packageName) }
    }
}

private fun LazyListScope.loadingRow() {
    item(key = "loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

/** Rows grouped into one card, a hairline of the page between them. See [segmentShape]. */
@Composable
private fun Segments(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

/** A group's heading, in the Actions screen's style, and the line under it. */
@Composable
private fun Heading(title: String, hint: String) {
    Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A row's icon, on a tile: in the accent when what the row sets is on. */
@Composable
private fun IconTile(icon: ImageVector, on: Boolean) {
    val colours = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (on) colours.primary else colours.primaryContainer.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (on) colours.onPrimary else colours.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** A setting that is on or off: its icon, what it does, and its switch, the whole row the switch. */
@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colours.surfaceVariant.copy(alpha = 0.65f))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, on = checked)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colours.onSurface)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colours.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        // The row is the target; the switch only shows where it stands.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** One of a few answers: its icon, what it means, and a tick on the one chosen. */
@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val ink = if (selected) colours.onPrimaryContainer else colours.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colours.primaryContainer else colours.surfaceVariant.copy(alpha = 0.65f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, on = selected)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Tick(selected)
    }
}

/** A filled tick for the chosen, an empty ring for the rest. */
@Composable
private fun Tick(selected: Boolean) {
    Icon(
        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(24.dp),
    )
}

/** An app, and whether the bar steps aside in it: the whole row the tick, filled while it does. */
@Composable
private fun AppRow(app: InstalledApp, hidden: Boolean, shape: Shape, side: Dp, onToggle: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    val ink = if (hidden) colours.onPrimaryContainer else colours.onSurface
    Row(
        modifier = Modifier
            .padding(start = side, end = side, bottom = 3.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (hidden) colours.primaryContainer else colours.surfaceVariant.copy(alpha = 0.65f))
            .toggleable(value = hidden, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = app.icon
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                fontSize = 15.sp,
                fontWeight = if (hidden) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = ink.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Tick(hidden)
    }
}

private const val TAB_CHOSEN = 0
private const val TAB_ALL = 1

/** The home screen's side margin. */
private val SIDE_MARGIN = 16.dp

/** The home screen's widest column. */
private val LANDSCAPE_MAX_WIDTH = 920.dp

/** Between the two panes on its side: wider than a card gap, so they read as two. */
private val PANE_GAP = 20.dp

/** How much of the width the settings take on its side, as the Actions screen's pad does. */
private const val SETTINGS_SHARE = 0.4f
