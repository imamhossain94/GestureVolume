package com.newagedevs.gesturevolume.ui.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.newagedevs.gesturevolume.R
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.utils.SearchRouter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Runs the system's speech recogniser for the Deck and acts on what it hears.
 *
 * An overlay window has no Activity of its own, and `startActivityForResult` needs one — so the
 * Deck starts this, which is invisible, finishes the moment it has an answer, and routes that
 * answer exactly as the typed search would. The user sees the recogniser's own dialog and then
 * whatever they asked for.
 */
@AndroidEntryPoint
class VoiceSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var preference: SharedPref

    private val recognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (spoken.isNotEmpty()) handle(spoken)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nothing is drawn: the recogniser's own UI is the only thing the user should see.
        if (savedInstanceState != null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_voice_prompt))
        try {
            recognizer.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.search_voice_unavailable, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** The same routing the typed field uses, so speaking a sum answers it and a name finds it. */
    private fun handle(spoken: String) {
        val search = preference.search
        val route = SearchRouter.route(
            input = spoken,
            numberAction = search.getNumberAction(),
            defaultProvider = search.getDefaultProvider(),
            calculatorEnabled = search.getInlineCalculator()
        )
        when (route) {
            is SearchRouter.Route.Empty -> Unit
            is SearchRouter.Route.Calculation ->
                Toast.makeText(this, "${route.expression} = ${route.result}", Toast.LENGTH_LONG).show()
            is SearchRouter.Route.PhoneNumber ->
                start(Intent(Intent.ACTION_DIAL, "tel:${route.digits}".toUri()))
            is SearchRouter.Route.Web ->
                start(Intent(Intent.ACTION_VIEW, SearchRouter.webUrl(route.provider, route.query).toUri()))
        }
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.action_unavailable_on_device, Toast.LENGTH_SHORT).show()
        }
    }
}
