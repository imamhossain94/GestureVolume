package com.newagedevs.gesturevolume

import com.newagedevs.gesturevolume.ui.components.previewUnit
import com.newagedevs.gesturevolume.ui.components.previewOrigin
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.overlay.ContextMenuCard
import com.newagedevs.gesturevolume.overlay.deck.DeckPalette
import com.newagedevs.gesturevolume.overlay.deck.DeckPreviewStrip
import com.newagedevs.gesturevolume.overlay.deck.DeckTiles
import com.newagedevs.gesturevolume.ui.components.DemoGesture
import com.newagedevs.gesturevolume.ui.components.DeviceArt
import com.newagedevs.gesturevolume.ui.components.GestureDemoOverlay
import com.newagedevs.gesturevolume.ui.components.GestureDemoState
import com.newagedevs.gesturevolume.ui.components.PREVIEW_STAGE_TAG
import com.newagedevs.gesturevolume.ui.components.PreviewStage
import com.newagedevs.gesturevolume.ui.components.scaleToFit
import com.newagedevs.gesturevolume.ui.view.QuickSliderView
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog
import com.newagedevs.gesturevolume.utils.HandlerActions
import com.newagedevs.gesturevolume.utils.SliderFill
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The settings previews on the walkthrough's phone, drawn on this phone: the Deck's strip, the
 * long-press menu and the Quick panel, each on the glass with the frame round it. The frames are
 * left in the app's cache as PNGs (stage_*.png), to be looked at.
 */
@RunWith(AndroidJUnit4::class)
class PreviewStagesRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theDeckStandsAgainstTheEdgeOfTheGlass() {
        val image = stage("deck", Alignment.CenterEnd) {
            DeckPreviewStrip(
                tiles = DeckTiles.ALL,
                palette = DeckPalette(surface = Color(0xE61D1B2B), accent = Color(0xFF4F46E5)),
                widthDp = 64f,
                cornerDp = 30f,
                glass = false,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        assertPhone(image)
    }

    @Test
    fun theMenuSitsInTheMiddleOfTheGlass() {
        val image = stage("menu", Alignment.Center) {
            ContextMenuCard(
                entries = HandlerActionCatalog.contextMenuEntries(HandlerActions.DEFAULT_CONTEXT_MENU.toList()),
                grid = true,
                onSelect = {},
                modifier = Modifier.scaleToFit(0.8f, horizontal = 12.dp, top = 44.dp, bottom = 12.dp),
            )
        }
        assertPhone(image)
        assertMenuOnGlass(image)
    }

    @Test
    fun aLongMenuIsDrawnSmallerToFitTheGlass() {
        // Every action there is, as a list: far taller than the part of the phone that shows.
        val image = stage("menu_long", Alignment.Center) {
            ContextMenuCard(
                entries = HandlerActionCatalog.contextMenuEntries(HandlerActionCatalog.ALL.map { it.action }),
                grid = false,
                onSelect = {},
                modifier = Modifier.scaleToFit(0.8f, horizontal = 12.dp, top = 44.dp, bottom = 12.dp),
            )
        }
        assertMenuOnGlass(image)
    }

    /**
     * The menu's dark card is somewhere on the glass, and nowhere near its edges: not in the rows
     * of the status bar and the camera, not in the last rows before the stage cuts the phone off,
     * and not down either side of the glass.
     */
    private fun assertMenuOnGlass(image: Bitmap) {
        val density = compose.activity.resources.displayMetrics.density
        val (unit, origin) = fit(image)
        val left = ((origin.x + DeviceArt.SCREEN_L * unit) * density).toInt()
        val right = ((origin.x + DeviceArt.SCREEN_R * unit) * density).toInt()
        val top = ((origin.y + DeviceArt.SCREEN_T * unit) * density).toInt()
        val bottom = image.height - 1
        val camera = (left + right) / 2
        fun dark(x: Int, y: Int): Boolean {
            val c = image.getPixel(x, y)
            return android.graphics.Color.red(c) < 70 && android.graphics.Color.green(c) < 70 && android.graphics.Color.blue(c) < 80
        }
        // Clear of the glass's rounded top corners, where the frame shows through and is as dark.
        val corner = (DeviceArt.SCREEN_CORNER * unit * density).toInt()
        fun darkInRows(from: Int, to: Int): Int {
            var n = 0
            for (y in from until to) for (x in left + corner until right - corner) {
                if (abs(x - camera) > (8 * density).toInt() && dark(x, y)) n++
            }
            return n
        }
        fun darkInColumns(from: Int, to: Int): Int {
            var n = 0
            for (x in from until to) for (y in top + (30 * density).toInt() until bottom) if (dark(x, y)) n++
            return n
        }
        val middle = darkInRows((top + bottom) / 2, (top + bottom) / 2 + 1)
        assertTrue("no menu on the glass", middle > 20)
        // The status bar and the camera come down to 36.5 of the scene's units. From a dp into the
        // glass: its top row, rounded down, can be the frame's edge.
        val statusBar = ((origin.y + 36.5f * unit) * density).toInt()
        assertTrue("the menu is under the status bar", darkInRows(top + density.toInt(), statusBar) == 0)
        assertTrue("the menu runs off the bottom of the phone", darkInRows(bottom - (6 * density).toInt(), bottom) == 0)
        assertTrue("the menu runs off the left of the glass", darkInColumns(left + 2, left + (6 * density).toInt()) == 0)
        assertTrue("the menu runs off the right of the glass", darkInColumns(right - (6 * density).toInt(), right - 2) == 0)
    }

    @Test
    fun theQuickPanelStandsAgainstTheEdgeOfTheGlass() {
        val image = stage("slider", Alignment.CenterEnd) {
            AndroidView(
                factory = { context ->
                    QuickSliderView(context).apply {
                        setColors(0xFF1C1C20.toInt(), android.graphics.Color.WHITE)
                        setCornerRadiusDp(22f)
                        setFillStyle(SliderFill.GLIMMER)
                        setContentMargins(35f, 35f)
                        setIcon(R.drawable.ic_vol_increase)
                    }
                },
                update = { view ->
                    view.setExpansion(1f)
                    view.setCommitted()
                    view.setValue(0.6f)
                },
                modifier = Modifier
                    .width(28.dp)
                    .height(178.dp),
            )
        }
        assertPhone(image)
    }

    /** [content] on the preview stage, [alignment] on its glass, saved as stage_[name].png. */
    private fun stage(name: String, alignment: Alignment, content: @Composable () -> Unit): Bitmap {
        compose.setContent {
            Box(modifier = Modifier.width(STAGE_WIDTH.dp)) {
                PreviewStage(contentAlignment = alignment) { content() }
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag(PREVIEW_STAGE_TAG).captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "stage_$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }

    @Test
    fun aBarOnTheRightIsReachedByARightHand() {
        val image = handOn("hand_right", barAtStart = false)
        val (left, right) = skinBySide(image)
        assertTrue("the hand is not on the right: $left left, $right right", right > left * 2)
    }

    @Test
    fun aBarOnTheLeftIsReachedByALeftHand() {
        val image = handOn("hand_left", barAtStart = true)
        val (left, right) = skinBySide(image)
        assertTrue("the hand is not on the left: $left left, $right right", left > right * 2)
    }

    @Test
    fun movingTheBarAcrossTakesTheHandWithItAtOnce() {
        var barAtStart by mutableStateOf(false)
        val demo = GestureDemoState(listOf(DemoGesture.TAP, DemoGesture.SWIPE_UP), passes = 2)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(modifier = Modifier.width(STAGE_WIDTH.dp)) {
                PreviewStage(
                    contentAlignment = if (barAtStart) Alignment.CenterStart else Alignment.CenterEnd,
                    overGlass = {
                        GestureDemoOverlay(
                            state = demo,
                            barAtStart = barAtStart,
                            barInset = 12.dp,
                            showBar = true,
                            modifier = Modifier.matchParentSize(),
                        )
                    },
                ) {}
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        val (leftBefore, rightBefore) = skinBySide(capture("stage_switch_0"))
        assertTrue("no hand on the right to start with", rightBefore > 500 && rightBefore > leftBefore * 2)

        barAtStart = true
        compose.mainClock.advanceTimeBy(32)
        val (leftNow, rightNow) = skinBySide(capture("stage_switch_1"))
        assertTrue("the hand stayed on the right: $rightNow", rightNow < 50)

        compose.mainClock.advanceTimeBy(2000)
        val (leftAfter, rightAfter) = skinBySide(capture("stage_switch_2"))
        assertTrue("the hand did not come in on the left: $leftAfter left, $rightAfter right", leftAfter > 500 && leftAfter > rightAfter * 2)
    }

    private fun capture(name: String): Bitmap {
        val image = compose.onNodeWithTag(PREVIEW_STAGE_TAG).captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return image
    }

    /** The demo's hand at the bar on one side of the stage, a moment into its tap. */
    private fun handOn(name: String, barAtStart: Boolean): Bitmap {
        val demo = GestureDemoState(listOf(DemoGesture.TAP, DemoGesture.SWIPE_UP), passes = 1)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(modifier = Modifier.width(STAGE_WIDTH.dp)) {
                PreviewStage(
                    contentAlignment = if (barAtStart) Alignment.CenterStart else Alignment.CenterEnd,
                    overGlass = {
                        GestureDemoOverlay(
                            state = demo,
                            barAtStart = barAtStart,
                            barInset = 12.dp,
                            showBar = true,
                            modifier = Modifier.matchParentSize(),
                        )
                    },
                ) {}
            }
        }
        // In from off the phone and onto the bar.
        compose.mainClock.advanceTimeBy(1500)
        val image = compose.onNodeWithTag(PREVIEW_STAGE_TAG).captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, "stage_$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return image
    }

    /** How much of the hand's skin is in the left half of [image], and how much in the right. */
    private fun skinBySide(image: Bitmap): Pair<Int, Int> {
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        var left = 0
        var right = 0
        pixels.forEachIndexed { i, c ->
            val r = android.graphics.Color.red(c)
            val g = android.graphics.Color.green(c)
            val b = android.graphics.Color.blue(c)
            if (r > 200 && g in 140..220 && b in 90..190 && r - b > 40) {
                if (i % image.width < image.width / 2) left++ else right++
            }
        }
        return left to right
    }

    /**
     * The frame down the left side of the phone, and the wallpaper's lavender just inside it: where
     * the walkthrough's scene puts them, in the stage's close-up of the phone's top.
     */
    private fun assertPhone(image: Bitmap) {
        val density = compose.activity.resources.displayMetrics.density
        val (unit, origin) = fit(image)
        // Well down the glass, below the status bar, and still on the stage.
        val y = ((origin.y + 90f * unit) * density).toInt()
        assertTrue("the stage cuts the phone off above its middle", y < image.height)
        // The frame's side runs from 40 to 46 of the scene's units.
        val frame = image.getPixel(((origin.x + 43f * unit) * density).toInt(), y)
        assertTrue("no frame at the phone's side: %08X".format(frame), near(frame, DeviceArt.Frame.toArgb()))
        val glass = image.getPixel(((origin.x + (DeviceArt.SCREEN_L + 6f) * unit) * density).toInt(), y)
        val r = android.graphics.Color.red(glass)
        val b = android.graphics.Color.blue(glass)
        assertTrue("no wallpaper inside the frame: %08X".format(glass), b > 200 && r > 140 && b > r)
    }

    /** The stage's scene unit and where its scene sits, in dp, worked out as the stage does from its size. */
    private fun fit(stage: Bitmap): Pair<Float, Offset> {
        val density = compose.activity.resources.displayMetrics.density
        val unit = previewUnit(stage.width / density, stage.height / density)
        return unit to previewOrigin(stage.width / density, unit)
    }

    private fun near(a: Int, b: Int): Boolean =
        abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) < 12 &&
            abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) < 12 &&
            abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) < 12

    private companion object {
        /** A phone-width stage, in dp. */
        const val STAGE_WIDTH = 360f
    }
}
