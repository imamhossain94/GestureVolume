package com.newagedevs.gesturevolume.view.ui.feedback

import android.os.Bundle
import android.widget.CheckBox
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.extensions.openMailApp
import com.newagedevs.gesturevolume.utils.Constants


class FeedbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_feedback)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val topAppBar: MaterialToolbar = findViewById(R.id.topAppBar)
        topAppBar.setNavigationOnClickListener { finish() }

        val closeButton: Button = findViewById(R.id.cancel_button)
        val sendButton: Button = findViewById(R.id.send_button)
        val otherIssue: TextInputLayout = findViewById(R.id.other_issue)

        val checkBoxAppCrashes: CheckBox = findViewById(R.id.checkbox_app_crashes)
        val checkBoxTooManyAds: CheckBox = findViewById(R.id.checkbox_too_many_ads)
        val checkBoxAppFreezes: CheckBox = findViewById(R.id.checkbox_app_freezes)
        val checkBoxNotUserFriendly: CheckBox = findViewById(R.id.checkbox_not_user_friendly)

        closeButton.setOnClickListener {
            finish()
        }

        sendButton.setOnClickListener {
            val checkBoxStates = getCheckBoxStates(
                listOf(
                    checkBoxAppCrashes,
                    checkBoxTooManyAds,
                    checkBoxAppFreezes,
                    checkBoxNotUserFriendly
                )
            )

            val selectedProblems = checkBoxStates.filter { it.value }
            var answers = selectedProblems.keys.joinToString(separator = "\n") { id ->
                when (id) {
                    R.id.checkbox_app_crashes -> "App crashes during download"
                    R.id.checkbox_too_many_ads -> "Too many ads"
                    R.id.checkbox_app_freezes -> "App freezes or becomes unresponsive"
                    R.id.checkbox_not_user_friendly -> "App is not user-friendly"
                    else -> ""
                }
            }

            val other = otherIssue.editText?.text.toString().trim()

            if (other.isNotEmpty()) {
                answers += "\n\nOther: $other"
            }

            openMailApp(this, "App Feedback", Constants.feedbackMail, answers)
        }
    }

    private fun getCheckBoxStates(checkBoxes: List<CheckBox>): Map<Int, Boolean> {
        val checkBoxMap = mutableMapOf<Int, Boolean>()

        checkBoxes.forEach { checkBox ->
            checkBoxMap[checkBox.id] = checkBox.isChecked
        }

        return checkBoxMap
    }

}
