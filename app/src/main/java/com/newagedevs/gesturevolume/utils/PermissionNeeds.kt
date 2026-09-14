package com.newagedevs.gesturevolume.utils

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.QuickSliderStore
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.service.OverlayRuntime

/**
 * Which permissions the user's own configuration has made necessary, and what asked for each.
 *
 * Every optional permission is optional in the abstract and required in practice the moment the
 * user chooses the feature behind it. Putting a brightness action on a swipe, or switching the
 * notification controls on, used to succeed quietly and then do nothing, with the explanation
 * living on a screen the user had no reason to visit. So the answer is computed here, once, as a
 * list of [Need]s — a permission and the [Feature] that wants it — and every place that warns
 * reads the same list: the Permissions card on the home screen, the Deck screen, the notes beside
 * settings, and the permission cards on the Permissions screen.
 *
 * The decision itself is [compute], a pure function of a [Config] snapshot and the [Grants], so it
 * can be tested without a device. [read] is the thin Android half that fills both in.
 */
object PermissionNeeds {

    /** A permission a feature can wait on. Declared in the order the Permissions screen lists them. */
    enum class Permission {
        OVERLAY,
        WRITE_SETTINGS,
        ACCESSIBILITY,
        NOTIFICATION_POLICY,
        NOTIFICATIONS,
        CONTACTS,
        PHONE,
    }

    /** The screen that holds a setting. [HOME] is the app itself. */
    enum class Screen { HOME, APPEARANCE, ACTIONS, DECK, QUICK_SLIDER, LONG_PRESS_MENU, VISIBILITY, PERMISSIONS }

    /** A setting that can make a permission necessary, and where the user finds it. */
    enum class Feature(val screen: Screen, @param:StringRes val labelRes: Int) {
        FLOATING_BAR(Screen.HOME, R.string.permission_feature_floating_bar),
        SINGLE_TAP(Screen.ACTIONS, R.string.permission_feature_single_tap),
        DOUBLE_TAP(Screen.ACTIONS, R.string.permission_feature_double_tap),
        TRIPLE_TAP(Screen.ACTIONS, R.string.permission_feature_triple_tap),
        LONG_PRESS(Screen.ACTIONS, R.string.permission_feature_long_press),
        SWIPE_UP(Screen.ACTIONS, R.string.permission_feature_swipe_up),
        SWIPE_DOWN(Screen.ACTIONS, R.string.permission_feature_swipe_down),
        SWIPE_IN(Screen.ACTIONS, R.string.permission_feature_swipe_in),
        SWIPE_OUT(Screen.ACTIONS, R.string.permission_feature_swipe_out),
        NOTIFICATION_CONTROLS(Screen.PERMISSIONS, R.string.permission_feature_notification_controls),
        LONG_PRESS_MENU_ENTRY(Screen.LONG_PRESS_MENU, R.string.permission_feature_menu_entry),
        DECK_TILE(Screen.DECK, R.string.permission_feature_deck_tile),
        DECK_SEARCH_CONTACTS(Screen.DECK, R.string.permission_feature_deck_contacts),
        DECK_DIRECT_CALL(Screen.DECK, R.string.permission_feature_deck_direct_call),
        QUICK_SLIDER_BRIGHTNESS(Screen.QUICK_SLIDER, R.string.permission_feature_slider_brightness),
        QUICK_SLIDER_INSTANT_KEYS(Screen.QUICK_SLIDER, R.string.permission_feature_slider_instant_keys),
        HIDE_IN_APPS(Screen.VISIBILITY, R.string.permission_feature_hide_in_apps),
    }

    /** One missing permission, and the feature that is waiting on it. */
    data class Need(val permission: Permission, val feature: Feature)

    /** What the user has switched on, read out of preferences. */
    data class Config(
        /** The gesture slots, keyed by their feature ([Feature.SINGLE_TAP] to [Feature.SWIPE_OUT]). */
        val slotActions: Map<Feature, String> = emptyMap(),
        /** Every entry the long-press menu shows, pinned ones included. */
        val menuActions: List<String> = emptyList(),
        /** The ids of the Deck tiles switched on. */
        val deckTiles: Set<String> = emptySet(),
        val sliderTarget: String = QuickSliderStore.TARGET_MEDIA,
        val volumeKeyMode: String = QuickSliderStore.VOLUME_KEYS_OFF,
        /** True when the bar is set to step aside for at least one app. */
        val hideInApps: Boolean = false,
        val notificationControls: Boolean = false,
        val searchContacts: Boolean = false,
        val directCall: Boolean = false,
    )

    /** What Android has granted. Everything granted by default, so a test names only what is not. */
    data class Grants(
        val overlay: Boolean = true,
        val writeSettings: Boolean = true,
        val accessibility: Boolean = true,
        val notificationPolicy: Boolean = true,
        val notifications: Boolean = true,
        val contacts: Boolean = true,
        val phone: Boolean = true,
    ) {
        fun has(permission: Permission): Boolean = when (permission) {
            Permission.OVERLAY -> overlay
            Permission.WRITE_SETTINGS -> writeSettings
            Permission.ACCESSIBILITY -> accessibility
            Permission.NOTIFICATION_POLICY -> notificationPolicy
            Permission.NOTIFICATIONS -> notifications
            Permission.CONTACTS -> contacts
            Permission.PHONE -> phone
        }
    }

    /** Every [Need] outstanding, ordered by permission and then by where it was found. */
    data class Needs(val all: List<Need> = emptyList()) {
        /** The missing permissions, each once, in the Permissions screen's order. */
        val permissions: List<Permission> get() = all.map { it.permission }.distinct()

        /** How many permissions need attention: a permission wanted twice is still one grant. */
        val missingCount: Int get() = permissions.size

        val anyMissing: Boolean get() = all.isNotEmpty()

        /** The first thing to fix: the one the home card names and the Permissions screen flashes. */
        val first: Need? get() = all.firstOrNull()

        fun isMissing(permission: Permission): Boolean = all.any { it.permission == permission }

        /** The features waiting on [permission], each once. */
        fun featuresFor(permission: Permission): List<Feature> =
            all.filter { it.permission == permission }.map { it.feature }.distinct()

        /** The needs raised by settings on [screen]. */
        fun forScreen(screen: Screen): List<Need> = all.filter { it.feature.screen == screen }

        val overlayMissing: Boolean get() = isMissing(Permission.OVERLAY)
        val writeSettingsMissing: Boolean get() = isMissing(Permission.WRITE_SETTINGS)
        val accessibilityMissing: Boolean get() = isMissing(Permission.ACCESSIBILITY)
    }

    /** The permissions [action] cannot work without, whether or not they are granted. */
    fun permissionsFor(action: String, sliderTarget: String): List<Permission> = buildList {
        // The Quick panel counts as a brightness action when that is what it is set to drive. It is
        // not one by name, so the slots that open it used to carry no warning at all.
        val writesBrightness = HandlerActions.needsWriteSettings(action) ||
            (action == HandlerActions.OPEN_QUICK_SLIDER && sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS)
        if (writesBrightness) add(Permission.WRITE_SETTINGS)
        if (HandlerActions.needsAccessibility(action)) add(Permission.ACCESSIBILITY)
        if (HandlerActions.needsNotificationPolicy(action)) add(Permission.NOTIFICATION_POLICY)
    }

    /**
     * The permission a Deck tile needs, or null. By id, against the tiles' own constants, so this
     * stays free of the tile list's icons: the brightness card writes the brightness, rotation is a
     * system setting, Do Not Disturb needs its access, and lock and screenshot are the service's.
     */
    fun permissionForDeckTile(id: String): Permission? = when (id) {
        DeckTiles.BRIGHTNESS, DeckTiles.ROTATION -> Permission.WRITE_SETTINGS
        DeckTiles.DND -> Permission.NOTIFICATION_POLICY
        DeckTiles.SCREENSHOT, DeckTiles.LOCK -> Permission.ACCESSIBILITY
        else -> null
    }

    /** The pure decision: which of the permissions [config] needs are missing from [grants]. */
    fun compute(config: Config, grants: Grants): Needs {
        val found = LinkedHashSet<Need>()
        fun need(permission: Permission, feature: Feature) {
            if (!grants.has(permission)) found += Need(permission, feature)
        }

        // Required outright: the bar cannot be drawn without it.
        need(Permission.OVERLAY, Feature.FLOATING_BAR)

        config.slotActions.forEach { (feature, action) ->
            permissionsFor(action, config.sliderTarget).forEach { need(it, feature) }
        }
        // An entry the user put in the menu is one they intend to tap.
        config.menuActions.forEach { action ->
            permissionsFor(action, config.sliderTarget).forEach { need(it, Feature.LONG_PRESS_MENU_ENTRY) }
        }
        config.deckTiles.forEach { id -> permissionForDeckTile(id)?.let { need(it, Feature.DECK_TILE) } }
        if (config.searchContacts) need(Permission.CONTACTS, Feature.DECK_SEARCH_CONTACTS)
        if (config.directCall) need(Permission.PHONE, Feature.DECK_DIRECT_CALL)

        if (config.sliderTarget == QuickSliderStore.TARGET_BRIGHTNESS) {
            need(Permission.WRITE_SETTINGS, Feature.QUICK_SLIDER_BRIGHTNESS)
        }
        if (config.volumeKeyMode == QuickSliderStore.VOLUME_KEYS_INSTANT) {
            need(Permission.ACCESSIBILITY, Feature.QUICK_SLIDER_INSTANT_KEYS)
        }
        if (config.hideInApps) need(Permission.ACCESSIBILITY, Feature.HIDE_IN_APPS)
        if (config.notificationControls) need(Permission.NOTIFICATIONS, Feature.NOTIFICATION_CONTROLS)

        // Stable, so within a permission the features keep the order they were found in.
        return Needs(found.sortedBy { it.permission.ordinal })
    }

    /** Every action the user has bound to a gesture slot, keyed by the slot. */
    fun slotActions(preference: SharedPref): Map<Feature, String> = linkedMapOf(
        Feature.SINGLE_TAP to preference.getHandlerSingleTapAction(),
        Feature.DOUBLE_TAP to preference.getHandlerDoubleTapAction(),
        Feature.TRIPLE_TAP to preference.getHandlerTripleTapAction(),
        Feature.LONG_PRESS to preference.getHandlerLongTapAction(),
        Feature.SWIPE_UP to preference.getHandlerSwipeUpAction(),
        Feature.SWIPE_DOWN to preference.getHandlerSwipeDownAction(),
        Feature.SWIPE_IN to preference.getHandlerSwipeInAction(),
        Feature.SWIPE_OUT to preference.getHandlerSwipeOutAction(),
    )

    /** A snapshot of every setting that can need a permission. */
    fun config(preference: SharedPref): Config = Config(
        slotActions = slotActions(preference),
        // Through the catalog, so the pinned entries the menu adds are counted too.
        menuActions = HandlerActionCatalog
            .contextMenuEntries(preference.getContextMenuOrder())
            .map { it.action },
        deckTiles = preference.deck.getEnabledTiles() ?: DeckTiles.defaultEnabled(),
        sliderTarget = preference.slider.getTarget(),
        volumeKeyMode = preference.slider.getVolumeKeyMode(),
        hideInApps = preference.getHandlerHiddenApps().isNotEmpty(),
        notificationControls = preference.getShowNotification(),
        searchContacts = preference.search.getIndexContacts(),
        directCall = preference.search.getDirectCall(),
    )

    /** What Android currently allows. */
    fun grants(context: Context): Grants = Grants(
        overlay = Settings.canDrawOverlays(context),
        writeSettings = Settings.System.canWrite(context),
        accessibility = OverlayRuntime.isAccessibilityEnabled(context),
        notificationPolicy = hasNotificationPolicyAccess(context),
        notifications = hasNotificationPermission(context),
        contacts = hasPermission(context, Manifest.permission.READ_CONTACTS),
        phone = hasPermission(context, Manifest.permission.CALL_PHONE),
    )

    fun read(context: Context, preference: SharedPref): Needs =
        compute(config(preference), grants(context))

    /**
     * What [action] still needs before it can work, or null when nothing is standing in its way.
     * Per action, for the note shown under it where it is chosen.
     */
    fun missingFor(context: Context, preference: SharedPref, action: String): Permission? {
        val needed = permissionsFor(action, preference.slider.getTarget())
        if (needed.isEmpty()) return null
        val grants = grants(context)
        return needed.firstOrNull { !grants.has(it) }
    }

    /** Whether [permission] is granted right now. */
    fun isGranted(context: Context, permission: Permission): Boolean = grants(context).has(permission)

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Whether Android currently lets this app post notifications. Always true below Android 13. */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /** Whether the user has granted Do Not Disturb access on the system screen. */
    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }
}
