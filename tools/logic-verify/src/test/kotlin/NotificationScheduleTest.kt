import com.dadsvictory.domain.NotificationSchedule
import com.dadsvictory.domain.NotificationSlot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationScheduleTest {

    private val london = ZoneId.of("Europe/London")

    private fun at(text: String, zone: ZoneId = london): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun readable(millis: Long, zone: ZoneId = london): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().toString()

    @Test
    fun `a time later today fires today`() {
        val next = NotificationSchedule.nextTriggerMillis(8 * 60, at("2026-03-10T06:00:00"), london)
        assertEquals("2026-03-10T08:00", readable(next))
    }

    @Test
    fun `a time already gone fires tomorrow`() {
        val next = NotificationSchedule.nextTriggerMillis(8 * 60, at("2026-03-10T09:30:00"), london)
        assertEquals("2026-03-11T08:00", readable(next))
    }

    @Test
    fun `firing exactly on the minute rolls to tomorrow rather than firing twice`() {
        val next = NotificationSchedule.nextTriggerMillis(8 * 60, at("2026-03-10T08:00:00"), london)
        assertEquals("2026-03-11T08:00", readable(next))
    }

    @Test
    fun `the next trigger is always in the future`() {
        val now = at("2026-06-15T13:59:59")
        for (slot in NotificationSlot.entries) {
            val next = NotificationSchedule.nextTriggerMillis(slot.defaultMinuteOfDay, now, london)
            assertTrue(next > now, "${slot.id} scheduled in the past")
        }
    }

    /**
     * The UK clocks go forward at 01:00 on 29 March 2026 and back on 25 October.
     * An 08:00 notification must still be at 08:00 local time on both days, which
     * is why the calculation goes through the calendar rather than adding 24 hours.
     */
    @Test
    fun `an 8am alarm is still 8am local across the spring clock change`() {
        val next = NotificationSchedule.nextTriggerMillis(8 * 60, at("2026-03-28T09:00:00"), london)
        assertEquals("2026-03-29T08:00", readable(next))
    }

    @Test
    fun `an 8am alarm is still 8am local across the autumn clock change`() {
        val next = NotificationSchedule.nextTriggerMillis(8 * 60, at("2026-10-24T09:00:00"), london)
        assertEquals("2026-10-25T08:00", readable(next))
    }

    @Test
    fun `a time that does not exist on a clock-change day still produces a valid instant`() {
        // 01:30 does not exist on 29 March 2026 in London.
        val next = NotificationSchedule.nextTriggerMillis(90, at("2026-03-29T00:30:00"), london)
        assertTrue(next > at("2026-03-29T00:30:00"))
        assertTrue(next < at("2026-03-30T00:00:00"), "should still fire on the same day, not skip it")
    }

    @Test
    fun `midnight and one minute to midnight are both valid`() {
        assertEquals("2026-05-02T00:00", readable(NotificationSchedule.nextTriggerMillis(0, at("2026-05-01T10:00:00"), london)))
        assertEquals("2026-05-01T23:59", readable(NotificationSchedule.nextTriggerMillis(23 * 60 + 59, at("2026-05-01T10:00:00"), london)))
    }

    @Test
    fun `an out-of-range minute is clamped instead of crashing`() {
        val next = NotificationSchedule.nextTriggerMillis(99_999, at("2026-05-01T10:00:00"), london)
        assertTrue(next > at("2026-05-01T10:00:00"))
    }

    @Test
    fun `the rotation index advances once a day, in local time`() {
        val a = NotificationSchedule.rotationIndex(at("2026-05-01T23:59:00"), london)
        val b = NotificationSchedule.rotationIndex(at("2026-05-02T00:01:00"), london)
        assertEquals(a + 1, b)
    }

    @Test
    fun `the greeting matches the time of day`() {
        assertTrue(NotificationSchedule.greeting(at("2026-05-01T08:00:00"), london).startsWith("Good morning"))
        assertTrue(NotificationSchedule.greeting(at("2026-05-01T14:00:00"), london).startsWith("Good afternoon"))
        assertTrue(NotificationSchedule.greeting(at("2026-05-01T21:00:00"), london).startsWith("Good evening"))
        assertTrue(NotificationSchedule.greeting(at("2026-05-01T00:30:00"), london).startsWith("Good morning"))
    }

    @Test
    fun `times are formatted for both 12 and 24 hour phones`() {
        assertEquals("8:00 AM", NotificationSchedule.formatTime(8 * 60, use24Hour = false))
        assertEquals("08:00", NotificationSchedule.formatTime(8 * 60, use24Hour = true))
        assertEquals("2:00 PM", NotificationSchedule.formatTime(14 * 60, use24Hour = false))
        assertEquals("14:00", NotificationSchedule.formatTime(14 * 60, use24Hour = true))
        assertEquals("12:00 AM", NotificationSchedule.formatTime(0, use24Hour = false))
        assertEquals("12:30 PM", NotificationSchedule.formatTime(12 * 60 + 30, use24Hour = false))
        assertEquals("11:59 PM", NotificationSchedule.formatTime(23 * 60 + 59, use24Hour = false))
    }

    @Test
    fun `the three default slots are the ones the brief asked for`() {
        assertEquals(8 * 60, NotificationSlot.MORNING.defaultMinuteOfDay)
        assertEquals(14 * 60, NotificationSlot.AFTERNOON.defaultMinuteOfDay)
        assertEquals(20 * 60, NotificationSlot.EVENING.defaultMinuteOfDay)
        assertEquals(3, NotificationSlot.entries.size, "exactly three a day, no more")
    }
}
