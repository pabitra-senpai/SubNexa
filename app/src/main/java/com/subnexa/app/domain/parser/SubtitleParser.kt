package com.subnexa.app.domain.parser

import com.subnexa.app.domain.model.SubtitleEntry
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

data class SubtitleParseResult(
  val entries: List<SubtitleEntry>,
  val skippedCount: Int,
  val detectedFormat: String
)

object SubtitleParser {

  private val TAG_REGEX = Regex("<[^>]+>")

  /**
   * Decodes input bytes into String with BOM checks and UTF-8 / Windows-1252 fallback.
   */
  fun decodeBytes(bytes: ByteArray): String {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
      return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
      return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
    }
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
      return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
    }

    // Try strict UTF-8 decode
    val utf8Decoder = StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)

    return try {
      utf8Decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) {
      // Fallback to Windows-1252
      try {
        String(bytes, Charset.forName("windows-1252"))
      } catch (_: Exception) {
        String(bytes, StandardCharsets.ISO_8859_1)
      }
    }
  }

  fun parse(inputStream: InputStream, filenameHint: String = ""): SubtitleParseResult {
    val bytes = inputStream.readBytes()
    val content = decodeBytes(bytes)
    return parseText(content, filenameHint)
  }

  fun parseText(rawContent: String, filenameHint: String = ""): SubtitleParseResult {
    // Normalize newlines
    val normalized = rawContent.replace("\r\n", "\n").replace('\r', '\n')
    val isVtt = normalized.trimStart().startsWith("WEBVTT") || filenameHint.endsWith(".vtt", ignoreCase = true)

    return if (isVtt) {
      parseVtt(normalized)
    } else {
      parseSrt(normalized)
    }
  }

  /**
   * Parses SRT format subtitles.
   */
  private fun parseSrt(content: String): SubtitleParseResult {
    val entries = mutableListOf<SubtitleEntry>()
    var skipped = 0
    var nextId = 1L

    // Split into blocks separated by one or more blank lines
    val rawBlocks = content.split(Regex("\n{2,}"))

    for (block in rawBlocks) {
      val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
      if (lines.isEmpty()) continue

      var timeLineIndex = -1
      for (i in lines.indices) {
        if (lines[i].contains("-->")) {
          timeLineIndex = i
          break
        }
      }

      if (timeLineIndex == -1) {
        skipped++
        continue
      }

      val timeLine = lines[timeLineIndex]
      val timeParts = timeLine.split("-->")
      if (timeParts.size != 2) {
        skipped++
        continue
      }

      val startMs = TimeUtils.parseTimestamp(timeParts[0].trim())
      // In SRT sometimes settings or extra tokens follow end timestamp
      val endToken = timeParts[1].trim().split(Regex("\\s+"))[0]
      val endMs = TimeUtils.parseTimestamp(endToken)

      if (startMs == null || endMs == null || endMs <= startMs || startMs < 0) {
        skipped++
        continue
      }

      val textLines = lines.drop(timeLineIndex + 1)
      val text = textLines.joinToString("\n").trim()
      if (text.isEmpty()) {
        skipped++
        continue
      }

      entries.add(
        SubtitleEntry(
          id = nextId++,
          startMs = startMs,
          endMs = endMs,
          text = text
        )
      )
    }

    entries.sortBy { it.startMs }
    return SubtitleParseResult(entries = entries, skippedCount = skipped, detectedFormat = "SRT")
  }

  /**
   * Parses WebVTT format subtitles.
   */
  private fun parseVtt(content: String): SubtitleParseResult {
    val entries = mutableListOf<SubtitleEntry>()
    var skipped = 0
    var nextId = 1L

    val lines = content.lines()
    var i = 0

    // Skip WEBVTT header and any header metadata
    while (i < lines.size) {
      val line = lines[i].trim()
      if (line.startsWith("WEBVTT")) {
        i++
        // Skip subsequent header lines until blank line
        while (i < lines.size && lines[i].trim().isNotEmpty()) {
          i++
        }
        break
      }
      i++
    }

    while (i < lines.size) {
      val trimmed = lines[i].trim()
      if (trimmed.isEmpty()) {
        i++
        continue
      }

      // Check for comments or style/region blocks to skip
      if (trimmed.startsWith("NOTE") || trimmed.startsWith("STYLE") || trimmed.startsWith("REGION")) {
        i++
        while (i < lines.size && lines[i].trim().isNotEmpty()) {
          i++
        }
        continue
      }

      // Cue might have identifier line before time line
      var timeLine = trimmed
      if (!timeLine.contains("-->") && i + 1 < lines.size && lines[i + 1].contains("-->")) {
        i++
        timeLine = lines[i].trim()
      }

      if (timeLine.contains("-->")) {
        val parts = timeLine.split("-->")
        if (parts.size >= 2) {
          val startMs = TimeUtils.parseTimestamp(parts[0].trim())
          // End timestamp might have cue settings e.g. line:90% position:50%
          val endPart = parts[1].trim().split(Regex("\\s+"))[0]
          val endMs = TimeUtils.parseTimestamp(endPart)

          i++
          val textBuilder = StringBuilder()
          while (i < lines.size && lines[i].trim().isNotEmpty()) {
            if (textBuilder.isNotEmpty()) textBuilder.append("\n")
            textBuilder.append(lines[i].trim())
            i++
          }

          val text = textBuilder.toString().trim()
          if (startMs != null && endMs != null && endMs > startMs && startMs >= 0 && text.isNotEmpty()) {
            entries.add(
              SubtitleEntry(
                id = nextId++,
                startMs = startMs,
                endMs = endMs,
                text = text
              )
            )
          } else {
            skipped++
          }
        } else {
          skipped++
          i++
        }
      } else {
        skipped++
        i++
      }
    }

    entries.sortBy { it.startMs }
    return SubtitleParseResult(entries = entries, skippedCount = skipped, detectedFormat = "VTT")
  }

  /**
   * Strips formatting tags like <i>, <b>, <u>, <font color="...">, <c.class>, <v voice> for overlay display.
   */
  fun stripTags(rawText: String): String {
    return rawText.replace(TAG_REGEX, "")
  }

  /**
   * Fast binary search to find active subtitle entry for the current video playhead.
   * Assumes entries are sorted by startMs.
   */
  fun findActiveEntry(entries: List<SubtitleEntry>, positionMs: Long): SubtitleEntry? {
    if (entries.isEmpty()) return null

    var low = 0
    var high = entries.size - 1

    while (low <= high) {
      val mid = (low + high) ushr 1
      val entry = entries[mid]

      when {
        positionMs < entry.startMs -> high = mid - 1
        positionMs > entry.endMs -> low = mid + 1
        else -> return entry
      }
    }

    // Secondary check for rare slight overlap or edge condition
    return entries.firstOrNull { it.startMs <= positionMs && positionMs <= it.endMs }
  }
}
