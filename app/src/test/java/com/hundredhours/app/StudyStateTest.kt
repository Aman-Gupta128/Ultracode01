package com.hundredhours.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyStateTest {
    private val t0 = 1_700_000_000_000L

    @Test
    fun startingWeekStartsTimerAndCountsDown() {
        val s = StudyState().startWeek(t0, 100)
        assertTrue(s.isRunning)
        assertEquals(100 * HOUR_MS, s.remainingMs(t0))
        assertEquals(99 * HOUR_MS, s.remainingMs(t0 + HOUR_MS))
        assertEquals("99:00:00", formatHms(s.remainingMs(t0 + HOUR_MS)))
    }

    @Test
    fun pauseAndResumeOnlyCountsStudyTime() {
        var s = StudyState().startWeek(t0, 100)
        s = s.pause(t0 + 2 * HOUR_MS)
        assertFalse(s.isRunning)
        // Paused for 5 hours: nothing changes.
        assertEquals(98 * HOUR_MS, s.remainingMs(t0 + 7 * HOUR_MS))
        s = s.start(t0 + 7 * HOUR_MS)
        assertEquals(97 * HOUR_MS, s.remainingMs(t0 + 8 * HOUR_MS))
    }

    @Test
    fun timeAfterWeekEndDoesNotCount() {
        var s = StudyState().startWeek(t0, 100)
        s = s.pause(t0 + HOUR_MS).start(t0 + WEEK_MS - HOUR_MS)
        val later = t0 + WEEK_MS + 5 * HOUR_MS
        assertEquals(2 * HOUR_MS, s.studiedMs(later))
        s = s.settle(later)
        assertFalse(s.isRunning)
        assertEquals(t0 + WEEK_MS, s.sessions.last().end)
        assertTrue(s.isWeekOver(later))
        assertEquals(s, s.start(later)) // can't start once the week is over
    }

    @Test
    fun remainingNeverNegativeAndExtraIsTracked() {
        val s = StudyState().startWeek(t0, 2)
        assertEquals(0, s.remainingMs(t0 + 3 * HOUR_MS))
        assertEquals(HOUR_MS, s.extraMs(t0 + 3 * HOUR_MS))
    }

    @Test
    fun dailyBarsSplitAcrossMidnight() {
        var s = StudyState().startWeek(t0, 100).pause(t0) // no time
        s = s.start(t0 + DAY_MS - HOUR_MS).pause(t0 + DAY_MS + 2 * HOUR_MS)
        val daily = s.dailyMs(t0 + 2 * DAY_MS)
        assertEquals(HOUR_MS, daily[0])
        assertEquals(2 * HOUR_MS, daily[1])
        assertEquals(0, daily[2])
    }

    @Test
    fun undoAndAdjust() {
        var s = StudyState().startWeek(t0, 100).pause(t0 + HOUR_MS)
        s = s.start(t0 + 2 * HOUR_MS).pause(t0 + 4 * HOUR_MS)
        assertEquals(3 * HOUR_MS, s.studiedMs(t0 + 5 * HOUR_MS))
        s = s.undoLastSession()
        assertEquals(HOUR_MS, s.studiedMs(t0 + 5 * HOUR_MS))
        s = s.adjust(t0 + 5 * HOUR_MS, 30 * MINUTE_MS)
        assertEquals(90 * MINUTE_MS, s.studiedMs(t0 + 5 * HOUR_MS))
        s = s.adjust(t0 + 5 * HOUR_MS, -10 * HOUR_MS)
        assertEquals(0, s.studiedMs(t0 + 5 * HOUR_MS)) // never below zero
    }

    @Test
    fun paceIsRemainingOverDaysLeft() {
        val s = StudyState().startWeek(t0, 70).pause(t0)
        assertEquals(10 * HOUR_MS, s.paceMsPerDay(t0))
        assertNull(s.paceMsPerDay(t0 + WEEK_MS))
    }

    @Test
    fun formatting() {
        assertEquals("100:00:00", formatHms(100 * HOUR_MS))
        assertEquals("4d 6h", formatShort(4 * DAY_MS + 6 * HOUR_MS + 5 * MINUTE_MS))
        assertEquals("5h 12m", formatShort(5 * HOUR_MS + 12 * MINUTE_MS))
        assertEquals("12m", formatShort(12 * MINUTE_MS))
    }
}
