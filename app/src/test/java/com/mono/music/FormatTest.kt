package com.mono.music

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The clock under the seek bar and the totals on list headers. Both are pure
 * arithmetic, so they run on a plain JVM with no emulator.
 */
class FormatTest {

    @Test
    fun `clock pads seconds`() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:07", formatClock(7_000))
        assertEquals("3:51", formatClock(231_000))
        assertEquals("10:00", formatClock(600_000))
    }

    @Test
    fun `clock never shows a negative position`() {
        assertEquals("0:00", formatClock(-5_000))
    }

    @Test
    fun `clock rounds down to the second in progress`() {
        assertEquals("1:00", formatClock(60_999))
    }

    @Test
    fun `long form drops the hour when there is none`() {
        assertEquals("0 min", formatLong(0))
        assertEquals("46 min", formatLong(46 * 60_000L))
        assertEquals("3 hr 46 min", formatLong((3 * 3600 + 46 * 60) * 1000L))
    }

    @Test
    fun `audio files are recognised by extension regardless of case`() {
        assertEquals(true, isAudio("Bad Habit.mp3"))
        assertEquals(true, isAudio("Borderline.FLAC"))
        assertEquals(false, isAudio("cover.jpg"))
        assertEquals(false, isAudio("no-extension"))
    }
}
