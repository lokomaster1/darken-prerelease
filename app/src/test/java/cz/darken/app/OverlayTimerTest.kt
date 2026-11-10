package cz.darken.app

import cz.darken.app.timer.OverlayTimer
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class OverlayTimerTest {
    private val prague = ZoneId.of("Europe/Prague")

    @Test fun schedulesTodayBeforeTheSelectedTime() {
        val timer = OverlayTimer(hour = 7)
        assertEquals(Instant.parse("2026-11-09T06:00:00Z").toEpochMilli(),
            timer.nextOccurrence(Instant.parse("2026-11-09T05:00:00Z"), prague))
    }

    @Test fun selectingAnElapsedTimeSchedulesTomorrow() {
        val timer = OverlayTimer(hour = 7)
        assertEquals(Instant.parse("2026-11-10T06:00:00Z").toEpochMilli(),
            timer.nextOccurrence(Instant.parse("2026-11-09T06:00:00Z"), prague))
    }

    @Test fun springGapUsesFirstValidTime() {
        val timer = OverlayTimer(hour = 2, minute = 30)
        assertEquals(Instant.parse("2026-03-29T01:00:00Z").toEpochMilli(),
            timer.nextOccurrence(Instant.parse("2026-03-28T23:00:00Z"), prague))
    }

    @Test fun autumnOverlapUsesFirstOccurrence() {
        val timer = OverlayTimer(hour = 2, minute = 30)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(),
            timer.nextOccurrence(Instant.parse("2026-10-24T22:00:00Z"), prague))
    }

    @Test fun autumnOverlapDoesNotScheduleSecondOccurrence() {
        val timer = OverlayTimer(hour = 2, minute = 30)
        assertEquals(Instant.parse("2026-10-26T01:30:00Z").toEpochMilli(),
            timer.nextOccurrence(Instant.parse("2026-10-25T00:45:00Z"), prague))
    }

    @Test fun oneShotTurnsItselfOffAndKeepsUserTime() {
        val timer = OverlayTimer(enabled = true, hour = 7, minute = 15, repeatDaily = false,
            nextTriggerAt = Instant.parse("2026-11-09T06:15:00Z").toEpochMilli())
        val result = timer.afterFiring(Instant.parse("2026-11-09T06:15:01Z"), prague)
        assertFalse(result.enabled)
        assertEquals(0L, result.nextTriggerAt)
        assertEquals(7, result.hour)
        assertEquals(15, result.minute)
        assertFalse(result.repeatDaily)
    }

    @Test fun delayedDailyAlarmSkipsMissedDaysWithoutRepeatedShutdowns() {
        val timer = OverlayTimer(enabled = true, repeatDaily = true,
            nextTriggerAt = Instant.parse("2026-11-07T06:00:00Z").toEpochMilli())
        val result = timer.afterFiring(Instant.parse("2026-11-09T09:00:00Z"), prague)
        assertTrue(result.enabled)
        assertEquals(Instant.parse("2026-11-10T06:00:00Z").toEpochMilli(), result.nextTriggerAt)
    }

    @Test fun clockRollbackDoesNotRepeatAnAlreadyFiredDay() {
        val timer = OverlayTimer(lastFiredDate = "2026-11-09")
        assertEquals(Instant.parse("2026-11-10T06:00:00Z").toEpochMilli(),
            timer.nextOccurrence(Instant.parse("2026-11-09T05:00:00Z"), prague))
    }

    @Test fun dailyTimeFollowsNewTimeZone() {
        val timer = OverlayTimer()
        assertEquals(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(),
            timer.nextOccurrence(Instant.parse("2026-11-09T05:00:00Z"), ZoneId.of("America/New_York")))
    }

    @Test fun equalEnabledTimesConflictButDisabledTimersDoNot() {
        val stop = OverlayTimer(enabled = true, hour = 7, minute = 30)
        val start = OverlayTimer(enabled = true, hour = 7, minute = 30)
        assertTrue(start.conflictsWith(stop))
        assertFalse(start.copy(enabled = false).conflictsWith(stop))
        assertFalse(start.conflictsWith(stop.copy(enabled = false)))
        assertFalse(start.copy(minute = 31).conflictsWith(stop))
    }

    @Test fun startupAcceptsNormalAlarmDeliveryJitter() {
        val due = Instant.parse("2026-11-10T19:00:00Z")
        val timer = OverlayTimer(enabled = true, hour = 20, nextTriggerAt = due.toEpochMilli())
        assertTrue(timer.acceptsStartupAt(due))
        assertTrue(timer.acceptsStartupAt(due.plusSeconds(299)))
        assertFalse(timer.acceptsStartupAt(due.minusSeconds(1)))
    }

    @Test fun anOldStartupMustNotDimTheNextMorning() {
        val due = Instant.parse("2026-11-10T19:00:00Z")
        val timer = OverlayTimer(enabled = true, hour = 20, repeatDaily = true, nextTriggerAt = due.toEpochMilli())
        assertFalse(timer.acceptsStartupAt(due.plusSeconds(301)))
        assertFalse(timer.acceptsStartupAt(due.plusSeconds(12 * 3600)))
        val next = timer.afterFiring(due.plusSeconds(12 * 3600), prague)
        assertEquals(Instant.parse("2026-11-11T19:00:00Z").toEpochMilli(), next.nextTriggerAt)
    }

    @Test fun aDisabledOrUnscheduledTimerIsNeverDue() {
        val now = Instant.parse("2026-11-10T19:00:00Z")
        assertFalse(OverlayTimer(enabled = false, nextTriggerAt = now.toEpochMilli()).isDue(now))
        assertFalse(OverlayTimer(enabled = true).isDue(now))
    }
}
