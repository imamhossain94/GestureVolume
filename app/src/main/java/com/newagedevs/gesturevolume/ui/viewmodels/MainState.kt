package com.newagedevs.gesturevolume.ui.viewmodels

import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.utils.ActionIcon
import com.newagedevs.gesturevolume.utils.HandlerActions

data class MainState(
    val isRunning: Boolean = false,
    val isProActivated: Boolean = false,
    val clickAction: String = "Open volume UI",
    val clickActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_vol_increase),
    // These match the SharedPref defaults they are immediately overwritten with. They used to
    // name different actions entirely, which meant the first frame after launch advertised
    // settings the user did not have.
    val doubleClickAction: String = HandlerActions.NONE,
    val doubleClickActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_nothing),
    val tripleClickAction: String = HandlerActions.NONE,
    val tripleClickActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_nothing),
    val longClickAction: String = HandlerActions.REPOSITION,
    val longClickActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_move),
    val swipeUpAction: String = "Increase volume and show UI",
    val swipeUpActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_vol_increase),
    val swipeDownAction: String = "Decrease volume and show UI",
    val swipeDownActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_vol_decrease),
    val swipeInAction: String = HandlerActions.OPEN_DECK,
    val swipeInActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_nothing),
    val swipeOutAction: String = HandlerActions.NONE,
    val swipeOutActionIcon: ActionIcon = ActionIcon.Res(R.drawable.ic_nothing),
    val hasOverlayPermission: Boolean = false,
    val hasWriteSettingsPermission: Boolean = false,
    /** Whether the accessibility service is switched on in system settings. */
    val isAccessibilityEnabled: Boolean = false,
    /**
     * How many permissions the user's own configuration has made necessary but which are not
     * granted — see [com.newagedevs.gesturevolume.utils.PermissionNeeds].
     *
     * On the main screen so the Permissions card can say something is wrong without the user
     * having to open it and compare every row against what they set elsewhere.
     */
    val missingPermissionCount: Int = 0,
    /**
     * Every missing permission and the setting that wants it. Refreshed with the count above, on
     * every return to the home screen, so its cards warn about what the other screens just changed.
     */
    val permissionNeeds: com.newagedevs.gesturevolume.utils.PermissionNeeds.Needs =
        com.newagedevs.gesturevolume.utils.PermissionNeeds.Needs(),
    /**
     * True when the user has put the bar away with "Hide handler".
     *
     * In state, not just in SharedPref, because the app is the second route back from it — the
     * notification being the first — and a route back has to be visible to be a route. While the
     * app is in the foreground the bar is hidden regardless, so its absence tells the user
     * nothing.
     */
    val isHandlerHidden: Boolean = false,
    /**
     * True while a brightness action is waiting on WRITE_SETTINGS.
     *
     * Carried in state rather than through [MainEffect], because the effect channel has a single
     * consumer — a second screen collecting it would swallow toasts intended for the first.
     */
    val pendingWriteSettingsRequest: Boolean = false,
    /**
     * True right after the user bound an action only the accessibility service can perform while
     * that service is off. The action is saved regardless; this only asks them to switch it on.
     */
    val showAccessibilityPrompt: Boolean = false,
    /** The same, for a Do Not Disturb action bound without Do Not Disturb access. */
    val showDndPrompt: Boolean = false,
    /**
     * True after an update, until What's new is opened: the dot on the home screen's icon and the
     * "New" in the drawer. See SharedPref.hasUnseenWhatsNew.
     */
    val hasUnseenWhatsNew: Boolean = false,
    /** Regular or advanced: whether the home screen opens its advanced features. See UserMode. */
    val userMode: String = com.newagedevs.gesturevolume.utils.UserMode.REGULAR,
    val theme: Int = 0,
    val language: String = "en"
)
