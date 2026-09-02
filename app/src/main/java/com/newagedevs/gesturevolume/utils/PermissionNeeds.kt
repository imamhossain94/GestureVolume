package com.newagedevs.gesturevolume.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.newagedevs.gesturevolume.data.local.SharedPref

/**
 * Which optional permissions the user's own configuration has made necessary.
 *
 * Every one of these is optional in the abstract and required in practice the moment the user
 * chooses the feature behind it — and that is the gap this closes. Picking Lock, or switching the
 * notification controls on, used to succeed quietly and then do nothing, with the explanation
 * living on a screen the user had no reason to visit. Now the same answer is computed in one
 * place and shown in two: on the Permissions card on the main screen, and on the individual
 * permission cards inside.
 *
 * One place rather than two, because the interesting cases are combinations — Lock on a long
 * press *and* in the menu, brightness on a swipe *and* on a double tap — and two implementations
 * of that would disagree the first time an action moved.
 */
object PermissionNeeds {

    data class Needs(
        /** Required outright. Without it there is no floating bar at all. */
        val overlayMissing: Boolean,
        /** The Lock action is configured somewhere and nothing can lock the screen. */
        val lockMissing: Boolean,
        /** The notification controls are on and Android is refusing to post them. */
        val notificationMissing: Boolean,
        /** A brightness action is configured and WRITE_SETTINGS has not been granted. */
        val writeSettingsMissing: Boolean
    ) {
        /** How many things need attention, for a card that has one line to say it in. */
        val missingCount: Int
            get() = listOf(
                overlayMissing, lockMissing, notificationMissing, writeSettingsMissing
            ).count { it }

        val anyMissing: Boolean get() = missingCount > 0

        /** True when only optional extras are missing — the app itself still works. */
        val onlyOptionalMissing: Boolean get() = !overlayMissing && anyMissing
    }

    fun read(context: Context, preference: SharedPref): Needs {
        val tapActions = listOf(
            preference.getHandlerSingleTapAction(),
            preference.getHandlerDoubleTapAction(),
            preference.getHandlerLongTapAction()
        )
        val swipeActions = listOf(
            preference.getHandlerSwipeUpAction(),
            preference.getHandlerSwipeDownAction()
        )

        // The long-press menu counts as "configured" too: an entry the user put there is one they
        // intend to tap, and the pinned entries are added by the catalog rather than chosen, so
        // the stored set alone would miss nothing but could include less than the menu shows.
        val menuActions = HandlerActionCatalog
            .contextMenuEntries(preference.getContextMenuItems())
            .map { it.action }

        val lockConfigured = HandlerActions.LOCK in tapActions || HandlerActions.LOCK in menuActions
        val brightnessConfigured = (tapActions + swipeActions + menuActions)
            .any { HandlerActions.needsWriteSettings(it) }

        return Needs(
            overlayMissing = !Settings.canDrawOverlays(context),
            lockMissing = lockConfigured && !LockScreenUtil(context).canLock(),
            notificationMissing = preference.getShowNotification() &&
                    !hasNotificationPermission(context),
            writeSettingsMissing = brightnessConfigured && !Settings.System.canWrite(context)
        )
    }

    /** Whether Android currently lets this app post notifications. Always true below Android 13. */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
}
