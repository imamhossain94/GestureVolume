package com.newagedevs.gesturevolume.ui.viewmodels

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.manager.PurchaseEvent
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.helper.ApplovinAdsManager
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.service.OverlayServiceInterface
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.LockScreenUtil
import com.newagedevs.gesturevolume.utils.PermissionNeeds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.Observer
import com.newagedevs.gesturevolume.helper.extensions.shareApp
import com.newagedevs.gesturevolume.helper.extensions.openAppStore
import com.newagedevs.gesturevolume.livedata.LiveDataManager

@HiltViewModel
class MainViewModel @Inject constructor(
    val preference: SharedPref,
    val billingManager: BillingManager
) : ViewModel() {

    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val _effect = Channel<MainEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    var overlayService: OverlayServiceInterface? = null
    private var serviceConnection: ServiceConnection? = null
    private var isBound = false

    var lastBackPressedTime: Long = 0

    var adsManager: ApplovinAdsManager? = null

    private var messageObserver: Observer<String>? = null

    init {
        initializeData()
    }

    private fun initializeData() {
        _state.value = _state.value.copy(
            isRunning = preference.isRunning(),
            isProActivated = preference.isProFeatureActivated(),
            clickAction = preference.getHandlerSingleTapAction(),
            doubleClickAction = preference.getHandlerDoubleTapAction(),
            longClickAction = preference.getHandlerLongTapAction(),
            swipeUpAction = preference.getHandlerSwipeUpAction(),
            swipeDownAction = preference.getHandlerSwipeDownAction(),
            clickActionIcon = getActionIcon(preference.getHandlerSingleTapAction()),
            doubleClickActionIcon = getActionIcon(preference.getHandlerDoubleTapAction()),
            longClickActionIcon = getActionIcon(preference.getHandlerLongTapAction()),
            swipeUpActionIcon = getSwipeUpIcon(preference.getHandlerSwipeUpAction()),
            swipeDownActionIcon = getSwipeDownIcon(preference.getHandlerSwipeDownAction()),
            isHandlerHidden = preference.isHandlerHidden(),
            theme = preference.getTheme(),
            language = preference.getLanguage()
        )
    }

    /**
     * Which setting a pending brightness action belongs to.
     *
     * Typed rather than a boolean: the same brightness-permission gate is reachable from all five
     * action slots, and a boolean "was it swipe up?" would silently write a tap action into a swipe
     * preference — destroying a setting the user had already configured.
     */
    private enum class ActionSlot { SINGLE_TAP, DOUBLE_TAP, LONG_TAP, SWIPE_UP, SWIPE_DOWN }

    private var pendingBrightnessAction: Pair<String, ActionSlot>? = null


    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.ToggleService -> toggleService(event.isRunning, event.context)
            is MainEvent.SetClickAction -> setClickAction(event.action, event.context)
            is MainEvent.SetDoubleClickAction -> setDoubleClickAction(event.action, event.context)
            is MainEvent.SetLongClickAction -> setLongClickAction(event.action, event.context)
            is MainEvent.SetSwipeUpAction -> setSwipeUpAction(event.action, event.context)
            is MainEvent.SetSwipeDownAction -> setSwipeDownAction(event.action, event.context)
            is MainEvent.UpdatePermissionsStatus -> updatePermissionsStatus(event.context)
            is MainEvent.SyncServiceState -> syncServiceState(event.context)
            is MainEvent.WriteSettingsResult -> onWriteSettingsResult(event.context)
            MainEvent.CancelPendingBrightnessAction -> {
                pendingBrightnessAction = null
                _state.value = _state.value.copy(pendingWriteSettingsRequest = false)
            }
            is MainEvent.SetHandlerHidden -> setHandlerHidden(event.hidden, event.context)
            is MainEvent.ResetAllSettings -> resetAllSettings(event.context)
            MainEvent.ShowProDialog -> showProDialog()
        }
    }

    private fun updatePermissionsStatus(context: Context) {
        val hasOverlay = Settings.canDrawOverlays(context)
        val needs = PermissionNeeds.read(context, preference)

        _state.value = _state.value.copy(
            hasOverlayPermission = hasOverlay,
            hasWriteSettingsPermission = Settings.System.canWrite(context),
            missingPermissionCount = needs.missingCount,
            // Refreshed here because this runs on every ON_RESUME, and the bar can be hidden from
            // the overlay's own menu or the notification while the app sits in the background.
            isHandlerHidden = preference.isHandlerHidden()
        )
    }

    /**
     * Shows or hides the bar deliberately, from inside the app.
     *
     * Un-hiding does **not** send `user_show`. That command builds the handler window there and
     * then, and the app deliberately keeps the bar out of the way while it is in the foreground —
     * so the bar would pop up over the screen the user just tapped, contradicting the message
     * telling them it will be back when they leave. Clearing the preference is enough: the `show`
     * the Activity already sends from `onPause` puts the bar back on the way out.
     *
     * Hiding, by contrast, has to reach the service, since there may be a window to take down.
     *
     * Either way the notification is re-posted, because its first button swaps between Show and
     * Hide and only the service can replace it.
     */
    private fun setHandlerHidden(hidden: Boolean, context: Context) {
        preference.setHandlerHidden(hidden)
        _state.value = _state.value.copy(isHandlerHidden = hidden)
        if (!preference.isRunning()) return
        val intent = Intent(context, OverlayService::class.java)
            .setAction(if (hidden) "user_hide" else "refresh_notification")
        try {
            context.startService(intent)
        } catch (_: Exception) {
            // Service not running; the preference above is still the durable answer.
        }
    }

    /**
     * Gates an action that needs WRITE_SETTINGS.
     *
     * @return true when the caller should go ahead and persist the action.
     */
    private fun requireWriteSettings(action: String, slot: ActionSlot, context: Context): Boolean {
        if (!HandlerActions.needsWriteSettings(action)) return true
        if (Settings.System.canWrite(context)) return true

        pendingBrightnessAction = action to slot
        _state.value = _state.value.copy(pendingWriteSettingsRequest = true)
        return false
    }

    /**
     * Notices that the Lock action has been chosen with no way to actually lock, and asks.
     *
     * Called *after* the action is saved, not instead of saving it. The previous code returned
     * early and launched the Device Admin prompt, which meant the user's choice was thrown away:
     * they granted admin, came back, and the action was still whatever it had been before. Now the
     * setting is theirs either way and the permission is a separate question.
     */
    private fun requireLockPermission(action: String, context: Context) {
        if (action != HandlerActions.LOCK) return
        if (LockScreenUtil(context).canLock()) return
        _state.value = _state.value.copy(pendingLockPermissionRequest = true)
    }

    /** The user accepted the prompt: off to the accessibility list to switch the service on. */
    fun onLockPermissionChoice(context: Context) {
        _state.value = _state.value.copy(pendingLockPermissionRequest = false)
        val util = LockScreenUtil(context)
        if (!util.accessibilitySupported()) return
        preference.setAppOpenAdPaused(true)
        util.openAccessibilitySettings()
    }

    /**
     * Raises the lock-permission prompt when [actions] contains Lock and nothing can lock.
     *
     * Public because the long-press menu picker chooses actions too, and choosing Lock there used
     * to save silently — the entry appeared in the menu, and tapping it on the bar did nothing but
     * show a message long after the moment the user could have connected it to what they had just
     * set. The tap-action pickers reach the same prompt through [requireLockPermission].
     */
    fun checkLockPermission(actions: Set<String>, context: Context) {
        if (HandlerActions.LOCK !in actions) return
        if (LockScreenUtil(context).canLock()) return
        _state.value = _state.value.copy(pendingLockPermissionRequest = true)
    }

    fun cancelLockPermissionRequest() {
        _state.value = _state.value.copy(pendingLockPermissionRequest = false)
    }

    /** Applies whatever the user was trying to set before we sent them to grant the permission. */
    private fun onWriteSettingsResult(context: Context) {
        updatePermissionsStatus(context)
        _state.value = _state.value.copy(pendingWriteSettingsRequest = false)

        val (action, slot) = pendingBrightnessAction ?: return
        pendingBrightnessAction = null
        if (!Settings.System.canWrite(context)) return

        when (slot) {
            ActionSlot.SINGLE_TAP -> setClickAction(action, context)
            ActionSlot.DOUBLE_TAP -> setDoubleClickAction(action, context)
            ActionSlot.LONG_TAP -> setLongClickAction(action, context)
            ActionSlot.SWIPE_UP -> setSwipeUpAction(action, context)
            ActionSlot.SWIPE_DOWN -> setSwipeDownAction(action, context)
        }
        sendUpdateToService(context)
    }

    private fun syncServiceState(context: Context) {
        val actualRunning = isServiceRunning(context, OverlayService::class.java)
        val prefRunning = preference.isRunning()
        
        if (actualRunning != _state.value.isRunning || actualRunning != prefRunning) {
            // If actual state differs from state/pref, we trust actual state
            // But if user intended it to run (pref is true) but it's not (actual is false),
            // it means it was killed. We might want to restart it here too,
            // but for UI sync, we just update the state.
            
            _state.value = _state.value.copy(isRunning = actualRunning)
            
            // Note: We don't necessarily update SharedPref here because if it's true 
            // and service is dead, our restart mechanisms should bring it back.
            // However, for UI toggle sync, we use actualRunning.
        }
    }

    fun showToast(message: String) {
        viewModelScope.launch {
            _effect.send(MainEffect.ShowToast(message))
        }
    }

    private fun toggleService(isRunning: Boolean, context: Context) {
        // Overlay permission is the only hard gate. The notification permission is asked for
        // separately, below, and never blocks: the old code refused to start the service at all
        // when POST_NOTIFICATIONS was declined, which made the toggle fail silently.
        if (!Settings.canDrawOverlays(context)) {
            // Reset state before requesting permission
            preference.setRunning(false)
            _state.value = _state.value.copy(isRunning = false)

            viewModelScope.launch {
                _effect.send(MainEffect.RequestOverlayPermission)
                _effect.send(MainEffect.ShowToast(context.getString(R.string.overlay_permission_toast)))
            }
            return
        }

        // Only update preference and state if permissions are granted
        preference.setRunning(isRunning)
        _state.value = _state.value.copy(isRunning = isRunning)

        if (isRunning) {
            // Switching the service on is an explicit request for the bar, so it overrides a
            // previous "Hide handler". Without this the toggle would go green and nothing would
            // appear — the service starts with no action, which is the path that deliberately
            // leaves a hidden bar hidden.
            preference.setHandlerHidden(false)
            _state.value = _state.value.copy(isHandlerHidden = false)
            maybeAskForNotificationPermission(context)
            // The interstitial is requested here but shown when the service actually connects.
            // It used to be shown on this line — before the service had started — so it landed
            // while the user was still waiting to find out whether the thing had worked, and it
            // fired just as readily when the start then failed.
            startOverlayService(context, announceWithAd = true)
        } else {
            stopOverlayService(context)
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS the first time the service is switched on, and never again.
     *
     * Android 13+ stops showing the dialog after two refusals, so repeating the request on every
     * start would be a no-op that reads as a bug. Skipped entirely when the user has already
     * turned the notification off in settings — there would be nothing to post.
     */
    private fun maybeAskForNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!preference.getShowNotification()) return
        if (preference.hasAskedNotificationPermission()) return
        if (
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        preference.setAskedNotificationPermission(true)
        viewModelScope.launch { _effect.send(MainEffect.RequestNotificationPermission) }
    }

    /**
     * Show an interstitial at an ordinary transition — opening Appearance or Actions — gated by
     * the SharedPref cooldowns, the per-session cap and the post-install grace period.
     *
     * Interstitial is by far the top-earning format ($3.10 eCPM) yet it fired only on the service
     * toggle before, so it barely showed; a few genuine break points lift revenue while the caps
     * protect retention.
     */
    fun maybeShowInterstitialAd() {
        if (_state.value.isProActivated) return
        if (preference.shouldShowInterstitialAd()) {
            adsManager?.showInterstitialAd(
                loaded = { preference.saveInterstitialAdTime() }
            )
        }
    }

    /**
     * The service has started and the handler is ready: show the one interstitial that is allowed
     * during the post-install grace period.
     *
     * Separate from [maybeShowInterstitialAd] rather than a boolean on it, so the exception to the
     * grace period is visible at the call site and cannot spread to the transition placements by
     * someone passing the wrong argument.
     */
    private fun maybeShowServiceStartInterstitial() {
        if (_state.value.isProActivated) return
        if (preference.shouldShowServiceStartInterstitial()) {
            adsManager?.showInterstitialAd(
                loaded = { preference.saveInterstitialAdTime() }
            )
        }
    }

    /**
     * @param announceWithAd true only when the user just switched the service on themselves. The
     *   silent repair path calls this too, and a system-killed service quietly coming back is not
     *   a moment to show anybody an ad. Carried as a parameter rather than a field so a bind that
     *   never connects cannot leave it armed for the next caller.
     */
    private fun startOverlayService(context: Context, announceWithAd: Boolean = false) {
        val service = Intent(context, OverlayService::class.java)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service)
            } else {
                context.startService(service)
            }
        } catch (e: Exception) {
            context.startService(service)
        }
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                overlayService = (service as OverlayService.LocalBinder).instance()
                isBound = true
                // The service is bound and the handler is configured and ready, which is the
                // moment setup is finished — the one full-screen ad the grace period allows.
                if (announceWithAd) maybeShowServiceStartInterstitial()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                overlayService = null
                isBound = false
            }
        }
        serviceConnection?.let {
            context.bindService(service, it, Context.BIND_AUTO_CREATE)
        }
    }

    fun stopOverlayService(context: Context) {
        // Unbind if we're bound
        if (isBound) {
            try {
                serviceConnection?.let { context.unbindService(it) }
            } catch (_: Exception) { /* already unbound */ }
            isBound = false
        }
        overlayService = null

        // Always stop the service directly rather than routing via intents to avoid LiveDataManager loops
        try {
            context.stopService(Intent(context, OverlayService::class.java))
        } catch (_: Exception) {}
    }

    /**
     * Re-bind to an already-running OverlayService.
     * Called from MainActivity.onStart() to recover the binding after the app
     * was cleared from recents and reopened.
     */
    /**
     * If the user wants the overlay running but the system has killed the service, bring it back.
     *
     * Called while the app is in the foreground, which is exactly when starting a foreground
     * service is unambiguously allowed — so this is the one repair path that cannot be refused
     * by the Android 12+ background-start restrictions.
     */
    fun repairServiceIfNeeded(context: Context) {
        if (!preference.isRunning()) return
        if (isServiceRunning(context, OverlayService::class.java)) return
        if (!Settings.canDrawOverlays(context)) return
        startOverlayService(context)
    }

    fun rebindToServiceIfRunning(context: Context) {
        if (preference.isRunning() && !isBound) {
            val actuallyRunning = isServiceRunning(context, OverlayService::class.java)
            if (actuallyRunning) {
                val service = Intent(context, OverlayService::class.java)
                serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) {
                        overlayService = (service as OverlayService.LocalBinder).instance()
                        isBound = true
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        overlayService = null
                        isBound = false
                    }
                }
                serviceConnection?.let {
                    context.bindService(service, it, Context.BIND_AUTO_CREATE)
                }
            } else {
                // Service died — sync state
                _state.value = _state.value.copy(isRunning = false)
            }
        }
    }

    /**
     * Send an "update" command to the running service so it reloads
     * its handler with the latest settings from SharedPref.
     */
    fun sendUpdateToService(context: Context) {
        if (preference.isRunning()) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = "update"
            }
            try {
                context.startService(intent)
            } catch (_: Exception) { /* service not running */ }
        }
    }

    /**
     * Factory-resets the app: every setting back to default, the overlay stopped, the UI in step.
     *
     * The service is stopped *before* the wipe rather than after. `resetAll` clears `isRunning`,
     * and a stop issued after that reads a preference that already says the service is off — so
     * the overlay would be left running with no record of it and no switch showing it.
     */
    private fun resetAllSettings(context: Context) {
        stopOverlayService(context)
        overlayService?.shouldFinish = true
        preference.resetAll()
        initializeData()
        _state.value = _state.value.copy(
            isRunning = false,
            isHandlerHidden = false,
            pendingWriteSettingsRequest = false,
            pendingLockPermissionRequest = false
        )
    }

    /**
     * Re-posts the ongoing notification, and nothing else.
     *
     * Deliberately not [sendUpdateToService], which rebuilds the handler window: the bar is hidden
     * while the app is in the foreground, so a rebuild would make it appear on top of the settings
     * screen the moment the switch was flipped.
     */
    fun refreshServiceNotification(context: Context) {
        if (!preference.isRunning()) return
        val intent = Intent(context, OverlayService::class.java)
            .setAction("refresh_notification")
        try {
            context.startService(intent)
        } catch (_: Exception) {
            // Service not running; the preference is already written either way.
        }
    }

    private fun setClickAction(action: String, context: Context) {
        if (!requireWriteSettings(action, ActionSlot.SINGLE_TAP, context)) return
        preference.setHandlerSingleTapAction(action)
        requireLockPermission(action, context)
        _state.value = _state.value.copy(
            clickAction = action,
            clickActionIcon = getActionIcon(action)
        )
    }

    private fun setDoubleClickAction(action: String, context: Context) {
        if (!requireWriteSettings(action, ActionSlot.DOUBLE_TAP, context)) return
        preference.setHandlerDoubleTapAction(action)
        requireLockPermission(action, context)
        _state.value = _state.value.copy(
            doubleClickAction = action,
            doubleClickActionIcon = getActionIcon(action)
        )
    }

    private fun setLongClickAction(action: String, context: Context) {
        if (!requireWriteSettings(action, ActionSlot.LONG_TAP, context)) return
        preference.setHandlerLongTapAction(action)
        requireLockPermission(action, context)
        _state.value = _state.value.copy(
            longClickAction = action,
            longClickActionIcon = getActionIcon(action)
        )
    }

    private fun setSwipeUpAction(action: String, context: Context) {
        if (!requireWriteSettings(action, ActionSlot.SWIPE_UP, context)) return
        preference.setHandlerSwipeUpAction(action)
        _state.value = _state.value.copy(
            swipeUpAction = action,
            swipeUpActionIcon = getSwipeUpIcon(action)
        )
    }

    private fun setSwipeDownAction(action: String, context: Context) {
        if (!requireWriteSettings(action, ActionSlot.SWIPE_DOWN, context)) return
        preference.setHandlerSwipeDownAction(action)
        _state.value = _state.value.copy(
            swipeDownAction = action,
            swipeDownActionIcon = getSwipeDownIcon(action)
        )
    }

    private fun showProDialog() {
        viewModelScope.launch {
            _effect.send(MainEffect.ShowProDialog)
        }
    }

    /**
     * One lookup, from the same catalog the dialogs and the overlay menu read.
     *
     * This used to be a `when` listing every action by hand, which meant "Mute or Unmute" — never
     * in the list — showed the do-nothing icon on the main screen for as long as it has existed.
     */
    private fun getActionIcon(action: String): Int =
        HandlerActionCatalog.entryFor(action)?.iconRes ?: R.drawable.ic_nothing

    private fun getSwipeUpIcon(action: String): Int {
        return when (action) {
            HandlerActions.NONE -> R.drawable.ic_nothing
            HandlerActions.INCREASE_VOLUME -> R.drawable.ic_vol_plus
            HandlerActions.INCREASE_VOLUME_UI -> R.drawable.ic_vol_increase
            HandlerActions.INCREASE_BRIGHTNESS -> R.drawable.ic_brightness_up
            else -> R.drawable.ic_nothing
        }
    }

    private fun getSwipeDownIcon(action: String): Int {
        return when (action) {
            HandlerActions.NONE -> R.drawable.ic_nothing
            HandlerActions.DECREASE_VOLUME -> R.drawable.ic_vol_minus
            HandlerActions.DECREASE_VOLUME_UI -> R.drawable.ic_vol_decrease
            HandlerActions.DECREASE_BRIGHTNESS -> R.drawable.ic_brightness_down
            else -> R.drawable.ic_nothing
        }
    }

    fun handleMenuOption(option: String, context: Context) {
        when (option) {
            "Premium" -> purchasePro(context as Activity)
            "Theme" -> viewModelScope.launch { _effect.send(MainEffect.ShowThemeDialog) }
            "Language" -> viewModelScope.launch { _effect.send(MainEffect.ShowLanguageDialog) }
            "Share" -> shareApp(context)
            "Feedback" -> viewModelScope.launch {
                _effect.send(MainEffect.NavigateToFeedback)
            }
            "Other apps" -> openAppStore(context, Constants.PUBLISHER_URL) {
                viewModelScope.launch {
                    _effect.send(MainEffect.ShowToast(context.getString(R.string.cannot_open_play_store)))
                }
            }
            "Rate us" -> openAppStore(context, Constants.APP_STORE_ID) {
                viewModelScope.launch {
                    _effect.send(MainEffect.ShowToast(context.getString(R.string.cannot_open_play_store)))
                }
            }
            "About" -> viewModelScope.launch {
                _effect.send(MainEffect.NavigateToAbout)
            }
            "Troubleshoot" -> viewModelScope.launch {
                _effect.send(MainEffect.NavigateToTroubleshoot)
            }
            "Reset" -> viewModelScope.launch {
                _effect.send(MainEffect.ConfirmResetApp)
            }
        }
    }

    /**
     * Initialize In-App Purchase connector
     * Sets up purchase listeners and handles purchase/restore events
     */
    fun initializeIAP(context: Context) {
        billingManager.initialize()

        viewModelScope.launch {
            billingManager.lifetimePrice.collect { price ->
                _effect.send(MainEffect.ProductDetailsLoaded(price))
            }
        }
        
        viewModelScope.launch {
            billingManager.events.collect { event ->
                when(event) {
                    PurchaseEvent.PURCHASE_SUCCESS -> {
                        adsManager?.destroyAds()
                        adsManager = null
                        _effect.send(MainEffect.ShowToast(context.getString(R.string.purchase_success)))
                        _state.value = _state.value.copy(isProActivated = true)
                    }
                    PurchaseEvent.PURCHASE_RESTORED -> {
                        adsManager?.destroyAds()
                        adsManager = null
                        _effect.send(MainEffect.ShowToast(context.getString(R.string.purchase_restored)))
                        _state.value = _state.value.copy(isProActivated = true)
                    }
                    PurchaseEvent.ALREADY_OWNED -> {
                        _effect.send(MainEffect.ShowToast(context.getString(R.string.item_already_owned)))
                    }
                    PurchaseEvent.PURCHASE_FAILURE -> {
                        _effect.send(MainEffect.ShowToast(context.getString(R.string.purchase_failed)))
                    }
                    PurchaseEvent.NOTHING_TO_RESTORE -> {
                        _effect.send(MainEffect.ShowToast(context.getString(R.string.nothing_to_restore)))
                    }
                }
            }
        }
        
        viewModelScope.launch {
            billingManager.isPremium.collect { isPremium ->
                if (isPremium) {
                    _state.value = _state.value.copy(isProActivated = true)
                }
            }
        }
    }

    fun initializeAdsManager(activity: Activity) {
        if (_state.value.isProActivated) {
            adsManager?.destroyAds()
            adsManager = null
            return
        }

        if (adsManager == null) {
            // Pass preferences to the ads manager
            adsManager = ApplovinAdsManager(activity)
        }
    }

    fun purchasePro(activity: Activity) {
        if (_state.value.isProActivated) {
            viewModelScope.launch {
                _effect.send(MainEffect.ShowToast(activity.getString(R.string.already_have_premium)))
            }
            return
        }

        billingManager.purchase(activity, BuildConfig.PRODUCT_LIFETIME)
    }

    fun onBackPressed(context: Context, finishActivity: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressedTime > 2000) {
            viewModelScope.launch {
                _effect.send(MainEffect.ShowToast(context.getString(R.string.press_back_again_to_exit)))
            }
            lastBackPressedTime = currentTime
        } else {
            finishActivity()
        }
    }


    fun observeCommunicator(activity: Activity) {
        if (messageObserver != null) return

        messageObserver = Observer { message ->
            when (message) {
                "show" -> {
                    // Handle show command - could restart or show overlay
                    if (!_state.value.isRunning && Settings.canDrawOverlays(activity)) {
                        startOverlayService(activity)
                        preference.setRunning(true)
                        _state.value = _state.value.copy(isRunning = true)
                    }
                }
                "stop" -> {
                    // Handle stop command - stop the service
                    stopOverlayService(activity)
                    preference.setRunning(false)
                    _state.value = _state.value.copy(isRunning = false, isHandlerHidden = false)
                    overlayService?.shouldFinish = true
                }
                // The notification's first button, pressed while the app is open behind it. The
                // service has already acted; this is only the app hearing about it, so that the
                // "Handler is hidden" card appears and disappears in step rather than waiting for
                // the next ON_RESUME.
                "user_hide" -> _state.value = _state.value.copy(isHandlerHidden = true)
                "user_show" -> _state.value = _state.value.copy(isHandlerHidden = false)
                else -> {

                }
            }
        }

        LiveDataManager.communicator().observe(activity as LifecycleOwner, messageObserver!!)
    }

    fun removeObserver() {
        messageObserver?.let {
            LiveDataManager.communicator().removeObserver(it)
            messageObserver = null
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun getNextBackground(): String {
        val backgrounds = listOf(
            "https://images.pexels.com/photos/1226302/pexels-photo-1226302.jpeg",
            "https://images.pexels.com/photos/1366630/pexels-photo-1366630.jpeg",
            "https://images.pexels.com/photos/14584298/pexels-photo-14584298.jpeg",
            "https://images.pexels.com/photos/20462015/pexels-photo-20462015.jpeg",
            "https://images.pexels.com/photos/2406450/pexels-photo-2406450.jpeg",
            "https://images.pexels.com/photos/3722752/pexels-photo-3722752.jpeg",
            "https://images.pexels.com/photos/109998/pexels-photo-109998.jpeg",
            "https://images.pexels.com/photos/1172675/pexels-photo-1172675.jpeg",
            "https://images.pexels.com/photos/1809644/pexels-photo-1809644.jpeg",
            "https://images.pexels.com/photos/40896/larch-conifer-cone-branch-tree-40896.jpeg",
            "https://images.pexels.com/photos/60597/dahlia-red-blossom-bloom-60597.jpeg",
            "https://images.pexels.com/photos/73813/balkan-anemone-flower-blossom-bloom-73813.jpeg",
            "https://images.pexels.com/photos/27551220/pexels-photo-27551220.jpeg",
            "https://images.pexels.com/photos/103659/cosmea-blossom-bloom-cosmos-103659.jpeg",
            "https://images.pexels.com/photos/10747640/pexels-photo-10747640.jpeg",
            "https://images.pexels.com/photos/1671431/pexels-photo-1671431.jpeg",
            "https://images.pexels.com/photos/2224401/pexels-photo-2224401.jpeg"
        )

        val lastIndex = preference.sharedPreferences.getInt("last_bg_index", -1)
        val nextIndex = (lastIndex + 1) % backgrounds.size

        preference.sharedPreferences.edit { putInt("last_bg_index", nextIndex) }

        return backgrounds[nextIndex]
    }

    fun setTheme(theme: Int) {
        preference.setTheme(theme)
        _state.value = _state.value.copy(theme = theme)
    }

    fun setLanguage(language: String) {
        preference.setLanguage(language)
        _state.value = _state.value.copy(language = language)
    }

    override fun onCleared() {
        super.onCleared()
        adsManager?.destroyAds()
        adsManager = null
        messageObserver = null
    }
}