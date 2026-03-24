package com.newagedevs.gesturevolume.ui.viewmodels

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limurse.iap.DataWrappers
import com.limurse.iap.IapConnector
import com.limurse.iap.PurchaseServiceListener
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.helper.ApplovinAdsManager
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.service.OverlayServiceInterface
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.utils.LockScreenUtil
import com.newagedevs.gesturevolume.utils.NotificationUtil
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
    val preference: SharedPref
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
    var iapConnector: IapConnector? = null
    private var productDetails: DataWrappers.PricingPhase? = null

    private var messageObserver: Observer<String>? = null

    init {
        initializeData()
    }

    private fun initializeData() {
        _state.value = _state.value.copy(
            isRunning = preference.isRunning(),
            isProActivated = preference.isProFeatureActivated(),
            gravity = preference.getHandlerPosition(),
            translationY = preference.getHandlerTranslationY(),
            color = preference.getHandlerColor(),
            clickAction = preference.getHandlerSingleTapAction(),
            doubleClickAction = preference.getHandlerDoubleTapAction(),
            longClickAction = preference.getHandlerLongTapAction(),
            swipeUpAction = preference.getHandlerSwipeUpAction(),
            swipeDownAction = preference.getHandlerSwipeDownAction(),
            gravityIcon = when (preference.getHandlerPosition()) {
                "Left" -> R.drawable.ic_align_left
                "Right" -> R.drawable.ic_align_right
                else -> R.drawable.ic_align_right
            },
            clickActionIcon = getActionIcon(preference.getHandlerSingleTapAction()),
            doubleClickActionIcon = getActionIcon(preference.getHandlerDoubleTapAction()),
            longClickActionIcon = getActionIcon(preference.getHandlerLongTapAction()),
            swipeUpActionIcon = getSwipeUpIcon(preference.getHandlerSwipeUpAction()),
            swipeDownActionIcon = getSwipeDownIcon(preference.getHandlerSwipeDownAction())
        )
    }

    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.ToggleService -> toggleService(event.isRunning, event.context)
            is MainEvent.SetServiceRunning -> setServiceRunning(event.isRunning)
            is MainEvent.SetGravity -> setGravity(event.gravity)
            is MainEvent.SetColor -> setColor(event.color)
            is MainEvent.SetClickAction -> setClickAction(event.action, event.context)
            is MainEvent.SetDoubleClickAction -> setDoubleClickAction(event.action, event.context)
            is MainEvent.SetLongClickAction -> setLongClickAction(event.action, event.context)
            is MainEvent.SetSwipeUpAction -> setSwipeUpAction(event.action)
            is MainEvent.SetSwipeDownAction -> setSwipeDownAction(event.action)
            is MainEvent.UpdatePermissionsStatus -> updatePermissionsStatus(event.context)
            is MainEvent.SyncServiceState -> syncServiceState(event.context)
            MainEvent.ShowProDialog -> showProDialog()
        }
    }

    private fun updatePermissionsStatus(context: Context) {
        val hasOverlay = Settings.canDrawOverlays(context)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationUtil(context).isPermissionGranted()
        } else {
            true
        }

        _state.value = _state.value.copy(
            hasOverlayPermission = hasOverlay,
            hasNotificationPermission = hasNotification
        )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (isRunning && !NotificationUtil(context).isPermissionGranted()) {
                // Reset state before requesting permission
                preference.setRunning(false)
                _state.value = _state.value.copy(isRunning = false)

                viewModelScope.launch {
                    _effect.send(MainEffect.RequestNotificationPermission)
                }
                return
            }
        }

        if (!Settings.canDrawOverlays(context)) {
            // Reset state before requesting permission
            preference.setRunning(false)
            _state.value = _state.value.copy(isRunning = false)

            viewModelScope.launch {
                _effect.send(MainEffect.RequestOverlayPermission)
                _effect.send(MainEffect.ShowToast("Please grant overlay permission to continue."))
            }
            return
        }

        // Only update preference and state if permissions are granted
        preference.setRunning(isRunning)
        _state.value = _state.value.copy(isRunning = isRunning)

        if (isRunning) {
            // Show interstitial ad with cooldown check
            if (!_state.value.isProActivated) {
                if (preference.shouldShowInterstitialAd()) {
                    adsManager?.showInterstitialAd(
                        loaded = { preference.saveInterstitialAdTime() }
                    )
                }
            }
            startOverlayService(context)
        } else {
            stopOverlayService(context)
        }
    }

    private fun setServiceRunning(isRunning: Boolean) {
        preference.setRunning(isRunning)
        _state.value = _state.value.copy(isRunning = isRunning)
    }

    private fun startOverlayService(context: Context) {
        val service = Intent(context, OverlayService::class.java)
        if (!isServiceRunning(context, OverlayService::class.java)) {
            context.startService(service)
        }
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
    }

    fun stopOverlayService(context: Context) {
        val service = Intent(context, OverlayService::class.java)
        if (isBound) {
            serviceConnection?.let { context.unbindService(it) }
            context.stopService(service)
            isBound = false
        }
    }

    private fun setGravity(gravity: String) {
        preference.setHandlerPosition(gravity)
        val icon = when (gravity) {
            "Left" -> R.drawable.ic_align_left
            "Right" -> R.drawable.ic_align_right
            else -> R.drawable.ic_align_right
        }
        _state.value = _state.value.copy(gravity = gravity, gravityIcon = icon)
    }

    private fun setColor(color: Int) {
        preference.setHandlerColor(color)
        _state.value = _state.value.copy(color = color)
    }

    private fun setClickAction(action: String, context: Context) {
        val lockScreenUtil = LockScreenUtil(context)
        if (action == "Lock" && !lockScreenUtil.active()) {
            lockScreenUtil.enableAdmin()
            return
        }
        preference.setHandlerSingleTapAction(action)
        _state.value = _state.value.copy(
            clickAction = action,
            clickActionIcon = getActionIcon(action)
        )
    }

    private fun setDoubleClickAction(action: String, context: Context) {
        val lockScreenUtil = LockScreenUtil(context)
        if (action == "Lock" && !lockScreenUtil.active()) {
            lockScreenUtil.enableAdmin()
            return
        }
        preference.setHandlerDoubleTapAction(action)
        _state.value = _state.value.copy(
            doubleClickAction = action,
            doubleClickActionIcon = getActionIcon(action)
        )
    }

    private fun setLongClickAction(action: String, context: Context) {
        if (!_state.value.isProActivated) {
            purchasePro(context as Activity)
            return
        }
        val lockScreenUtil = LockScreenUtil(context)
        if (action == "Lock" && !lockScreenUtil.active()) {
            lockScreenUtil.enableAdmin()
            return
        }
        preference.setHandlerLongTapAction(action)
        _state.value = _state.value.copy(
            longClickAction = action,
            longClickActionIcon = getActionIcon(action)
        )
    }

    private fun setSwipeUpAction(action: String) {
        preference.setHandlerSwipeUpAction(action)
        _state.value = _state.value.copy(
            swipeUpAction = action,
            swipeUpActionIcon = getSwipeUpIcon(action)
        )
    }

    private fun setSwipeDownAction(action: String) {
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

    private fun getActionIcon(action: String): Int {
        return when (action) {
            "None" -> R.drawable.ic_nothing
            "Open volume UI" -> R.drawable.ic_vol_increase
            "Mute" -> R.drawable.ic_mute
            "Active Music Overlay" -> R.drawable.ic_music_ui
            "Lock" -> R.drawable.ic_lock
            "Hide Handler" -> R.drawable.ic_visibility_hide
            "Open App" -> R.drawable.ic_app_open
            else -> R.drawable.ic_nothing
        }
    }

    private fun getSwipeUpIcon(action: String): Int {
        return when (action) {
            "None" -> R.drawable.ic_nothing
            "Increase volume" -> R.drawable.ic_vol_plus
            "Increase volume and show UI" -> R.drawable.ic_vol_increase
            else -> R.drawable.ic_nothing
        }
    }

    private fun getSwipeDownIcon(action: String): Int {
        return when (action) {
            "None" -> R.drawable.ic_nothing
            "Decrease volume" -> R.drawable.ic_vol_minus
            "Decrease volume and show UI" -> R.drawable.ic_vol_decrease
            else -> R.drawable.ic_nothing
        }
    }

    fun handleMenuOption(option: String, context: Context) {
        when (option) {
            "Premium" -> purchasePro(context as Activity)
            "Share" -> shareApp(context)
            "Feedback" -> viewModelScope.launch {
                _effect.send(MainEffect.NavigateToFeedback)
            }
            "Other apps" -> openAppStore(context, Constants.PUBLISHER_URL) {
                viewModelScope.launch {
                    _effect.send(MainEffect.ShowToast("Cannot open Play Store"))
                }
            }
            "Rate us" -> openAppStore(context, Constants.APP_STORE_ID) {
                viewModelScope.launch {
                    _effect.send(MainEffect.ShowToast("Cannot open Play Store"))
                }
            }
            "About" -> viewModelScope.launch {
                _effect.send(MainEffect.NavigateToAbout)
            }
        }
    }

    /**
     * Initialize In-App Purchase connector
     * Sets up purchase listeners and handles purchase/restore events
     */
    fun initializeIAP(context: Context) {
        if (iapConnector != null) return // Already initialized

        iapConnector = IapConnector(
            context = context,
            nonConsumableKeys = listOf(BuildConfig.PRODUCT_LIFETIME),
            key = BuildConfig.BASE64_PUBLIC_KEY,
            enableLogging = BuildConfig.DEBUG
        )

        iapConnector?.addPurchaseListener(object : PurchaseServiceListener {
            override fun onPricesUpdated(iapKeyPrices: Map<String, DataWrappers.ProductDetails>) {
                val product = iapKeyPrices[BuildConfig.PRODUCT_LIFETIME]
                productDetails = product?.offers?.firstOrNull()?.pricingPhases?.first()

                viewModelScope.launch {
                    productDetails?.let { details ->
                        _effect.send(MainEffect.ProductDetailsLoaded(details))
                    }
                }
            }

            override fun onProductPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                if (purchaseInfo.sku == BuildConfig.PRODUCT_LIFETIME && purchaseInfo.purchaseState == 1) {
                    preference.setProFeatureActivated(true)
                    _state.value = _state.value.copy(isProActivated = true)

                    // Destroy ads after successful purchase
                    adsManager?.destroyAds()
                    adsManager = null

                    viewModelScope.launch {
                        _effect.send(MainEffect.ShowToast("Purchase successful! Premium activated."))
                    }
                }
            }

            override fun onProductRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                if (purchaseInfo.sku == BuildConfig.PRODUCT_LIFETIME && purchaseInfo.purchaseState == 1) {
                    preference.setProFeatureActivated(true)
                    _state.value = _state.value.copy(isProActivated = true)

                    // Destroy ads after restore
                    adsManager?.destroyAds()
                    adsManager = null

                    viewModelScope.launch {
                        _effect.send(MainEffect.ShowToast("Purchase restored! Premium activated."))
                    }
                }
            }

            override fun onPurchaseFailed(purchaseInfo: DataWrappers.PurchaseInfo?, billingResponseCode: Int?) {
                if (!_state.value.isProActivated) {
                    val message = when (billingResponseCode) {
                        1 -> "Purchase canceled"
                        2 -> "Service unavailable. Please try again."
                        3 -> "Billing service unavailable"
                        4 -> "Item unavailable"
                        5 -> "Developer error"
                        6 -> "Error during purchase"
                        7 -> "Item already owned"
                        8 -> "Item not owned"
                        else -> "Purchase failed. Please try again."
                    }
                    viewModelScope.launch {
                        _effect.send(MainEffect.ShowToast(message))
                    }
                }
            }
        })
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
                _effect.send(MainEffect.ShowToast("You already have premium!"))
            }
            return
        }

        iapConnector?.purchase(activity, BuildConfig.PRODUCT_LIFETIME)
    }

    fun onBackPressed(finishActivity: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressedTime > 2000) {
            viewModelScope.launch {
                _effect.send(MainEffect.ShowToast("Press back again to exit"))
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
                    _state.value = _state.value.copy(isRunning = false)
                    overlayService?.shouldFinish = true
                }
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

    override fun onCleared() {
        super.onCleared()
        adsManager?.destroyAds()
        adsManager = null
        iapConnector = null
        messageObserver = null
    }
}