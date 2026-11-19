package com.subnexa.app

import com.subnexa.app.domain.edit.TimelineEditing
import com.subnexa.app.domain.edit.TimelineEditing.Span
import com.subnexa.app.domain.model.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineEditingTest {

  private fun entry(id: Long, start: Long, end: Long) =
    SubtitleEntry(id = id, startMs = start, endMs = end, text = "t$id")

  private val line = entry(1, 1000, 3000)
  private val all = listOf(entry(2, 0, 800), entry(3, 3500, 5000), line)
  private val targets = TimelineEditing.snapTargets(all, excludeId = 1, playheadMs = 2000)

  @Test
  fun snapTargets_skipsTheDraggedLineAndAddsPlayhead() {
    assertEquals(setOf(0L, 800L, 3500L, 5000L, 2000L), targets.toSet())
    assertEquals(5, targets.size)
  }

  @Test
  fun snap_sticksOnlyWithinThreshold() {
    assertEquals(3500L, TimelineEditing.snap(3450, targets, 100))
    assertEquals(3300L, TimelineEditing.snap(3300, targets, 100))
    assertEquals(3450L, TimelineEditing.snap(3450, targets, 0))
  }

  @Test
  fun trimStart_keepsMinimumDurationAndZero() {
    assertEquals(Span(2900, 3000), TimelineEditing.trimStart(line, 4000, targets, 0))
    assertEquals(Span(0, 3000), TimelineEditing.trimStart(line, -500, targets, 0))
  }

  @Test
  fun trimEnd_keepsMinimumDurationAndVideoEnd() {
    assertEquals(Span(1000, 1100), TimelineEditing.trimEnd(line, 1050, targets, 0, 0))
    assertEquals(Span(1000, 10000), TimelineEditing.trimEnd(line, 99999, targets, 0, 10000))
    assertEquals(Span(1000, 3500), TimelineEditing.trimEnd(line, 3460, targets, 100, 0))
  }

  @Test
  fun move_keepsDurationAndStaysInsideVideo() {
    assertEquals(Span(0, 2000), TimelineEditing.move(line, -300, targets, 0, 0))
    assertEquals(Span(8000, 10000), TimelineEditing.move(line, 9500, targets, 0, 10000))
  }

  @Test
  fun move_snapsTheEndWhenOnlyTheEndIsClose() {
    assertEquals(Span(1500, 3500), TimelineEditing.move(line, 1520, targets, 100, 0))
  }

  @Test
  fun fillLanes_putsOverlappingAndDuplicateLinesInDifferentRows() {
    val lanes = TimelineEditing.fillLanes(
      listOf(entry(1, 0, 1000), entry(2, 0, 1000), entry(3, 500, 1500), entry(4, 1000, 2000)),
      emptyMap(),
      4
    )
    assertEquals(0, lanes[1])
    assertEquals(1, lanes[2])
    assertEquals(2, lanes[3])
    // Starts exactly when line 1 ends, so it reuses the first row.
    assertEquals(0, lanes[4])
  }

  @Test
  fun fillLanes_keepsRowsTheUserChoseAndPlacesNewLinesAroundThem() {
    val old = listOf(entry(1, 0, 1000), entry(2, 2000, 3000))
    val chosen = mapOf(1L to 3, 2L to 0)
    val duplicate = entry(3, 0, 1000)
    val lanes = TimelineEditing.fillLanes(old + duplicate, chosen, 4)
    assertEquals(3, lanes[1])
    assertEquals(0, lanes[2])
    // Row 0 is free between 0 and 1000, so the duplicate goes there.
    assertEquals(0, lanes[3])
    assertEquals(3, lanes.size)
  }

  @Test
  fun fillLanes_neverUsesMoreRowsThanAsked() {
    val many = (1L..8L).map { entry(it, 0, 1000) }
    val lanes = TimelineEditing.fillLanes(many, emptyMap(), 4)
    assertEquals(8, lanes.size)
    assertEquals(true, lanes.values.all { it in 0..3 })
  }

  @Test
  fun fillLanes_ignoresStoredRowsOutsideTheRange() {
    val lanes = TimelineEditing.fillLanes(listOf(entry(1, 0, 1000)), mapOf(1L to 9), 4)
    assertEquals(0, lanes[1])
  }
}
