package com.subnexa.app.domain.edit

import com.subnexa.app.domain.model.SubtitleEntry

/**
 * Pure helpers for changing subtitle lines. They never touch state, time or Android,
 * so the editor view model only has to decide *when* to call them.
 */
object SubtitleEditing {

  /** A line can never be made shorter than this by nudging or shifting. */
  const val MIN_DURATION_MS = 100L

  data class ShiftResult(
    val entries: List<SubtitleEntry>,
    /** How many lines would have started before 00:00:00.000 and were held there. */
    val clampedCount: Int
  )

  /** Moves every line by [offsetMs]. Lines are never pushed before 00:00:00.000. */
  fun shiftAll(entries: List<SubtitleEntry>, offsetMs: Long): ShiftResult {
    val clampedCount = entries.count { it.startMs + offsetMs < 0L }
    val shifted = entries.map { entry ->
      val newStart = (entry.startMs + offsetMs).coerceAtLeast(0L)
      val duration = (entry.endMs - entry.startMs).coerceAtLeast(MIN_DURATION_MS)
      val newEnd = (entry.endMs + offsetMs).coerceAtLeast(newStart + duration)
      entry.copy(startMs = newStart, endMs = newEnd)
    }.sortedBy { it.startMs }
    return ShiftResult(shifted, clampedCount)
  }

  /** A copy of [entry] placed right after it, with the same text and a duration of at least 0.5s. */
  fun duplicate(entry: SubtitleEntry, newId: Long): SubtitleEntry {
    val duration = (entry.endMs - entry.startMs).coerceAtLeast(500L)
    val newStart = entry.endMs + 50L
    return SubtitleEntry(
      id = newId,
      startMs = newStart,
      endMs = newStart + duration,
      text = entry.text
    )
  }

  /**
   * Splits [entry] at [playheadMs] into two lines. The first keeps the id of [entry];
   * the second gets [newId]. The words are divided evenly between the two.
   * The playhead must lie strictly inside the line.
   */
  fun split(entry: SubtitleEntry, playheadMs: Long, newId: Long): Pair<SubtitleEntry, SubtitleEntry> {
    require(playheadMs > entry.startMs && playheadMs < entry.endMs) {
      "Playhead must be inside the subtitle to split"
    }
    val words = entry.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val midWordIndex = (words.size / 2).coerceAtLeast(1)
    val firstText = words.take(midWordIndex).joinToString(" ").ifEmpty { entry.text.trim() }
    val secondText = words.drop(midWordIndex).joinToString(" ").ifEmpty { "..." }

    val firstHalf = entry.copy(endMs = playheadMs, text = firstText)
    val secondHalf = SubtitleEntry(
      id = newId,
      startMs = playheadMs,
      endMs = entry.endMs,
      text = secondText
    )
    return firstHalf to secondHalf
  }

  /** Joins [entry] and [next] into one line that keeps the id and start of [entry]. */
  fun merge(entry: SubtitleEntry, next: SubtitleEntry): SubtitleEntry =
    entry.copy(
      endMs = maxOf(entry.endMs, next.endMs),
      text = "${entry.text.trim()}\n${next.text.trim()}"
    )

  /** New (start, end) after moving only the start of [entry] by [deltaMs]. */
  fun nudgeStart(entry: SubtitleEntry, deltaMs: Long): Pair<Long, Long> {
    val maxStart = (entry.endMs - MIN_DURATION_MS).coerceAtLeast(0L)
    val newStart = (entry.startMs + deltaMs).coerceIn(0L, maxStart)
    return newStart to entry.endMs
  }

  /** New (start, end) after moving only the end of [entry] by [deltaMs]. */
  fun nudgeEnd(entry: SubtitleEntry, deltaMs: Long): Pair<Long, Long> {
    val newEnd = (entry.endMs + deltaMs).coerceAtLeast(entry.startMs + MIN_DURATION_MS)
    return entry.startMs to newEnd
  }

  /** New (start, end) after moving the whole of [entry] by [deltaMs], never before 0. */
  fun nudgeBoth(entry: SubtitleEntry, deltaMs: Long): Pair<Long, Long> {
    val shift = maxOf(deltaMs, -entry.startMs)
    return (entry.startMs + shift) to (entry.endMs + shift)
  }
}
