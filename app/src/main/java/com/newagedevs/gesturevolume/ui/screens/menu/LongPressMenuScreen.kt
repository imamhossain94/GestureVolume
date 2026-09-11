package com.newagedevs.gesturevolume.ui.screens.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.overlay.rememberPanelEntrance
import com.newagedevs.gesturevolume.overlay.panelFrame
import com.newagedevs.gesturevolume.overlay.ContextMenuCard
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.components.PanelAnimationSelector
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.ColorPickerControl
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.SliderControl
import com.newagedevs.gesturevolume.ui.screens.handler_action.SettingSwitchItem
import com.newagedevs.gesturevolume.ui.components.PanelThemeSelector
import com.newagedevs.gesturevolume.ui.components.PreviewStage
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.ContextMenuLayout
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.PanelTheme
import com.newagedevs.gesturevolume.utils.HandlerActions

/**
 * Builds the long-press menu: what is in it, what order it is in, and how it is drawn.
 *
 * **Why a screen and not a dialog.** It was a dialog, and it had outgrown one twice over. Its
 * lower half is a list of roughly thirty available actions, which in a dialog means a scrolling
 * region inside a scrolling region inside a bounded box — and the box has to be bounded, or an
 * `AlertDialog` asked for its intrinsic height is taller than the screen. The upper half is a list
 * the user *arranges*, which wants room to see several rows at once while moving one. Neither
 * survives being squeezed into the middle of the screen with a scrim around it.
 *
 * **Why there is no Apply.** The dialog had one, because a dialog is a transaction. A screen is
 * not: everything here is written straight through, like the Quick panel's settings screen, and
 * the menu is rebuilt from preferences the next time it opens. There is nothing that can be half
 * applied, so there is nothing to confirm.
 *
 * **Why it opens with a preview.** Everything below it is a list of names, and a name is a poor
 * description of a menu — the questions people actually have are "how big is it?", "does the last
 * row look odd?", "can I read that label?". The preview is the real
 * [com.newagedevs.gesturevolume.overlay.ContextMenuCard], not a drawing of one, so it answers
 * them by construction and cannot drift from the thing it depicts.
 *
 * One entry cannot be removed: "Hide handler". Since the floating ✕ that used to appear mid-drag
 * was removed, this menu is the only way to put the bar away from the bar itself, and a picker
 * that let the user delete their last route out would be a trap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPressMenuScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val preference = remember { viewModel.preference }

    val shown = remember {
        val placed = preference.getContextMenuOrder()
            .filter { HandlerActionCatalog.entryFor(it) != null }
        // The pinned entries are seeded in if the stored order does not name them. The menu adds
        // them anyway when it is drawn — see HandlerActionCatalog.contextMenuEntries — so without
        // this the screen would list "Hide handler" as available while the menu already shows it.
        val missing = HandlerActions.ALWAYS_IN_CONTEXT_MENU.filterNot { it in placed }
        (placed + missing).toMutableStateList()
    }
    var layout by remember { mutableStateOf(preference.getContextMenuLayout()) }
    var panelTheme by remember { mutableStateOf(preference.getPanelTheme()) }
    var panelAnimation by remember { mutableStateOf(preference.getPanelAnimation()) }
    var animationSpeed by remember { mutableFloatStateOf(preference.getPanelAnimationSpeed()) }
    var menuColor by remember { mutableStateOf(preference.getMenuColor()?.let { Color(it) }) }
    var menuAlpha by remember { mutableIntStateOf(preference.getMenuAlpha()) }
    val menuSurface = remember(menuColor, menuAlpha) {
        menuColor?.let { ((menuAlpha.toLong() and 0xFF) shl 24) or (it.toArgb().toLong() and 0xFFFFFF) }
    }

    // Bumped whenever the entrance is picked, which is what makes the preview play it again.
    var replay by remember { mutableIntStateOf(0) }
    val entrance = rememberPanelEntrance(
        animation = panelAnimation,
        towardLeft = preference.getHandlerPosition() == "Left",
        replayKey = replay,
        speed = animationSpeed,
    )

    // One wallpaper per visit; see the note in HandlerAppearanceScreen. The menu's own chrome is a
    // fixed dark card with no theme to follow, so a photograph behind it is the only honest way to
    // show how much of the screen it covers and how it reads against one.
    val bgImage = remember { viewModel.getNextBackground() }

    fun persist() = preference.setContextMenuOrder(shown.toList())

    val available = HandlerActionCatalog.CONTEXT_MENU_CANDIDATES.filterNot { it.action in shown }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.context_menu_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { padding ->
        // Preview pinned, controls scrolling underneath — the shape the Appearance screen uses.
        // The menu is the one panel whose settings all change how it *looks*, so losing sight of
        // it while you change them is the worst possible arrangement.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.context_menu_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )

                PreviewStage(backgroundImageURL = bgImage) {
                    ContextMenuCard(
                        entries = HandlerActionCatalog.contextMenuEntries(shown.toList()),
                        grid = layout == ContextMenuLayout.GRID,
                        // Inert. This is a picture of the menu, and a tile that ran its action
                        // from the settings screen would be a trap rather than a convenience.
                        onSelect = {},
                        modifier = Modifier
                            .scale(PREVIEW_SCALE)
                            .panelFrame(entrance.value),
                        theme = panelTheme,
                        surfaceOverride = menuSurface,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Column(
            modifier = Modifier
                // Capped and centred. Every row here is a label on the left and three small
                // buttons on the right, and on a landscape phone at full width those two halves
                // end up a hand's breadth apart with nothing between them.
                .widthIn(max = CONTENT_MAX_WIDTH)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.context_menu_layout))
            LayoutSelector(
                layout = layout,
                onLayoutChange = {
                    layout = it
                    preference.setContextMenuLayout(it)
                },
            )

            Spacer(Modifier.height(22.dp))
            PanelThemeSelector(
                theme = panelTheme,
                onThemeChange = {
                    panelTheme = it
                    preference.setPanelTheme(it)
                },
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.context_menu_colour))
            Card {
                SettingSwitchItem(
                    title = stringResource(R.string.context_menu_own_colour),
                    description = stringResource(R.string.context_menu_own_colour_desc),
                    checked = menuColor != null,
                    onCheckedChange = { on ->
                        // Seeded from the material's own surface, so turning it on is a starting
                        // point rather than a blank. Turning it off forgets the colour entirely —
                        // see SharedPref.getMenuColor on why absent and default are not the same.
                        val seed = Color((PanelTheme.menuPalette(panelTheme).surface and 0xFFFFFF).toInt() or (0xFF shl 24))
                        menuColor = if (on) seed else null
                        preference.setMenuColor(if (on) seed.toArgb() else null)
                    },
                )
                if (menuColor != null) {
                    Spacer(Modifier.height(12.dp))
                    ColorPickerControl(
                        label = stringResource(R.string.color),
                        color = menuColor ?: Color.Black,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onColorChange = { menuColor = it; preference.setMenuColor(it.toArgb()) },
                    )
                    Spacer(Modifier.height(12.dp))
                    SliderControl(
                        label = stringResource(R.string.opacity),
                        value = menuAlpha.toFloat(),
                        valueRange = 40f..255f,
                        valueDisplay = "${(menuAlpha / 255f * 100).toInt()}%",
                        borderColor = MaterialTheme.colorScheme.primary,
                        onValueChange = { menuAlpha = it.toInt(); preference.setMenuAlpha(it.toInt()) },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            PanelAnimationSelector(
                animation = panelAnimation,
                onAnimationChange = {
                    panelAnimation = it
                    preference.setPanelAnimation(it)
                    // Replays it on the preview above. Picking an entrance from a list of words
                    // is picking blind; the point of the preview is that the word is followed by
                    // the thing it names.
                    replay++
                },
                speed = animationSpeed,
                onSpeedChange = {
                    animationSpeed = it
                    preference.setPanelAnimationSpeed(it)
                    replay++
                },
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.context_menu_shown))
            Card {
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.context_menu_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                shown.forEachIndexed { index, action ->
                    val entry = HandlerActionCatalog.entryFor(action) ?: return@forEachIndexed
                    ShownRow(
                        entry = entry,
                        position = index + 1,
                        pinned = action in HandlerActions.ALWAYS_IN_CONTEXT_MENU,
                        canMoveUp = index > 0,
                        canMoveDown = index < shown.lastIndex,
                        onMoveUp = { shown.move(index, index - 1); persist() },
                        onMoveDown = { shown.move(index, index + 1); persist() },
                        onRemove = { shown.removeAt(index); persist() },
                    )
                }
            }

            if (available.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionLabel(stringResource(R.string.context_menu_hidden))
                Card {
                    available.forEach { entry ->
                        HiddenRow(
                            entry = entry,
                            onAdd = { shown.add(entry.action); persist() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
        }
        }
    }
}

/** The widest the settings column gets. Beyond this a row is two halves and a gap. */
private val CONTENT_MAX_WIDTH = 560.dp

/**
 * How much the preview is shrunk.
 *
 * The menu is drawn at full size and scaled down, rather than given smaller tiles, because the
 * point of showing the real card is that it is the real card — a preview with its own dimensions
 * would be the second implementation this refactor exists to avoid. Eight-tenths keeps a
 * three-row grid inside the stage.
 */
private const val PREVIEW_SCALE = 0.8f

/** Moves one item without disturbing the rest. */
private fun SnapshotStateList<String>.move(from: Int, to: Int) {
    if (to !in indices) return
    add(to, removeAt(from))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

/** Grid or list, as two halves of one pill. Mirrors the side picker on the Appearance screen. */
@Composable
private fun LayoutSelector(layout: String, onLayoutChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LayoutSelectorHalf(
            label = stringResource(R.string.context_menu_layout_grid),
            selected = layout == ContextMenuLayout.GRID,
            onClick = { onLayoutChange(ContextMenuLayout.GRID) },
            modifier = Modifier.weight(1f),
        )
        LayoutSelectorHalf(
            label = stringResource(R.string.context_menu_layout_list),
            selected = layout == ContextMenuLayout.LIST,
            onClick = { onLayoutChange(ContextMenuLayout.LIST) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LayoutSelectorHalf(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ShownRow(
    entry: HandlerActionCatalog.Entry,
    position: Int,
    pinned: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The position, because the order is the point of this list and a row that has just been
        // moved should be able to say where it landed without the user counting.
        Text(
            text = "$position",
            modifier = Modifier.width(20.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        // The same chip the menu itself puts behind every icon, in this screen's colours. It is
        // what stops a list of settings rows from looking unrelated to the thing it configures.
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = entry.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            if (pinned) {
                Text(
                    text = stringResource(R.string.context_menu_always),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.context_menu_move_up),
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.context_menu_move_down),
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onRemove, enabled = !pinned, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.context_menu_hide_item),
                modifier = Modifier.size(18.dp),
                tint = if (pinned) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun HiddenRow(
    entry: HandlerActionCatalog.Entry,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onAdd)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            ActionIconImage(
                icon = entry.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Icon(
            Icons.Default.Add,
            contentDescription = stringResource(R.string.context_menu_show_item),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
