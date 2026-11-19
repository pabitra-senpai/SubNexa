package com.subnexa.app.domain.edit

import com.subnexa.app.domain.model.SubtitleEntry
import kotlin.math.abs

/**
 * Pure helpers for the timeline view: trimming, moving and snapping a line.
 * They never touch state, time or Android, so they are easy to test.
 */
object TimelineEditing {

  /** A line can never be made shorter than this by dragging. */
  const val MIN_DURATION_MS = SubtitleEditing.MIN_DURATION_MS

  /** Start and end of a line, in milliseconds. */
  data class Span(val startMs: Long, val endMs: Long)

  /**
   * Time points a dragged edge can stick to: the edges of all other lines and the playhead.
   * Built once when a drag starts, not on every frame.
   */
  fun snapTargets(entries: List<SubtitleEntry>, excludeId: Long, playheadMs: Long): LongArray {
    val result = ArrayList<Long>(entries.size * 2 + 1)
    for (e in entries) {
      if (e.id == excludeId) continue
      result += e.startMs
      result += e.endMs
    }
    result += playheadMs
    return result.toLongArray()
  }

  /** The target closest to [valueMs] if it is within [thresholdMs], otherwise [valueMs]. */
  fun snap(valueMs: Long, targets: LongArray, thresholdMs: Long): Long {
    if (thresholdMs <= 0L) return valueMs
    var best = valueMs
    var bestDistance = thresholdMs + 1
    for (t in targets) {
      val d = abs(t - valueMs)
      if (d < bestDistance) {
        best = t
        bestDistance = d
      }
    }
    return if (bestDistance <= thresholdMs) best else valueMs
  }

  /**
   * Drags the start edge to [rawStartMs]. The end never changes. The result keeps at least
   * [MIN_DURATION_MS] and never goes before 00:00:00.000.
   */
  fun trimStart(entry: SubtitleEntry, rawStartMs: Long, targets: LongArray, thresholdMs: Long): Span {
    val snapped = snap(rawStartMs, targets, thresholdMs)
    val start = snapped.coerceIn(0L, (entry.endMs - MIN_DURATION_MS).coerceAtLeast(0L))
    return Span(start, entry.endMs)
  }

  /**
   * Drags the end edge to [rawEndMs]. The start never changes. The result keeps at least
   * [MIN_DURATION_MS]. When [durationMs] is known (> 0) the end stays inside the video.
   */
  fun trimEnd(
    entry: SubtitleEntry,
    rawEndMs: Long,
    targets: LongArray,
    thresholdMs: Long,
    durationMs: Long
  ): Span {
    val snapped = snap(rawEndMs, targets, thresholdMs)
    val minEnd = entry.startMs + MIN_DURATION_MS
    val maxEnd = if (durationMs > 0L) maxOf(durationMs, minEnd) else Long.MAX_VALUE
    return Span(entry.startMs, snapped.coerceIn(minEnd, maxEnd))
  }

  /**
   * Moves the whole line so that it starts at [rawStartMs], keeping its duration. Either the
   * start or the end sticks to the nearest target, whichever is closer. The line stays between
   * 00:00:00.000 and the end of the video (when [durationMs] is known and the line fits).
   */
  fun move(
    entry: SubtitleEntry,
    rawStartMs: Long,
    targets: LongArray,
    thresholdMs: Long,
    durationMs: Long
  ): Span {
    val length = entry.endMs - entry.startMs
    val snappedStart = snap(rawStartMs, targets, thresholdMs)
    val snappedEnd = snap(rawStartMs + length, targets, thresholdMs) - length
    // A side that found nothing to stick to has not moved; only sides that did compete.
    val startSnapped = snappedStart != rawStartMs
    val endSnapped = snappedEnd != rawStartMs
    var start = when {
      startSnapped && endSnapped ->
        if (abs(snappedStart - rawStartMs) <= abs(snappedEnd - rawStartMs)) snappedStart else snappedEnd
      startSnapped -> snappedStart
      endSnapped -> snappedEnd
      else -> rawStartMs
    }
    if (durationMs > 0L && length <= durationMs) {
      start = start.coerceAtMost(durationMs - length)
    }
    start = start.coerceAtLeast(0L)
    return Span(start, start + length)
  }

  /**
   * Gives every line that has no row yet a row, and returns the rows of all lines.
   *
   * Lines already in [existing] keep their row (the user may have dragged them there). A new
   * line goes into the first row where it overlaps nothing, so duplicates and overlapping lines
   * end up in different rows. When every row is busy it goes into the row where it overlaps
   * the least.
   */
  fun fillLanes(entries: List<SubtitleEntry>, existing: Map<Long, Int>, laneCount: Int): Map<Long, Int> {
    val lanes = laneCount.coerceAtLeast(1)
    val result = HashMap<Long, Int>(entries.size * 2)
    val placed = Array(lanes) { ArrayList<SubtitleEntry>() }
    val missing = ArrayList<SubtitleEntry>()
    for (entry in entries) {
      val lane = existing[entry.id]
      if (lane != null && lane in 0 until lanes) {
        result[entry.id] = lane
        placed[lane].add(entry)
      } else {
        missing.add(entry)
      }
    }
    if (missing.isEmpty()) return result

    val ordered = missing.sortedWith(compareBy({ it.startMs }, { it.endMs }, { it.id }))
    if (result.isEmpty()) {
      // Nothing placed yet (first time): a single pass over the sorted lines is enough.
      val laneEnd = LongArray(lanes) { Long.MIN_VALUE }
      for (entry in ordered) {
        var lane = -1
        for (i in 0 until lanes) {
          if (laneEnd[i] <= entry.startMs) {
            lane = i
            break
          }
        }
        if (lane == -1) {
          lane = 0
          for (i in 1 until lanes) if (laneEnd[i] < laneEnd[lane]) lane = i
        }
        laneEnd[lane] = maxOf(laneEnd[lane], entry.endMs)
        result[entry.id] = lane
      }
      return result
    }

    // Some lines were already placed: compare each new line with what is in every row.
    for (entry in ordered) {
      var best = 0
      var bestOverlap = Long.MAX_VALUE
      for (i in 0 until lanes) {
        var overlap = 0L
        for (other in placed[i]) {
          val o = minOf(other.endMs, entry.endMs) - maxOf(other.startMs, entry.startMs)
          if (o > 0L) overlap += o
        }
        if (overlap < bestOverlap) {
          bestOverlap = overlap
          best = i
          if (overlap == 0L) break
        }
      }
      result[entry.id] = best
      placed[best].add(entry)
    }
    return result
  }
}
