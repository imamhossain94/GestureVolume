package com.newagedevs.gesturevolume.view.ui.main

import android.Manifest
import android.app.ActivityManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.databinding.ActivityMainBinding
import com.newagedevs.gesturevolume.service.LockScreenUtil
import com.newagedevs.gesturevolume.service.OverlayService
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

        OverlayService.stop(this)
        //this.stopService(Intent(this, OverlayService::class.java))

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
                OverlayService.start(this)
            }
        } else if (requestCode == WALLPAPER_REQUEST_CODE) {
            setupPreviewFrame()
        } else if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {

            if (LockScreenUtil(this).active()) {
                viewModel.clickActionIcon = R.drawable.ic_lock
                viewModel.clickAction = "Lock"
            }
        }

    }

    override fun onPause() {
        super.onPause()
        if(!OverlayService.isRunning(this)){
            OverlayService.start(this)
        }
    }

    override fun onResume() {
        super.onResume()
        OverlayService.stop(this)
    }

    companion object {
        const val OVERLAY_REQUEST_CODE = 1
        const val WALLPAPER_REQUEST_CODE = 2
        const val DEVICE_ADMIN_REQUEST_CODE = 2
    }

}