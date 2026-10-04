package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.manager.BillingManager
import com.newagedevs.gesturevolume.ui.screens.walkthrough.WalkthroughScreen
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import com.newagedevs.gesturevolume.ui.viewmodels.MainViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every page of the walkthrough, at sizes it has to fit: a phone upright, a small one with large
 * text in German, a phone on its side, a short one on its side, and a tablet. Run as a tour, which
 * writes nothing. Left as walklayout_<size>_<page>.png.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughLayoutRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val viewModel by lazy {
        val preference = SharedPref(compose.activity)
        MainViewModel(preference, BillingManager(compose.activity, preference))
    }

    @Test
    fun phone() = walk("phone", null)

    @Test
    fun smallLargeTextGerman() = walk(
        "small_de",
        DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 568.dp)) then
            DeviceConfigurationOverride.FontScale(1.3f) then
            DeviceConfigurationOverride.Locales(LocaleList("de")),
    )

    @Test
    fun landscape() = walk("landscape", DeviceConfigurationOverride.ForcedSize(DpSize(891.dp, 411.dp)))

    @Test
    fun shortLandscape() = walk(
        "landscape_short",
        DeviceConfigurationOverride.ForcedSize(DpSize(640.dp, 340.dp)) then DeviceConfigurationOverride.FontScale(1.15f),
    )

    @Test
    fun tablet() = walk("tablet", DeviceConfigurationOverride.ForcedSize(DpSize(800.dp, 1280.dp)))

    private fun walk(name: String, override: DeviceConfigurationOverride?) {
        compose.setContent {
            GestureVolumeTheme(darkTheme = false) {
                if (override == null) {
                    WalkthroughScreen(viewModel = viewModel, tour = true, onComplete = {})
                } else {
                    DeviceConfigurationOverride(override) {
                        WalkthroughScreen(viewModel = viewModel, tour = true, onComplete = {})
                    }
                }
            }
        }
        // On through every page with its primary button, or past a permission page with its Skip,
        // which sits level with Allow: nothing is asked for. Until the counter says it is the last.
        for (page in 1..MAX_PAGES) {
            Thread.sleep(900)
            save("walklayout_${name}_$page")
            if (isLastPage()) return
            val buttons = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            val nodes = buttons.fetchSemanticsNodes()
            if (nodes.isEmpty()) return
            val last = nodes.last().boundsInRoot
            val beforeLast = nodes.getOrNull(nodes.lastIndex - 1)?.boundsInRoot
            val asks = beforeLast != null && beforeLast.top == last.top && beforeLast.height == last.height
            buttons[if (asks) nodes.lastIndex - 1 else nodes.lastIndex].performClick()
        }
    }

    /** Whether the top row's "n/N" has n at N. Measured even where the row had no room to show it. */
    private fun isLastPage(): Boolean {
        val counter = compose.onAllNodes(SemanticsMatcher("counter") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { COUNTER.matches(it.text) } == true
        }).fetchSemanticsNodes().firstOrNull() ?: return false
        val (at, of) = COUNTER.find(counter.config[SemanticsProperties.Text].first { COUNTER.matches(it.text) }.text)!!.destructured
        return at == of
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val MAX_PAGES = 9
        val COUNTER = Regex("""^(\d+)/(\d+)$""")
    }
}
