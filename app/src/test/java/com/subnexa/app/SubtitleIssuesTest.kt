package com.subnexa.app

import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.validation.IssueType
import com.subnexa.app.domain.validation.SubtitleIssues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleIssuesTest {

  private fun entry(id: Long, start: Long, end: Long, text: String) =
    SubtitleEntry(id = id, startMs = start, endMs = end, text = text)

  @Test
  fun cleanLinesHaveNoIssues() {
    val result = SubtitleIssues.analyze(
      listOf(
        entry(1, 1_000, 3_000, "Hello there"),
        entry(2, 4_000, 6_000, "General Kenobi")
      )
    )
    assertTrue(result.isEmpty())
  }

  @Test
  fun overlapIsFlaggedOnBothLines() {
    val result = SubtitleIssues.analyze(
      listOf(
        entry(1, 1_000, 4_000, "First line"),
        entry(2, 3_000, 6_000, "Second line")
      )
    )
    assertTrue(IssueType.OVERLAP in result.getValue(1))
    assertTrue(IssueType.OVERLAP in result.getValue(2))
  }

  @Test
  fun touchingLinesAreNotOverlapping() {
    val result = SubtitleIssues.analyze(
      listOf(
        entry(1, 1_000, 3_000, "First line"),
        entry(2, 3_000, 5_000, "Second line")
      )
    )
    assertTrue(result.isEmpty())
  }

  @Test
  fun emptyShortAndLongAreFlagged() {
    val result = SubtitleIssues.analyze(
      listOf(
        entry(1, 0, 2_000, "<i> </i>"),
        entry(2, 5_000, 5_200, "Hi"),
        entry(3, 10_000, 20_000, "A very long wait")
      )
    )
    assertTrue(IssueType.EMPTY in result.getValue(1))
    assertTrue(IssueType.TOO_SHORT in result.getValue(2))
    assertTrue(IssueType.TOO_LONG in result.getValue(3))
  }

  @Test
  fun fastReadingUsesVisibleCharactersOnly() {
    val fast = SubtitleIssues.analyze(
      listOf(entry(1, 0, 1_000, "This line has far too many letters"))
    )
    assertTrue(IssueType.FAST_READING in fast.getValue(1))

    // Same visible text, tags do not count
    val tagged = SubtitleIssues.analyze(
      listOf(entry(1, 0, 3_000, "<i>Short</i>"))
    )
    assertFalse(tagged.containsKey(1L))
  }

  @Test
  fun combiningMarksDoNotCountAsExtraCharacters() {
    // "e" followed by a combining accent is one visible character
    assertEquals(1, SubtitleIssues.countCharacters("e\u0301"))
    assertEquals(3, SubtitleIssues.countCharacters("abc"))
  }
}
