package com.newagedevs.gesturevolume.view.ui.main

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.res.ResourcesCompat
import com.applovin.mediation.MaxError
import com.maxkeppeler.sheets.color.ColorSheet
import com.maxkeppeler.sheets.core.SheetStyle
import com.maxkeppeler.sheets.option.DisplayMode
import com.maxkeppeler.sheets.option.Option
import com.maxkeppeler.sheets.option.OptionSheet
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityMainBinding
import com.newagedevs.gesturevolume.extensions.px
import com.newagedevs.gesturevolume.extensions.toast
import com.newagedevs.gesturevolume.helper.ApplovinAdsCallback
import com.newagedevs.gesturevolume.helper.ApplovinAdsManager
import com.newagedevs.gesturevolume.persistence.SharedPrefRepository
import com.newagedevs.gesturevolume.service.LockScreenUtil
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.service.OverlayServiceInterface
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.view.CustomSheet
import com.newagedevs.gesturevolume.view.HandlerView
import com.skydoves.bindables.BindingActivity
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel


class MainActivity : BindingActivity<ActivityMainBinding>(R.layout.activity_main), HandlerView.HandlerPositionChangeListener {

    private val viewModel: MainViewModel by viewModel()
    private val preference: SharedPrefRepository by inject()

    private lateinit var adsManager: ApplovinAdsManager
    private lateinit var handlerView: HandlerView

    var overlayService: OverlayServiceInterface? = null
    private var serviceConnection: ServiceConnection? = null
    private var isBound = false
    private var isRunning = false

    var lastBackPressedTime: Long = 0

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
        super.onCreate(savedInstanceState)

        binding {
            vm = viewModel
        }

        val service = Intent(this, OverlayService::class.java)

        adsManager = ApplovinAdsManager(this, viewModel, binding, object : ApplovinAdsCallback {
            override fun onInterstitialAdLoaded() {

                startService(service)
                serviceConnection = MyServiceConnection()
                serviceConnection?.let {
                    bindService(service, it, BIND_AUTO_CREATE)
                    binding.toggleService.text = "Service On"
                }

            }

            override fun onInterstitialAdFailed(error: MaxError) {

                startService(service)
                serviceConnection = MyServiceConnection()
                serviceConnection?.let {
                    bindService(service, it, BIND_AUTO_CREATE)
                    binding.toggleService.text = "Service On"
                }

            }
        })

        isRunning = preference.isRunning()
        handlerView = HandlerView(this)
        handlerView.setHandlerPositionChangeListener(this)

        if(isRunning) {
            if(!isServiceRunning(OverlayService::class.java)) {
                startService(service)
            }
            serviceConnection = MyServiceConnection()
            serviceConnection?.let {
                bindService(service, it, BIND_AUTO_CREATE)
            }
        }

        OverlayService.communicator.observe(this@MainActivity) {
            it?.let {
                // Do what you need to do here
                when (it) {
                    "show" -> {
                        //finish()
                    }
                    "stop" -> {
                        preference.setRunning(false)
                        isRunning = false
                        binding.toggleService.isChecked = isRunning
                        binding.toggleService.text = if (isRunning) "Service On" else "Service Off"
                    }
                }
            }
        }

        binding.rootLayout.addView(handlerView)
        binding.toggleService.isChecked = isRunning
        binding.toggleService.text = if (isRunning) "Service On" else "Service Off"
        binding.toggleService.setOnCheckedChangeListener  { view, isChecked ->
            preference.setRunning(isChecked)
            isRunning = isChecked

            if (isChecked) {
                adsManager.createAndShowInterstitialAd()
            } else {
                if (isBound) {
                    serviceConnection?.let { unbindService(it) }
                    stopService(service)
                    isBound = false
                    view.text = "Service Off"
                }
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

        adsManager.createBannerAd()

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
    }

    fun gravityPicker(view: View) {
        OptionSheet().show(view.context) {
            title("Select your handedness or the gravity of the handler")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.gravityDrawables[0], Constants.gravityTitles[0]),
                Option(Constants.gravityDrawables[1], Constants.gravityTitles[1]),
            )
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
        OptionSheet().show(view.context) {
            title("Select the height or size of the handler")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.sizeDrawables[0], Constants.sizeTitles[0]),
                Option(Constants.sizeDrawables[1], Constants.sizeTitles[1]),
                Option(Constants.sizeDrawables[2], Constants.sizeTitles[2]),
            )
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
        OptionSheet().show(view.context) {
            title("Select the width or thickness of the handler")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.widthDrawables[0], Constants.widthTitles[0]),
                Option(Constants.widthDrawables[1], Constants.widthTitles[1]),
                Option(Constants.widthDrawables[2], Constants.widthTitles[2]),
            )
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

        OptionSheet().show(view.context) {
            title("What should happen when you tap on the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.tapActionDrawables[0], Constants.tapActionTitles[0]),
                Option(Constants.tapActionDrawables[1], Constants.tapActionTitles[1]),
                Option(Constants.tapActionDrawables[2], Constants.tapActionTitles[2]),
                Option(Constants.tapActionDrawables[3], Constants.tapActionTitles[3]),
                Option(Constants.tapActionDrawables[4], Constants.tapActionTitles[4]),
                Option(Constants.tapActionDrawables[5], Constants.tapActionTitles[5]),
                Option(Constants.tapActionDrawables[6], Constants.tapActionTitles[6])
            )
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
            title("What should happen when you double tap on the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.tapActionDrawables[0], Constants.tapActionTitles[0]),
                Option(Constants.tapActionDrawables[1], Constants.tapActionTitles[1]),
                Option(Constants.tapActionDrawables[2], Constants.tapActionTitles[2]),
                Option(Constants.tapActionDrawables[3], Constants.tapActionTitles[3]),
                Option(Constants.tapActionDrawables[4], Constants.tapActionTitles[4]),
                Option(Constants.tapActionDrawables[5], Constants.tapActionTitles[5]),
                Option(Constants.tapActionDrawables[6], Constants.tapActionTitles[6])
            )
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

        OptionSheet().show(view.context) {
            title("What should happen when you long on the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.tapActionDrawables[0], Constants.tapActionTitles[0]),
                Option(Constants.tapActionDrawables[1], Constants.tapActionTitles[1]),
                Option(Constants.tapActionDrawables[2], Constants.tapActionTitles[2]),
                Option(Constants.tapActionDrawables[3], Constants.tapActionTitles[3]),
                Option(Constants.tapActionDrawables[4], Constants.tapActionTitles[4]),
                Option(Constants.tapActionDrawables[5], Constants.tapActionTitles[5]),
                Option(Constants.tapActionDrawables[6], Constants.tapActionTitles[6])
            )
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


    fun swipeUpActionPicker(view: View) {
        OptionSheet().show(view.context) {
            title("What should happen when you swipe the upper half of the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.swipeUpDrawables[0], Constants.swipeUpTitles[0]),
                Option(Constants.swipeUpDrawables[1], Constants.swipeUpTitles[1]),
                Option(Constants.swipeUpDrawables[2], Constants.swipeUpTitles[2]),
            )
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
        OptionSheet().show(view.context) {
            title("What should happen when you swipe the bottom half of the handler?")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(Constants.swipeDownDrawables[0], Constants.swipeDownTitles[0]),
                Option(Constants.swipeDownDrawables[1], Constants.swipeDownTitles[1]),
                Option(Constants.swipeDownDrawables[2], Constants.swipeDownTitles[2]),
            )
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

//        CustomSheet().show(this@MainActivity) {
//            style(SheetStyle.BOTTOM_SHEET)
//            title("Upgrade to Pro")
//            content("Are you sure you want to exit? Hope you will come back again.")
//            onPositive("Exit") {
//                finish()
//            }
//        }

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


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                OverlayService.start(this)
            }
        } else if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            if (LockScreenUtil(this).active()) {
                viewModel.clickActionIcon = R.drawable.ic_lock
                viewModel.clickAction = "Lock"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isRunning = preference.isRunning()
        binding.toggleService.isChecked = isRunning
        binding.toggleService.text = if (isRunning) "Service On" else "Service Off"
        overlayService?.hide()
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