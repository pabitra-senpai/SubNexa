package com.subnexa.app.domain.embedded

import com.subnexa.app.domain.model.SubtitleEntry
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Pure (Android-free, Media3-free) logic that turns raw container subtitle samples into
 * [SubtitleEntry] objects. Kept separate so it can be unit-tested on the JVM.
 */

/** Subtitle codecs SubNexa can read as text. Anything else is treated as not editable. */
enum class EmbeddedKind(val label: String) {
  SRT("SRT"),
  ASS("ASS/SSA"),
  VTT("WebVTT"),
  MP4_TEXT("MP4 text");

  companion object {
    /** Maps a Media3 sample MIME type to a readable text kind, or null if not supported. */
    fun fromMime(mime: String?): EmbeddedKind? = when (mime) {
      "application/x-subrip" -> SRT
      "text/x-ssa" -> ASS
      "text/vtt" -> VTT
      "application/x-quicktime-tx3g" -> MP4_TEXT
      else -> null
    }
  }
}

/** A subtitle track found inside a video container. */
data class EmbeddedTrack(
  val trackId: Int,
  /** 1-based position among the subtitle tracks of the file. */
  val number: Int,
  val languageCode: String?,
  val title: String?,
  val mimeType: String,
  val kind: EmbeddedKind?,
  val isDefault: Boolean,
  val isForced: Boolean
) {
  val isEditable: Boolean get() = kind != null

  val languageName: String
    get() {
      val raw = languageCode?.trim()?.lowercase(Locale.ROOT)
      if (raw.isNullOrEmpty() || raw == "und") return "Unknown language"
      val two = ISO_639_2_TO_1[raw] ?: raw
      val name = Locale.forLanguageTag(two).getDisplayLanguage(Locale.ENGLISH)
      return if (name.isBlank() || name.equals(two, ignoreCase = true)) raw.uppercase(Locale.ROOT) else name
    }

  /** Codec label, e.g. "SRT", or "PGS (image)" for tracks that cannot be edited. */
  val formatName: String
    get() = kind?.label ?: when (mimeType) {
      "application/pgs" -> "PGS (image)"
      "application/vobsub" -> "VobSub (image)"
      "application/dvbsubs" -> "DVB (image)"
      else -> "Not supported"
    }

  /** Title shown in lists, e.g. "English" or "English - Full dialogue". */
  val displayName: String
    get() = if (title.isNullOrBlank()) languageName else "$languageName - ${title.trim()}"

  private companion object {
    val ISO_639_2_TO_1 = mapOf(
      "eng" to "en", "hin" to "hi", "ben" to "bn", "jpn" to "ja", "spa" to "es",
      "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de", "por" to "pt",
      "rus" to "ru", "ara" to "ar", "chi" to "zh", "zho" to "zh", "kor" to "ko",
      "ita" to "it", "tam" to "ta", "tel" to "te", "urd" to "ur", "tha" to "th",
      "ind" to "id", "may" to "ms", "msa" to "ms", "vie" to "vi", "tur" to "tr",
      "dut" to "nl", "nld" to "nl", "pol" to "pl", "swe" to "sv", "heb" to "he"
    )
  }
}

/** One raw sample as delivered by the container extractor. */
class RawSubtitleSample(val timeUs: Long, val data: ByteArray)

object EmbeddedSubtitleSamples {

  // Media3's MatroskaExtractor wraps every text sample in a fixed 32/38 byte prefix and encodes
  // the sample DURATION (not an absolute end time) in a placeholder timecode inside that prefix.
  private const val SRT_PREFIX_LEN = 32
  private const val SRT_END_OFFSET = 19
  private const val SRT_END_LEN = 12

  private const val ASS_PREFIX_LEN = 32
  private const val ASS_END_OFFSET = 21
  private const val ASS_END_LEN = 10

  private const val VTT_PREFIX_LEN = 38
  private const val VTT_END_OFFSET = 25
  private const val VTT_END_LEN = 12

  /** Used for the last MP4 text sample, which has no following sample to define its end. */
  private const val MP4_LAST_SAMPLE_MS = 3000L

  private val ASS_OVERRIDE_REGEX = Regex("\\{[^}]*\\}")
  private val ASS_DRAWING_REGEX = Regex("\\{[^}]*\\\\p[1-9][^}]*\\}")
  private val HTML_TAG_REGEX = Regex("<[^>]+>")

  fun toEntries(kind: EmbeddedKind, samples: List<RawSubtitleSample>): List<SubtitleEntry> {
    val parsed: List<Triple<Long, Long, String>> = when (kind) {
      EmbeddedKind.SRT -> samples.mapNotNull { parseSrtSample(it) }
      EmbeddedKind.ASS -> samples.mapNotNull { parseAssSample(it) }
      EmbeddedKind.VTT -> samples.mapNotNull { parseVttSample(it) }
      EmbeddedKind.MP4_TEXT -> parseMp4Samples(samples)
    }

    var nextId = 1L
    return parsed
      .filter { it.third.isNotBlank() && it.second > it.first && it.first >= 0 }
      .sortedBy { it.first }
      .map { (start, end, text) ->
        SubtitleEntry(id = nextId++, startMs = start, endMs = end, text = text.trim())
      }
  }

  // ---------- Matroska: S_TEXT/UTF8 ----------

  private fun parseSrtSample(s: RawSubtitleSample): Triple<Long, Long, String>? {
    if (s.data.size <= SRT_PREFIX_LEN) return null
    val durationMs = parseSrtLikeTimecode(s.data, SRT_END_OFFSET, SRT_END_LEN, ',') ?: return null
    val raw = String(s.data, SRT_PREFIX_LEN, s.data.size - SRT_PREFIX_LEN, StandardCharsets.UTF_8)
    val start = s.timeUs / 1000
    return Triple(start, start + durationMs, cleanMarkup(raw))
  }

  // ---------- Matroska: S_TEXT/ASS ----------

  private fun parseAssSample(s: RawSubtitleSample): Triple<Long, Long, String>? {
    if (s.data.size <= ASS_PREFIX_LEN) return null
    val timecode = String(s.data, ASS_END_OFFSET, ASS_END_LEN, StandardCharsets.US_ASCII)
    val parts = timecode.split(':')
    if (parts.size != 4) return null
    val h = parts[0].toLongOrNull() ?: return null
    val m = parts[1].toLongOrNull() ?: return null
    val sec = parts[2].toLongOrNull() ?: return null
    val cs = parts[3].toLongOrNull() ?: return null
    val durationMs = ((h * 60 + m) * 60 + sec) * 1000 + cs * 10

    // Remaining payload: ReadOrder,Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Text
    val payload = String(s.data, ASS_PREFIX_LEN, s.data.size - ASS_PREFIX_LEN, StandardCharsets.UTF_8)
    var idx = -1
    repeat(8) {
      idx = payload.indexOf(',', idx + 1)
      if (idx < 0) return null
    }
    val rawText = payload.substring(idx + 1)

    // Vector drawings (typesetting / signs) are not editable text.
    if (ASS_DRAWING_REGEX.containsMatchIn(rawText)) return null

    val text = rawText
      .replace(ASS_OVERRIDE_REGEX, "")
      .replace("\\N", "\n")
      .replace("\\n", " ")
      .replace("\\h", " ")
      .lines()
      .joinToString("\n") { it.trim() }
    val start = s.timeUs / 1000
    return Triple(start, start + durationMs, text)
  }

  // ---------- Matroska: S_TEXT/WEBVTT ----------

  private fun parseVttSample(s: RawSubtitleSample): Triple<Long, Long, String>? {
    if (s.data.size <= VTT_PREFIX_LEN) return null
    val durationMs = parseSrtLikeTimecode(s.data, VTT_END_OFFSET, VTT_END_LEN, '.') ?: return null
    val raw = String(s.data, VTT_PREFIX_LEN, s.data.size - VTT_PREFIX_LEN, StandardCharsets.UTF_8)
    val start = s.timeUs / 1000
    return Triple(start, start + durationMs, cleanMarkup(raw))
  }

  // ---------- MP4: tx3g (mov_text) ----------

  private fun parseMp4Samples(samples: List<RawSubtitleSample>): List<Triple<Long, Long, String>> {
    val sorted = samples.sortedBy { it.timeUs }
    val result = mutableListOf<Triple<Long, Long, String>>()
    for (i in sorted.indices) {
      val s = sorted[i]
      val text = decodeTx3g(s.data)
      if (text.isBlank()) continue // empty sample = gap marker
      val start = s.timeUs / 1000
      val end = if (i + 1 < sorted.size) sorted[i + 1].timeUs / 1000 else start + MP4_LAST_SAMPLE_MS
      result.add(Triple(start, end, text))
    }
    return result
  }

  private fun decodeTx3g(data: ByteArray): String {
    if (data.size < 2) return ""
    val len = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    if (len == 0) return ""
    val available = minOf(len, data.size - 2)
    val charset =
      if (available >= 2 &&
        ((data[2].toInt() and 0xFF) == 0xFE && (data[3].toInt() and 0xFF) == 0xFF)
      ) StandardCharsets.UTF_16BE
      else if (available >= 2 &&
        ((data[2].toInt() and 0xFF) == 0xFF && (data[3].toInt() and 0xFF) == 0xFE)
      ) StandardCharsets.UTF_16LE
      else StandardCharsets.UTF_8
    val raw = String(data, 2, available, charset).removePrefix("\uFEFF")
    return cleanMarkup(raw)
  }

  // ---------- Helpers ----------

  /** Parses "HH:MM:SS<sep>mmm" located at [offset] in [data]. Returns milliseconds. */
  private fun parseSrtLikeTimecode(data: ByteArray, offset: Int, len: Int, sep: Char): Long? {
    if (offset + len > data.size) return null
    val tc = String(data, offset, len, StandardCharsets.US_ASCII)
    val main = tc.split(sep)
    if (main.size != 2) return null
    val hms = main[0].split(':')
    if (hms.size != 3) return null
    val h = hms[0].toLongOrNull() ?: return null
    val m = hms[1].toLongOrNull() ?: return null
    val s = hms[2].toLongOrNull() ?: return null
    val ms = main[1].toLongOrNull() ?: return null
    return ((h * 60 + m) * 60 + s) * 1000 + ms
  }

  private fun cleanMarkup(raw: String): String =
    raw.replace("\r\n", "\n").replace('\r', '\n')
      .replace(HTML_TAG_REGEX, "")
      .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
      .lines().joinToString("\n") { it.trim() }
      .trim()
}
