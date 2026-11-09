package com.subnexa.app.domain.exporter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.subnexa.app.domain.model.ExportFormat
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.TimeUtils
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object SubtitleExporter {

  /**
   * Normalizes subtitle text by converting \r\n to \n, trimming lines,
   * collapsing any run of blank lines into a single newline, and stripping leading/trailing whitespace.
   */
  fun normalizeText(rawText: String): String {
    return rawText
      .replace("\r\n", "\n")
      .replace('\r', '\n')
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n")
  }

  fun generateSrtContent(entries: List<SubtitleEntry>): String {
    val sorted = entries.sortedBy { it.startMs }
    val sb = StringBuilder()
    var index = 1
    for (entry in sorted) {
      val text = normalizeText(entry.text)
      if (text.isEmpty()) continue
      sb.append(index++).append("\n")
      val start = TimeUtils.formatTimestamp(entry.startMs, ',')
      val end = TimeUtils.formatTimestamp(entry.endMs, ',')
      sb.append(start).append(" --> ").append(end).append("\n")
      sb.append(text).append("\n\n")
    }
    return sb.toString()
  }

  fun generateVttContent(entries: List<SubtitleEntry>): String {
    val sorted = entries.sortedBy { it.startMs }
    val sb = StringBuilder()
    sb.append("WEBVTT\n\n")
    for (entry in sorted) {
      val text = normalizeText(entry.text)
      if (text.isEmpty()) continue
      val start = TimeUtils.formatTimestamp(entry.startMs, '.')
      val end = TimeUtils.formatTimestamp(entry.endMs, '.')
      sb.append(start).append(" --> ").append(end).append("\n")
      sb.append(text).append("\n\n")
    }
    return sb.toString()
  }

  /**
   * Writes the exported subtitle file to the chosen SAF folder.
   * Resolves duplicate filenames by appending " (1)", " (2)", etc.
   */
  fun exportToFile(
    context: Context,
    folderUri: Uri,
    baseFileName: String,
    format: ExportFormat,
    entries: List<SubtitleEntry>
  ): Result<String> {
    return try {
      val documentTree = DocumentFile.fromTreeUri(context, folderUri)
        ?: return Result.failure(IllegalStateException("Cannot access destination folder"))

      val cleanBaseName = baseFileName.trim().removeSuffix(".srt").removeSuffix(".vtt").ifEmpty { "Subtitles" }
      val extension = format.extension
      val mimeType = if (format == ExportFormat.VTT) "text/vtt" else "application/x-subrip"

      var targetFileName = "$cleanBaseName.$extension"
      var counter = 1

      // Avoid overwriting existing files
      while (documentTree.findFile(targetFileName) != null) {
        targetFileName = "$cleanBaseName ($counter).$extension"
        counter++
      }

      val createdFile = documentTree.createFile(mimeType, targetFileName)
        ?: return Result.failure(IllegalStateException("Could not create file in selected folder"))

      val content = when (format) {
        ExportFormat.SRT -> generateSrtContent(entries)
        ExportFormat.VTT -> generateVttContent(entries)
      }

      context.contentResolver.openOutputStream(createdFile.uri)?.use { outputStream ->
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
          writer.write(content)
          writer.flush()
        }
      } ?: return Result.failure(IllegalStateException("Failed to open output stream"))

      Result.success(createdFile.name ?: targetFileName)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
