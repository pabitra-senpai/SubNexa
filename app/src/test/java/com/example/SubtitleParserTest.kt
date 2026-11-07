package com.example

import com.example.domain.exporter.SubtitleExporter
import com.example.domain.model.SubtitleEntry
import com.example.domain.parser.SubtitleParser
import com.example.domain.parser.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

  @Test
  fun testTimeUtilsFormatAndParse() {
    val ms = 3724567L // 01:02:04.567
    val formattedDot = TimeUtils.formatTimestamp(ms, '.')
    assertEquals("01:02:04.567", formattedDot)

    val formattedComma = TimeUtils.formatTimestamp(ms, ',')
    assertEquals("01:02:04,567", formattedComma)

    val parsed = TimeUtils.parseTimestamp(formattedDot)
    assertEquals(ms, parsed)

    val parsedComma = TimeUtils.parseTimestamp(formattedComma)
    assertEquals(ms, parsedComma)
  }

  @Test
  fun testParseSrtContent() {
    val srt = """
      1
      00:00:01,000 --> 00:00:04,000
      Hello <i>world</i>!

      2
      00:00:05.500 --> 00:00:08.200
      Second subtitle line
    """.trimIndent()

    val result = SubtitleParser.parseText(srt, "sample.srt")
    assertEquals(2, result.entries.size)
    assertEquals(1000L, result.entries[0].startMs)
    assertEquals(4000L, result.entries[0].endMs)
    assertEquals("Hello <i>world</i>!", result.entries[0].text)
    assertEquals("Hello world!", SubtitleParser.stripTags(result.entries[0].text))

    assertEquals(5500L, result.entries[1].startMs)
    assertEquals(8200L, result.entries[1].endMs)
    assertEquals("Second subtitle line", result.entries[1].text)
  }

  @Test
  fun testParseVttContent() {
    val vtt = """
      WEBVTT
      NOTE This is a comment

      00:01.500 --> 00:04.000 line:90% position:50%
      <c.yellow>First</c> VTT cue

      00:00:05.000 --> 00:00:09.500
      Second cue line
    """.trimIndent()

    val result = SubtitleParser.parseText(vtt, "sample.vtt")
    assertEquals(2, result.entries.size)
    assertEquals(1500L, result.entries[0].startMs)
    assertEquals(4000L, result.entries[0].endMs)
    assertEquals("First VTT cue", SubtitleParser.stripTags(result.entries[0].text))

    assertEquals(5000L, result.entries[1].startMs)
    assertEquals(9500L, result.entries[1].endMs)
  }

  @Test
  fun testActiveEntryBinarySearch() {
    val entries = listOf(
      SubtitleEntry(id = 1, startMs = 1000L, endMs = 3000L, text = "Line 1"),
      SubtitleEntry(id = 2, startMs = 4000L, endMs = 6000L, text = "Line 2"),
      SubtitleEntry(id = 3, startMs = 8000L, endMs = 10000L, text = "Line 3")
    )

    assertNull(SubtitleParser.findActiveEntry(entries, 500L))

    val active1 = SubtitleParser.findActiveEntry(entries, 1500L)
    assertNotNull(active1)
    assertEquals(1L, active1?.id)

    assertNull(SubtitleParser.findActiveEntry(entries, 3500L))

    val active2 = SubtitleParser.findActiveEntry(entries, 5000L)
    assertNotNull(active2)
    assertEquals(2L, active2?.id)
  }

  @Test
  fun testExportSrtAndVtt() {
    val entries = listOf(
      SubtitleEntry(id = 1, startMs = 1000L, endMs = 3000L, text = "Hello"),
      SubtitleEntry(id = 2, startMs = 4000L, endMs = 6000L, text = "World")
    )

    val srt = SubtitleExporter.generateSrtContent(entries)
    assertTrue(srt.contains("1\n00:00:01,000 --> 00:00:03,000\nHello"))
    assertTrue(srt.contains("2\n00:00:04,000 --> 00:00:06,000\nWorld"))

    val vtt = SubtitleExporter.generateVttContent(entries)
    assertTrue(vtt.startsWith("WEBVTT\n\n"))
    assertTrue(vtt.contains("00:00:01.000 --> 00:00:03.000\nHello"))
  }
}
