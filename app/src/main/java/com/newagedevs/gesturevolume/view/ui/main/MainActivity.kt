package com.newagedevs.gesturevolume.view.ui.main

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import com.maxkeppeler.sheets.core.SheetStyle
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityMainBinding
import com.newagedevs.gesturevolume.overlays.OverlayService
import com.newagedevs.gesturevolume.view.ui.CustomSheet
import com.skydoves.bindables.BindingActivity
import org.koin.android.viewmodel.ext.android.viewModel

class MainActivity : BindingActivity<ActivityMainBinding>(R.layout.activity_main) {

    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding {
            vm = viewModel
        }

        setupPreviewFrame()

//        if (OverlayService.running) {
//            this.stopService(Intent(this, OverlayService::class.java))
//        }

    }

    private fun setupPreviewFrame() {
        val preview = findViewById<ImageView>(R.id.bg)
        val wallpaperManager = WallpaperManager.getInstance(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                WALLPAPER_REQUEST_CODE
            )
            return
        }

        val wallpaperDrawable = wallpaperManager.drawable
        preview.setImageDrawable(wallpaperDrawable)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                viewModel.startOverlayService(this)
            }
        } else if (requestCode == WALLPAPER_REQUEST_CODE) {
            setupPreviewFrame()
        }

    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        CustomSheet().show(this@MainActivity) {
            style(SheetStyle.BOTTOM_SHEET)
            title("Confirm Exit")
            content("Are you sure you want to exit? Hope you will come back again.")
            onPositive("Exit") {
                finish()
            }
        }
    }

    companion object {
        const val OVERLAY_REQUEST_CODE = 1
        const val WALLPAPER_REQUEST_CODE = 2
    }

}