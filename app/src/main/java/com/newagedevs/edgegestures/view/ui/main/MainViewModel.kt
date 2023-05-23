package com.newagedevs.edgegestures.view.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.Bindable
import com.maxkeppeler.sheets.color.ColorSheet
import com.maxkeppeler.sheets.option.DisplayMode
import com.maxkeppeler.sheets.option.Option
import com.maxkeppeler.sheets.option.OptionSheet
import com.newagedevs.edgegestures.R
import com.newagedevs.edgegestures.extensions.*
import com.newagedevs.edgegestures.model.AppHandler
import com.newagedevs.edgegestures.overlays.OverlayService
import com.newagedevs.edgegestures.repository.MainRepository
import com.newagedevs.edgegestures.utils.Constants
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

    // Handler settings
    fun gravityPicker(view: View) {
        val gravityDrawables = listOf(R.drawable.ic_align_left, R.drawable.ic_align_right)
        val gravityTitles = listOf("Left", "Right")

        OptionSheet().show(view.context) {
            title("Select handler position")
            columns(2)
            with(
                Option(gravityDrawables[0], gravityTitles[0]),
                Option(gravityDrawables[1], gravityTitles[1]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, gravityDrawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = gravityTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                gravityIcon = gravityDrawables[index]
                gravity = gravityTitles[index]
            }
        }
    }

    fun sizePicker(view: View) {
        val sizeDrawables = listOf(R.drawable.ic_small, R.drawable.ic_medium, R.drawable.ic_large)
        val sizeTitles = listOf("Small", "Medium", "Large")

        OptionSheet().show(view.context) {
            title("Select handler size")
            columns(3)
            with(
                Option(sizeDrawables[0], sizeTitles[0]),
                Option(sizeDrawables[1], sizeTitles[1]),
                Option(sizeDrawables[2], sizeTitles[2]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, sizeDrawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = sizeTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                sizeIcon = sizeDrawables[index]
                size = sizeTitles[index]
            }
        }
    }

    fun widthPicker(view: View) {
        val widthDrawables = listOf(R.drawable.ic_small, R.drawable.ic_regular, R.drawable.ic_bold)
        val widthTitles = listOf("Slim", "Regular", "Bold")

        OptionSheet().show(view.context) {
            title("Select handler width")
            columns(3)
            with(
                Option(widthDrawables[0], widthTitles[0]),
                Option(widthDrawables[1], widthTitles[1]),
                Option(widthDrawables[2], widthTitles[2]),
            )
            onPositive { index: Int, _: Option ->

                val textView = view as TextView

                val image = ResourcesCompat.getDrawable(resources, widthDrawables[index], null)
                image?.setBounds(0, 0, 24.px, 24.px)

                textView.text = widthTitles[index]
                textView.setCompoundDrawables(image, null, null, null)

                widthIcon = widthDrawables[index]
                width = widthTitles[index]
            }
        }

    }

    fun colorPicker(view: View) {

        ColorSheet().show(view.context) {
            title("Select handler color")
            onPositive {
                color = it
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
                        if (OverlayService.running) {
                            requireActivity().stopService(Intent(requireActivity(), OverlayService::class.java))
                        }
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
        )

        toast=""
        if (checkDrawOverlayPermission(view.context)) {
            mainRepository.setHandler(handler)
            startOverlayService(context = view.context)
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
        }
    }

    // Overlay Settings
    private fun checkDrawOverlayPermission(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            (context as Activity).startActivityForResult(intent, MainActivity.OVERLAY_REQUEST_CODE)
            return false
        }
        return true
    }

    fun startOverlayService(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    init {
        Timber.d("injection DashboardViewModel")
        initializeData()
    }

}


