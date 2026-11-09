package com.subnexa.app

import com.subnexa.app.domain.exporter.SubtitleExporter
import com.subnexa.app.domain.model.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleExporterTest {

  @Test
  fun testBlankLineCollapsingInExporter() {
    val entries = listOf(
      SubtitleEntry(
        id = 1,
        startMs = 1000L,
        endMs = 3000L,
        text = "  \n\nFirst line\n\n\n\nSecond line\n\n  "
      )
    )

    val srt = SubtitleExporter.generateSrtContent(entries)
    // Should collapse multi-newlines to single newline and trim leading/trailing
    assertTrue(srt.contains("First line\nSecond line"))
    assertFalse(srt.contains("First line\n\nSecond line"))

    val vtt = SubtitleExporter.generateVttContent(entries)
    assertTrue(vtt.contains("First line\nSecond line"))
  }

  @Test
  fun testEntriesAreSortedByStartMs() {
    val entries = listOf(
      SubtitleEntry(id = 3, startMs = 8000L, endMs = 10000L, text = "Third"),
      SubtitleEntry(id = 1, startMs = 1000L, endMs = 3000L, text = "First"),
      SubtitleEntry(id = 2, startMs = 4000L, endMs = 6000L, text = "Second")
    )

    val srt = SubtitleExporter.generateSrtContent(entries)
    val lines = srt.lines()
    val firstIdx = lines.indexOf("First")
    val secondIdx = lines.indexOf("Second")
    val thirdIdx = lines.indexOf("Third")

    assertTrue(firstIdx in 0 until secondIdx)
    assertTrue(secondIdx in 0 until thirdIdx)

    // Numbers in SRT should be 1, 2, 3 in sequential order
    val num1Idx = lines.indexOf("1")
    val num2Idx = lines.indexOf("2")
    val num3Idx = lines.indexOf("3")
    assertTrue(num1Idx < num2Idx && num2Idx < num3Idx)
  }

  @Test
  fun testTimestampFormatSrtCommaVttDot() {
    val entries = listOf(
      SubtitleEntry(id = 1, startMs = 3724567L, endMs = 3730890L, text = "Sample")
    )

    val srt = SubtitleExporter.generateSrtContent(entries)
    // SRT uses comma for milliseconds
    assertTrue(srt.contains("01:02:04,567 --> 01:02:10,890"))

    val vtt = SubtitleExporter.generateVttContent(entries)
    // VTT uses dot for milliseconds
    assertTrue(vtt.startsWith("WEBVTT\n\n"))
    assertTrue(vtt.contains("01:02:04.567 --> 01:02:10.890"))
  }

  @Test
  fun testEmptyAndBlankEntriesAreSkipped() {
    val entries = listOf(
      SubtitleEntry(id = 1, startMs = 1000L, endMs = 2000L, text = "   \n\n  "),
      SubtitleEntry(id = 2, startMs = 3000L, endMs = 4000L, text = "Valid text"),
      SubtitleEntry(id = 3, startMs = 5000L, endMs = 6000L, text = "")
    )

    val srt = SubtitleExporter.generateSrtContent(entries)
    assertTrue(srt.contains("Valid text"))
    // Only one valid entry should be emitted, numbered 1
    assertTrue(srt.startsWith("1\n00:00:03,000 --> 00:00:04,000\nValid text"))
    assertFalse(srt.contains("2\n"))

    val vtt = SubtitleExporter.generateVttContent(entries)
    assertTrue(vtt.contains("Valid text"))
    val vttCues = vtt.split("-->")
    assertEquals(2, vttCues.size) // 1 delimiter means 1 cue
  }
}
