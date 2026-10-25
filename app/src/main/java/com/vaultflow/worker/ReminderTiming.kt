package com.vaultflow.worker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object ReminderTiming {
    fun triggerAt(expirationMillis: Long, daysBefore: Int, zone: ZoneId): Long =
        Instant.ofEpochMilli(expirationMillis).atZone(zone).toLocalDate()
            .minusDays(daysBefore.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    fun daysUntil(expirationMillis: Long, today: LocalDate, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(expirationMillis).atZone(zone).toLocalDate())

    fun shouldNotify(expirationMillis: Long, daysBefore: Int, selectedIntervals: Set<Int>, today: LocalDate, zone: ZoneId): Boolean {
        val remaining = daysUntil(expirationMillis, today, zone)
        val nextInterval = selectedIntervals.filter { it < daysBefore }.maxOrNull() ?: -1
        return remaining in (nextInterval + 1)..daysBefore.toLong()
    }
}
