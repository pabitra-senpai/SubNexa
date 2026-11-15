package com.subnexa.app.domain.search

import com.subnexa.app.domain.model.SubtitleEntry

data class ReplaceAllResult(
  val entries: List<SubtitleEntry>,
  val changedLines: Int,
  val occurrences: Int
)

/** Plain-text (not regex) find and replace over subtitle lines. */
object SubtitleSearch {

  /** Ids of the lines that contain [query], in time order. */
  fun findMatchIds(entries: List<SubtitleEntry>, query: String, matchCase: Boolean): List<Long> {
    if (query.isEmpty()) return emptyList()
    return entries
      .sortedBy { it.startMs }
      .filter { it.text.contains(query, ignoreCase = !matchCase) }
      .map { it.id }
  }

  fun countOccurrences(text: String, query: String, matchCase: Boolean): Int {
    if (query.isEmpty()) return 0
    var count = 0
    var from = 0
    while (true) {
      val at = text.indexOf(query, from, ignoreCase = !matchCase)
      if (at < 0) return count
      count++
      from = at + query.length
    }
  }

  fun replaceInText(text: String, query: String, replacement: String, matchCase: Boolean): String {
    if (query.isEmpty()) return text
    return text.replace(query, replacement, ignoreCase = !matchCase)
  }

  fun replaceAll(
    entries: List<SubtitleEntry>,
    query: String,
    replacement: String,
    matchCase: Boolean
  ): ReplaceAllResult {
    if (query.isEmpty()) return ReplaceAllResult(entries, 0, 0)
    var changedLines = 0
    var occurrences = 0
    val updated = entries.map { entry ->
      val found = countOccurrences(entry.text, query, matchCase)
      if (found == 0) {
        entry
      } else {
        changedLines++
        occurrences += found
        entry.copy(text = replaceInText(entry.text, query, replacement, matchCase))
      }
    }
    return ReplaceAllResult(updated, changedLines, occurrences)
  }
}
