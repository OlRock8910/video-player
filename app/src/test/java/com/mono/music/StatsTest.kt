package com.mono.music

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The listening statistics. Stats deliberately holds no Android types, so all of
 * it runs on a plain JVM with no emulator.
 *
 * Times are built relative to a fixed midday "now" rather than to the wall
 * clock, so the assertions do not change meaning depending on when or where the
 * suite runs.
 */
class StatsTest {

    private val now: Long = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.MARCH)
        set(Calendar.DAY_OF_MONTH, 12)
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val day = 86_400_000L
    private val minute = 60_000L

    private val library = listOf(
        TrackRef("primary:Music/a.mp3", "Angel", "Massive Attack"),
        TrackRef("primary:Music/b.mp3", "Teardrop", "Massive Attack"),
        TrackRef("primary:Music/c.mp3", "Glory Box", "Portishead"),
    )

    private fun summarise(plays: List<Play>, period: String = "week"): StatsSummary {
        val range = Stats.rangeFor(period, plays, now)
        return Stats.summarise(plays, library, range.from, range.to, range.grain)
    }

    @Test
    fun `span reads as hours and minutes`() {
        assertEquals("38 sec", Stats.formatSpan(38_000))
        assertEquals("47 min", Stats.formatSpan(47 * minute))
        assertEquals("1 hr", Stats.formatSpan(60 * minute))
        assertEquals("2 hr 14 min", Stats.formatSpan((2 * 60 + 14) * minute))
        assertEquals("0 sec", Stats.formatSpan(0))
        assertEquals("0 sec", Stats.formatSpan(-5_000))
    }

    @Test
    fun `a week is seven day buckets ending today`() {
        val range = Stats.rangeFor("week", emptyList(), now)
        assertEquals(Grain.DAY, range.grain)
        assertEquals(now, range.to)

        val summary = summarise(emptyList())
        assertEquals(7, summary.buckets.size)
        assertEquals(0L, summary.totalMs)
        assertEquals(0, summary.playCount)
    }

    @Test
    fun `today is bucketed by the hour, zero-padded and in plain digits`() {
        val range = Stats.rangeFor("today", emptyList(), now)
        assertEquals(Grain.HOUR, range.grain)

        val buckets = Stats.summarise(emptyList(), library, range.from, range.to, range.grain).buckets
        // Midnight through midday inclusive.
        assertEquals(13, buckets.size)
        assertEquals("00:00", buckets.first().label)
        assertEquals("09:00", buckets[9].label)
        assertEquals("12:00", buckets.last().label)
    }

    @Test
    fun `a week is labelled by weekday and a month by date`() {
        assertEquals("Thu", summarise(emptyList()).buckets.last().label)
        assertEquals("12 Mar", summarise(emptyList(), "month").buckets.last().label)
        assertEquals("Mar", summarise(emptyList(), "year").buckets.last().label)
    }

    @Test
    fun `all time starts at the month of the earliest play`() {
        val plays = listOf(Play("primary:Music/a.mp3", now - 400 * day, 5 * minute))
        val range = Stats.rangeFor("all", plays, now)
        assertEquals(Grain.MONTH, range.grain)
        assertTrue(range.from <= now - 400 * day)
        // Fourteen months back, inclusive of both ends, is fourteen columns.
        assertEquals(14, summarise(plays, "all").buckets.size)
    }

    @Test
    fun `totals add up and plays outside the window are left out`() {
        val plays = listOf(
            Play("primary:Music/a.mp3", now - 2 * day, 10 * minute),
            Play("primary:Music/b.mp3", now - 2 * day, 4 * minute),
            Play("primary:Music/a.mp3", now - 1 * day, 6 * minute),
            // A month old: inside "all time", outside a week.
            Play("primary:Music/c.mp3", now - 30 * day, 90 * minute),
        )

        val week = summarise(plays)
        assertEquals(20 * minute, week.totalMs)
        assertEquals(3, week.playCount)
        assertEquals(2, week.uniqueCount)
        assertEquals(2, week.activeDays)

        assertEquals(110 * minute, summarise(plays, "all").totalMs)
    }

    @Test
    fun `each play lands in the bucket its start falls in`() {
        val plays = listOf(
            Play("primary:Music/a.mp3", now - 2 * day, 10 * minute),
            Play("primary:Music/b.mp3", now, 3 * minute),
        )
        val buckets = summarise(plays).buckets

        // Seven buckets, oldest first: two days ago is index 4, today is 6.
        assertEquals(10 * minute, buckets[4].ms)
        assertEquals(0L, buckets[5].ms)
        assertEquals(3 * minute, buckets[6].ms)
        assertEquals(plays.sumOf { it.ms }, buckets.sumOf { it.ms })
    }

    @Test
    fun `top songs are ranked by time listened`() {
        val plays = listOf(
            // Played more often, listened to less.
            Play("primary:Music/a.mp3", now - day, 1 * minute),
            Play("primary:Music/a.mp3", now - day, 1 * minute),
            Play("primary:Music/a.mp3", now - day, 1 * minute),
            Play("primary:Music/b.mp3", now - day, 20 * minute),
        )
        val top = summarise(plays).topTracks

        assertEquals("Teardrop", top[0].title)
        assertEquals(20 * minute, top[0].ms)
        assertEquals(1, top[0].count)
        assertEquals("Angel", top[1].title)
        assertEquals(3, top[1].count)
    }

    @Test
    fun `artists gather the time and the plays of their tracks`() {
        val plays = listOf(
            Play("primary:Music/a.mp3", now - day, 5 * minute),
            Play("primary:Music/b.mp3", now - day, 7 * minute),
            Play("primary:Music/c.mp3", now - day, 4 * minute),
        )
        val artists = summarise(plays).topArtists

        assertEquals(2, artists.size)
        assertEquals("Massive Attack", artists[0].artist)
        assertEquals(12 * minute, artists[0].ms)
        assertEquals(2, artists[0].count)
        assertEquals("Portishead", artists[1].artist)
    }

    @Test
    fun `a track no longer in the folder still counts, under its file name`() {
        val plays = listOf(Play("primary:Music/Old/deleted.mp3", now - day, 8 * minute))
        val top = summarise(plays).topTracks

        assertEquals(1, top.size)
        assertEquals("deleted.mp3", top[0].title)
        assertEquals("Unknown artist", top[0].artist)
        assertEquals(8 * minute, top[0].ms)
    }

    @Test
    fun `only the busiest five are reported`() {
        val plays = (1..9).map { Play("primary:Music/$it.mp3", now - day, it * minute) }
        val top = summarise(plays).topTracks

        assertEquals(5, top.size)
        assertEquals(9 * minute, top[0].ms)
        assertEquals(5 * minute, top[4].ms)
    }
}
