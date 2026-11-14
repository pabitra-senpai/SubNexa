@file:OptIn(UnstableApi::class)

package com.subnexa.app.domain.embedded

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.subnexa.app.domain.model.SubtitleEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException

/**
 * Finds and extracts text subtitle tracks stored inside a video container (MKV, MP4, ...),
 * using Media3's own container extractors. No new dependency is needed: they ship with
 * media3-exoplayer, which SubNexa already uses.
 *
 * Two phases:
 *  1. [listTracks]    - reads only the container header (fast), returns the track list.
 *  2. [extractTrack]  - reads the whole file once, keeps only the chosen track's samples
 *                       (slow for big files, so it reports progress).
 */
object EmbeddedSubtitleExtractor {

  private const val TAG = "SubNexaEmbedded"
  private const val SCAN_TIMEOUT_MS = 20_000L
  private const val PROGRESS_INTERVAL_MS = 150L

  suspend fun listTracks(context: Context, uri: Uri): List<EmbeddedTrack> =
    withContext(Dispatchers.IO) {
      val collector = Collector(wantedTrackId = null)
      try {
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
          runExtractor(context, uri, collector, stopWhenTracksEnded = true, onProgress = null)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Unreadable container: behave as "no subtitle tracks".
        Log.w(TAG, "Could not scan video for subtitle tracks", e)
      }
      val tracks = collector.buildTrackList()
      Log.i(
        TAG,
        "Scan finished: ${collector.formats.size} track(s) seen, " +
          "${tracks.size} subtitle track(s): " +
          tracks.joinToString { "${it.languageCode}/${it.mimeType}" }
      )
      tracks
    }

  /**
   * @param onProgress 0f..1f, or null when the total size is unknown.
   * @throws Exception on I/O or container errors (caller shows a message).
   */
  suspend fun extractTrack(
    context: Context,
    uri: Uri,
    track: EmbeddedTrack,
    onProgress: (Float?) -> Unit
  ): List<SubtitleEntry> {
    val kind = track.kind ?: return emptyList()
    val collector = Collector(wantedTrackId = track.trackId)
    withContext(Dispatchers.IO) {
      runExtractor(context, uri, collector, stopWhenTracksEnded = false, onProgress = onProgress)
    }
    return EmbeddedSubtitleSamples.toEntries(kind, collector.samples)
  }

  // ---------------------------------------------------------------------------------------

  private suspend fun runExtractor(
    context: Context,
    uri: Uri,
    collector: Collector,
    stopWhenTracksEnded: Boolean,
    onProgress: ((Float?) -> Unit)?
  ) {
    SourceReader(context, uri).use { reader ->
      var input = reader.open(0L)
      val extractor = selectExtractor(input) ?: return
      try {
        extractor.init(collector)
        val seekPosition = PositionHolder()
        var lastReportMs = 0L
        var bestProgress = 0f
        while (true) {
          currentCoroutineContext().ensureActive()

          val result = try {
            extractor.read(input, seekPosition)
          } catch (_: EOFException) {
            Extractor.RESULT_END_OF_INPUT
          }

          if (result == Extractor.RESULT_END_OF_INPUT) break
          if (result == Extractor.RESULT_SEEK) input = reader.open(seekPosition.position)
          if (stopWhenTracksEnded && collector.tracksEnded) break

          if (onProgress != null) {
            val now = System.currentTimeMillis()
            if (now - lastReportMs >= PROGRESS_INTERVAL_MS) {
              lastReportMs = now
              val total = reader.totalLength
              if (total > 0) {
                bestProgress = maxOf(bestProgress, (input.position.toFloat() / total).coerceIn(0f, 1f))
                onProgress(bestProgress)
              } else {
                onProgress(null)
              }
            }
          }
        }
        onProgress?.invoke(1f)
      } finally {
        extractor.release()
      }
    }
  }

  /**
   * By default Media3 converts text tracks to its internal "media3 cues" format while extracting.
   * We need the original SRT / ASS / WebVTT / tx3g samples, so transcoding must be switched off.
   */
  @Suppress("DEPRECATION")
  private fun createRawExtractors(): Array<Extractor> =
    DefaultExtractorsFactory()
      .setTextTrackTranscodingEnabled(false)
      .createExtractors()

  private fun selectExtractor(input: ExtractorInput): Extractor? {
    var chosen: Extractor? = null
    for (candidate in createRawExtractors()) {
      if (chosen == null) {
        try {
          if (candidate.sniff(input)) chosen = candidate
        } catch (_: Exception) {
          // Not this container (or truncated header): try the next extractor.
        } finally {
          input.resetPeekPosition()
        }
      }
      if (candidate !== chosen) candidate.release()
    }
    return chosen
  }

  /** Opens the video URI and (re)opens it at arbitrary positions when an extractor asks to seek. */
  private class SourceReader(context: Context, private val uri: Uri) : Closeable {
    private val dataSource: DataSource = DefaultDataSource.Factory(context).createDataSource()
    var totalLength: Long = C.LENGTH_UNSET.toLong()
      private set

    fun open(position: Long): ExtractorInput {
      dataSource.close()
      var length = dataSource.open(DataSpec.Builder().setUri(uri).setPosition(position).build())
      if (length != C.LENGTH_UNSET.toLong()) length += position
      if (position == 0L) totalLength = length
      return DefaultExtractorInput(dataSource, position, length)
    }

    override fun close() {
      try {
        dataSource.close()
      } catch (_: Exception) {
      }
    }
  }

  /**
   * Receives everything the container extractor produces. Samples of the wanted subtitle track
   * are kept; the data of every other track (video, audio, ...) is read and thrown away.
   */
  private class Collector(private val wantedTrackId: Int?) : ExtractorOutput {
    val formats = LinkedHashMap<Int, Format>()
    val trackTypes = HashMap<Int, Int>()
    val samples = ArrayList<RawSubtitleSample>()

    @Volatile
    var tracksEnded = false

    override fun track(id: Int, type: Int): TrackOutput {
      trackTypes[id] = type
      return Sink(id, collect = (wantedTrackId != null && wantedTrackId == id))
    }

    override fun endTracks() {
      tracksEnded = true
    }

    override fun seekMap(seekMap: SeekMap) {}

    fun buildTrackList(): List<EmbeddedTrack> {
      val result = ArrayList<EmbeddedTrack>()
      for ((id, format) in formats) {
        if (trackTypes[id] != C.TRACK_TYPE_TEXT) continue
        val mime = format.sampleMimeType ?: continue
        result.add(
          EmbeddedTrack(
            trackId = id,
            number = result.size + 1,
            languageCode = format.language,
            title = format.label,
            mimeType = mime,
            kind = EmbeddedKind.fromMime(mime),
            isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0,
            isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
          )
        )
      }
      return result
    }

    private inner class Sink(private val id: Int, private val collect: Boolean) : TrackOutput {
      private val pending = ByteArrayOutputStream()
      private val scratch = ByteArray(32 * 1024)

      override fun format(format: Format) {
        formats[id] = format
      }

      override fun sampleData(
        input: DataReader,
        length: Int,
        allowEndOfInput: Boolean,
        sampleDataPart: Int
      ): Int {
        val read = input.read(scratch, 0, minOf(length, scratch.size))
        if (read == C.RESULT_END_OF_INPUT) {
          if (allowEndOfInput) return C.RESULT_END_OF_INPUT
          throw EOFException()
        }
        if (collect && read > 0) pending.write(scratch, 0, read)
        return read
      }

      override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (collect) {
          val buffer = ByteArray(length)
          data.readBytes(buffer, 0, length)
          pending.write(buffer, 0, length)
        } else {
          data.skipBytes(length)
        }
      }

      override fun sampleMetadata(
        timeUs: Long,
        flags: Int,
        size: Int,
        offset: Int,
        cryptoData: TrackOutput.CryptoData?
      ) {
        if (!collect) return
        val all = pending.toByteArray()
        val end = all.size - offset
        val start = end - size
        if (size > 0 && start >= 0 && end <= all.size) {
          samples.add(RawSubtitleSample(timeUs, all.copyOfRange(start, end)))
        }
        pending.reset()
        if (offset > 0 && end >= 0) pending.write(all, end, offset)
      }
    }
  }
}
