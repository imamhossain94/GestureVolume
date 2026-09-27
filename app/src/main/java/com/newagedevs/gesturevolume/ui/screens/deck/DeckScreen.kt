package com.newagedevs.gesturevolume.ui.screens.deck

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Animation
import com.newagedevs.gesturevolume.ui.components.ChoiceChip
import com.newagedevs.gesturevolume.ui.components.HandleLook
import com.newagedevs.gesturevolume.ui.components.PermissionNote
import com.newagedevs.gesturevolume.ui.util.permissionsRoute
import com.newagedevs.gesturevolume.utils.PermissionNeeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.newagedevs.gesturevolume.ui.motion.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.newagedevs.gesturevolume.overlay.deck.DeckPalette
import com.newagedevs.gesturevolume.overlay.deck.DeckPreviewStrip
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.overlay.panelFrame
import com.newagedevs.gesturevolume.overlay.rememberPanelEntrance
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.components.DemoGesture
import com.newagedevs.gesturevolume.ui.components.GestureDemoOverlay
import com.newagedevs.gesturevolume.ui.components.rememberGestureDemoState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.newagedevs.gesturevolume.ui.components.PanelAnimationSelector
import com.newagedevs.gesturevolume.ui.components.PanelThemeSelector
import com.newagedevs.gesturevolume.ui.components.PreviewSettingsLayout
import com.newagedevs.gesturevolume.ui.components.PreviewStage
import com.newagedevs.gesturevolume.ui.components.panelAnimationLabel
import com.newagedevs.gesturevolume.ui.components.panelAnimationSpeedLabel
import com.newagedevs.gesturevolume.ui.components.panelThemeLabel
import com.newagedevs.gesturevolume.ui.screens.handler_action.ActionSettingItem
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.AppearanceSection
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.ColorPickerControl
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.PanelAnimation
import com.newagedevs.gesturevolume.utils.PanelTheme
import kotlinx.coroutines.delay

/**
 * The Deck's home in the app: what it holds, how it looks, and how it moves.
 *
 * Every control writes straight to the preference. The Deck reads its settings each time it
 * opens, so there is nothing to apply and nothing to discard; a slider moved here is a Deck
 * changed the next time it is pulled out.
 *
 * Grouped behind collapsible headers in the order every panel screen shares — content, size and
 * shape, colours, animation, behaviour — so a setting sits in the same place whichever panel it
 * belongs to. The page opens on what the Deck holds, which is why most people come here; the rest
 * are a tap away rather than a scroll past.
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

    // What the tiles and the search switched on from here still need, re-read on every return.
    val context = androidx.compose.ui.platform.LocalContext.current
    val deckNeeds = remember(version) {
        PermissionNeeds.read(context, preference).forScreen(PermissionNeeds.Screen.DECK)
    }
    val tilesNeed = deckNeeds.firstOrNull { it.feature == PermissionNeeds.Feature.DECK_TILE }?.permission
    val searchNeed = deckNeeds.firstOrNull { it.feature != PermissionNeeds.Feature.DECK_TILE }?.permission
    val openPermissions: (PermissionNeeds.Permission) -> Unit = { onNavigate(permissionsRoute(it)) }

    var panelTheme by remember { mutableStateOf(viewModel.preference.getPanelTheme()) }
    var width by remember { mutableStateOf(store.getWidthDp()) }
    var height by remember { mutableStateOf(store.getHeightFraction()) }
    var corner by remember { mutableStateOf(store.getCornerRadiusDp()) }
    var alpha by remember { mutableIntStateOf(store.getBackgroundAlpha()) }
    var background by remember { mutableStateOf(Color(store.getBackgroundColor())) }
    var accent by remember { mutableStateOf(Color(store.getAccentColor())) }
    var autoClose by remember { mutableIntStateOf(store.getAutoCloseSeconds()) }
    var utilitiesFirst by remember { mutableStateOf(store.getUtilitiesFirst()) }
    var panelAnimation by remember { mutableStateOf(viewModel.preference.getPanelAnimation()) }
    var animationSpeed by remember { mutableFloatStateOf(viewModel.preference.getPanelAnimationSpeed()) }

    val handlerOnLeft = remember { preference.getHandlerPosition() == "Left" }
    // A finger swiping in off the bar with the Deck following it out, twice on arrival and on
    // request after that. Which physical way "out" is follows the stage, which puts the Deck at the
    // start edge when the bar is on the left.
    val gestureDemo = rememberGestureDemoState(DECK_DEMO_STEPS)
    val deckOnLeft = handlerOnLeft != (LocalLayoutDirection.current == LayoutDirection.Rtl)
    val previewTiles = remember(version) {
        DeckTiles.visible(store.getTileOrder(), store.getEnabledTiles())
    }
    // Bumped whenever the entrance is picked, which is what makes the preview play it again.
    var replay by remember { mutableIntStateOf(0) }
    /*
     * Open, a breath, close, and open again, each time an animation is picked. The Deck leaves the
     * way it arrived, run backwards, so a preview that only ever played the arrival showed half of
     * what the choice decides. Ends open, so the page is never left without its preview; picking
     * again part-way through starts the sequence over.
     */
    var previewClosing by remember { mutableStateOf(false) }
    var demo by remember { mutableIntStateOf(0) }
    LaunchedEffect(demo) {
        if (demo == 0) return@LaunchedEffect
        val millis = PanelAnimation.scaledDurationMs(panelAnimation, animationSpeed).toLong()
        previewClosing = false
        replay++
        delay(millis + DEMO_HOLD_MS)
        previewClosing = true
        delay(millis + DEMO_GAP_MS)
        previewClosing = false
        replay++
    }
    val entrance = rememberPanelEntrance(
        panelAnimation,
        handlerOnLeft,
        replay,
        closing = previewClosing,
        speed = animationSpeed,
    )
    val previewPalette = remember(background, accent, alpha, panelTheme) {
        val forced = PanelTheme.panelSurface(panelTheme)
        DeckPalette(
            surface = forced?.let { Color(it) } ?: background.copy(alpha = alpha / 255f),
            accent = accent,
            surfaceAlpha = if (forced != null) 1f else PanelTheme.surfaceAlpha(panelTheme),
        )
    }

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
        // Preview pinned, controls scrolling beside or beneath it. The Deck had no preview at all,
        // so every colour and every number on this screen was set blind and checked by going out
        // and opening the thing.
        PreviewSettingsLayout(
            hint = stringResource(R.string.deck_card_subtitle),
            contentPadding = padding,
            preview = { modifier, fillHeight ->
                PreviewStage(
                    contentAlignment = if (handlerOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
                    fillHeight = fillHeight,
                    modifier = modifier,
                    // The hand reaches in over the frame, as the walkthrough's does.
                    overGlass = {
                        GestureDemoOverlay(
                            state = gestureDemo,
                            barAtStart = handlerOnLeft,
                            barInset = 12.dp,
                            showBar = true,
                            handle = remember { HandleLook.from(preference) },
                            modifier = Modifier.matchParentSize(),
                        )
                    },
                    // Under the phone: what the finger is doing, and the button that plays it.
                    demo = gestureDemo,
                ) {
                    // As tall as the Deck is on the phone, as far as the part of the phone that
                    // shows will take it: the glass is the phone's screen, drawn smaller.
                    val screenHeight = LocalConfiguration.current.let { maxOf(it.screenWidthDp, it.screenHeightDp) }
                    BoxWithConstraints(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = if (handlerOnLeft) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                    DeckPreviewStrip(
                        tiles = previewTiles,
                        palette = previewPalette,
                        widthDp = width,
                        cornerDp = corner,
                        glass = PanelTheme.hasLitEdge(panelTheme),
                        heightDp = minOf(screenHeight * height, maxHeight.value * 0.8f),
                        modifier = Modifier
                            .padding(horizontal = 14.dp)
                            .panelFrame { entrance.value }
                            // In step with the demo's swipe: out from the edge and into view as the
                            // finger crosses the stage. Read in the layer, so the frames of the
                            // demo redraw the strip without recomposing the screen.
                            .graphicsLayer {
                                val reveal = gestureDemo.progressOf(DemoGesture.SWIPE_IN)
                                // `this.`, because the screen's own `alpha` (the background opacity) would shadow it.
                                this.alpha = reveal
                                translationX = (1f - reveal) * 44.dp.toPx() * (if (deckOnLeft) -1f else 1f)
                            },
                    )
                    }
                }
            },
        ) {
            // ---- content ------------------------------------------------------------------------
            // What the Deck holds, and which half of it comes first.
            AppearanceSection(
                title = stringResource(R.string.section_content),
                icon = Icons.Filled.Widgets,
                summary = pluralStringResource(R.plurals.deck_tiles_count, tilesOn, tilesOn, DeckTiles.ALL.size),
                initiallyExpanded = true,
            ) {
                ActionSettingItem(
                    label = stringResource(R.string.deck_tiles_row),
                    description = stringResource(R.string.deck_tiles_intro),
                    value = pluralStringResource(R.plurals.deck_tiles_count, tilesOn, tilesOn, DeckTiles.ALL.size),
                    icon = ActionIcon.Vector(Icons.Filled.Widgets),
                    borderColor = MaterialTheme.colorScheme.primary,
                    onClick = { onNavigate("deck_tiles") }
                )
                tilesNeed?.let { PermissionNote(missing = it, onOpenPermissions = openPermissions) }
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
                searchNeed?.let { PermissionNote(missing = it, onOpenPermissions = openPermissions) }
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
                // The order of what is listed above, so it lives with the list rather than with
                // the panel's looks.
                SettingSwitchItem(
                    title = stringResource(R.string.deck_utilities_first),
                    description = stringResource(R.string.deck_utilities_first_desc),
                    checked = utilitiesFirst,
                    onCheckedChange = { utilitiesFirst = it; store.setUtilitiesFirst(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- size & shape -------------------------------------------------------------------
            AppearanceSection(
                title = stringResource(R.string.section_size_shape),
                icon = Icons.Filled.AspectRatio,
                summary = "${width.toInt()}dp · ${(height * 100).toInt()}% · ${corner.toInt()}dp",
            ) {
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
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- colours ------------------------------------------------------------------------
            // The material first, because it decides how much of the colours under it shows.
            AppearanceSection(
                title = stringResource(R.string.section_colours),
                icon = Icons.Filled.Palette,
                summary = "${stringResource(panelThemeLabel(panelTheme))} · ${(alpha / 255f * 100).toInt()}%",
            ) {
                PanelThemeSelector(
                    theme = panelTheme,
                    onThemeChange = {
                        panelTheme = it
                        viewModel.preference.setPanelTheme(it)
                    },
                )
                ThinDivider()
                ColorPickerControl(
                    label = stringResource(R.string.deck_background),
                    color = background,
                    borderColor = MaterialTheme.colorScheme.primary,
                    onColorChange = { background = it; store.setBackgroundColor(it.toArgb()) }
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
                    label = stringResource(R.string.deck_accent),
                    color = accent,
                    borderColor = MaterialTheme.colorScheme.primary,
                    onColorChange = { accent = it; store.setAccentColor(it.toArgb()) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- animation ----------------------------------------------------------------------
            AppearanceSection(
                title = stringResource(R.string.section_animation),
                icon = Icons.Filled.Animation,
                summary = "${stringResource(panelAnimationLabel(panelAnimation))} · ${panelAnimationSpeedLabel(animationSpeed)}",
            ) {
                // One choice for how every panel moves, shared with the long-press menu and the
                // Quick panel, but offered here too: the Deck's own page is where anyone looks for
                // how the Deck opens and closes.
                PanelAnimationSelector(
                    animation = panelAnimation,
                    onAnimationChange = {
                        panelAnimation = it
                        viewModel.preference.setPanelAnimation(it)
                        demo++
                    },
                    speed = animationSpeed,
                    onSpeedChange = {
                        animationSpeed = it
                        viewModel.preference.setPanelAnimationSpeed(it)
                        demo++
                    },
                    title = stringResource(R.string.deck_animation),
                    description = stringResource(R.string.deck_animation_desc),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- behaviour ----------------------------------------------------------------------
            AppearanceSection(
                title = stringResource(R.string.section_behaviour),
                icon = Icons.Filled.Tune,
                summary = if (autoClose == 0) {
                    stringResource(R.string.deck_auto_close_never)
                } else {
                    stringResource(R.string.deck_auto_close_seconds, autoClose)
                },
            ) {
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
                AutoCloseChips(
                    selected = autoClose,
                    onSelect = { autoClose = it; store.setAutoCloseSeconds(it) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** How long the preview holds the Deck open, and then hidden, between showing its two halves. */
private const val DEMO_HOLD_MS = 700L
private const val DEMO_GAP_MS = 350L

/** Never, or after so many seconds. Wraps, so five chips fit a narrow column in landscape. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoCloseChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(0, 5, 10, 20, 30).forEach { seconds ->
            ChoiceChip(
                label = if (seconds == 0) stringResource(R.string.deck_auto_close_never)
                else stringResource(R.string.deck_auto_close_seconds, seconds),
                selected = selected == seconds,
                onClick = { onSelect(seconds) },
            )
        }
    }
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

/** The Deck's demo: a swipe in off the bar, which is what opens it. */
private val DECK_DEMO_STEPS = listOf(DemoGesture.SWIPE_IN)
