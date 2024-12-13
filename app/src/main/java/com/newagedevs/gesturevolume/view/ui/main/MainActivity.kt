package com.newagedevs.gesturevolume.view.ui.main

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limurse.iap.DataWrappers
import com.limurse.iap.IapConnector
import com.limurse.iap.PurchaseServiceListener
import com.maxkeppeler.sheets.color.ColorSheet
import com.maxkeppeler.sheets.core.SheetStyle
import com.maxkeppeler.sheets.option.DisplayMode
import com.maxkeppeler.sheets.option.Option
import com.maxkeppeler.sheets.option.OptionSheet
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityMainBinding
import com.newagedevs.gesturevolume.extensions.getApplicationVersion
import com.newagedevs.gesturevolume.extensions.openAppStore
import com.newagedevs.gesturevolume.extensions.openMailApp
import com.newagedevs.gesturevolume.extensions.openWebPage
import com.newagedevs.gesturevolume.extensions.px
import com.newagedevs.gesturevolume.extensions.shareTheApp
import com.newagedevs.gesturevolume.extensions.toast
import com.newagedevs.gesturevolume.helper.ApplovinAdsManager
import com.newagedevs.gesturevolume.helper.NotificationUtil
import com.newagedevs.gesturevolume.livedata.LiveDataManager
import com.newagedevs.gesturevolume.persistence.SharedPrefRepository
import com.newagedevs.gesturevolume.service.LockScreenUtil
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.service.OverlayServiceInterface
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.view.CustomSheet
import com.newagedevs.gesturevolume.view.HandlerView
import com.newagedevs.gesturevolume.view.ui.about.AboutActivity
import com.newagedevs.gesturevolume.view.ui.feedback.FeedbackActivity
import com.skydoves.bindables.BindingActivity
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel


class MainActivity : BindingActivity<ActivityMainBinding>(R.layout.activity_main), HandlerView.HandlerPositionChangeListener {

    private var messageObserver:Observer<String>? = null

    private val viewModel: MainViewModel by viewModel()
    private val preference: SharedPrefRepository by inject()

    private var adsManager: ApplovinAdsManager? = null
    private var iapConnector: IapConnector? = null
    private var productDetails: DataWrappers.ProductDetails? = null

    private lateinit var handlerView: HandlerView

    var overlayService: OverlayServiceInterface? = null
    private var serviceConnection: ServiceConnection? = null
    private var isBound = false
    private var isRunning = false

    var lastBackPressedTime: Long = 0

    private lateinit var notificationUtil: NotificationUtil
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    inner class MyServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            overlayService = (service as OverlayService.LocalBinder).instance()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            overlayService = null
            isBound = false
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        binding {
            vm = viewModel
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setCommunicatorObserver()

        val service = Intent(this, OverlayService::class.java)

        notificationUtil = NotificationUtil(this)

        isRunning = preference.isRunning()
        handlerView = HandlerView(this)
        handlerView.setHandlerPositionChangeListener(this)

        binding.rootLayout.addView(handlerView)
        binding.toggleService.isChecked = isRunning
        binding.toggleService.text = if (isRunning) "Service On" else "Service Off"

        if(isRunning) {
            if(Settings.canDrawOverlays(this@MainActivity)) {
                if(!isServiceRunning(OverlayService::class.java)) {
                    startService(service)
                }
                serviceConnection = MyServiceConnection()
                serviceConnection?.let {
                    bindService(service, it, BIND_AUTO_CREATE)
                }
            }
        }

        binding.toggleService.setOnCheckedChangeListener  { view, isChecked ->
            preference.setRunning(isChecked)
            isRunning = isChecked

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if(isChecked && !notificationUtil.isPermissionGranted()){
                    notificationUtil.requestPermission(notificationPermissionLauncher)
                }
            }

            if(Settings.canDrawOverlays(this)) {
                if (isChecked) {
                    if(!preference.isProFeatureActivated()) {
                        adsManager?.showInterstitialAd()
                    }
                    startService(service)
                    serviceConnection = MyServiceConnection()
                    serviceConnection?.let {
                        bindService(service, it, BIND_AUTO_CREATE)
                        binding.toggleService.text = "Service On"
                    }
                } else {
                    if (isBound) {
                        serviceConnection?.let { unbindService(it) }
                        stopService(service)
                        isBound = false
                        view.text = "Service Off"
                    }
                }
            } else {
                requestOverlayPermission()
                binding.toggleService.isChecked = false
                view.text = "Service Off"
            }

        }

        // Set click listener for HandlerView
        handlerView.setOnClickListener {
            Toast.makeText(this, "Handler clicked", Toast.LENGTH_SHORT).show()
        }

        handlerView.setHandlerPositionIsLocked(false)
        handlerView.setTranslationYPosition(viewModel.translationY)
        handlerView.setViewGravity(if (viewModel.gravity == "Left") Gravity.START else Gravity.END)
        handlerView.setViewDimension(Constants.handlerWidthValue(viewModel.width), Constants.handlerSizeValue(viewModel.size))
        handlerView.setHandlerPositionChangeListener(this)
        handlerView.setVibrateOnClick(false)

        viewModel.color?.let {
            val alpha = (it shr 24) and 0xFF
            handlerView.setViewColor(it, alpha)
        }

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressedTime > 2000) {
                    toast("Press back again to exit")
                    lastBackPressedTime = currentTime
                } else {
                    isEnabled = false
                    finish()
                }
            }
        })

        overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            if (!Settings.canDrawOverlays(this)) {
                preference.setRunning(false)
                isRunning = false
                binding.toggleService.isChecked = false
                Toast.makeText(this, "Overlay permission not granted", Toast.LENGTH_SHORT).show()
            }
        }

        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            if(!notificationUtil.isPermissionGranted()) {
                Toast.makeText(this, "Post notification permission not granted", Toast.LENGTH_SHORT).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if(!notificationUtil.isPermissionGranted()){
                notificationUtil.requestPermission(notificationPermissionLauncher)
            }
        }

        updateProUI()

        // Pro features
        iapConnector = IapConnector(
            context = this,
            nonConsumableKeys = listOf(BuildConfig.PRODUCT_ID),
            key = BuildConfig.BASE_64_KEY,
            enableLogging = true
        )

        iapConnector?.addPurchaseListener(object : PurchaseServiceListener {
            override fun onPricesUpdated(iapKeyPrices: Map<String, List<DataWrappers.ProductDetails>>) {
                productDetails = iapKeyPrices[BuildConfig.PRODUCT_ID]?.first()
            }

            override fun onProductPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                // will be triggered whenever purchase succeeded
                toast("Succeeded purchase")
                preference.setProFeatureActivated(true)
                updateProUI()
            }

            override fun onProductRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                // will be triggered fetching owned products using IapConnector
                //toast("Product restored")
                if(purchaseInfo.sku == "lifetime" && purchaseInfo.purchaseState == 1){
                    preference.setProFeatureActivated(true)
                }
                updateProUI()
            }

            override fun onPurchaseFailed(purchaseInfo: DataWrappers.PurchaseInfo?, billingResponseCode: Int?) {
                if(!preference.isProFeatureActivated()) {
                    toast("Failed to purchase product")
                }
            }
        })
    }

    private fun updateProUI() {

        if(!preference.isProFeatureActivated()) {
            adsManager = ApplovinAdsManager(this)
            adsManager?.createBannerAd(binding.adsContainer)
            adsManager?.createNativeAds(binding.nativeAdsContainer)

            binding.bannerAdsView.setAdsData(Constants.inHouseAds) { appLink ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(appLink)
                }
                startActivity(intent)
            }
        } else {
            binding.adsContainer.visibility = View.GONE
            binding.nativeAdsContainer.visibility = View.GONE
            binding.bannerAdsView.visibility = View.GONE
            binding.adsContainer.removeAllViews()
            binding.nativeAdsContainer.removeAllViews()
        }

        if(!preference.isProFeatureActivated()) {
            val tapSettingView = binding.layoutTapSettings.root

//            val doubleTapProTag: View = tapSettingView.findViewById(R.id.double_tap_pro_tag)
            val longTapProTag: View = tapSettingView.findViewById(R.id.long_tap_pro_tag)

//            doubleTapProTag.visibility = View.VISIBLE
            longTapProTag.visibility = View.VISIBLE
        }
    }

    fun openMenu(view: View) {


        val options =  mutableListOf(
            Option(R.drawable.ic_share, "Share", "") { toast("Share") },
            Option(R.drawable.ic_feedback, "Feedback", "") { toast("Feedback") },
            Option(R.drawable.ic_privacy, "Privacy policy", "") { toast("Privacy policy") },
            Option(R.drawable.ic_playstore, "Other apps", "") { toast("Other apps") },
            Option(R.drawable.ic_star, "Rate us", "") { toast("Rate us") },
            Option(R.drawable.ic_github, "Source code", "") { toast("Source code") },
//            Option(R.drawable.ic_svg, "Icons by", "") { toast("Icons by") },
            Option(R.drawable.ic_nothing, "About", "") { toast("About") },
            Option(R.drawable.ic_power, "Exit", "") { toast("Exit") }
        )

         if(!preference.isProFeatureActivated()) {
             options.add(0, Option(R.drawable.ic_crown_2, "Premium"))
         }

        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("Menu")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(options)
            onPositive { index: Int, _: Option ->
                when (if (!preference.isProFeatureActivated()) index else index + 1) {
                    0 -> openProDialogue()
                    1 -> shareTheApp(requireActivity())
                    2 -> {
                        startActivity(Intent(this@MainActivity, FeedbackActivity::class.java))
                    }
                    3 -> openWebPage(requireActivity(), Constants.privacyPolicyUrl) { viewModel.toast = it }
                    4 -> openAppStore(requireActivity(), Constants.publisherName) { viewModel.toast = it }
                    5 -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Are You Enjoying?")
                            .setMessage("If you like ${getString(R.string.app_name)}, please give it a 5 starts rating in Google Play, Thank You")
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .setPositiveButton("Rate") { dialog, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.appStoreId)))
                                dialog.dismiss()
                            }.show()
                    }
                    6 -> openWebPage(requireActivity(), Constants.sourceCodeUrl) { viewModel.toast = it }
//                    7 -> viewModel.toast = "Icons by svgrepo.com"
                    7 -> {
                        startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                    }
                    8 -> requireActivity().finish()
                }
            }
        }
    }

    fun gravityPicker(view: View) {

        val options = mutableListOf(
            Option(Constants.gravityDrawables[0], Constants.gravityTitles[0], "") { toast(Constants.gravityTitles[0]) },
            Option(Constants.gravityDrawables[1], Constants.gravityTitles[1], "") { toast(Constants.gravityTitles[1]) }
        )

        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("Select your handedness or the gravity of the handler")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(options)
            onPositive { index: Int, _: Option ->
                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, Constants.gravityDrawables[index], null)?.apply {
                    setBounds(0, 0, 24.px, 24.px)
                }

                textView.text = Constants.gravityTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                viewModel.gravityIcon = Constants.gravityDrawables[index]
                viewModel.gravity = Constants.gravityTitles[index]

                preference.setHandlerPosition(Constants.gravityTitles[index])

                when (Constants.gravityTitles[index]) {
                    Constants.gravityTitles.first() -> {
                        handlerView.setViewGravity(Gravity.START)
                    }
                    Constants.gravityTitles.last() -> {
                        handlerView.setViewGravity(Gravity.END)
                    }
                }
            }
        }
    }


    fun sizePicker(view: View) {
        val options = mutableListOf(
            Option(Constants.sizeDrawables[0], Constants.sizeTitles[0], "") { toast(Constants.sizeTitles[0]) },
            Option(Constants.sizeDrawables[1], Constants.sizeTitles[1], "") { toast(Constants.sizeTitles[1]) },
            Option(Constants.sizeDrawables[2], Constants.sizeTitles[2], "") { toast(Constants.sizeTitles[2]) }
        )
        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("Select the height or size of the handler")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(options)
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, Constants.sizeDrawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = Constants.sizeTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                viewModel.sizeIcon = Constants.sizeDrawables[index]
                viewModel.size = Constants.sizeTitles[index]

                preference.setHandlerSize(Constants.sizeTitles[index])

                handlerView.setViewSize(Constants.handlerSizeValue(Constants.sizeTitles[index]))

            }
        }
    }


    fun widthPicker(view: View) {
        val options = mutableListOf(
            Option(Constants.widthDrawables[0], Constants.widthTitles[0], "") { toast(Constants.widthTitles[0]) },
            Option(Constants.widthDrawables[1], Constants.widthTitles[1], "") { toast(Constants.widthTitles[1]) },
            Option(Constants.widthDrawables[2], Constants.widthTitles[2], "") { toast(Constants.widthTitles[2]) }
        )
        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("Select the width or thickness of the handler")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(options)
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, Constants.widthDrawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = Constants.widthTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                viewModel.widthIcon = Constants.widthDrawables[index]
                viewModel.width = Constants.widthTitles[index]

                preference.setHandlerWidth(Constants.widthTitles[index])

                handlerView.setViewWidth(Constants.handlerWidthValue(Constants.widthTitles[index]))

            }
        }
    }

    fun colorPicker(view: View) {
        ColorSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("Select the color and transparency of the handler")
            onPositive {
                viewModel.color = it
                preference.setHandlerColor(it)

                handlerView.setViewColor(it)
            }
        }
    }


    fun clickActionPicker(view: View) {
        val lockScreenUtil =LockScreenUtil(view.context)
        val options = Constants.tapActionLists(view.context)
//        if (!preference.isProFeatureActivated()) {
//            val disabledIndices = listOf(3, 4, 5, 6)
//            for (index in disabledIndices) {
//                options[index] = Option(Constants.tapActionDrawables[index], Constants.tapActionTitles[index], "PRO") {
//                    toast(Constants.tapActionTitles[index])
//                }.disable()
//            }
//        }

        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("What should happen when you tap on the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(options)
            onPositive { index: Int, _: Option ->

                if(index == 4 && !lockScreenUtil.active()) {
                    lockScreenUtil.enableAdmin()
                    return@onPositive
                }else{
                    val textView = view as TextView

                    val image = ResourcesCompat.getDrawable(resources, Constants.tapActionDrawables[index], null)
                    image?.setBounds(0, 0, 24.px, 24.px)

                    textView.text = Constants.tapActionTitles[index]
                    textView.setCompoundDrawables(image, null, null, null)

                    viewModel.clickActionIcon = Constants.tapActionDrawables[index]
                    viewModel.clickAction = Constants.tapActionTitles[index]
                    preference.setHandlerSingleTapAction(Constants.tapActionTitles[index])
                }
            }
        }
    }

    fun doubleClickActionPicker(view: View) {
        val lockScreenUtil =LockScreenUtil(view.context)

        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("What should happen when you double tap on the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(Constants.tapActionLists(view.context))
            onPositive { index: Int, _: Option ->

                if(index == 4 && !lockScreenUtil.active()) {
                    lockScreenUtil.enableAdmin()
                    return@onPositive
                }else{
                    val textView = view as TextView

                    val image = ResourcesCompat.getDrawable(resources, Constants.tapActionDrawables[index], null)
                    image?.setBounds(0, 0, 24.px, 24.px)

                    textView.text = Constants.tapActionTitles[index]
                    textView.setCompoundDrawables(image, null, null, null)

                    viewModel.doubleClickActionIcon = Constants.tapActionDrawables[index]
                    viewModel.doubleClickAction = Constants.tapActionTitles[index]
                    preference.setHandlerDoubleTapAction(Constants.tapActionTitles[index])
                }
            }
        }

    }


    fun longClickActionPicker(view: View) {
        val lockScreenUtil =LockScreenUtil(view.context)
        if (!preference.isProFeatureActivated()) {
            openProDialogue()
        }

        if (preference.isProFeatureActivated()) {
            OptionSheet().show(view.context) {
                style(SheetStyle.DIALOG)
                title("What should happen when you long on the handler?")
                columns(3)
                displayMode(DisplayMode.GRID_VERTICAL)
                with(Constants.tapActionLists(view.context))
                onPositive { index: Int, _: Option ->
                    if(index == 4 && !lockScreenUtil.active()) {
                        lockScreenUtil.enableAdmin()
                        return@onPositive
                    }else{
                        val textView = view as TextView

                        val image = ResourcesCompat.getDrawable(resources, Constants.tapActionDrawables[index], null)
                        image?.setBounds(0, 0, 24.px, 24.px)

                        textView.text = Constants.tapActionTitles[index]
                        textView.setCompoundDrawables(image, null, null, null)

                        viewModel.longClickActionIcon = Constants.tapActionDrawables[index]
                        viewModel.longClickAction = Constants.tapActionTitles[index]

                        preference.setHandlerLongTapAction(Constants.tapActionTitles[index])

                    }
                }
            }
        }
    }


    fun swipeUpActionPicker(view: View) {
        val options = mutableListOf(
            Option(Constants.swipeUpDrawables[0], Constants.swipeUpTitles[0], "") { toast(Constants.swipeUpTitles[0]) },
            Option(Constants.swipeUpDrawables[1], Constants.swipeUpTitles[1], "") { toast(Constants.swipeUpTitles[1]) },
            Option(Constants.swipeUpDrawables[2], Constants.swipeUpTitles[2], "") { toast(Constants.swipeUpTitles[2]) }
        )

//        if(!preference.isProFeatureActivated()) {
//            options[2] = Option(Constants.swipeUpDrawables[2], Constants.swipeUpTitles[2], "PRO") {
//                toast(Constants.swipeUpTitles[2])
//            }.disable()
//        }

        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("What should happen when you swipe the upper half of the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(options)
            onPositive { index: Int, _: Option ->
                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, Constants.swipeUpDrawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = Constants.swipeUpTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                viewModel.swipeUpActionIcon = Constants.swipeUpDrawables[index]
                viewModel.swipeUpAction = Constants.swipeUpTitles[index]

                preference.setHandlerSwipeUpAction(Constants.swipeUpTitles[index])


            }
        }
    }

    fun swipeDownActionPicker(view: View) {
        val options = mutableListOf(
            Option(Constants.swipeDownDrawables[0], Constants.swipeDownTitles[0], "") { toast(Constants.swipeDownTitles[0]) },
            Option(Constants.swipeDownDrawables[1], Constants.swipeDownTitles[1], "") { toast(Constants.swipeDownTitles[1]) },
            Option(Constants.swipeDownDrawables[2], Constants.swipeDownTitles[2], "") { toast(Constants.swipeDownTitles[2]) }
        )

//        if(!preference.isProFeatureActivated()) {
//            options[2] = Option(Constants.swipeDownDrawables[2], Constants.swipeDownTitles[2], "PRO") {
//                toast(Constants.swipeDownTitles[2])
//            }.disable()
//        }

        OptionSheet().show(view.context) {
            style(SheetStyle.DIALOG)
            title("What should happen when you swipe the bottom half of the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(options)
            onPositive { index: Int, _: Option ->
                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, Constants.swipeDownDrawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = Constants.swipeDownTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                viewModel.swipeDownActionIcon = Constants.swipeDownDrawables[index]
                viewModel.swipeDownAction = Constants.swipeDownTitles[index]

                preference.setHandlerSwipeDownAction(Constants.swipeDownTitles[index])


            }
        }
    }

    fun closeApp(view: View) {
        view.context
        finish()
    }

    private fun openProDialogue() {

        val title = "Upgrade to Pro"
        val description = "Gesture Volume has been, and always will be, free of charge and free of ads. This open-source project was created to replace broken volume keys and to extend the lifespan of existing volume keys. If you appreciate my work and would like to buy me a coffee, you can optionally unlock PRO features: access to all handler actions and all gesture actions. Your support is highly appreciated."
        val productPrice = productDetails?.price

        CustomSheet().show(this@MainActivity) {
            style(SheetStyle.DIALOG)
            title(title)
            description(description)
            productPrice?.let { price(it) }
            onPositive("Upgrade") {
                iapConnector?.purchase(this@MainActivity, BuildConfig.PRODUCT_ID)
            }
            onNegative("Cancel") {

            }
        }

    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${this.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            }
        } else if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            if (LockScreenUtil(this).active()) {
                viewModel.clickActionIcon = R.drawable.ic_lock
                viewModel.clickAction = "Lock"
            }
        }
    }

    private fun setCommunicatorObserver() {
        messageObserver = Observer {
            when (it) {
                "show" -> {
                    if(overlayService?.shouldFinish == true){
                        overlayService?.shouldFinish = false
                        finish()
                    }
                }
                "stop" -> {
                    preference.setRunning(false)
                    isRunning = false
                    binding.toggleService.isChecked = isRunning
                    binding.toggleService.text = if (isRunning) "Service On" else "Service Off"
                }
            }
            overlayService?.shouldFinish = true
        }

        messageObserver?.let {
            LiveDataManager.communicator().observe(this, it)
        }
    }

    override fun onResume() {
        super.onResume()
        isRunning = preference.isRunning()
        binding.toggleService.isChecked = isRunning
        binding.toggleService.text = if (isRunning) "Service On" else "Service Off"
        overlayService?.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        adsManager?.destroyAds()
    }

    override fun onVertical(rawY: Float) {
        preference.setHandlerTranslationY(rawY)
    }

    override fun onVertical(rawY: Int) { }

    companion object {
        const val OVERLAY_REQUEST_CODE = 1
        const val DEVICE_ADMIN_REQUEST_CODE = 3
    }
}