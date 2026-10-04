package com.newagedevs.gesturevolume.ui.screens.walkthrough

import com.newagedevs.gesturevolume.ui.components.screenWash
import com.newagedevs.gesturevolume.ui.components.stageBackdrop
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import com.newagedevs.gesturevolume.ui.motion.IconButton
import com.newagedevs.gesturevolume.ui.motion.OutlinedButton
import com.newagedevs.gesturevolume.ui.motion.Springs
import com.newagedevs.gesturevolume.ui.motion.TextButton
import com.newagedevs.gesturevolume.ui.motion.pressBounce
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import com.newagedevs.gesturevolume.utils.UserMode

/**
 * Every page the walkthrough can show, in the order they come. Which tutorial pages appear
 * depends on the style chosen on [Style]. The order also gives every move a direction, so a page
 * slides in from the side it sits on, whichever way the user is going.
 */
internal enum class WalkPage {
    Intro, Style, Simple, QuickSlider, Deck, LongPress, Move, Permission, Notifications, Accessibility,
}

/**
 * The pages for [mode], in order.
 *
 * @param askNotifications whether this phone asks for notifications at all: Android 13 and later.
 *   Below that they are allowed unless switched off, and a page asking for them would be a page
 *   with nothing to ask.
 * @param granted permission pages to leave out, for a tour: an install that has a permission
 *   already has nothing to be asked on its page. A first walkthrough keeps them all, granted or
 *   not, and says on each that it is done.
 */
internal fun pagesFor(mode: String, askNotifications: Boolean, granted: Set<WalkPage> = emptySet()): List<WalkPage> = buildList {
    add(WalkPage.Intro)
    add(WalkPage.Style)
    if (mode == UserMode.ADVANCED) {
        add(WalkPage.QuickSlider)
        add(WalkPage.Deck)
        add(WalkPage.LongPress)
    } else {
        add(WalkPage.Simple)
    }
    // Both styles: wherever the bar starts is somewhere, and a thumb has its own idea of where.
    add(WalkPage.Move)
    add(WalkPage.Permission)
    // Both styles, and optional: the bar works without it, and it is where the bar's controls live
    // while the app is closed.
    if (askNotifications) add(WalkPage.Notifications)
    // Advanced only: its defaults are what need the service — volume keys that open the Quick
    // slider at once, and Lock screen and Screenshot on the Deck. Last, and optional, because the
    // bar works without it and cannot work without the overlay page.
    if (mode == UserMode.ADVANCED) add(WalkPage.Accessibility)
}.filterNot { it.asksPermission && it in granted }

/** The last page that teaches rather than asks: where the walkthrough says "Got it". */
internal fun lastTutorialOf(pages: List<WalkPage>): WalkPage? = pages.lastOrNull { !it.asksPermission }

/** The pages that ask for a permission, each with Allow and Skip of its own. */
internal val WalkPage.asksPermission: Boolean
    get() = this == WalkPage.Permission || this == WalkPage.Notifications || this == WalkPage.Accessibility

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

/**
 * The words on [page] for the [mode] highlighted. [tourMode], in a tour, is the style the user is
 * on: the style page then shows the two styles rather than asking for one.
 */
private fun pageText(page: WalkPage, mode: String, tourMode: String? = null): PageText =
    if (page == WalkPage.Style && tourMode != null) {
        pageTextFor(page, mode).let {
            PageText(
                R.string.walk_style_tour_chip, R.string.walk_style_tour_title,
                if (tourMode == UserMode.ADVANCED) R.string.walk_style_tour_subtitle_advanced else R.string.walk_style_tour_subtitle_simple,
                it.caption, it.animation, it.scene, it.points,
            )
        }
    } else {
        pageTextFor(page, mode)
    }

private fun pageTextFor(page: WalkPage, mode: String): PageText = when (page) {
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
    // The bar the style brings, being moved: the button, or the tab turning to face its new edge.
    WalkPage.Move -> PageText(
        R.string.walk_move_chip, R.string.walk_move_title, R.string.walk_move_subtitle,
        R.string.walk_move_caption, R.string.walk_move_animation,
        if (mode == UserMode.ADVANCED) WalkScene.MoveTab else WalkScene.MoveButton,
        listOf(R.string.walk_move_point_1, R.string.walk_move_point_2, R.string.walk_move_point_3, R.string.walk_move_point_4),
    )
    WalkPage.Permission -> PageText(
        R.string.walk_permission_chip, R.string.walk_permission_title, R.string.walk_permission_subtitle,
        null, R.string.walk_permission_animation, WalkScene.Permission,
        listOf(R.string.walk_permission_point_1, R.string.walk_permission_point_2, R.string.walk_permission_point_3, R.string.walk_permission_point_4),
    )
    WalkPage.Notifications -> PageText(
        R.string.walk_notif_chip, R.string.walk_notif_title, R.string.walk_notif_subtitle,
        null, R.string.walk_notif_animation, WalkScene.Notifications,
        listOf(R.string.walk_notif_point_1, R.string.walk_notif_point_2, R.string.walk_notif_point_3, R.string.walk_notif_point_4),
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

/**
 * How soon a refusal of the notification request comes back when no one was asked: Android has
 * stopped showing it after two refusals. Nobody reads the question and answers it this fast.
 */
private const val UNASKED_WITHIN_MS = 500L

/** Narrower than this, or not wider than tall, the page stays one column. */
private val TWO_PANE_MIN_WIDTH = 560.dp

/** The widest the page grows in one column: on a tablet held upright, a phone's page made larger. */
private val ONE_PANE_MAX_WIDTH = 600.dp

/** The widest the page grows in two columns, the home screen's widest. */
private val TWO_PANE_MAX_WIDTH = 920.dp

/** How much of the width the card takes in two columns: two fifths, as on the preview screens. */
private const val CARD_SHARE = 0.4f

/** Between the card and the words in two columns, as on the preview screens. */
private val PANE_GAP = 20.dp

/** The least height a scene is drawn at, upright: under it, the page scrolls rather than shrinking it. */
private val SCENE_MIN_HEIGHT = 180.dp

/** How much of the top row Skip may take; the progress has the rest. */
private const val SKIP_SHARE = 0.5f

/** Narrower than this, at the default text size, the points go one to a row. */
private val POINTS_TWO_UP_MIN_WIDTH = 260.dp

/** The buttons at the foot of a page: at least this tall, and taller for two lines of words. */
private val ACTION_HEIGHT = 56.dp

/**
 * The walkthrough: what the bar is, a choice of style, how its parts work, and the permissions it
 * needs — on a first launch.
 *
 * @param tour the same pages as a tour of the app, for someone who already has it set up — offered
 *   once after an update and kept in the menu. A tour changes nothing: the style page shows the two
 *   styles without applying either, whose presets would replace the bar the user has made their
 *   own; the permission pages are only the ones still missing; and leaving it writes nothing.
 */
@Composable
fun WalkthroughScreen(
    viewModel: MainViewModel = hiltViewModel(),
    tour: Boolean = false,
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
    // In a tour, the style the user is on, highlighted to begin with and named on the style page.
    val tourMode = remember { if (tour) viewModel.preference.getUserMode() else null }
    var selectedMode by rememberSaveable { mutableStateOf(tourMode ?: UserMode.REGULAR) }
    var modeChosen by rememberSaveable { mutableStateOf(false) }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    // A second tap on Start or Skip while the first is navigating away must not finish twice.
    var finished by remember { mutableStateOf(false) }
    // A tour's permission pages are the ones missing as it opens. Worked out once, so a permission
    // granted part-way does not pull its page out from under the page count.
    val grantedAtStart = remember {
        if (!tour) {
            emptySet()
        } else {
            buildSet {
                if (Settings.canDrawOverlays(context)) add(WalkPage.Permission)
                if (PermissionNeeds.hasNotificationPermission(context)) add(WalkPage.Notifications)
                if (OverlayRuntime.isAccessibilityEnabled(context)) add(WalkPage.Accessibility)
            }
        }
    }
    val pages = remember(selectedMode) {
        pagesFor(
            selectedMode,
            askNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            granted = grantedAtStart,
        )
    }
    val index = pageIndex.coerceIn(0, pages.lastIndex)
    val page = pages[index]

    val hasOverlayPermission = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val hasNotifications = remember { mutableStateOf(PermissionNeeds.hasNotificationPermission(context)) }
    val hasAccessibility = remember { mutableStateOf(OverlayRuntime.isAccessibilityEnabled(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    /** When the notification request went out, to tell a refusal from no one having been asked. */
    var notificationsAskedAt by remember { mutableLongStateOf(0L) }

    // Checked on every return to the screen too, not only when the settings screen hands back a
    // result: some phones come back without one, and the switch can be flipped from elsewhere. The
    // accessibility list never hands anything back, so this is the only way it is ever seen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasOverlayPermission.value = Settings.canDrawOverlays(context)
        hasNotifications.value = PermissionNeeds.hasNotificationPermission(context)
        hasAccessibility.value = OverlayRuntime.isAccessibilityEnabled(context)
    }

    fun openNotificationSettings() {
        // Kept paused on the way back as well: the walkthrough lifts it when it closes.
        viewModel.preference.setAppOpenAdPaused(true)
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifications.value = granted
        // Refused twice before, Android stops showing the question and the answer comes straight
        // back. Back that fast, no one was asked, so the app's notification settings are the way
        // left. An answer someone actually gave stands: Skip is beside Allow for moving on.
        if (!granted && SystemClock.elapsedRealtime() - notificationsAskedAt < UNASKED_WITHIN_MS) {
            openNotificationSettings()
        }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        // Asked here, so switching the bar on later does not ask a second time. See
        // MainViewModel.maybeAskForNotificationPermission.
        viewModel.preference.setAskedNotificationPermission(true)
        notificationsAskedAt = SystemClock.elapsedRealtime()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        // A tour leaves everything as it found it: the style, the first-launch flag and the ads.
        if (tour) {
            onComplete()
            return
        }
        // Leaving before the choice is leaving as a regular user: the style that needs no
        // explaining, and the one a skipped walkthrough has taught nothing more than.
        if (!modeChosen) viewModel.chooseUserMode(UserMode.REGULAR)
        viewModel.preference.setFirstLaunchCompleted()
        // Just walked through: the home screen has no tour to offer.
        viewModel.preference.markTourOffered()
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
                // In a tour the styles are only looked at: switched here, the style's preset would
                // replace the bar the user has made their own. The home screen switches it.
                if (!tour) {
                    viewModel.chooseUserMode(selectedMode)
                    modeChosen = true
                }
                goTo(index + 1)
            }
            WalkPage.Permission -> if (hasOverlayPermission.value) next() else requestOverlayPermission()
            WalkPage.Notifications -> if (hasNotifications.value) next() else requestNotifications()
            // The disclosure first, every time, as everywhere else the app asks for the service.
            WalkPage.Accessibility -> if (hasAccessibility.value) next() else showAccessibilityDisclosure = true
            // Out of the walkthrough from the last: a tour with nothing left to ask ends on one.
            else -> next()
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
    // A cool wash over the theme's background, shared with the screens that show a preview: see
    // screenWash. It was a peach wash, which fought the blues of the pictures below.

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colours.background
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .screenWash()
                .systemBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Wider than tall, and wide enough for two columns: a phone on its side, a tablet. Stacked,
            // the words and the buttons left the card a strip a few rows high there, or nothing at all.
            // Measured off the window rather than the orientation, so a split screen counts as its size.
            val twoPane = maxWidth > maxHeight && maxWidth >= TWO_PANE_MIN_WIDTH
            Column(
                modifier = Modifier
                    .widthIn(max = if (twoPane) TWO_PANE_MAX_WIDTH else ONE_PANE_MAX_WIDTH)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                TopRow(
                    index = index,
                    count = pages.size,
                    // The permission pages have their own Skip beside Allow; two would be one too many.
                    showSkip = !page.asksPermission,
                    // A tour has nothing to put off: it is closed.
                    skipLabel = if (tour) R.string.close else R.string.skip_for_now,
                    onSkip = ::finish,
                    onBack = { goTo(index - 1) },
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
                        twoPane = twoPane,
                        isLastTutorial = shown == lastTutorialOf(pages),
                        isLastPage = shown == pages.last(),
                        selectedMode = selectedMode,
                        tourMode = tourMode,
                        onSelectMode = { selectedMode = it },
                        granted = when (shown) {
                            WalkPage.Accessibility -> hasAccessibility.value
                            WalkPage.Notifications -> hasNotifications.value
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
}

@Composable
private fun TopRow(index: Int, count: Int, showSkip: Boolean, @StringRes skipLabel: Int, onSkip: () -> Unit, onBack: () -> Unit) {
    val colours = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Skip is measured before the progress, so a long one — "Fürs Erste überspringen", or any of
        // them in large text — is held to about half the row, and the progress makes do with the rest.
        val skipMaxWidth = maxWidth * SKIP_SHARE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back to the page before, from every page but the first: the step the system's Back
            // already takes, where the eye looks for it. It opens out ahead of the dots as the first
            // page is left, rather than the dots jumping aside for it.
            AnimatedVisibility(
                visible = index > 0,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = (-8).dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colours.onSurface,
                    )
                }
            }
            Progress(index = index, count = count, modifier = Modifier.weight(1f))
            if (showSkip) {
                TextButton(onClick = onSkip, modifier = Modifier.widthIn(max = skipMaxWidth)) {
                    Text(
                        text = stringResource(skipLabel),
                        color = colours.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The dots, then "3/9". Where the row has no room for both — a small phone, large text, a long
 * Skip — the count goes first; where even the dots do not fit, the count stands in for them, since
 * it says the same in a fraction of the width.
 */
@Composable
private fun Progress(index: Int, count: Int, modifier: Modifier = Modifier) {
    val colours = MaterialTheme.colorScheme
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
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
            Text(
                text = stringResource(R.string.walk_progress, index + 1, count),
                style = MaterialTheme.typography.labelMedium,
                color = colours.onSurfaceVariant,
                maxLines = 1
            )
        },
    ) { measurables, constraints ->
        val free = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0)
        val dots = measurables[0].measure(free)
        val counter = measurables[1].measure(free)
        val gap = 10.dp.roundToPx()
        val room = constraints.maxWidth
        val shown = when {
            dots.width + gap + counter.width <= room -> listOf(dots, counter)
            dots.width <= room -> listOf(dots)
            else -> listOf(counter)
        }
        val height = shown.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(room, height) {
            var x = 0
            shown.forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width + gap
            }
        }
    }
}

/**
 * One page, in one of two shapes. Upright, the words, the card, the points and the buttons run
 * down the page, and the card takes whatever height the rest leaves; see [UprightPage]. [twoPane],
 * the card stands in the left two fifths, as tall as the page, and the rest beside it. Either way
 * the buttons keep to the bottom, and what scrolls, when the room runs short, is the words.
 */
@Composable
private fun PageContent(
    page: WalkPage,
    twoPane: Boolean,
    isLastTutorial: Boolean,
    isLastPage: Boolean,
    selectedMode: String,
    tourMode: String?,
    onSelectMode: (String) -> Unit,
    granted: Boolean,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    val text = pageText(page, selectedMode, tourMode)

    val header: @Composable () -> Unit = {
        Column {
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
        }
    }

    val card: @Composable (Modifier) -> Unit = { modifier ->
        when {
            page == WalkPage.Style -> StyleChoice(
                selected = selectedMode,
                onSelect = onSelectMode,
                modifier = modifier
            )
            page == WalkPage.Permission -> PermissionCard(
                scene = WalkScene.Permission,
                description = stringResource(R.string.walk_permission_animation),
                body = stringResource(R.string.walkthrough_overlay_desc),
                grantedLabel = stringResource(R.string.permission_granted_check),
                settledAt = PERMISSION_SETTLED,
                granted = granted,
                modifier = modifier
            )
            page == WalkPage.Notifications -> PermissionCard(
                scene = WalkScene.Notifications,
                description = stringResource(R.string.walk_notif_animation),
                body = stringResource(R.string.walk_notif_card),
                grantedLabel = stringResource(R.string.walk_notif_granted),
                settledAt = NOTIFICATIONS_SETTLED,
                granted = granted,
                modifier = modifier
            )
            page == WalkPage.Accessibility -> PermissionCard(
                scene = WalkScene.Accessibility,
                description = stringResource(R.string.walk_access_animation),
                body = stringResource(R.string.walk_access_card),
                grantedLabel = stringResource(R.string.walk_access_granted),
                settledAt = ACCESSIBILITY_SETTLED,
                granted = granted,
                modifier = modifier
            )
            text.scene != null -> IllustrationCard(
                scene = text.scene,
                caption = text.caption?.let { stringResource(it) }.orEmpty(),
                description = text.animation?.let { stringResource(it) }.orEmpty(),
                modifier = modifier
            )
        }
    }

    val points: @Composable () -> Unit = {
        // The style page's grid changes with the highlighted card; a crossfade keeps that calm.
        Crossfade(targetState = text.points, label = "walkthrough-points") { points ->
            PointGrid(points)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (twoPane) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PANE_GAP)
            ) {
                // Level with the chip across the way.
                card(
                    Modifier
                        .weight(CARD_SHARE)
                        .fillMaxHeight()
                        .padding(top = 6.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f - CARD_SHARE)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        header()
                        Spacer(modifier = Modifier.height(16.dp))
                        points()
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    PageActions(page, granted, isLastTutorial, isLastPage, onPrimary, onSkip)
                }
            }
        } else {
            UprightPage(
                // The style cards are all there is to that card: no scene to leave room for.
                sceneFloor = if (page == WalkPage.Style) 0.dp else SCENE_MIN_HEIGHT,
                header = {
                    header()
                    Spacer(modifier = Modifier.height(16.dp))
                },
                card = { card(Modifier.fillMaxSize()) },
                points = {
                    Spacer(modifier = Modifier.height(12.dp))
                    points()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            PageActions(page, granted, isLastTutorial, isLastPage, onPrimary, onSkip)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * The page upright: [header] above the card, [points] below it, and the card in between taking
 * whatever height those two leave.
 *
 * Where that would leave the card less than its own words and [sceneFloor] more — a short phone, a
 * split screen, large text — the page scrolls instead, and the card keeps that much. Squeezed,
 * the scene shrank to a sliver, and with the phone on its side to nothing at all.
 */
@Composable
private fun UprightPage(
    sceneFloor: Dp,
    header: @Composable () -> Unit,
    card: @Composable () -> Unit,
    points: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val viewport = constraints.maxHeight
        Layout(
            contents = listOf(header, card, points),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) { (above, middle, below), outer ->
            val width = outer.maxWidth
            val loose = Constraints(maxWidth = width)
            val top = above.map { it.measure(loose) }
            val bottom = below.map { it.measure(loose) }
            val used = top.sumOf { it.height } + bottom.sumOf { it.height }
            // The card's own words, a caption or a permission's reason, and room for the scene.
            val floor = (middle.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0) + sceneFloor.roundToPx()
            val cardHeight = maxOf(viewport - used, floor)
            val cards = middle.map { it.measure(Constraints.fixed(width, cardHeight)) }
            layout(width, used + cardHeight) {
                var y = 0
                (top + cards + bottom).forEach {
                    it.place(0, y)
                    y += it.height
                }
            }
        }
    }
}

/** Allow and Skip on a page that asks and has not been answered yet; one button on the rest. */
@Composable
private fun PageActions(
    page: WalkPage,
    granted: Boolean,
    isLastTutorial: Boolean,
    isLastPage: Boolean,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
) {
    if (page.asksPermission && !granted) {
        AnswerPair(
            skip = {
                OutlinedButton(onClick = onSkip, shape = ActionShape) {
                    ActionLabel(R.string.skip_for_now)
                }
            },
            allow = {
                Button(onClick = onPrimary, shape = ActionShape) {
                    ActionLabel(R.string.walk_allow)
                }
            },
        )
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
                .heightIn(min = ACTION_HEIGHT),
            shape = ActionShape
        ) {
            ActionLabel(label)
        }
    }
}

/**
 * Skip and Allow side by side, half the width each, where both words fit on one line there; where
 * either would not, Allow above Skip, each the full width. Halved, "Fürs Erste überspringen" in
 * large text broke mid-word over four lines.
 */
@Composable
private fun AnswerPair(skip: @Composable () -> Unit, allow: @Composable () -> Unit) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            skip()
            allow()
        },
    ) { measurables, constraints ->
        val (skipButton, allowButton) = measurables
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val minHeight = ACTION_HEIGHT.roundToPx()
        val half = (width - gap) / 2
        val sideBySide = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half }
        if (sideBySide) {
            val each = Constraints(minWidth = half, maxWidth = half, minHeight = minHeight)
            val left = skipButton.measure(each)
            val right = allowButton.measure(each)
            val height = maxOf(left.height, right.height)
            layout(width, height) {
                left.placeRelative(0, 0)
                right.placeRelative(width - half, 0)
            }
        } else {
            val each = Constraints(minWidth = width, maxWidth = width, minHeight = minHeight)
            val top = allowButton.measure(each)
            val bottom = skipButton.measure(each)
            layout(width, top.height + gap + bottom.height) {
                top.placeRelative(0, 0)
                bottom.placeRelative(0, top.height + gap)
            }
        }
    }
}

@Composable
private fun ActionLabel(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun IllustrationCard(scene: WalkScene, caption: String, description: String, modifier: Modifier = Modifier) {
    val colours = MaterialTheme.colorScheme
    // On the same backdrop as the preview cards: the phone stands on it the same way everywhere.
    Surface(modifier = modifier, shape = CardShape, color = Color.Transparent) {
        Column(modifier = Modifier.stageBackdrop()) {
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
    // Scrolls only beside the words, on a page on its side too short for the two cards. Upright the
    // whole page scrolls instead, and this always has room for both.
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
    // On the same backdrop as the preview cards: the phone stands on it the same way everywhere.
    Surface(modifier = modifier, shape = CardShape, color = Color.Transparent) {
        Column(modifier = Modifier.stageBackdrop()) {
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

/**
 * What the page covers, two to a row, each row as tall as its taller chip. One to a row where two
 * would leave each chip too narrow for its words: a small phone, or large text, which used to cut
 * every chip off at its second line.
 */
@Composable
private fun PointGrid(points: List<Int>) {
    val colours = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints {
        val perRow = if (maxWidth < POINTS_TWO_UP_MIN_WIDTH * fontScale) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            points.chunked(perRow).forEach { pair ->
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
                    if (pair.size < perRow) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
