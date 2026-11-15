package com.subnexa.app

import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.search.SubtitleSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSearchTest {

  private val entries = listOf(
    SubtitleEntry(id = 1, startMs = 1_000, endMs = 2_000, text = "Naruto runs"),
    SubtitleEntry(id = 2, startMs = 3_000, endMs = 4_000, text = "naruto and NARUTO"),
    SubtitleEntry(id = 3, startMs = 5_000, endMs = 6_000, text = "Sasuke waits")
  )

  @Test
  fun findsLinesIgnoringCaseByDefault() {
    assertEquals(listOf(1L, 2L), SubtitleSearch.findMatchIds(entries, "naruto", matchCase = false))
  }

  @Test
  fun matchCaseNarrowsResults() {
    assertEquals(listOf(2L), SubtitleSearch.findMatchIds(entries, "naruto", matchCase = true))
  }

  @Test
  fun emptyQueryMatchesNothing() {
    assertEquals(emptyList<Long>(), SubtitleSearch.findMatchIds(entries, "", matchCase = false))
  }

  @Test
  fun countsEveryOccurrence() {
    assertEquals(3, SubtitleSearch.countOccurrences("naruto and NARUTO and Naruto", "naruto", false))
    assertEquals(1, SubtitleSearch.countOccurrences("naruto and NARUTO and Naruto", "naruto", true))
  }

  @Test
  fun replaceAllReportsWhatChanged() {
    val result = SubtitleSearch.replaceAll(entries, "naruto", "Boruto", matchCase = false)
    assertEquals(2, result.changedLines)
    assertEquals(3, result.occurrences)
    assertEquals("Boruto runs", result.entries[0].text)
    assertEquals("Boruto and Boruto", result.entries[1].text)
    assertEquals("Sasuke waits", result.entries[2].text)
  }

  @Test
  fun replacementIsLiteralText() {
    assertEquals("a \$1 b", SubtitleSearch.replaceInText("a x b", "x", "\$1", matchCase = true))
  }
}
