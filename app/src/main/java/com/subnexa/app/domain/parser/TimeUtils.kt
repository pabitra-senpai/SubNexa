package com.subnexa.app.domain.parser

import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

  /**
   * Formats milliseconds into HH:MM:SS.mmm or HH:MM:SS,mmm
   */
  fun formatTimestamp(ms: Long, delimiter: Char = '.'): String {
    val clampedMs = if (ms < 0) 0L else ms
    val hours = TimeUnit.MILLISECONDS.toHours(clampedMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(clampedMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(clampedMs) % 60
    val millis = clampedMs % 1000

    return String.format(Locale.US, "%02d:%02d:%02d%c%03d", hours, minutes, seconds, delimiter, millis)
  }

  /**
   * Parses HH:MM:SS.mmm, HH:MM:SS,mmm, MM:SS.mmm, or MM:SS,mmm into milliseconds.
   * Returns null if parsing fails or invalid.
   */
  fun parseTimestamp(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // Replace comma with dot for uniform parsing
    val normalized = trimmed.replace(',', '.')
    val parts = normalized.split(":")
    return try {
      when (parts.size) {
        3 -> {
          // HH:MM:SS.mmm
          val hours = parts[0].toLong()
          val minutes = parts[1].toLong()
          val secParts = parts[2].split(".")
          val seconds = secParts[0].toLong()
          val millis = if (secParts.size > 1) {
            secParts[1].padEnd(3, '0').take(3).toLong()
          } else 0L

          if (hours < 0 || minutes !in 0..59 || seconds !in 0..59 || millis !in 0..999) return null
          hours * 3600000L + minutes * 60000L + seconds * 1000L + millis
        }
        2 -> {
          // MM:SS.mmm
          val minutes = parts[0].toLong()
          val secParts = parts[1].split(".")
          val seconds = secParts[0].toLong()
          val millis = if (secParts.size > 1) {
            secParts[1].padEnd(3, '0').take(3).toLong()
          } else 0L

          if (minutes < 0 || seconds !in 0..59 || millis !in 0..999) return null
          minutes * 60000L + seconds * 1000L + millis
        }
        else -> null
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Formats current and total duration e.g. 00:12:04.250 / 00:24:10.000
   */
  fun formatDuration(currentMs: Long, totalMs: Long): String {
    val cur = formatTimestamp(currentMs, '.')
    val tot = formatTimestamp(if (totalMs > 0) totalMs else 0L, '.')
    return "$cur / $tot"
  }

  /**
   * Relative time string e.g. "Edited 2 hours ago", "Edited just now"
   */
  fun timeAgo(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    if (diff < 60000L) return "Edited just now"
    val minutes = diff / 60000L
    if (minutes < 60L) return "Edited $minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
    val hours = minutes / 60L
    if (hours < 24L) return "Edited $hours ${if (hours == 1L) "hour" else "hours"} ago"
    val days = hours / 24L
    if (days < 30L) return "Edited $days ${if (days == 1L) "day" else "days"} ago"
    val months = days / 30L
    return "Edited $months ${if (months == 1L) "month" else "months"} ago"
  }
}
