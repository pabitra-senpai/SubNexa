package com.subnexa.app.domain.sync

import com.subnexa.app.domain.model.SubtitleEntry
import kotlin.math.roundToLong

data class TwoPointSyncResult(
  val entries: List<SubtitleEntry>,
  /** New time per old time: 1.0 means no stretching, above 1.0 the subtitles get slower. */
  val scale: Double,
  /** How many lines would have started before 00:00:00.000 and were held at 0. */
  val clampedCount: Int
)

/**
 * Fixes subtitles that start in sync but drift apart later. The user picks two lines and says
 * when each should start; every start and end time is then mapped linearly, so the first
 * anchor lands exactly on its new time, the second anchor lands exactly on its new time, and
 * everything in between (and around) is shifted and stretched to match.
 */
object TwoPointSync {

  /**
   * @return null when the anchors are unusable: a line is missing, the two lines start at the
   * same time, or the new times are not in the same order as the old ones.
   */
  fun apply(
    entries: List<SubtitleEntry>,
    firstId: Long,
    firstNewStartMs: Long,
    lastId: Long,
    lastNewStartMs: Long
  ): TwoPointSyncResult? {
    val first = entries.firstOrNull { it.id == firstId } ?: return null
    val last = entries.firstOrNull { it.id == lastId } ?: return null
    val oldSpan = last.startMs - first.startMs
    val newSpan = lastNewStartMs - firstNewStartMs
    if (oldSpan <= 0L || newSpan <= 0L || firstNewStartMs < 0L) return null

    val scale = newSpan.toDouble() / oldSpan.toDouble()
    fun mapTime(t: Long): Long = firstNewStartMs + ((t - first.startMs) * scale).roundToLong()

    var clamped = 0
    val shifted = entries.map { entry ->
      val mappedStart = mapTime(entry.startMs)
      val mappedEnd = mapTime(entry.endMs)
      val newStart = mappedStart.coerceAtLeast(0L)
      val newEnd = if (mappedStart >= 0L) {
        maxOf(mappedEnd, newStart + 1L)
      } else {
        clamped++
        val duration = ((entry.endMs - entry.startMs) * scale).roundToLong().coerceAtLeast(100L)
        maxOf(mappedEnd, newStart + duration)
      }
      entry.copy(startMs = newStart, endMs = newEnd)
    }.sortedBy { it.startMs }

    return TwoPointSyncResult(entries = shifted, scale = scale, clampedCount = clamped)
  }
}
