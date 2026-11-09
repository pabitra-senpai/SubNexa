package com.subnexa.app

import com.subnexa.app.domain.parser.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeUtilsTest {

  @Test
  fun testParseTimestampThreeParts() {
    val msExpected = 3724567L // 1h 2m 4s 567ms

    // Comma delimiter (SRT)
    val parsedComma = TimeUtils.parseTimestamp("01:02:04,567")
    assertEquals(msExpected, parsedComma)

    // Dot delimiter (VTT)
    val parsedDot = TimeUtils.parseTimestamp("01:02:04.567")
    assertEquals(msExpected, parsedDot)

    // Without millis
    val parsedNoMillis = TimeUtils.parseTimestamp("01:02:04")
    assertEquals(3724000L, parsedNoMillis)
  }

  @Test
  fun testParseTimestampTwoParts() {
    val msExpected = 124567L // 2m 4s 567ms

    val parsedDot = TimeUtils.parseTimestamp("02:04.567")
    assertEquals(msExpected, parsedDot)

    val parsedComma = TimeUtils.parseTimestamp("02:04,567")
    assertEquals(msExpected, parsedComma)

    val parsedNoMillis = TimeUtils.parseTimestamp("02:04")
    assertEquals(124000L, parsedNoMillis)
  }

  @Test
  fun testParseTimestampInvalidInputsReturnNull() {
    assertNull(TimeUtils.parseTimestamp(""))
    assertNull(TimeUtils.parseTimestamp("   "))
    assertNull(TimeUtils.parseTimestamp("invalid"))
    assertNull(TimeUtils.parseTimestamp("12"))
    assertNull(TimeUtils.parseTimestamp("01:02:03:04")) // 4 parts
    assertNull(TimeUtils.parseTimestamp("01:60:00,000")) // minutes > 59
    assertNull(TimeUtils.parseTimestamp("01:00:60,000")) // seconds > 59
    assertNull(TimeUtils.parseTimestamp("-01:00:00,000")) // negative hours
    assertNull(TimeUtils.parseTimestamp("01:-05:00,000")) // negative minutes
    assertNull(TimeUtils.parseTimestamp("ab:cd:ef,ghi")) // non-numeric
  }

  @Test
  fun testFormatTimestamp() {
    val ms = 3724567L

    val formattedDot = TimeUtils.formatTimestamp(ms, '.')
    assertEquals("01:02:04.567", formattedDot)

    val formattedComma = TimeUtils.formatTimestamp(ms, ',')
    assertEquals("01:02:04,567", formattedComma)

    // Negative ms clamped to 0
    val formattedNegative = TimeUtils.formatTimestamp(-100L, '.')
    assertEquals("00:00:00.000", formattedNegative)
  }

  @Test
  fun testFormatDuration() {
    val duration = TimeUtils.formatDuration(65000L, 120000L)
    assertEquals("00:01:05.000 / 00:02:00.000", duration)
  }
}
