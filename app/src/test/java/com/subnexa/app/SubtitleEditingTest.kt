package com.subnexa.app

import com.subnexa.app.domain.edit.SubtitleEditing
import com.subnexa.app.domain.model.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubtitleEditingTest {

  private fun entry(id: Long, start: Long, end: Long, text: String = "t$id") =
    SubtitleEntry(id = id, startMs = start, endMs = end, text = text)

  @Test
  fun shiftAll_movesEveryLine() {
    val result = SubtitleEditing.shiftAll(
      listOf(entry(1, 500, 1000), entry(2, 2000, 3000)),
      offsetMs = 500
    )
    assertEquals(0, result.clampedCount)
    assertEquals(listOf(1000L, 2500L), result.entries.map { it.startMs })
    assertEquals(listOf(1500L, 3500L), result.entries.map { it.endMs })
  }

  @Test
  fun shiftAll_holdsLinesAtZeroAndCountsThem() {
    val result = SubtitleEditing.shiftAll(
      listOf(entry(1, 500, 1000), entry(2, 2000, 3000)),
      offsetMs = -1000
    )
    assertEquals(1, result.clampedCount)
    assertEquals(listOf(0L, 1000L), result.entries.map { it.startMs })
    // The clamped line keeps its original duration of 500 ms.
    assertEquals(listOf(500L, 2000L), result.entries.map { it.endMs })
  }

  @Test
  fun duplicate_placesCopyRightAfterOriginal() {
    val copy = SubtitleEditing.duplicate(entry(1, 1000, 2000, "Hi"), newId = 9)
    assertEquals(9L, copy.id)
    assertEquals(2050L, copy.startMs)
    assertEquals(3050L, copy.endMs)
    assertEquals("Hi", copy.text)
  }

  @Test
  fun duplicate_shortLineGetsMinimumHalfSecond() {
    val copy = SubtitleEditing.duplicate(entry(1, 1000, 1100), newId = 2)
    assertEquals(1150L, copy.startMs)
    assertEquals(1650L, copy.endMs)
  }

  @Test
  fun split_dividesWordsAtPlayhead() {
    val (first, second) = SubtitleEditing.split(
      entry(1, 1000, 5000, "one two three four"),
      playheadMs = 3000,
      newId = 7
    )
    assertEquals(1L, first.id)
    assertEquals(1000L, first.startMs)
    assertEquals(3000L, first.endMs)
    assertEquals("one two", first.text)
    assertEquals(7L, second.id)
    assertEquals(3000L, second.startMs)
    assertEquals(5000L, second.endMs)
    assertEquals("three four", second.text)
  }

  @Test
  fun split_singleWordLeavesPlaceholderInSecondHalf() {
    val (first, second) = SubtitleEditing.split(
      entry(1, 1000, 5000, "hello"),
      playheadMs = 2000,
      newId = 2
    )
    assertEquals("hello", first.text)
    assertEquals("...", second.text)
  }

  @Test
  fun split_rejectsPlayheadOutsideLine() {
    assertThrows(IllegalArgumentException::class.java) {
      SubtitleEditing.split(entry(1, 1000, 5000), playheadMs = 1000, newId = 2)
    }
    assertThrows(IllegalArgumentException::class.java) {
      SubtitleEditing.split(entry(1, 1000, 5000), playheadMs = 5000, newId = 2)
    }
  }

  @Test
  fun merge_keepsFirstIdAndJoinsText() {
    val merged = SubtitleEditing.merge(
      entry(1, 1000, 2000, "A "),
      entry(2, 1500, 3000, " B")
    )
    assertEquals(1L, merged.id)
    assertEquals(1000L, merged.startMs)
    assertEquals(3000L, merged.endMs)
    assertEquals("A\nB", merged.text)
  }

  @Test
  fun nudgeStart_isLimitedByEndAndZero() {
    val e = entry(1, 1000, 2000)
    assertEquals(1900L to 2000L, SubtitleEditing.nudgeStart(e, 2000))
    assertEquals(0L to 2000L, SubtitleEditing.nudgeStart(e, -2000))
    assertEquals(1250L to 2000L, SubtitleEditing.nudgeStart(e, 250))
  }

  @Test
  fun nudgeEnd_keepsMinimumDuration() {
    val e = entry(1, 1000, 2000)
    assertEquals(1000L to 1100L, SubtitleEditing.nudgeEnd(e, -2000))
    assertEquals(1000L to 2250L, SubtitleEditing.nudgeEnd(e, 250))
  }

  @Test
  fun nudgeBoth_neverGoesBeforeZero() {
    val e = entry(1, 300, 800)
    assertEquals(0L to 500L, SubtitleEditing.nudgeBoth(e, -500))
    assertEquals(400L to 900L, SubtitleEditing.nudgeBoth(e, 100))
  }
}
