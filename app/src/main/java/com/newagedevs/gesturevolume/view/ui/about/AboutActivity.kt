package com.newagedevs.gesturevolume.view.ui.about

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.getApplicationVersion

class AboutActivity : AppCompatActivity() {

    private lateinit var appVersion: TextView
    private lateinit var topAppBar: MaterialToolbar

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_about)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        topAppBar = findViewById(R.id.topAppBar)

        topAppBar.setNavigationOnClickListener { finish() }

        appVersion = findViewById(R.id.tv_app_version)
        appVersion.text = "${getString(R.string.app_name)} " + getApplicationVersion()
    }

}
