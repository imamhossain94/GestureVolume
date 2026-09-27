package com.newagedevs.gesturevolume.ui.screens.handler_action

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.AppGestureStore.Slot
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.components.actionDisplayName
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.ui.screens.deck.InstalledApp
import com.newagedevs.gesturevolume.ui.screens.deck.loadLaunchableApps
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The picker as a destination of its own, for the bar's gestures everywhere: what [slot] is set to
 * comes from the view model, and choosing sets it and comes back — where the Actions screen shows
 * whatever the choice asked for, the brightness grant or the accessibility service, since the view
 * model is the one both screens share.
 */
@Composable
fun ActionPickerRoute(
    viewModel: MainViewModel,
    slot: Slot,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val onLeft = remember { viewModel.preference.getHandlerPosition() == "Left" }
    ActionPickerScreen(
        slot = slot,
        currentAction = actionFor(state, slot),
        onLeft = onLeft,
        onBack = onDone,
        onSelect = { action ->
            viewModel.onEvent(setActionEvent(slot, action, context))
            onDone()
        },
    )
}

/**
 * Picks what one gesture does, on a screen of its own.
 *
 * It was a dialog: forty-odd tiles three abreast in a box a little over half the screen, each name
 * at ten points in two lines, and no way to find anything but to read them all. On a screen the
 * list has the room to be a list — each action a full line, its icon big enough to recognise, what
 * it needs said under its name — and it can be searched, and jumped through by its groups.
 *
 * Choosing is the end of it: the choice is made and the screen goes back, as a settings list does.
 *
 * @param subtitle under the title, for a picker that sets the gesture for one app only.
 * @param everywhereAction with [onUseEverywhere], offers "As everywhere" first: for an app's own
 *   gestures, where following the usual setting is a choice the list cannot otherwise show.
 * @param followsEverywhere that option is the one chosen, rather than [currentAction].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActionPickerScreen(
    slot: Slot,
    currentAction: String,
    onLeft: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    subtitle: String? = null,
    everywhereAction: String? = null,
    followsEverywhere: Boolean = false,
    onUseEverywhere: (() -> Unit)? = null,
) {
    // The Launch app line asks which app, on this same screen: the list gives way to the apps.
    var pickingApp by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler { if (pickingApp) pickingApp = false else onBack() }

    val chosen = if (followsEverywhere) null else currentAction
    val launched = chosen?.let { HandlerActions.launchedPackage(it) }
    val groups = remember(slot) { pickerGroups(slot) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (pickingApp) {
                                stringResource(R.string.app_gestures_pick_app)
                            } else {
                                stringResource(gestureName(slot))
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null && !pickingApp) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (pickingApp) pickingApp = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { padding ->
        if (pickingApp) {
            AppChooser(
                selected = launched,
                onPick = { onSelect(HandlerActions.launchApp(it)) },
                modifier = Modifier.padding(padding),
            )
        } else {
            ActionList(
                slot = slot,
                onLeft = onLeft,
                groups = groups,
                chosen = chosen,
                launched = launched,
                query = query,
                onQueryChange = { query = it },
                currentAction = currentAction,
                everywhereAction = everywhereAction.takeIf { onUseEverywhere != null },
                followsEverywhere = followsEverywhere,
                onUseEverywhere = onUseEverywhere,
                onPick = { entry ->
                    if (entry.action == HandlerActions.LAUNCH_APP) pickingApp = true else onSelect(entry.action)
                },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** What [slot] can be set to, grouped: the catalog, with a vertical swipe's own bindings first. */
internal fun pickerGroups(slot: Slot): List<Pair<HandlerActionCatalog.Group, List<HandlerActionCatalog.Entry>>> =
    when (slot) {
        // A vertical swipe can hold anything a tap can — a binding that is not steered fires once,
        // at the top of the stroke — with the ones steered by its length kept together at the
        // head, where the Quick panel, which both directions default to, leads.
        Slot.SWIPE_UP, Slot.SWIPE_DOWN -> {
            val steered = listOfNotNull(HandlerActionCatalog.entryFor(HandlerActions.OPEN_QUICK_SLIDER)) +
                HandlerActionCatalog.swipeAdjust(slot == Slot.SWIPE_UP)
            val rest = HandlerActionCatalog.grouped(allowReposition = false).mapNotNull { (group, entries) ->
                val kept = entries.filterNot { it.action == HandlerActions.OPEN_QUICK_SLIDER }
                if (kept.isEmpty()) null else group to kept
            }
            listOf(HandlerActionCatalog.Group.SWIPE to steered) + rest
        }
        // Moving the bar needs a gesture the finger holds, so only the long press offers it.
        else -> HandlerActionCatalog.grouped(allowReposition = slot == Slot.LONG_PRESS)
    }

/** One line of the list: a group's heading, or an action in it at [index] of [count]. */
private sealed interface Line {
    val key: String

    data class Heading(val group: HandlerActionCatalog.Group) : Line {
        override val key get() = "group:${group.name}"
    }

    data class Option(val entry: HandlerActionCatalog.Entry, val index: Int, val count: Int) : Line {
        override val key get() = "action:${entry.group.name}:${entry.action}"
    }
}

/** The items above the lines in the list: the header, and the search with the group chips. */
private const val LEADING_ITEMS = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionList(
    slot: Slot,
    onLeft: Boolean,
    groups: List<Pair<HandlerActionCatalog.Group, List<HandlerActionCatalog.Entry>>>,
    chosen: String?,
    launched: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    currentAction: String,
    everywhereAction: String?,
    followsEverywhere: Boolean,
    onUseEverywhere: (() -> Unit)?,
    onPick: (HandlerActionCatalog.Entry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = groups.flatMap { (_, entries) -> entries }.associate { it.action to stringResource(it.labelRes) }
    val groupLabels = HandlerActionCatalog.Group.entries.associateWith { stringResource(it.labelRes) }
    val q = query.trim()
    val shown = remember(groups, q, labels) {
        groups.mapNotNull { (group, entries) ->
            val kept = if (q.isEmpty()) {
                entries
            } else {
                entries.filter {
                    labels[it.action].orEmpty().contains(q, ignoreCase = true) ||
                        groupLabels[group].orEmpty().contains(q, ignoreCase = true)
                }
            }
            if (kept.isEmpty()) null else group to kept
        }
    }
    val lines = remember(shown) {
        shown.flatMap { (group, entries) ->
            listOf<Line>(Line.Heading(group)) + entries.mapIndexed { i, e -> Line.Option(e, i, entries.size) }
        }
    }
    val showEverywhere = everywhereAction != null && q.isEmpty()
    val firstLine = LEADING_ITEMS + if (showEverywhere) 1 else 0
    val headingIndex = remember(lines, firstLine) {
        lines.withIndex().filter { it.value is Line.Heading }
            .associate { (it.value as Line.Heading).group to firstLine + it.index }
    }

    val listState = rememberLazyListState()
    val chipState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The group the top of the list is in, for its chip to show it.
    val inView by remember(headingIndex) {
        derivedStateOf {
            val top = listState.firstVisibleItemIndex + 1
            headingIndex.entries.filter { it.value <= top }.maxByOrNull { it.value }?.key
                ?: headingIndex.entries.minByOrNull { it.value }?.key
        }
    }
    // Its chip kept in sight as the list scrolls past the groups, one chip of room before it.
    LaunchedEffect(inView, shown) {
        val at = shown.indexOfFirst { it.first == inView }
        if (at >= 0) chipState.animateScrollToItem((at - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "header") {
            PickerHeader(
                slot = slot,
                onLeft = onLeft,
                currentAction = currentAction,
                followsEverywhere = followsEverywhere,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            )
        }
        stickyHeader(key = "search") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(bottom = 8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.action_picker_search)) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                if (q.isEmpty() && shown.size > 1) {
                    LazyRow(
                        state = chipState,
                        modifier = Modifier.padding(top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shown, key = { it.first.name }) { (group, _) ->
                            FilterChip(
                                selected = group == inView,
                                onClick = {
                                    val target = headingIndex[group] ?: return@FilterChip
                                    // Its heading just under the search, not behind it.
                                    scope.launch { listState.animateScrollToItem(target - 1) }
                                },
                                label = { Text(groupLabels[group].orEmpty()) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
            }
        }
        if (showEverywhere && everywhereAction != null && onUseEverywhere != null) {
            item(key = "everywhere") {
                val entry = HandlerActionCatalog.displayEntryFor(everywhereAction)
                ActionOption(
                    icon = entry?.icon ?: ActionIcon.Res(R.drawable.ic_nothing),
                    label = stringResource(R.string.app_gestures_use_everywhere),
                    caption = if (entry != null) actionDisplayName(everywhereAction) else stringResource(R.string.app_gestures_none),
                    needsAccessibility = false,
                    selected = followsEverywhere,
                    opensMore = false,
                    shape = RoundedCornerShape(20.dp),
                    onClick = onUseEverywhere,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }
        if (lines.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.action_picker_empty, q),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                )
            }
        }
        items(lines, key = { it.key }) { line ->
            when (line) {
                is Line.Heading -> GroupLabel(
                    text = groupLabels[line.group].orEmpty(),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
                )
                is Line.Option -> {
                    val entry = line.entry
                    val isLaunch = entry.action == HandlerActions.LAUNCH_APP
                    ActionOption(
                        icon = if (isLaunch && launched != null) ActionIcon.App(launched) else entry.icon,
                        label = labels[entry.action].orEmpty(),
                        // Which app, for the one line that stands for all of them.
                        caption = if (isLaunch && launched != null && chosen != null) actionDisplayName(chosen) else null,
                        needsAccessibility = entry.needsAccessibility,
                        selected = entry.action == chosen || (isLaunch && launched != null),
                        opensMore = isLaunch,
                        shape = segmentShape(line.index, line.count),
                        onClick = { onPick(entry) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 3.dp),
                    )
                }
            }
        }
    }
}

/** The gesture, how it is done, and what it does now. */
@Composable
private fun PickerHeader(
    slot: Slot,
    onLeft: Boolean,
    currentAction: String,
    followsEverywhere: Boolean,
    modifier: Modifier = Modifier,
) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colours.primaryContainer.copy(alpha = 0.55f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colours.surface.copy(alpha = 0.7f)),
        ) {
            GestureGlyph(
                slot = slot,
                onLeft = onLeft,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(gestureHint(slot)),
                style = MaterialTheme.typography.bodyMedium,
                color = colours.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val entry = HandlerActionCatalog.displayEntryFor(currentAction)
            val name = if (entry != null) actionDisplayName(currentAction) else stringResource(R.string.app_gestures_none)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIconImage(
                    icon = entry?.icon ?: ActionIcon.Res(R.drawable.ic_nothing),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colours.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (followsEverywhere) {
                        stringResource(R.string.app_gestures_as_everywhere, name)
                    } else {
                        stringResource(R.string.action_picker_now, name)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colours.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One action to choose: its icon, its name, and under the name what choosing it asks for — the
 * accessibility service — or, for Launch app, which app. The chosen one is filled and ticked.
 *
 * @param opensMore it leads to a further choice rather than being the choice, so it ends in a
 *   chevron when it is not the one chosen.
 */
@Composable
private fun ActionOption(
    icon: ActionIcon,
    label: String,
    caption: String?,
    needsAccessibility: Boolean,
    selected: Boolean,
    opensMore: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = MaterialTheme.colorScheme
    val ink = if (selected) colours.onPrimaryContainer else colours.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colours.primaryContainer else colours.surfaceVariant.copy(alpha = 0.65f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) colours.primary else colours.surface),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) colours.onPrimary else colours.primary,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    fontSize = 12.sp,
                    color = ink.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (needsAccessibility) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Accessibility,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = ink.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_needs_accessibility_badge),
                        fontSize = 12.sp,
                        color = ink.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
        when {
            selected -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colours.primary,
                modifier = Modifier.size(22.dp),
            )
            opensMore -> Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colours.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Every launchable app, searchable, for the Launch app action. The one already chosen is marked. */
@Composable
private fun AppChooser(selected: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_apps_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        val list = apps
        if (list == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val q = query.trim()
            val shown = if (q.isEmpty()) list else list.filter { it.label.contains(q, ignoreCase = true) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            ) {
                itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                    val isChosen = app.packageName == selected
                    val colours = MaterialTheme.colorScheme
                    Row(
                        modifier = Modifier
                            .padding(bottom = 3.dp)
                            .fillMaxWidth()
                            .clip(segmentShape(index, shown.size))
                            .background(if (isChosen) colours.primaryContainer else colours.surfaceVariant.copy(alpha = 0.65f))
                            .selectable(selected = isChosen, role = Role.RadioButton, onClick = { onPick(app.packageName) })
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(
                                bitmap = icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = app.label,
                            fontSize = 15.sp,
                            fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isChosen) colours.onPrimaryContainer else colours.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isChosen) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = colours.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
