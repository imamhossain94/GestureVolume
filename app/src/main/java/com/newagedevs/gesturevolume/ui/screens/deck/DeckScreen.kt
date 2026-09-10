package com.newagedevs.gesturevolume.ui.screens.deck

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.screens.handler_action.ActionSettingItem
import com.newagedevs.gesturevolume.ui.screens.handler_action.SectionTitle
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.ColorPickerControl
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.HandlerActions

/**
 * The Deck's home in the app: what it holds, how it looks, and how it opens.
 *
 * Every control writes straight to the preference. The Deck reads its settings each time it
 * opens, so there is nothing to apply and nothing to discard; a slider moved here is a Deck
 * changed the next time it is pulled out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val preference = viewModel.preference
    val store = preference.deck

    // Re-read the counts every time the screen is returned to, from the sub-screens.
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) version++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tilesOn = remember(version) { DeckTiles.visible(store.getTileOrder(), store.getEnabledTiles()).size }
    val appsCount = remember(version) { store.getAppShortcuts().size }
    val quickDialCount = remember(version) { store.getQuickDial().size }
    val notesCount = remember(version) { store.getNotes().size }
    val clipboardCount = remember(version) { preference.getClipboardEntries().size }

    var width by remember { mutableStateOf(store.getWidthDp()) }
    var height by remember { mutableStateOf(store.getHeightFraction()) }
    var corner by remember { mutableStateOf(store.getCornerRadiusDp()) }
    var alpha by remember { mutableIntStateOf(store.getBackgroundAlpha()) }
    var background by remember { mutableStateOf(Color(store.getBackgroundColor())) }
    var accent by remember { mutableStateOf(Color(store.getAccentColor())) }
    var autoClose by remember { mutableIntStateOf(store.getAutoCloseSeconds()) }
    var utilitiesFirst by remember { mutableStateOf(store.getUtilitiesFirst()) }

    val openers = remember(version) { deckOpeners(preference) }
    // Resolved here rather than inside joinToString: stringResource is composable, and a
    // non-inline lambda is not a composable context.
    val openerNames = openers.map { stringResource(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deck_title)) },
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
            // ---- how it opens -------------------------------------------------------------------
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (openerNames.isEmpty()) {
                                stringResource(R.string.deck_open_with_none)
                            } else {
                                stringResource(R.string.deck_open_with_summary, openerNames.joinToString(", "))
                            },
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    TextButton(onClick = { onNavigate("actions") }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.deck_change_gestures))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- contents -----------------------------------------------------------------------
            SectionTitle(stringResource(R.string.deck_contents_title), MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ActionSettingItem(
                        label = stringResource(R.string.deck_tiles_row),
                        description = stringResource(R.string.deck_tiles_intro),
                        value = pluralStringResource(R.plurals.deck_tiles_count, tilesOn, tilesOn, DeckTiles.ALL.size),
                        icon = ActionIcon.Vector(Icons.Filled.Widgets),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate("deck_tiles") }
                    )
                    Divider()
                    ActionSettingItem(
                        label = stringResource(R.string.deck_apps_row),
                        description = stringResource(R.string.deck_apps_intro),
                        value = pluralStringResource(R.plurals.deck_apps_pinned_count, appsCount, appsCount),
                        icon = ActionIcon.Vector(Icons.Filled.Apps),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate("deck_apps") }
                    )
                    Divider()
                    ActionSettingItem(
                        label = stringResource(R.string.deck_quick_dial_row),
                        description = stringResource(R.string.deck_quick_dial_intro),
                        value = pluralStringResource(R.plurals.deck_quick_dial_count, quickDialCount, quickDialCount),
                        icon = ActionIcon.Vector(Icons.Filled.Call),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate("deck_quick_dial") }
                    )
                    Divider()
                    ActionSettingItem(
                        label = stringResource(R.string.deck_search_row),
                        description = stringResource(R.string.tile_search_desc),
                        value = stringResource(R.string.deck_search_row_desc),
                        icon = ActionIcon.Vector(Icons.Filled.Search),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate("deck_search") }
                    )
                    Divider()
                    ActionSettingItem(
                        label = stringResource(R.string.deck_notes_row),
                        description = stringResource(R.string.tile_notes_desc),
                        value = pluralStringResource(R.plurals.deck_notes_count, notesCount, notesCount),
                        icon = ActionIcon.Vector(Icons.Filled.EditNote),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate("notes") }
                    )
                    Divider()
                    ActionSettingItem(
                        label = stringResource(R.string.deck_clipboard_row),
                        description = stringResource(R.string.tile_clipboard_desc),
                        value = pluralStringResource(R.plurals.deck_clipboard_count, clipboardCount, clipboardCount),
                        icon = ActionIcon.Vector(Icons.Filled.ContentPaste),
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate("clipboard") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- panel --------------------------------------------------------------------------
            SectionTitle(stringResource(R.string.deck_panel_title), MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SliderControl(
                        label = stringResource(R.string.deck_width),
                        value = width,
                        valueRange = 48f..96f,
                        valueDisplay = "${width.toInt()}dp",
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { width = it; store.setWidthDp(it) }
                    )
                    ThinDivider()
                    SliderControl(
                        label = stringResource(R.string.deck_height),
                        value = height,
                        valueRange = 0.3f..0.95f,
                        valueDisplay = "${(height * 100).toInt()}%",
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { height = it; store.setHeightFraction(it) }
                    )
                    ThinDivider()
                    SliderControl(
                        label = stringResource(R.string.deck_corner),
                        value = corner,
                        valueRange = 0f..48f,
                        valueDisplay = "${corner.toInt()}dp",
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { corner = it; store.setCornerRadiusDp(it) }
                    )
                    ThinDivider()
                    SliderControl(
                        label = stringResource(R.string.opacity),
                        value = alpha.toFloat(),
                        valueRange = 60f..255f,
                        valueDisplay = "${(alpha / 255f * 100).toInt()}%",
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { alpha = it.toInt(); store.setBackgroundAlpha(alpha) }
                    )
                    ThinDivider()
                    ColorPickerControl(
                        label = stringResource(R.string.deck_background),
                        color = background,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onColorChange = { background = it; store.setBackgroundColor(it.toArgb()) }
                    )
                    ThinDivider()
                    ColorPickerControl(
                        label = stringResource(R.string.deck_accent),
                        color = accent,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onColorChange = { accent = it; store.setAccentColor(it.toArgb()) }
                    )
                    ThinDivider()
                    Text(
                        text = stringResource(R.string.deck_auto_close),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.deck_auto_close_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf(0, 5, 10, 20, 30).forEach { seconds ->
                            val selected = autoClose == seconds
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { autoClose = seconds; store.setAutoCloseSeconds(seconds) }
                            ) {
                                Text(
                                    text = if (seconds == 0) stringResource(R.string.deck_auto_close_never)
                                    else stringResource(R.string.deck_auto_close_seconds, seconds),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 13.sp,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    ThinDivider()
                    SettingSwitchItem(
                        title = stringResource(R.string.deck_utilities_first),
                        description = stringResource(R.string.deck_utilities_first_desc),
                        checked = utilitiesFirst,
                        onCheckedChange = { utilitiesFirst = it; store.setUtilitiesFirst(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** The gestures currently bound to "Open deck", as label resources, for the summary card. */
private fun deckOpeners(preference: com.newagedevs.gesturevolume.data.local.SharedPref): List<Int> {
    val result = mutableListOf<Int>()
    if (preference.getHandlerSingleTapAction() == HandlerActions.OPEN_DECK) result += R.string.deck_open_with_single_tap
    if (preference.getHandlerDoubleTapAction() == HandlerActions.OPEN_DECK) result += R.string.deck_open_with_double_tap
    if (preference.getHandlerTripleTapAction() == HandlerActions.OPEN_DECK) result += R.string.deck_open_with_triple_tap
    if (preference.getHandlerLongTapAction() == HandlerActions.OPEN_DECK) result += R.string.deck_open_with_long_press
    if (preference.getHandlerSwipeInAction() == HandlerActions.OPEN_DECK) result += R.string.deck_open_with_swipe_in
    if (preference.getHandlerSwipeOutAction() == HandlerActions.OPEN_DECK) result += R.string.deck_open_with_swipe_out
    if (HandlerActions.OPEN_DECK in preference.getContextMenuItems()) result += R.string.deck_open_with_menu
    return result
}

@Composable
private fun Divider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    )
}

/** A tile's icon and name, for the lists that describe tiles. */
@Composable
fun TileGlyph(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    ActionIconImage(
        icon = ActionIcon.Vector(icon),
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.primary
    )
}
