package com.newagedevs.gesturevolume.view.ui.main

import android.view.View
import androidx.databinding.Bindable
import com.applovin.mediation.ads.MaxInterstitialAd
import com.maxkeppeler.sheets.option.DisplayMode
import com.maxkeppeler.sheets.option.Option
import com.maxkeppeler.sheets.option.OptionSheet
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.getApplicationVersion
import com.newagedevs.gesturevolume.extensions.openAppStore
import com.newagedevs.gesturevolume.extensions.openMailApp
import com.newagedevs.gesturevolume.extensions.openWebPage
import com.newagedevs.gesturevolume.extensions.shareTheApp
import com.newagedevs.gesturevolume.persistence.SharedPrefRepository
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.utils.Constants
import com.skydoves.bindables.BindingViewModel
import com.skydoves.bindables.bindingProperty
import timber.log.Timber


class MainViewModel(
    private val pref: SharedPrefRepository
) : BindingViewModel() {

    @get:Bindable
    var toast: String? by bindingProperty(null)

    @get:Bindable
    var enabledHandler: Boolean by bindingProperty(false)

    @get:Bindable
    var gravity: String? by bindingProperty("Right")


    @get:Bindable
    var gravityIcon: Int? by bindingProperty(R.drawable.ic_align_right)


    @get:Bindable
    var size: String? by bindingProperty("Medium")

    @get:Bindable
    var sizeIcon: Int? by bindingProperty(R.drawable.ic_medium)

    @get:Bindable
    var width: String? by bindingProperty("Slim")

    @get:Bindable
    var widthIcon: Int? by bindingProperty(R.drawable.ic_small)

    @get:Bindable
    var color: Int? by bindingProperty(null)

    @get:Bindable
    var translationY: Float by bindingProperty(260f)

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
    var swipeUpAction: String? by bindingProperty("Increase volume and show UI")

    @get:Bindable
    var swipeUpActionIcon: Int? by bindingProperty(R.drawable.ic_vol_increase)

    @get:Bindable
    var swipeDownAction: String? by bindingProperty("Decrease volume and show UI")

    @get:Bindable
    var swipeDownActionIcon: Int? by bindingProperty(R.drawable.ic_vol_increase)

    var interstitialAd: MaxInterstitialAd? = null
    var retryAttempt = 0.0

    fun toast(message: String) {
        toast = ""
        toast = message
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


    private fun initializeData() {
        // Handler properties
        gravity = pref.getHandlerPosition()
        translationY = pref.getHandlerTranslationY()
        color = pref.getHandlerColor()
        size = pref.getHandlerSize()
        width = pref.getHandlerWidth()
        clickAction = pref.getHandlerSingleTapAction()
        doubleClickAction = pref.getHandlerDoubleTapAction()
        longClickAction = pref.getHandlerLongTapAction()
        swipeUpAction =   pref.getHandlerSwipeUpAction()
        swipeDownAction =   pref.getHandlerSwipeDownAction()

        // Set icon
        gravityIcon = when (gravity) {
            "Left" -> R.drawable.ic_align_left
            "Right" -> R.drawable.ic_align_right
            else -> R.drawable.ic_align_right
        }
        sizeIcon = when (size) {
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
        swipeUpActionIcon = when (swipeUpAction) {
            "None" -> R.drawable.ic_nothing
            "Increase volume" -> R.drawable.ic_vol_plus
            "Increase volume and show UI" -> R.drawable.ic_vol_increase
            else -> R.drawable.ic_nothing
        }
        swipeDownActionIcon = when (swipeDownAction) {
            "None" -> R.drawable.ic_nothing
            "Decrease volume" -> R.drawable.ic_vol_minus
            "Decrease volume and show UI" -> R.drawable.ic_vol_decrease
            else -> R.drawable.ic_nothing
        }

    }

    // Overlay Settings
    init {
        Timber.d("injection DashboardViewModel")
        initializeData()
    }

}


