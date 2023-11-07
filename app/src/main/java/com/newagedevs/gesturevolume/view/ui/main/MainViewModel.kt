package com.newagedevs.gesturevolume.view.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.Bindable
import com.applovin.mediation.ads.MaxInterstitialAd
import com.maxkeppeler.sheets.color.ColorSheet
import com.maxkeppeler.sheets.option.DisplayMode
import com.maxkeppeler.sheets.option.Option
import com.maxkeppeler.sheets.option.OptionSheet
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.*
import com.newagedevs.gesturevolume.model.AppHandler
import com.newagedevs.gesturevolume.repository.MainRepository
import com.newagedevs.gesturevolume.repository.SharedPrefRepository
import com.newagedevs.gesturevolume.service.LockScreenUtil
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.utils.Constants
import com.newagedevs.gesturevolume.utils.SharedData
import com.skydoves.bindables.BindingViewModel
import com.skydoves.bindables.bindingProperty
import timber.log.Timber
import java.lang.ref.WeakReference


class MainViewModel constructor(
    private val mainRepository: MainRepository,
    private val prefRepository: SharedPrefRepository
) : BindingViewModel() {

    @get:Bindable
    var toast: String? by bindingProperty(null)

    @get:Bindable
    var gravity: String? by bindingProperty("Right")

    @get:Bindable
    var gravityLand: String? by bindingProperty("Top")

    @get:Bindable
    var gravityIcon: Int? by bindingProperty(R.drawable.ic_align_right)

    @get:Bindable
    var gravityIconLand: Int? by bindingProperty(R.drawable.ic_align_top)

    @get:Bindable
    var size: String? by bindingProperty("Medium")

    @get:Bindable
    var sizeLand: String? by bindingProperty("Medium")

    @get:Bindable
    var sizeIcon: Int? by bindingProperty(R.drawable.ic_medium)

    @get:Bindable
    var sizeIconLand: Int? by bindingProperty(R.drawable.ic_medium)

    @get:Bindable
    var width: String? by bindingProperty("Slim")

    @get:Bindable
    var widthLand: String? by bindingProperty("Slim")

    @get:Bindable
    var widthIcon: Int? by bindingProperty(R.drawable.ic_small)

    @get:Bindable
    var widthIconLand: Int? by bindingProperty(R.drawable.ic_small)

    @get:Bindable
    var color: Int? by bindingProperty(null)

    @get:Bindable
    var colorLand: Int? by bindingProperty(null)

    @get:Bindable
    var topMargin: Float? by bindingProperty(260f)

    @get:Bindable
    var leftMargin: Float? by bindingProperty(260f)

    // Gesture action
    @get:Bindable
    var clickAction: String? by bindingProperty("Open volume UI")

    @get:Bindable
    var clickActionIcon: Int? by bindingProperty(R.drawable.ic_vol_increase)

    @get:Bindable
    var doubleClickAction: String? by bindingProperty("Mute")

    @get:Bindable
    var doubleClickActionIcon: Int? by bindingProperty(R.drawable.ic_mute)

    @get:Bindable
    var longClickAction: String? by bindingProperty("Active Music Overlay")

    @get:Bindable
    var longClickActionIcon: Int? by bindingProperty(R.drawable.ic_music_ui)

    @get:Bindable
    var upperSwipe: String? by bindingProperty("Increase volume and show UI")

    @get:Bindable
    var upperSwipeIcon: Int? by bindingProperty(R.drawable.ic_vol_increase)

    @get:Bindable
    var bottomSwipe: String? by bindingProperty("Decrease volume and show UI")

    @get:Bindable
    var bottomSwipeIcon: Int? by bindingProperty(R.drawable.ic_vol_increase)

    lateinit var interstitialAd: MaxInterstitialAd

    fun toast(message: String) {
        toast = ""
        toast = message
    }

    // Handler settings
    @SuppressLint("Range")
    fun gravityPicker(view: View) {
        val drawables = listOf(R.drawable.ic_align_left, R.drawable.ic_align_right)
        val titles = listOf("Left", "Right")

        OptionSheet().show(view.context) {
            title("Select your handedness or the gravity of the handler")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                gravityIcon = drawables[index]
                gravity = titles[index]
            }
        }
    }

    @SuppressLint("Range")
    fun gravityPickerLand(view: View) {
        val drawables = listOf(R.drawable.ic_align_top, R.drawable.ic_align_bottom)
        val titles = listOf("Top", "Bottom")

        OptionSheet().show(view.context) {
            title("Select your handedness or the gravity of the handler")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                gravityIconLand = drawables[index]
                gravityLand = titles[index]
            }
        }
    }

    @SuppressLint("Range")
    fun sizePicker(view: View) {
        val drawables = listOf(R.drawable.ic_small, R.drawable.ic_medium, R.drawable.ic_large)
        val titles = listOf("Small", "Medium", "Large")

        OptionSheet().show(view.context) {
            title("Select the height or size of the handler")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                sizeIcon = drawables[index]
                size = titles[index]
            }
        }
    }

    @SuppressLint("Range")
    fun sizePickerLand(view: View) {
        val drawables = listOf(R.drawable.ic_small, R.drawable.ic_medium, R.drawable.ic_large)
        val titles = listOf("Small", "Medium", "Large")

        OptionSheet().show(view.context) {
            title("Select the height or size of the handler")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                sizeIconLand = drawables[index]
                sizeLand = titles[index]
            }
        }
    }

    @SuppressLint("Range")
    fun widthPicker(view: View) {
        val drawables = listOf(R.drawable.ic_small, R.drawable.ic_regular, R.drawable.ic_bold)
        val titles = listOf("Slim", "Regular", "Bold")

        OptionSheet().show(view.context) {
            title("Select the width or thickness of the handler")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                widthIcon = drawables[index]
                width = titles[index]
            }
        }
    }

    @SuppressLint("Range")
    fun widthPickerLand(view: View) {
        val drawables = listOf(R.drawable.ic_small, R.drawable.ic_regular, R.drawable.ic_bold)
        val titles = listOf("Slim", "Regular", "Bold")

        OptionSheet().show(view.context) {
            title("Select the width or thickness of the handler")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                widthIconLand = drawables[index]
                widthLand = titles[index]
            }
        }
    }

    fun colorPicker(view: View) {
        ColorSheet().show(view.context) {
            title("Select the color and transparency of the handler")
            onPositive {
                color = it
            }
        }
    }

    fun colorPickerLand(view: View) {
        ColorSheet().show(view.context) {
            title("Select the color and transparency of the handler")
            onPositive {
                colorLand = it
            }
        }
    }

    @SuppressLint("Range")
    fun clickActionPicker(view: View) {
        val lockScreenUtil =LockScreenUtil(view.context)
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_increase, R.drawable.ic_mute, R.drawable.ic_music_ui, R.drawable.ic_lock, R.drawable.ic_visibility_hide, R.drawable.ic_app_open)
        val titles = listOf("None", "Open volume UI", "Mute", "Active Music Overlay", "Lock", "Hide Handler", "Open App")

        OptionSheet().show(view.context) {
            title("What should happen when you tap on the handler?")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
                Option(drawables[3], titles[3]),
                Option(drawables[4], titles[4]),
                Option(drawables[5], titles[5]),
                Option(drawables[6], titles[6])
            )
            onPositive { index: Int, _: Option ->

                if(index == 4 && !lockScreenUtil.active()) {
                    lockScreenUtil.enableAdmin()
                    return@onPositive
                }else{
                    val textView = view as TextView

                    val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                    image?.setBounds(0, 0, 24.px, 24.px)

                    textView.text = titles[index]
                    textView.setCompoundDrawables(image, null, null, null)

                    clickActionIcon = drawables[index]
                    clickAction = titles[index]
                }

            }
        }
    }

    @SuppressLint("Range")
    fun doubleClickActionPicker(view: View) {
        val lockScreenUtil =LockScreenUtil(view.context)
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_increase, R.drawable.ic_mute, R.drawable.ic_music_ui, R.drawable.ic_lock, R.drawable.ic_visibility_hide, R.drawable.ic_app_open)
        val titles = listOf("None", "Open volume UI", "Mute", "Active Music Overlay", "Lock", "Hide Handler", "Open App")

        OptionSheet().show(view.context) {
            title("What should happen when you double tap on the handler?")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
                Option(drawables[3], titles[3]),
                Option(drawables[4], titles[4]),
                Option(drawables[5], titles[5]),
                Option(drawables[6], titles[6])
            )
            onPositive { index: Int, _: Option ->

                if(index == 4 && !lockScreenUtil.active()) {
                    lockScreenUtil.enableAdmin()
                    return@onPositive
                }else{
                    val textView = view as TextView

                    val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                    image?.setBounds(0, 0, 24.px, 24.px)

                    textView.text = titles[index]
                    textView.setCompoundDrawables(image, null, null, null)

                    doubleClickActionIcon = drawables[index]
                    doubleClickAction = titles[index]
                }

            }
        }
    }

    @SuppressLint("Range")
    fun longClickActionPicker(view: View) {
        val lockScreenUtil =LockScreenUtil(view.context)
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_increase, R.drawable.ic_mute, R.drawable.ic_music_ui, R.drawable.ic_lock, R.drawable.ic_visibility_hide, R.drawable.ic_app_open)
        val titles = listOf("None", "Open volume UI", "Mute", "Active Music Overlay", "Lock", "Hide Handler", "Open App")

        OptionSheet().show(view.context) {
            title("What should happen when you long on the handler?")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
                Option(drawables[3], titles[3]),
                Option(drawables[4], titles[4]),
                Option(drawables[5], titles[5]),
                Option(drawables[6], titles[6])
            )
            onPositive { index: Int, _: Option ->
                if(index == 4 && !lockScreenUtil.active()) {
                    lockScreenUtil.enableAdmin()
                    return@onPositive
                }else{
                    val textView = view as TextView

                    val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                    image?.setBounds(0, 0, 24.px, 24.px)

                    textView.text = titles[index]
                    textView.setCompoundDrawables(image, null, null, null)

                    longClickActionIcon = drawables[index]
                    longClickAction = titles[index]
                }
            }
        }
    }

    @SuppressLint("Range")
    fun upperSwipeActionPicker(view: View) {
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_increase, R.drawable.ic_vol_plus)
        val titles = listOf("None", "Increase volume and show UI", "Increase volume")

        OptionSheet().show(view.context) {
            title("What should happen when you swipe the upper half of the handler?")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
            )
            onPositive { index: Int, _: Option ->
                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                upperSwipeIcon = drawables[index]
                upperSwipe = titles[index]
            }
        }
    }

    @SuppressLint("Range")
    fun bottomSwipeActionPicker(view: View) {
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_decrease, R.drawable.ic_vol_minus)
        val titles = listOf("None", "Decrease volume and show UI", "Decrease volume")

        OptionSheet().show(view.context) {
            title("What should happen when you swipe the bottom half of the handler?")
            columns(1)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
            )
            onPositive { index: Int, _: Option ->
                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, drawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = titles[index]
                textView.setCompoundDrawables(image, null, null, null)

                bottomSwipeIcon = drawables[index]
                bottomSwipe = titles[index]
            }
        }
    }

    fun openMenu(view: View) {
        val appVersion = getApplicationVersion()
        OptionSheet().show(view.context) {
            title("Menu")
            columns(3)
            displayMode(DisplayMode.GRID_VERTICAL)
            with(
                Option(R.drawable.ic_share, "Share"),
                Option(R.drawable.ic_edit, "Write us"),
                Option(R.drawable.ic_feedback, "Feedback"),
                Option(R.drawable.ic_bug, "Bug reports"),
                Option(R.drawable.ic_privacy, "Privacy policy"),
                Option(R.drawable.ic_playstore, "Other apps"),
                Option(R.drawable.ic_star, "Rate us"),
                Option(R.drawable.ic_github, "Source code"),
                Option(R.drawable.ic_svg, "Icons by"),
                Option(R.drawable.ic_plugin, "V:$appVersion"),
                Option(R.drawable.ic_power, "Exit"),
            )
            onPositive { index: Int, _: Option ->
                when (index) {
                    0 -> {
                        shareTheApp(requireActivity())
                    }
                    1 -> {
                        openMailApp(requireActivity(), "Writing about app", Constants.contactMail)
                    }
                    2 -> {
                        openMailApp(requireActivity(), "Feedback", Constants.feedbackMail)
                    }
                    3 -> {
                        openMailApp(requireActivity(), "Bug reports", Constants.feedbackMail)
                    }
                    4 -> {
                        openWebPage(requireActivity(), Constants.privacyPolicyUrl) { toast = it }
                    }
                    5 -> {
                        openAppStore(requireActivity(), Constants.publisherName) { toast = it }
                    }
                    6 -> {
                        openAppStore(requireActivity(), Constants.appStoreId) { toast = it }
                    }
                    7 -> {
                        openWebPage(requireActivity(), Constants.sourceCodeUrl) { toast = it }
                    }
                    8 -> {
                        toast = "Icons by svgrepo.com"
                    }
                    9 -> {
                        toast = "Version: $appVersion"
                    }
                    10 -> {
                        OverlayService.stop(requireActivity())
                        requireActivity().finish()
                    }
                }
            }
        }
    }

    // Landscape Handler settings
    fun switchLandActivity(view: View) {
        saveData()

        val activity = view.context as Activity
        SharedData.refActivity = WeakReference {
            interstitialAd.loadAd()
            LandConfigActivity.startActivity(activity)
        }

        val clickCount = prefRepository.getClickCount()
        if (clickCount == 0) {
            if (interstitialAd.isReady) {
                interstitialAd.showAd()
            } else {
                LandConfigActivity.startActivity(activity)
            }
            prefRepository.incrementClickCount()
        } else if (clickCount < Constants.showAdsOnEveryClick) {
            prefRepository.incrementClickCount()
            LandConfigActivity.startActivity(activity)
        } else {
            if (interstitialAd.isReady) {
                interstitialAd.showAd()
            } else {
                LandConfigActivity.startActivity(activity)
            }
            prefRepository.resetClickCount()
        }
    }

    fun finishLandActivity(view: View) {
        val activity = view.context as Activity
        SharedData.refActivity = WeakReference {
            interstitialAd.loadAd()
            activity.finish()
        }

        val clickCount = prefRepository.getClickCount()
        if (clickCount == 0) {
            if (interstitialAd.isReady) {
                interstitialAd.showAd()
            } else {
                activity.finish()
            }
            prefRepository.incrementClickCount()
        } else if (clickCount < Constants.showAdsOnEveryClick) {
            prefRepository.incrementClickCount()
            activity.finish()
        } else {
            if (interstitialAd.isReady) {
                interstitialAd.showAd()
            } else {
                activity.finish()
            }
            prefRepository.resetClickCount()
        }
    }

    fun submitData(view: View) {
        saveData()
        val activity = view.context as Activity
//        SharedData.refActivity = WeakReference {
//            activity.finish()
//        }

        if (OverlayService.hasPermission(activity)) {
            OverlayService.start(activity)
            toast("Configuration Saved!!")
            SharedData.shouldShowAppOpenAds = true

//            val clickCount = prefRepository.getClickCount()
//            if (clickCount == 0) {
//                if (interstitialAd.isReady) {
//                    interstitialAd.showAd()
//                } else {
//                    activity.finish()
//                }
//                prefRepository.incrementClickCount()
//            } else if (clickCount < Constants.showAdsOnEveryClick) {
//                prefRepository.incrementClickCount()
//                activity.finish()
//            } else {
//                if (interstitialAd.isReady) {
//                    interstitialAd.showAd()
//                } else {
//                    activity.finish()
//                }
//                prefRepository.resetClickCount()
//            }
        }else {
            toast("Please enable draw overlay permission!!")
        }

    }

    fun submitDataLand(view: View) {
        saveData()
        (view.context as Activity).finish()
        toast("Landscape Configuration Saved!!")
    }

    private fun saveData() {
        val handler = AppHandler(
            gravity = gravity,
            gravityLand = gravityLand,
            topMargin = topMargin,
            leftMargin = leftMargin,
            color = color,
            colorLand = colorLand,
            size = size,
            sizeLand = sizeLand,
            width = width,
            widthLand = widthLand,
            clickAction = clickAction,
            doubleClickAction = doubleClickAction,
            longClickAction = longClickAction,
            upperSwipe = upperSwipe,
            bottomSwipe = bottomSwipe,
        )
        mainRepository.setHandler(handler)
    }

    fun initializeData() {
        val handler = mainRepository.getHandler()

        if (handler != null) {
            gravity = handler.gravity
            gravityLand = handler.gravityLand
            topMargin = handler.topMargin
            leftMargin = handler.leftMargin
            color = handler.color
            colorLand = handler.colorLand
            size = handler.size
            sizeLand = handler.sizeLand
            width = handler.width
            widthLand = handler.widthLand
            clickAction = handler.clickAction
            doubleClickAction = handler.doubleClickAction
            longClickAction = handler.longClickAction
            upperSwipe = handler.upperSwipe
            bottomSwipe = handler.bottomSwipe

            // Set icon
            gravityIcon = when (gravity) {
                "Left" -> R.drawable.ic_align_left
                "Right" -> R.drawable.ic_align_right
                else -> R.drawable.ic_align_right
            }
            gravityIconLand = when (gravityLand) {
                "Top" -> R.drawable.ic_align_top
                "Bottom" -> R.drawable.ic_align_bottom
                else -> R.drawable.ic_align_top
            }
            sizeIcon = when (size) {
                "Small" -> R.drawable.ic_small
                "Medium" -> R.drawable.ic_medium
                "Large" -> R.drawable.ic_large
                else -> R.drawable.ic_small
            }
            sizeIconLand = when (size) {
                "Small" -> R.drawable.ic_small
                "Medium" -> R.drawable.ic_medium
                "Large" -> R.drawable.ic_large
                else -> R.drawable.ic_small
            }
            widthIcon = when (width) {
                "Slim" -> R.drawable.ic_small
                "Regular" -> R.drawable.ic_regular
                "Bold" -> R.drawable.ic_bold
                else -> R.drawable.ic_small
            }
            widthIconLand = when (width) {
                "Slim" -> R.drawable.ic_small
                "Regular" -> R.drawable.ic_regular
                "Bold" -> R.drawable.ic_bold
                else -> R.drawable.ic_small
            }

            // Action icons
            clickActionIcon = when (clickAction) {
                "None" -> R.drawable.ic_nothing
                "Open volume UI" -> R.drawable.ic_vol_increase
                "Mute" -> R.drawable.ic_mute
                "Active Music Overlay" -> R.drawable.ic_music_ui
                "Lock" -> R.drawable.ic_lock
                "Hide Handler" -> R.drawable.ic_visibility_hide
                "Open App" -> R.drawable.ic_app_open
                else -> R.drawable.ic_nothing
            }
            doubleClickActionIcon = when (doubleClickAction) {
                "None" -> R.drawable.ic_nothing
                "Open volume UI" -> R.drawable.ic_vol_increase
                "Mute" -> R.drawable.ic_mute
                "Active Music Overlay" -> R.drawable.ic_music_ui
                "Lock" -> R.drawable.ic_lock
                "Hide Handler" -> R.drawable.ic_visibility_hide
                "Open App" -> R.drawable.ic_app_open
                else -> R.drawable.ic_nothing
            }
            longClickActionIcon = when (longClickAction) {
                "None" -> R.drawable.ic_nothing
                "Open volume UI" -> R.drawable.ic_vol_increase
                "Mute" -> R.drawable.ic_mute
                "Active Music Overlay" -> R.drawable.ic_music_ui
                "Lock" -> R.drawable.ic_lock
                "Hide Handler" -> R.drawable.ic_visibility_hide
                "Open App" -> R.drawable.ic_app_open
                else -> R.drawable.ic_nothing
            }
            upperSwipeIcon = when (upperSwipe) {
                "None" -> R.drawable.ic_nothing
                "Increase volume" -> R.drawable.ic_vol_plus
                "Increase volume and show UI" -> R.drawable.ic_vol_increase
                else -> R.drawable.ic_nothing
            }
            bottomSwipeIcon = when (bottomSwipe) {
                "None" -> R.drawable.ic_nothing
                "Decrease volume" -> R.drawable.ic_vol_minus
                "Decrease volume and show UI" -> R.drawable.ic_vol_decrease
                else -> R.drawable.ic_nothing
            }
        }
    }

    // Overlay Settings
    init {
        Timber.d("injection DashboardViewModel")
        initializeData()
    }

}


