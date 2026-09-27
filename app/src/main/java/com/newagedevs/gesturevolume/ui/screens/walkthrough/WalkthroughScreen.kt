package com.newagedevs.gesturevolume.ui.screens.walkthrough

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.newagedevs.gesturevolume.GestureApplication
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.service.OverlayRuntime
import com.newagedevs.gesturevolume.ui.components.AccessibilityDisclosureDialog
import com.newagedevs.gesturevolume.ui.motion.Button
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import com.newagedevs.gesturevolume.ui.motion.Springs
import com.newagedevs.gesturevolume.ui.motion.TextButton
import com.newagedevs.gesturevolume.ui.motion.pressBounce
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.UserMode

/**
 * Every page the walkthrough can show, in the order they come. Which tutorial pages appear
 * depends on the style chosen on [Style]. The order also gives every move a direction, so a page
 * slides in from the side it sits on, whichever way the user is going.
 */
private enum class WalkPage { Intro, Style, Simple, QuickSlider, Deck, LongPress, Permission, Accessibility }

private fun pagesFor(mode: String): List<WalkPage> = buildList {
    add(WalkPage.Intro)
    add(WalkPage.Style)
    if (mode == UserMode.ADVANCED) {
        add(WalkPage.QuickSlider)
        add(WalkPage.Deck)
        add(WalkPage.LongPress)
    } else {
        add(WalkPage.Simple)
    }
    add(WalkPage.Permission)
    // Advanced only: its defaults are what need the service — volume keys that open the Quick
    // slider at once, and Lock screen and Screenshot on the Deck. Last, and optional, because the
    // bar works without it and cannot work without the page before.
    if (mode == UserMode.ADVANCED) add(WalkPage.Accessibility)
}

/** The pages that ask for a permission, each with Allow and Skip of its own. */
private val WalkPage.asksPermission: Boolean
    get() = this == WalkPage.Permission || this == WalkPage.Accessibility

/** The words on a page. [scene] is null where the card holds something other than a gesture. */
private class PageText(
    @StringRes val chip: Int,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    @StringRes val caption: Int?,
    @StringRes val animation: Int?,
    val scene: WalkScene?,
    val points: List<Int>,
)

private fun pageText(page: WalkPage, mode: String): PageText = when (page) {
    WalkPage.Intro -> PageText(
        R.string.walk_intro_chip, R.string.walk_intro_title, R.string.walk_intro_subtitle,
        R.string.walk_intro_caption, R.string.walk_intro_animation, WalkScene.Intro,
        listOf(R.string.walk_intro_point_1, R.string.walk_intro_point_2, R.string.walk_intro_point_3, R.string.walk_intro_point_4),
    )
    WalkPage.Style -> PageText(
        R.string.walk_style_chip, R.string.walk_style_title, R.string.walk_style_subtitle,
        null, null, null,
        // What the highlighted style brings, so the grid answers "what do I get" as the cards flip.
        if (mode == UserMode.ADVANCED) {
            listOf(R.string.walk_style_advanced_point_1, R.string.walk_style_advanced_point_2, R.string.walk_style_advanced_point_3, R.string.walk_style_point_change)
        } else {
            listOf(R.string.walk_style_simple_point_1, R.string.walk_style_simple_point_2, R.string.walk_style_simple_point_3, R.string.walk_style_point_change)
        },
    )
    WalkPage.Simple -> PageText(
        R.string.walk_simple_chip, R.string.walk_simple_title, R.string.walk_simple_subtitle,
        R.string.walk_simple_caption, R.string.walk_simple_animation, WalkScene.Simple,
        listOf(R.string.walk_simple_point_1, R.string.walk_simple_point_2, R.string.walk_simple_point_3, R.string.walk_simple_point_4),
    )
    WalkPage.QuickSlider -> PageText(
        R.string.walk_slider_chip, R.string.walk_slider_title, R.string.walk_slider_subtitle,
        R.string.walk_slider_caption, R.string.walk_slider_animation, WalkScene.QuickSlider,
        listOf(R.string.walk_slider_point_1, R.string.walk_slider_point_2, R.string.walk_slider_point_3, R.string.walk_slider_point_4),
    )
    WalkPage.Deck -> PageText(
        R.string.walk_deck_chip, R.string.walk_deck_title, R.string.walk_deck_subtitle,
        R.string.walk_deck_caption, R.string.walk_deck_animation, WalkScene.Deck,
        listOf(R.string.walk_deck_point_1, R.string.walk_deck_point_2, R.string.walk_deck_point_3, R.string.walk_deck_point_4),
    )
    WalkPage.LongPress -> PageText(
        R.string.walk_menu_chip, R.string.walk_menu_title, R.string.walk_menu_subtitle,
        R.string.walk_menu_caption, R.string.walk_menu_animation, WalkScene.LongPress,
        listOf(R.string.walk_menu_point_1, R.string.walk_menu_point_2, R.string.walk_menu_point_3, R.string.walk_menu_point_4),
    )
    WalkPage.Permission -> PageText(
        R.string.walk_permission_chip, R.string.walk_permission_title, R.string.walk_permission_subtitle,
        null, R.string.walk_permission_animation, WalkScene.Permission,
        listOf(R.string.walk_permission_point_1, R.string.walk_permission_point_2, R.string.walk_permission_point_3, R.string.walk_permission_point_4),
    )
    WalkPage.Accessibility -> PageText(
        R.string.walk_access_chip, R.string.walk_access_title, R.string.walk_access_subtitle,
        null, R.string.walk_access_animation, WalkScene.Accessibility,
        listOf(R.string.walk_access_point_1, R.string.walk_access_point_2, R.string.walk_access_point_3, R.string.walk_access_point_4),
    )
}

private val CardShape = RoundedCornerShape(28.dp)
private val ActionShape = RoundedCornerShape(18.dp)
private val Granted = Color(0xFF10B981)
private val EdgeDot = Color(0xFFEF4444)

@Composable
fun WalkthroughScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.preference.setAppOpenAdPaused(true)
        onDispose {
            viewModel.preference.setAppOpenAdPaused(false)
        }
    }

    // The highlighted style is only a leaning until Continue commits it. The pages after the
    // choice follow the leaning, so flipping between the two cards changes the count as well.
    var selectedMode by rememberSaveable { mutableStateOf(UserMode.REGULAR) }
    var modeChosen by rememberSaveable { mutableStateOf(false) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    // A second tap on Start or Skip while the first is navigating away must not finish twice.
    var finished by remember { mutableStateOf(false) }
    val pages = remember(selectedMode) { pagesFor(selectedMode) }
    val index = pageIndex.coerceIn(0, pages.lastIndex)
    val page = pages[index]

    val hasOverlayPermission = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val hasAccessibility = remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    // Checked on every return to the screen too, not only when the settings screen hands back a
    // result: some phones come back without one, and the switch can be flipped from elsewhere. The
    // accessibility list never hands anything back, so this is the only way it is ever seen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasOverlayPermission.value = Settings.canDrawOverlays(context)
        hasAccessibility.value = OverlayRuntime.isAccessibilityEnabled(context)
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Resume app open ads now that the user has returned from the overlay permission screen
        viewModel.preference.setAppOpenAdPaused(false)
        hasOverlayPermission.value = Settings.canDrawOverlays(context)
    }

    fun goTo(target: Int) {
        pageIndex = target.coerceIn(0, pages.lastIndex)
    }

    fun finish() {
        if (finished) return
        finished = true
        // Leaving before the choice is leaving as a regular user: the style that needs no
        // explaining, and the one a skipped walkthrough has taught nothing more than.
        if (!modeChosen) viewModel.chooseUserMode(UserMode.REGULAR)
        viewModel.preference.setFirstLaunchCompleted()
        // Onboarding done — now safe to init ads + consent flow off the walkthrough.
        (context.applicationContext as? GestureApplication)?.initializeAdsIfNeeded()
        onComplete()
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri()
        )
        // Pause ads while the user is in the overlay permission screen
        viewModel.preference.setAppOpenAdPaused(true)
        overlayPermissionLauncher.launch(intent)
    }

    val isLastPage = index == pages.lastIndex

    /** On to the next page, or out of the walkthrough from the last. */
    fun next() {
        if (isLastPage) finish() else goTo(index + 1)
    }

    fun onPrimary() {
        when (page) {
            WalkPage.Style -> {
                viewModel.chooseUserMode(selectedMode)
                modeChosen = true
                goTo(index + 1)
            }
            WalkPage.Permission -> if (hasOverlayPermission.value) next() else requestOverlayPermission()
            // The disclosure first, every time, as everywhere else the app asks for the service.
            WalkPage.Accessibility -> if (hasAccessibility.value) next() else showAccessibilityDisclosure = true
            else -> goTo(index + 1)
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAccept = {
                showAccessibilityDisclosure = false
                viewModel.preference.setAcceptedAccessibilityDisclosure(true)
                // Kept paused on the way back as well: the walkthrough lifts it when it closes.
                viewModel.preference.setAppOpenAdPaused(true)
                try {
                    context.startActivity(OverlayRuntime.accessibilitySettingsIntent())
                } catch (_: Exception) {
                    // No accessibility list on this build: nothing to wait for, so on it goes.
                    next()
                }
            },
            onDismiss = { showAccessibilityDisclosure = false },
        )
    }

    // Back steps back through the pages; only on the first does it leave.
    BackHandler(enabled = index > 0) { goTo(index - 1) }

    val colours = MaterialTheme.colorScheme
    // A warm wash over the theme's background: peach on light, a faint ember on dark, so the
    // pages feel like a welcome without fighting the theme.
    val warm = if (colours.background.luminance() > 0.5f) Color(0xFFFFE9D6) else Color(0xFF3A2A20)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colours.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(warm, colours.background)))
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            TopRow(
                index = index,
                count = pages.size,
                // The permission pages have their own Skip beside Allow; two would be one too many.
                showSkip = !page.asksPermission,
                onSkip = ::finish,
            )

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(Springs.ScreenOffset) { width -> direction * width / 4 } + fadeIn(Springs.ScreenFade))
                        .togetherWith(slideOutHorizontally(Springs.ScreenOffset) { width -> -direction * width / 4 } + fadeOut(Springs.ScreenFade))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "walkthrough-page",
            ) { shown ->
                PageContent(
                    page = shown,
                    isLastTutorial = shown == pages.getOrNull(pages.indexOf(WalkPage.Permission) - 1),
                    isLastPage = shown == pages.last(),
                    selectedMode = selectedMode,
                    onSelectMode = { selectedMode = it },
                    granted = when (shown) {
                        WalkPage.Accessibility -> hasAccessibility.value
                        else -> hasOverlayPermission.value
                    },
                    onPrimary = ::onPrimary,
                    // Skips this permission, not the rest: the Advanced walkthrough still has the
                    // accessibility page to offer after the overlay one.
                    onSkip = ::next,
                )
            }
        }
    }
}

@Composable
private fun TopRow(index: Int, count: Int, showSkip: Boolean, onSkip: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until count) {
                val width by animateDpAsState(
                    targetValue = if (i == index) 20.dp else 6.dp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                    label = "progress-width",
                )
                val colour by animateColorAsState(
                    targetValue = if (i <= index) colours.primary else colours.onSurface.copy(alpha = 0.15f),
                    label = "progress-colour",
                )
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(colour)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.walk_progress, index + 1, count),
            style = MaterialTheme.typography.labelMedium,
            color = colours.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showSkip) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.skip_for_now), color = colours.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PageContent(
    page: WalkPage,
    isLastTutorial: Boolean,
    isLastPage: Boolean,
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    granted: Boolean,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val text = pageText(page, selectedMode)
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(6.dp))
        Surface(shape = CircleShape, color = colours.secondaryContainer) {
            Text(
                text = stringResource(text.chip),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = colours.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(text.title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = colours.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(text.subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colours.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                page == WalkPage.Style -> StyleChoice(
                    selected = selectedMode,
                    onSelect = onSelectMode,
                    modifier = Modifier.fillMaxSize()
                )
                page == WalkPage.Permission -> PermissionCard(
                    scene = WalkScene.Permission,
                    description = stringResource(R.string.walk_permission_animation),
                    body = stringResource(R.string.walkthrough_overlay_desc),
                    grantedLabel = stringResource(R.string.permission_granted_check),
                    settledAt = PERMISSION_SETTLED,
                    granted = granted,
                    modifier = Modifier.fillMaxSize()
                )
                page == WalkPage.Accessibility -> PermissionCard(
                    scene = WalkScene.Accessibility,
                    description = stringResource(R.string.walk_access_animation),
                    body = stringResource(R.string.walk_access_card),
                    grantedLabel = stringResource(R.string.walk_access_granted),
                    settledAt = ACCESSIBILITY_SETTLED,
                    granted = granted,
                    modifier = Modifier.fillMaxSize()
                )
                text.scene != null -> IllustrationCard(
                    scene = text.scene,
                    caption = text.caption?.let { stringResource(it) }.orEmpty(),
                    description = text.animation?.let { stringResource(it) }.orEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        // The style page's grid changes with the highlighted card; a crossfade keeps that calm.
        Crossfade(targetState = text.points, label = "walkthrough-points") { points ->
            PointGrid(points)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (page.asksPermission && !granted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = ActionShape
                ) {
                    Text(stringResource(R.string.skip_for_now), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = ActionShape
                ) {
                    Text(stringResource(R.string.walk_allow), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            val label = when {
                page.asksPermission -> if (isLastPage) R.string.walk_start else R.string.walk_continue
                isLastTutorial -> R.string.walk_got_it
                else -> R.string.walk_continue
            }
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = ActionShape
            ) {
                Text(stringResource(label), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun IllustrationCard(scene: WalkScene, caption: String, description: String, modifier: Modifier = Modifier) {
    val colours = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = CardShape, color = colours.surfaceContainer) {
        Column {
            WalkthroughIllustration(
                scene = scene,
                description = description,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelLarge,
                    color = colours.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                ActiveEdge()
            }
        }
    }
}

/** A red dot, breathing, beside "Active edge": the side of the screen the bar is listening on. */
@Composable
private fun ActiveEdge() {
    val breath = rememberInfiniteTransition(label = "active-edge").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "active-edge-alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                // Read in the layer, so the breathing redraws the dot and recomposes nothing.
                .graphicsLayer { alpha = breath.value }
                .clip(CircleShape)
                .background(EdgeDot)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.walk_active_edge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StyleChoice(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // Scrolls only where a short screen leaves the two cards less room than they need.
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StyleCard(
            mode = UserMode.REGULAR,
            title = R.string.walk_style_simple_title,
            body = R.string.walk_style_simple_desc,
            selected = selected == UserMode.REGULAR,
            onSelect = onSelect
        )
        StyleCard(
            mode = UserMode.ADVANCED,
            title = R.string.walk_style_advanced_title,
            body = R.string.walk_style_advanced_desc,
            selected = selected == UserMode.ADVANCED,
            onSelect = onSelect
        )
    }
}

@Composable
private fun StyleCard(
    mode: String,
    @StringRes title: Int,
    @StringRes body: Int,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val border by animateColorAsState(
        targetValue = if (selected) colours.primary else colours.outlineVariant,
        label = "style-border",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colours.primaryContainer.copy(alpha = 0.55f) else colours.surfaceContainer,
        label = "style-container",
    )
    Surface(
        shape = shape,
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .pressBounce(interaction)
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = { onSelect(mode) }
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = colours.surfaceContainerHighest) {
                StylePreview(
                    advanced = mode == UserMode.ADVANCED,
                    selected = selected,
                    modifier = Modifier.size(width = 64.dp, height = 88.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colours.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colours.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Shows the state; the whole card takes the tap.
            RadioButton(selected = selected, onClick = null)
        }
    }
}

/** A permission page's card: the switch being flipped, what it is for, and a tick once it is on. */
@Composable
private fun PermissionCard(
    scene: WalkScene,
    description: String,
    body: String,
    grantedLabel: String,
    settledAt: Float,
    granted: Boolean,
    modifier: Modifier = Modifier,
) {
    val colours = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = CardShape, color = colours.surfaceContainer) {
        Column {
            WalkthroughIllustration(
                scene = scene,
                description = description,
                // Once granted there is nothing left to show happening: it rests switched on.
                settledAt = if (granted) settledAt else null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colours.onSurfaceVariant
                )
                AnimatedVisibility(visible = granted) {
                    Surface(
                        color = Granted.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(
                            text = grantedLabel,
                            color = Granted,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** What the page covers, two to a row, each row as tall as its taller chip. */
@Composable
private fun PointGrid(points: List<Int>) {
    val colours = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { point ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colours.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colours.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(point),
                                style = MaterialTheme.typography.labelMedium,
                                color = colours.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
