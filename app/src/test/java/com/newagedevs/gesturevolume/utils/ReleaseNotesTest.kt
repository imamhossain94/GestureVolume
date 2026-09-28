package com.newagedevs.gesturevolume.utils

import com.newagedevs.gesturevolume.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The What's new history, and the guard that keeps it in step with the version. */
class ReleaseNotesTest {

    @Test
    fun `the version being built has notes`() {
        // Fails the build that bumps versionName without writing what the release changes.
        assertNotNull(BuildConfig.VERSION_NAME, ReleaseNotes.releaseFor(BuildConfig.VERSION_NAME))
    }

    @Test
    fun `the newest release is first`() {
        val newest = ReleaseNotes.releases.first()
        assertEquals(ReleaseNotes.normalize(BuildConfig.VERSION_NAME), newest.version)
    }

    @Test
    fun `releases run newest to oldest, each once, each with notes`() {
        val releases = ReleaseNotes.releases
        assertEquals(releases.size, releases.map { it.version }.toSet().size)
        releases.forEach { assertTrue(it.version, it.notes.isNotEmpty()) }
        val versions = releases.map { v -> v.version.split('.').map { it.toInt() } }
        versions.zipWithNext().forEach { (newer, older) ->
            assertTrue("$newer before $older", compareVersions(newer, older) > 0)
        }
        // Dated entries, milestones included, never go forward in time down the list.
        val dates = ReleaseNotes.HISTORY.mapNotNull {
            when (it) {
                is ReleaseNotes.Release -> it.date
                is ReleaseNotes.Milestone -> it.date
            }
        }
        dates.zipWithNext().forEach { (newer, older) -> assertTrue("$newer before $older", !newer.isBefore(older)) }
    }

    @Test
    fun `only the oldest release is the first one`() {
        val first = ReleaseNotes.releases.filter { it.firstRelease }
        assertEquals(listOf(ReleaseNotes.releases.last()), first)
    }

    @Test
    fun `a build suffix is not part of the version`() {
        assertEquals("1.5.1", ReleaseNotes.normalize("1.5.1-sideload"))
        assertEquals("1.5.0", ReleaseNotes.normalize("1.5.0"))
    }

    @Test
    fun `a leading emoji is split off, and a plain note keeps its words`() {
        assertEquals("🎚" to "Quick slider", ReleaseNotes.splitEmoji("🎚 Quick slider"))
        assertEquals("⚒️" to "Bug fixes", ReleaseNotes.splitEmoji("⚒️ Bug fixes"))
        val plain = ReleaseNotes.splitEmoji("Bug fixes and policy compliance update")
        assertNull(plain.first)
        assertEquals("Bug fixes and policy compliance update", plain.second)
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
