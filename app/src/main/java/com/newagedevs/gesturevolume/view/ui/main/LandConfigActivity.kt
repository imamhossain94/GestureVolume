package com.newagedevs.gesturevolume.view.ui.main

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityLandConfigBinding
import com.skydoves.bindables.BindingActivity
import com.skydoves.bundler.intentOf
import org.koin.android.viewmodel.ext.android.viewModel


class LandConfigActivity : BindingActivity<ActivityLandConfigBinding>(R.layout.activity_land_config) {

    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding {
            vm = viewModel
        }

        setupPreviewFrame()
    }

    private fun setupPreviewFrame() {
        val preview = findViewById<ImageView>(R.id.bg)
        val wallpaperManager = WallpaperManager.getInstance(this)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            try{
                val wallpaperDrawable = wallpaperManager.drawable
                preview.setImageDrawable(wallpaperDrawable)
            } catch (_: IllegalStateException) { }
        }
    }

    companion object {
        fun startActivity(
            context: Context,
        ) = context.intentOf<LandConfigActivity> {
            context.startActivity(intent, null)
        }
    }

}