package cz.darken.app.timer

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Persistent user choice and the next occurrence, independent of the overlay lifecycle. */
data class OverlayTimer(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val repeatDaily: Boolean = false,
    val nextTriggerAt: Long = 0,
    val lastFiredDate: String = "",
) {
    fun isDue(now: Instant): Boolean = enabled && nextTriggerAt > 0 && nextTriggerAt <= now.toEpochMilli()

    fun conflictsWith(other: OverlayTimer): Boolean =
        enabled && other.enabled && hour == other.hour && minute == other.minute

    /** Allow delivery jitter, but never resurrect a long-expired startup after a night asleep. */
    fun acceptsStartupAt(now: Instant): Boolean =
        isDue(now) && now.toEpochMilli() - nextTriggerAt <= 5 * 60 * 1000L

    fun nextOccurrence(now: Instant, zone: ZoneId): Long {
        var date = now.atZone(zone).toLocalDate()
        while (true) {
            val local = LocalDateTime.of(date, LocalTime.of(hour, minute))
            val offsets = zone.rules.getValidOffsets(local)
            // On a spring clock change use the first valid time after the gap.
            val candidate = if (offsets.isEmpty()) {
                zone.rules.getTransition(local)!!.instant
            } else {
                // On an autumn clock change use only the first occurrence.
                local.toInstant(offsets.first())
            }
            if (candidate.isAfter(now) && date.toString() != lastFiredDate) {
                return candidate.toEpochMilli()
            }
            date = date.plusDays(1)
        }
    }

    fun afterFiring(now: Instant, zone: ZoneId): OverlayTimer {
        val firedDate = Instant.ofEpochMilli(nextTriggerAt).atZone(zone).toLocalDate()
        val updated = copy(
            enabled = repeatDaily,
            nextTriggerAt = 0,
            lastFiredDate = firedDate.toString(),
        )
        return if (repeatDaily) updated.copy(nextTriggerAt = updated.nextOccurrence(now, zone)) else updated
    }
}
