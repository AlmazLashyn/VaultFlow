package com.vaultflow.worker

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimingTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val expiration = LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun delayedWorkUsesNonOverlappingSelectedReminderWindows() {
        assertTrue(ReminderTiming.shouldNotify(expiration, 30, setOf(30, 7, 1), LocalDate.of(2026, 11, 12), zone))
        assertFalse(ReminderTiming.shouldNotify(expiration, 30, setOf(30, 7, 1), LocalDate.of(2026, 11, 13), zone))
        assertTrue(ReminderTiming.shouldNotify(expiration, 7, setOf(30, 7, 1), LocalDate.of(2026, 11, 13), zone))
        assertTrue(ReminderTiming.shouldNotify(expiration, 1, setOf(30, 7, 1), LocalDate.of(2026, 11, 20), zone))
        assertFalse(ReminderTiming.shouldNotify(expiration, 1, setOf(30, 7, 1), LocalDate.of(2026, 11, 21), zone))
    }

    @Test fun triggerUsesLocalStartOfDayAcrossDaylightSavingChanges() {
        val dueDate = LocalDate.of(2026, 3, 10)
        val dueMillis = dueDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val trigger = ReminderTiming.triggerAt(dueMillis, 1, zone)
        assertEquals(dueDate.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), trigger)
    }
}
