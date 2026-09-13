package com.newagedevs.gesturevolume.utils

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.service.OverlayRuntime

/**
 * Which optional permissions the user's own configuration has made necessary.
 *
 * Every one of these is optional in the abstract and required in practice the moment the user
 * chooses the feature behind it — and that is the gap this closes. Putting a brightness action on
 * a swipe, or switching the notification controls on, used to succeed quietly and then do nothing,
 * with the explanation living on a screen the user had no reason to visit. Now the same answer is
 * computed in one place and shown in two: on the Permissions card on the main screen, and on the
 * individual permission cards inside.
 *
 * One place rather than two, because the interesting cases are combinations — brightness on a
 * swipe *and* on a double tap, in the menu *and* on a tap — and two implementations of that would
 * disagree the first time an action moved.
 */
object PermissionNeeds {

    data class Needs(
        /** Required outright, unless the accessibility service is drawing the bar instead. */
        val overlayMissing: Boolean,
        /** The notification controls are on and Android is refusing to post them. */
        val notificationMissing: Boolean,
        /** A brightness or auto-rotate action is configured and WRITE_SETTINGS has not been granted. */
        val writeSettingsMissing: Boolean,
        /**
         * An action only the accessibility service can perform is configured, or the bar is set to
         * run without a notification, and the service is off.
         */
        val accessibilityMissing: Boolean,
        /** A Do Not Disturb action is configured and Do Not Disturb access has not been granted. */
        val dndMissing: Boolean,
        /** Contact search is switched on and the Contacts permission has not been granted. */
        val contactsMissing: Boolean,
        /** Direct calling is switched on and the Phone permission has not been granted. */
        val phoneMissing: Boolean,
        /** The weather tile is switched on and location has not been granted. */
        val locationMissing: Boolean
    ) {
        /** How many things need attention, for a card that has one line to say it in. */
        val missingCount: Int
            get() = listOf(
                overlayMissing, notificationMissing, writeSettingsMissing,
                accessibilityMissing, dndMissing, contactsMissing, phoneMissing, locationMissing
            ).count { it }

        val anyMissing: Boolean get() = missingCount > 0

        /** True when only optional extras are missing — the app itself still works. */
        val onlyOptionalMissing: Boolean get() = !overlayMissing && anyMissing
    }

    /** Every action the user has bound anywhere: the gesture slots and the long-press menu. */
    fun configuredActions(preference: SharedPref): List<String> {
        val slots = listOf(
            preference.getHandlerSingleTapAction(),
            preference.getHandlerDoubleTapAction(),
            preference.getHandlerTripleTapAction(),
            preference.getHandlerLongTapAction(),
            preference.getHandlerSwipeUpAction(),
            preference.getHandlerSwipeDownAction(),
            preference.getHandlerSwipeInAction(),
            preference.getHandlerSwipeOutAction()
        )
        // The long-press menu counts as "configured" too: an entry the user put there is one they
        // intend to tap, and the pinned entries are added by the catalog rather than chosen, so
        // the stored set alone would miss nothing but could include less than the menu shows.
        val menuActions = HandlerActionCatalog
            .contextMenuEntries(preference.getContextMenuOrder())
            .map { it.action }
        return slots + menuActions
    }

    fun read(context: Context, preference: SharedPref): Needs {
        val actions = configuredActions(preference)
        val accessibilityEnabled = OverlayRuntime.isAccessibilityEnabled(context)
        val accessibilityWanted = actions.any { HandlerActions.needsAccessibility(it) } ||
            preference.getOverlayHostMode() == OverlayHostMode.ACCESSIBILITY

        return Needs(
            // With the accessibility service drawing the bar the overlay permission is not used.
            overlayMissing = !Settings.canDrawOverlays(context) &&
                !(preference.getOverlayHostMode() == OverlayHostMode.ACCESSIBILITY && accessibilityEnabled),
            notificationMissing = preference.getShowNotification() &&
                preference.getOverlayHostMode() == OverlayHostMode.NOTIFICATION &&
                !hasNotificationPermission(context),
            writeSettingsMissing = actions.any { HandlerActions.needsWriteSettings(it) } &&
                !Settings.System.canWrite(context),
            accessibilityMissing = accessibilityWanted && !accessibilityEnabled,
            dndMissing = actions.any { HandlerActions.needsNotificationPolicy(it) } &&
                !hasNotificationPolicyAccess(context),
            // The Deck's own switches, each asked for only once its feature is switched on.
            contactsMissing = preference.search.getIndexContacts() &&
                !hasPermission(context, Manifest.permission.READ_CONTACTS),
            phoneMissing = preference.search.getDirectCall() &&
                !hasPermission(context, Manifest.permission.CALL_PHONE),
            locationMissing = weatherTileEnabled(preference) &&
                !hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) &&
                !hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether the weather tile is switched on.
     *
     * Read through the tile registry rather than the raw set, so a fresh install — which has no
     * stored set at all — is measured against the defaults, exactly as the Deck itself is.
     */
    private fun weatherTileEnabled(preference: SharedPref): Boolean {
        val enabled = preference.deck.getEnabledTiles() ?: DeckTiles.defaultEnabled()
        return DeckTiles.WEATHER in enabled
    }

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
