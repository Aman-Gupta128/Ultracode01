package com.hundredhours.app

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS
const val WEEK_MS = 7 * DAY_MS
const val DEFAULT_GOAL_HOURS = 100

/** A finished stretch of studying, in epoch milliseconds. */
data class Session(val start: Long, val end: Long) {
    val durationMs: Long get() = end - start
}

/** A manual correction: [deltaMs] added (positive) or removed (negative) at time [at]. */
data class Adjustment(val at: Long, val deltaMs: Long)

/**
 * Everything the app knows. Pure data plus pure functions, so all timer maths is unit-testable.
 * Time only counts inside the 7-day window [weekStart, weekEnd).
 */
data class StudyState(
    val weekStart: Long? = null,
    val goalMs: Long = DEFAULT_GOAL_HOURS * HOUR_MS,
    val sessions: List<Session> = emptyList(),
    val adjustments: List<Adjustment> = emptyList(),
    val runningSince: Long? = null,
) {
    val hasWeek: Boolean get() = weekStart != null
    val weekEnd: Long? get() = weekStart?.plus(WEEK_MS)
    val isRunning: Boolean get() = runningSince != null

    fun isWeekOver(now: Long): Boolean = weekEnd?.let { now >= it } ?: false

    /** Total counted study time, clipped to the week window, including the running session. */
    fun studiedMs(now: Long): Long {
        val ws = weekStart ?: return 0
        val we = ws + WEEK_MS
        var total = sessions.sumOf { overlap(it.start, it.end, ws, we) }
        runningSince?.let { total += overlap(it, now, ws, we) }
        total += adjustments.sumOf { it.deltaMs }
        return total.coerceAtLeast(0)
    }

    fun remainingMs(now: Long): Long = (goalMs - studiedMs(now)).coerceAtLeast(0)

    /** Time studied beyond the goal (0 until the goal is reached). */
    fun extraMs(now: Long): Long = (studiedMs(now) - goalMs).coerceAtLeast(0)

    fun weekLeftMs(now: Long): Long = weekEnd?.let { (it - now).coerceAtLeast(0) } ?: 0

    /** 0-based index of the current day in the week (0..6). */
    fun dayIndex(now: Long): Int {
        val ws = weekStart ?: return 0
        return ((now - ws) / DAY_MS).toInt().coerceIn(0, 6)
    }

    /** Study hours needed per remaining day to hit the goal; null if nothing is left or the week is over. */
    fun paceMsPerDay(now: Long): Long? {
        val remaining = remainingMs(now)
        val left = weekLeftMs(now)
        if (remaining <= 0 || left <= 0) return null
        val days = left.toDouble() / DAY_MS
        return (remaining / days.coerceAtLeast(1.0 / 24)).toLong()
    }

    /** Study time per day of the week (7 entries), sessions split across midnight boundaries. */
    fun dailyMs(now: Long): LongArray {
        val result = LongArray(7)
        val ws = weekStart ?: return result
        val all = sessions + listOfNotNull(runningSince?.let { Session(it, now) })
        for (day in 0 until 7) {
            val ds = ws + day * DAY_MS
            val de = ds + DAY_MS
            result[day] = all.sumOf { overlap(it.start, it.end, ds, de) }
        }
        for (adj in adjustments) {
            val day = ((adj.at - ws) / DAY_MS).toInt().coerceIn(0, 6)
            result[day] += adj.deltaMs
        }
        for (i in result.indices) result[i] = result[i].coerceAtLeast(0)
        return result
    }

    // ---- Transitions ----

    fun startWeek(now: Long, goalHours: Int): StudyState =
        StudyState(weekStart = now, goalMs = goalHours.coerceIn(1, 168) * HOUR_MS, runningSince = now)

    fun start(now: Long): StudyState =
        if (!hasWeek || isRunning || isWeekOver(now)) this else copy(runningSince = now)

    /** Stops the running session. Anything after the week end is not counted. */
    fun pause(now: Long): StudyState {
        val since = runningSince ?: return this
        val end = minOf(now, weekEnd ?: now)
        val newSessions = if (end > since) sessions + Session(since, end) else sessions
        return copy(sessions = newSessions, runningSince = null)
    }

    /** If the week has ended while running, close the session at the week end. */
    fun settle(now: Long): StudyState = if (isRunning && isWeekOver(now)) pause(now) else this

    /** Throw away the running session without counting it. */
    fun discardRunning(): StudyState = copy(runningSince = null)

    fun undoLastSession(): StudyState =
        if (sessions.isEmpty()) this else copy(sessions = sessions.dropLast(1))

    fun adjust(now: Long, deltaMs: Long): StudyState {
        if (!hasWeek || deltaMs == 0L) return this
        val at = minOf(now, weekEnd!! - 1)
        return copy(adjustments = adjustments + Adjustment(at, deltaMs))
    }

    fun reset(): StudyState = StudyState(goalMs = goalMs)

    companion object {
        fun overlap(a: Long, b: Long, from: Long, to: Long): Long =
            (minOf(b, to) - maxOf(a, from)).coerceAtLeast(0)
    }
}

/** 100:00:00 style. Hours can exceed two digits. */
fun formatHms(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** "4d 6h", "5h 12m" or "12m" style. */
fun formatShort(ms: Long): String {
    val totalMin = ms.coerceAtLeast(0) / MINUTE_MS
    val d = totalMin / (24 * 60)
    val h = (totalMin % (24 * 60)) / 60
    val m = totalMin % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

/** Hours with one decimal, e.g. "14.3". */
fun formatHoursDecimal(ms: Long): String = "%.1f".format(ms.toDouble() / HOUR_MS)
