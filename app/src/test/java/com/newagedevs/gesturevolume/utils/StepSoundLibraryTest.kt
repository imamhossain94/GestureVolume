package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The step sound is one stored string that names either one of the app's own sounds or a sound on
 * the phone. These pin down that a value is read back as what it names, and that anything else —
 * a sound a newer build added, a cloud restore, garbage — falls back to the tick rather than to
 * silence or a crash.
 */
class StepSoundLibraryTest {

    @Test
    fun `the tick is the default and comes first`() {
        assertEquals(StepSoundLibrary.TICK, StepSoundLibrary.DEFAULT)
        assertEquals(StepSoundLibrary.TICK, StepSoundLibrary.IDS.first())
    }

    @Test
    fun `every sound of the app's own is kept as it is`() {
        StepSoundLibrary.IDS.forEach { assertEquals(it, StepSoundLibrary.sanitize(it)) }
        assertEquals(StepSoundLibrary.IDS.size, StepSoundLibrary.IDS.toSet().size)
    }

    @Test
    fun `an unknown or missing sound is the tick`() {
        assertEquals(StepSoundLibrary.DEFAULT, StepSoundLibrary.sanitize(null))
        assertEquals(StepSoundLibrary.DEFAULT, StepSoundLibrary.sanitize(""))
        assertEquals(StepSoundLibrary.DEFAULT, StepSoundLibrary.sanitize("laser"))
    }

    @Test
    fun `a sound from the phone round-trips through its stored form`() {
        val uri = "content://media/internal/audio/media/42?title=Pixie%20Dust"
        val stored = StepSoundLibrary.device(uri)
        assertTrue(StepSoundLibrary.isDevice(stored))
        assertEquals(stored, StepSoundLibrary.sanitize(stored))
        assertEquals(uri, StepSoundLibrary.deviceUri(stored))
    }

    @Test
    fun `a sound from the phone with no uri is the tick`() {
        assertEquals(StepSoundLibrary.DEFAULT, StepSoundLibrary.sanitize(StepSoundLibrary.DEVICE_PREFIX))
        assertEquals(StepSoundLibrary.DEFAULT, StepSoundLibrary.sanitize(StepSoundLibrary.DEVICE_PREFIX + "  "))
    }

    @Test
    fun `the app's own sounds name no uri`() {
        StepSoundLibrary.IDS.forEach {
            assertFalse(StepSoundLibrary.isDevice(it))
            assertNull(StepSoundLibrary.deviceUri(it))
        }
    }
}
