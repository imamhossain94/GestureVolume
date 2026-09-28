package com.newagedevs.gesturevolume

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newagedevs.gesturevolume.ui.screens.walkthrough.WalkScene
import com.newagedevs.gesturevolume.ui.screens.walkthrough.drawWalkScene
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The walkthrough's scenes, drawn on this phone at moments through their loops: the Deck as one
 * column down the edge, and a moved bar carried across to the other side.
 *
 * Drawn at twice the scene's own size, so a point of the scene is two pixels here. The frames are
 * left in the app's cache as PNGs (walk_*.png), to be looked at.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughScenesRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everySceneDrawsThroughItsLoop() {
        WalkScene.entries.forEach { scene ->
            MOMENTS.forEachIndexed { i, t ->
                val frame = render(scene, t)
                save(frame, "walk_${scene.name}_$i.png")
                // More than the ground: the phone at the very least.
                assertTrue("$scene at $t is blank", count(frame, 0, 0, W, H) { it != GROUND } > W * H / 4)
            }
        }
    }

    @Test
    fun theDeckIsOneColumnDownTheEdge() {
        // Open, midway through its loop.
        val open = render(WalkScene.Deck, 0.6f)
        // Down the strip, between its tiles and its side, its glass from top to bottom...
        val box = (px(191f) - px(188f)) * (px(160f) - px(56f))
        val strip = count(open, px(188f), px(56f), px(191f), px(160f)) { isDeckPanel(it) }
        assertTrue("no strip down the edge ($strip of $box)", strip > box * 9 / 10)
        // ...and across the middle of the screen, where the grid used to be, none of it.
        val middle = count(open, px(80f), px(60f), px(170f), px(160f)) { isDeckPanel(it) }
        assertTrue("the Deck reaches across the screen ($middle)", middle == 0)
    }

    @Test
    fun aMovedTabEndsOnTheOtherEdge() {
        val tab = { c: Int -> android.graphics.Color.red(c) < 16 && android.graphics.Color.green(c) < 16 }
        val before = render(WalkScene.MoveTab, 0.1f)
        val after = render(WalkScene.MoveTab, 0.86f)
        // The strips of screen along each edge, over the stretch the bar is carried down.
        val rightBefore = count(before, px(225f), px(70f), px(234f), px(150f), tab)
        val leftAfter = count(after, px(46f), px(110f), px(55f), px(195f), tab)
        val rightAfter = count(after, px(225f), px(70f), px(234f), px(195f), tab)
        assertTrue("no tab on the right to start with ($rightBefore)", rightBefore > 200)
        assertTrue("no tab on the left once let go ($leftAfter)", leftAfter > 200)
        assertTrue("a tab left behind on the right ($rightAfter)", rightAfter < 20)
    }

    private fun render(scene: WalkScene, t: Float): Bitmap {
        val image = ImageBitmap(W, H)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(W.toFloat(), H.toFloat())) {
            drawRect(Color(GROUND))
            drawWalkScene(scene, t, Path())
        }
        return image.asAndroidBitmap()
    }

    /** How many pixels of [b] in the box from ([l], [t]) to ([r], [bottom]) pass [test]. */
    private fun count(b: Bitmap, l: Int, t: Int, r: Int, bottom: Int, test: (Int) -> Boolean): Int {
        val w = r - l
        val h = bottom - t
        val pixels = IntArray(w * h)
        b.getPixels(pixels, 0, w, l, t, w, h)
        return pixels.count(test)
    }

    /** The Deck's dark glass over the wallpaper: dark, and bluer than it is red. */
    private fun isDeckPanel(c: Int): Boolean {
        val r = android.graphics.Color.red(c)
        val g = android.graphics.Color.green(c)
        val b = android.graphics.Color.blue(c)
        return r < 70 && g < 70 && b in 40..110 && b > r
    }

    /** A point of the scene, in pixels here. */
    private fun px(v: Float): Int = (v * SCALE).toInt()

    private fun save(b: Bitmap, name: String) {
        File(context.cacheDir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** Twice the scene's 280 x 200, so it fills exactly, without letterboxing. */
        const val SCALE = 2f
        const val W = 560
        const val H = 400
        const val GROUND = 0xFFF3EDF7.toInt()
        val MOMENTS = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.86f)
    }
}
