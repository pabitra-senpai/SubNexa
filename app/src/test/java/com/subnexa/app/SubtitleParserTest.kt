package com.subnexa.app

import com.subnexa.app.domain.exporter.SubtitleExporter
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

  @Test
  fun testSrtWithWhitespaceOnlyBlankLines() {
    val srt = "1\n00:00:01,000 --> 00:00:02,000\nFirst cue\n   \t  \n2\n00:00:03,000 --> 00:00:05,000\nSecond cue\n\t\n3\n00:00:06,000 --> 00:00:08,000\nThird cue"
    val result = SubtitleParser.parseText(srt, "test.srt")
    assertEquals(3, result.entries.size)
    assertEquals("First cue", result.entries[0].text)
    assertEquals("Second cue", result.entries[1].text)
    assertEquals("Third cue", result.entries[2].text)
  }

  @Test
  fun testSingleBlockWithMultipleTimelineLines() {
    // Malformed block containing two cues without empty line separator
    val srt = """
      1
      00:00:01,000 --> 00:00:03,000
      First subtitle text
      2
      00:00:04,000 --> 00:00:07,000
      Second subtitle text
    """.trimIndent()

    val result = SubtitleParser.parseText(srt, "multi_timeline.srt")
    assertEquals(2, result.entries.size)
    assertEquals(1000L, result.entries[0].startMs)
    assertEquals(3000L, result.entries[0].endMs)
    assertEquals("First subtitle text", result.entries[0].text)

    assertEquals(4000L, result.entries[1].startMs)
    assertEquals(7000L, result.entries[1].endMs)
    assertEquals("Second subtitle text", result.entries[1].text)
  }

  @Test
  fun testVttWithoutHeaderWhenFilenameEndsInVtt() {
    val vttWithoutHeader = """
      00:01.000 --> 00:04.000
      Cue without WEBVTT header

      00:05.000 --> 00:08.000
      Second cue line
    """.trimIndent()

    val result = SubtitleParser.parseText(vttWithoutHeader, "movie_subs.vtt")
    assertEquals(2, result.entries.size)
    assertEquals(1000L, result.entries[0].startMs)
    assertEquals(4000L, result.entries[0].endMs)
    assertEquals("Cue without WEBVTT header", result.entries[0].text)
    assertEquals(5000L, result.entries[1].startMs)
    assertEquals(8000L, result.entries[1].endMs)
    assertEquals("Second cue line", result.entries[1].text)
  }

  @Test
  fun testBengaliAndHindiTextRoundTrip() {
    val originalText1 = "বাংলা সাবটাইটেল সম্পাদন ও সিঙ্ক — বিজ্ঞান ও প্রযুক্তি"
    val originalText2 = "नमस्ते दुनिया! हिंदी उपशीर्षक — प्रौद्योगिकी ও विद्या"

    val srt = """
      1
      00:00:01,200 --> 00:00:04,500
      $originalText1

      2
      00:00:05,000 --> 00:00:08,000
      $originalText2
    """.trimIndent()

    val result = SubtitleParser.parseText(srt, "multilingual.srt")
    assertEquals(2, result.entries.size)
    assertEquals(originalText1, result.entries[0].text)
    assertEquals(originalText2, result.entries[1].text)

    val exportedSrt = SubtitleExporter.generateSrtContent(result.entries)
    assertTrue(exportedSrt.contains(originalText1))
    assertTrue(exportedSrt.contains(originalText2))

    val reparsed = SubtitleParser.parseText(exportedSrt, "exported.srt")
    assertEquals(2, reparsed.entries.size)
    assertEquals(originalText1, reparsed.entries[0].text)
    assertEquals(originalText2, reparsed.entries[1].text)
  }

  @Test
  fun testFindActiveIndexAndFindActiveEntry() {
    val entries = listOf(
      SubtitleEntry(id = 1, startMs = 1000L, endMs = 3000L, text = "First"),
      SubtitleEntry(id = 2, startMs = 5000L, endMs = 8000L, text = "Second"),
      SubtitleEntry(id = 3, startMs = 10000L, endMs = 12000L, text = "Third")
    )

    // Before all
    assertEquals(-1, SubtitleParser.findActiveIndex(entries, 500L))
    assertNull(SubtitleParser.findActiveEntry(entries, 500L))

    // In first cue (boundary and interior)
    assertEquals(0, SubtitleParser.findActiveIndex(entries, 1000L))
    assertEquals("First", SubtitleParser.findActiveEntry(entries, 1000L)?.text)
    assertEquals(0, SubtitleParser.findActiveIndex(entries, 2000L))
    assertEquals("First", SubtitleParser.findActiveEntry(entries, 2000L)?.text)
    assertEquals(0, SubtitleParser.findActiveIndex(entries, 3000L))
    assertEquals("First", SubtitleParser.findActiveEntry(entries, 3000L)?.text)

    // In gap between first and second
    assertEquals(-1, SubtitleParser.findActiveIndex(entries, 3500L))
    assertNull(SubtitleParser.findActiveEntry(entries, 3500L))

    // In second cue
    assertEquals(1, SubtitleParser.findActiveIndex(entries, 6000L))
    assertEquals("Second", SubtitleParser.findActiveEntry(entries, 6000L)?.text)

    // In third cue
    assertEquals(2, SubtitleParser.findActiveIndex(entries, 11000L))
    assertEquals("Third", SubtitleParser.findActiveEntry(entries, 11000L)?.text)

    // After all cues
    assertEquals(-1, SubtitleParser.findActiveIndex(entries, 15000L))
    assertNull(SubtitleParser.findActiveEntry(entries, 15000L))

    // Empty list
    assertEquals(-1, SubtitleParser.findActiveIndex(emptyList(), 1000L))
    assertNull(SubtitleParser.findActiveEntry(emptyList(), 1000L))
  }
}
