package com.subnexa.app.domain.validation

import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.SubtitleParser
import java.text.BreakIterator

enum class IssueType(val label: String) {
  OVERLAP("Overlaps a neighbor"),
  EMPTY("Empty text"),
  TOO_SHORT("Too short"),
  TOO_LONG("Too long"),
  FAST_READING("Reads too fast")
}

/** Finds common subtitle problems so they can be flagged in the list. */
object SubtitleIssues {
  const val MIN_DURATION_MS = 500L
  const val MAX_DURATION_MS = 7_000L
  const val MAX_CHARS_PER_SECOND = 20.0

  /**
   * @return entry id -> problems of that entry. Entries without problems are not in the map.
   * A line that overlaps its neighbour is flagged on both lines.
   */
  fun analyze(entries: List<SubtitleEntry>): Map<Long, List<IssueType>> {
    val sorted = entries.sortedBy { it.startMs }
    val result = LinkedHashMap<Long, List<IssueType>>()

    sorted.forEachIndexed { i, entry ->
      val previous = sorted.getOrNull(i - 1)
      val next = sorted.getOrNull(i + 1)
      val found = ArrayList<IssueType>(2)

      if ((previous != null && entry.startMs < previous.endMs) ||
        (next != null && next.startMs < entry.endMs)
      ) {
        found += IssueType.OVERLAP
      }

      val visibleText = SubtitleParser.stripTags(entry.text).replace("\r", "").replace("\n", "")
      if (visibleText.isBlank()) found += IssueType.EMPTY

      val durationMs = entry.endMs - entry.startMs
      if (durationMs < MIN_DURATION_MS) found += IssueType.TOO_SHORT
      if (durationMs > MAX_DURATION_MS) found += IssueType.TOO_LONG

      if (durationMs > 0L && visibleText.isNotBlank()) {
        val charsPerSecond = countCharacters(visibleText) / (durationMs / 1000.0)
        if (charsPerSecond > MAX_CHARS_PER_SECOND) found += IssueType.FAST_READING
      }

      if (found.isNotEmpty()) result[entry.id] = found
    }
    return result
  }

  /** Counts what a viewer sees as characters (letters with their marks count once). */
  internal fun countCharacters(text: String): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
  }
}
