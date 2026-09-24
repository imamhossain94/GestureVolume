package com.newagedevs.gesturevolume

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.newagedevs.gesturevolume.ui.motion.Button
import com.newagedevs.gesturevolume.ui.theme.GestureVolumeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's physics on a real screen: a pressed thing gives and comes back, a Material button
 * does too, and a list pulled past its top follows the finger and springs back when let go.
 * Drawn in a test activity of its own, so nothing on the phone's screen is touched.
 */
@RunWith(AndroidJUnit4::class)
class BouncePhysicsDeviceTest {

    @get:Rule
    val rule = createComposeRule()

    private fun redPixels(tag: String): Int {
        val image = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return pixels.count { red(it) }
    }

    private fun red(pixel: Int): Boolean =
        ((pixel shr 16) and 0xFF) > 180 && ((pixel shr 8) and 0xFF) < 90 && (pixel and 0xFF) < 90

    /** The first row from the top of [tag] with red in it, or -1. */
    private fun firstRedRow(tag: String): Int {
        val image = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val row = IntArray(image.width)
        for (y in 0 until image.height) {
            image.getPixels(row, 0, image.width, 0, y, image.width, 1)
            if (row.count { red(it) } > image.width / 3) return y
        }
        return -1
    }

    @Test
    fun aPressedCardGivesAndComesBack() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            GestureVolumeTheme(darkTheme = true) {
                Box(Modifier.size(300.dp).background(Color.Black).testTag("stage"), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(200.dp, 100.dp).clickable {}.background(Color.Red).testTag("card"))
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val rest = redPixels("stage")
        rule.onNodeWithTag("card").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        val pressed = redPixels("stage")
        rule.onNodeWithTag("card").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(2000)
        val after = redPixels("stage")
        // 8 dp off 200 dp is 4% a side: about 92% of the area.
        assertTrue("pressed $pressed of $rest", pressed < rest * 0.96f && pressed > rest * 0.85f)
        assertEquals("back to rest", rest.toFloat(), after.toFloat(), rest * 0.005f)
    }

    @Test
    fun aMaterialButtonGivesToo() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            GestureVolumeTheme(darkTheme = true) {
                Box(Modifier.size(300.dp).background(Color.Black).testTag("stage"), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {},
                        modifier = Modifier.size(200.dp, 60.dp).testTag("button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.Red),
                    ) { Text("") }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val rest = redPixels("stage")
        rule.onNodeWithTag("button").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        val pressed = redPixels("stage")
        rule.onNodeWithTag("button").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(2000)
        val after = redPixels("stage")
        assertTrue("pressed $pressed of $rest", pressed < rest * 0.96f)
        assertEquals("back to rest", rest.toFloat(), after.toFloat(), rest * 0.01f)
    }

    @Test
    fun aListPulledPastItsTopFollowsAndSpringsBack() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            GestureVolumeTheme(darkTheme = true) {
                LazyColumn(Modifier.size(300.dp, 400.dp).background(Color.Black).testTag("list")) {
                    items(30) { i ->
                        Box(Modifier.fillMaxWidth().height(60.dp).background(if (i == 0) Color.Red else Color.Blue))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        assertEquals(0, firstRedRow("list"))

        rule.onNodeWithTag("list").performTouchInput {
            down(Offset(centerX, 40f))
            repeat(10) { moveBy(Offset(0f, 40f)) }
        }
        rule.mainClock.advanceTimeBy(100)
        val pulled = firstRedRow("list")
        // 400 px of finger, less the touch slop, followed at under a half and less as it goes.
        assertTrue("pulled to $pulled", pulled in 40..220)

        rule.onNodeWithTag("list").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(3000)
        assertEquals("back at the top", 0, firstRedRow("list"))
    }
}
