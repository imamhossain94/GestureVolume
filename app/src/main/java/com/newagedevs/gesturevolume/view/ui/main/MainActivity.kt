package com.newagedevs.gesturevolume.view.ui.main

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.maxkeppeler.sheets.color.ColorSheet
import com.maxkeppeler.sheets.option.DisplayMode
import com.maxkeppeler.sheets.option.Option
import com.maxkeppeler.sheets.option.OptionSheet
import com.newagedevs.gesturevolume.BuildConfig
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityMainBinding
import com.newagedevs.gesturevolume.extensions.px
import com.newagedevs.gesturevolume.persistence.SharedPrefRepository
import com.newagedevs.gesturevolume.service.LockScreenUtil
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.service.OverlayServiceInterface
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.utils.SharedData
import com.newagedevs.gesturevolume.view.ui.HandlerView
import com.skydoves.bindables.BindingActivity
import org.koin.android.ext.android.inject
import org.koin.android.viewmodel.ext.android.viewModel
import java.util.concurrent.TimeUnit
import kotlin.math.pow


class MainActivity : BindingActivity<ActivityMainBinding>(R.layout.activity_main), HandlerView.HandlerPositionChangeListener {

    private val viewModel: MainViewModel by viewModel()
    private val preference: SharedPrefRepository by inject()
    private lateinit var handlerView: HandlerView
    private var retryAttempt = 0.0

    private var overlayServiceInterface: OverlayServiceInterface? = null

    private var isBound = false
    private var isRunning = false

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            overlayServiceInterface = (iBinder as OverlayService.LocalBinder).instance()
            isBound = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            overlayServiceInterface = null
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (isRunning) {
            val serviceIntent = Intent(this, OverlayService::class.java)
            if(!isServiceRunning(OverlayService::class.java)) {
                startForegroundService(serviceIntent)
            }
            bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
            overlayServiceInterface?.hide()
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding {
            vm = viewModel
        }

        isRunning = preference.isRunning()
        handlerView = HandlerView(this)
        handlerView.setHandlerPositionChangeListener(this)

        binding.rootLayout.addView(handlerView)
        binding.toggleService.isChecked = isRunning
        binding.toggleService.text = if (isRunning) "Service On" else "Service Off"
        binding.toggleService.setOnCheckedChangeListener  { view, isChecked ->
            preference.setRunning(isChecked)
            isRunning = isChecked

            if (isChecked && !isBound) {
                val serviceIntent = Intent(this, OverlayService::class.java)
                if(!isServiceRunning(OverlayService::class.java)){
                    startForegroundService(serviceIntent)
                }
                bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
                overlayServiceInterface?.show()
                isBound = true
                view.text = "Service On"
            } else if (!isChecked && isBound) {
                unbindService(connection)
                isBound = false
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
        viewModel.color?.let { handlerView.setViewColor(it, (it shr 24) and 0xFF) }
        handlerView.setViewDimension(Constants.handlerWidthValue(viewModel.width), Constants.handlerSizeValue(viewModel.size))
        handlerView.setHandlerPositionChangeListener(this)
        handlerView.setVibrateOnClick(false)

        createBannerAd()
        createInterstitialAd()
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
        finish()
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


    // ----------------------------------------------------------------
    private fun createBannerAd() {
        val bannerId = BuildConfig.banner_AdUnit
        val adView = MaxAdView(bannerId, this).apply {
            setListener(bannerAdsListener)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.banner_height)
            )
        }
        binding.adsContainer.addView(adView)
        adView.loadAd()
    }

    private val bannerAdsListener = object : MaxAdViewAdListener {
        override fun onAdLoaded(p0: MaxAd) {
            binding.adsContainer.visibility = View.VISIBLE
        }

        override fun onAdDisplayed(p0: MaxAd) {
            binding.adsContainer.visibility = View.VISIBLE
        }

        override fun onAdHidden(p0: MaxAd) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdClicked(p0: MaxAd) { }

        override fun onAdLoadFailed(p0: String, p1: MaxError) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdDisplayFailed(p0: MaxAd, p1: MaxError) {
            binding.adsContainer.visibility = View.GONE
        }

        override fun onAdExpanded(p0: MaxAd) { }

        override fun onAdCollapsed(p0: MaxAd) { }
    }

    private fun createInterstitialAd() {
        val interstitialId = BuildConfig.interstitial_AdUnit
        viewModel.interstitialAd = MaxInterstitialAd(interstitialId, this)
        viewModel.interstitialAd?.setListener(interstitialAdsListener)
        viewModel.interstitialAd?.loadAd()
    }

    private val interstitialAdsListener = object : MaxAdListener {
        override fun onAdLoaded(maxAd: MaxAd) {
            retryAttempt = 0.0
        }

        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
            retryAttempt++
            val delayMillis = TimeUnit.SECONDS.toMillis( 2.0.pow(6.0.coerceAtMost(retryAttempt)).toLong() )
            Handler(Looper.getMainLooper()).postDelayed( { viewModel.interstitialAd?.loadAd()  }, delayMillis )
        }

        override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
            SharedData.onAdsComplete()
        }

        override fun onAdDisplayed(maxAd: MaxAd) {
            //SharedData.onAdsComplete()
        }

        override fun onAdClicked(maxAd: MaxAd) {
            SharedData.onAdsComplete()
        }

        override fun onAdHidden(maxAd: MaxAd) {
            SharedData.onAdsComplete()
        }
    }
    // ----------------------------------------------------------------


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

//    override fun onPause() {
//        super.onPause()
//        if (isBound) {
//            unbindService(connection)
//            isBound = false
//        }
//    }

    override fun onResume() {
        super.onResume()
        isRunning = preference.isRunning()
        binding.toggleService.isChecked = isRunning
        binding.toggleService.text = if (isRunning) "Service On" else "Service Off"
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        if (isBound) {
//            unbindService(connection)
//            isBound = false
//        }
//    }

    override fun onVertical(rawY: Float) {
        preference.setHandlerTranslationY(rawY)
    }

    override fun onVertical(rawY: Int) { }

    companion object {
        const val OVERLAY_REQUEST_CODE = 1
        const val DEVICE_ADMIN_REQUEST_CODE = 3
    }
}