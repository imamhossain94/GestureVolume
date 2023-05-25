package com.newagedevs.gesturevolume.view.ui.main

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.Bindable
import com.maxkeppeler.sheets.color.ColorSheet
import com.maxkeppeler.sheets.option.DisplayMode
import com.maxkeppeler.sheets.option.Option
import com.maxkeppeler.sheets.option.OptionSheet
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.*
import com.newagedevs.gesturevolume.model.AppHandler
import com.newagedevs.gesturevolume.service.OverlayService
import com.newagedevs.gesturevolume.repository.MainRepository
import com.newagedevs.gesturevolume.service.LockScreenUtil
import com.newagedevs.gesturevolume.utils.Constants
import com.skydoves.bindables.BindingViewModel
import com.skydoves.bindables.bindingProperty
import timber.log.Timber


class MainViewModel constructor(
    private val mainRepository: MainRepository
) : BindingViewModel() {

    @get:Bindable
    var toast: String? by bindingProperty(null)

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
    var topMargin: Float? by bindingProperty(260f)

    // Gesture action
    @get:Bindable
    var clickAction: String? by bindingProperty("Open volume UI")

    @get:Bindable
    var clickActionIcon: Int? by bindingProperty(R.drawable.ic_music_ui)

    @get:Bindable
    var upperSwipe: String? by bindingProperty("Increase volume")

    @get:Bindable
    var upperSwipeIcon: Int? by bindingProperty(R.drawable.ic_vol_plus)

    @get:Bindable
    var bottomSwipe: String? by bindingProperty("Decrease volume")

    @get:Bindable
    var bottomSwipeIcon: Int? by bindingProperty(R.drawable.ic_vol_minus)

    // Handler settings
    fun gravityPicker(view: View) {
        val drawables = listOf(R.drawable.ic_align_left, R.drawable.ic_align_right)
        val titles = listOf("Left", "Right")

        OptionSheet().show(view.context) {
            title("Select your handedness or the gravity of the handler")
            columns(2)
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

    fun sizePicker(view: View) {
        val drawables = listOf(R.drawable.ic_small, R.drawable.ic_medium, R.drawable.ic_large)
        val titles = listOf("Small", "Medium", "Large")

        OptionSheet().show(view.context) {
            title("Select the height or size of the handler")
            columns(3)
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

    fun widthPicker(view: View) {
        val drawables = listOf(R.drawable.ic_small, R.drawable.ic_regular, R.drawable.ic_bold)
        val titles = listOf("Slim", "Regular", "Bold")

        OptionSheet().show(view.context) {
            title("Select the width or thickness of the handler")
            columns(3)
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

    fun colorPicker(view: View) {

        ColorSheet().show(view.context) {
            title("Select the color and transparency of the handler")
            onPositive {
                color = it
            }
        }

    }

    fun clickActionPicker(view: View) {
        val lockScreenUtil =LockScreenUtil(view.context)
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_mute, R.drawable.ic_lock, R.drawable.ic_music_ui)
        val titles = listOf("None", "Mute", "Lock", "Open volume UI")

        OptionSheet().show(view.context) {
            title("What should happen when you tap on the handler?")
            columns(2)
            with(
                Option(drawables[0], titles[0]),
                Option(drawables[1], titles[1]),
                Option(drawables[2], titles[2]),
                Option(drawables[3], titles[3]),
            )
            onPositive { index: Int, _: Option ->

                if(index == 2 && !lockScreenUtil.active()) {
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

    fun upperSwipeActionPicker(view: View) {
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_plus, R.drawable.ic_vol_increase)
        val titles = listOf("None", "Increase volume", "Increase volume and show UI")

        OptionSheet().show(view.context) {
            title("What should happen when you swipe the upper half of the handler?")
            columns(2)
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

    fun bottomSwipeActionPicker(view: View) {
        val drawables = listOf(R.drawable.ic_nothing, R.drawable.ic_vol_plus, R.drawable.ic_vol_increase)
        val titles = listOf("None", "Decrease volume", "Decrease volume and show UI")

        OptionSheet().show(view.context) {
            title("What should happen when you swipe the bottom half of the handler?")
            columns(2)
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

    fun submitData(view: View) {

        val activity = view.context as Activity
        val handler = AppHandler(
            gravity = gravity,
            topMargin = topMargin,
            color = color,
            size = size,
            width = width,
            clickAction = clickAction,
            upperSwipe = upperSwipe,
            bottomSwipe = bottomSwipe,
        )

        toast=""
        if (OverlayService.hasPermission(activity)) {
            mainRepository.setHandler(handler)
//            OverlayService.stop(activity)
            OverlayService.start(activity)
            toast="Configuration Saved!!"
            activity.finish()
        }else {
            toast="Please enable draw overlay permission!!"
        }

    }

    private fun initializeData() {
        val handler = mainRepository.getHandler()

        if (handler != null) {
            gravity = handler.gravity
            topMargin = handler.topMargin
            color = handler.color
            size = handler.size
            width = handler.width
            clickAction = handler.clickAction
            upperSwipe = handler.upperSwipe
            bottomSwipe = handler.bottomSwipe

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
                "Mute" -> R.drawable.ic_mute
                "Open volume UI" -> R.drawable.ic_music_ui
                else -> R.drawable.ic_music_ui
            }
            upperSwipeIcon = when (upperSwipe) {
                "None" -> R.drawable.ic_nothing
                "Increase volume" -> R.drawable.ic_vol_plus
                "Increase volume and show UI" -> R.drawable.ic_vol_increase
                else -> R.drawable.ic_vol_plus
            }
            bottomSwipeIcon = when (bottomSwipe) {
                "None" -> R.drawable.ic_nothing
                "Decrease volume" -> R.drawable.ic_vol_minus
                "Decrease volume and show UI" -> R.drawable.ic_vol_decrease
                else -> R.drawable.ic_vol_minus
            }
        }
    }

    // Overlay Settings


    init {
        Timber.d("injection DashboardViewModel")
        initializeData()
    }

}


