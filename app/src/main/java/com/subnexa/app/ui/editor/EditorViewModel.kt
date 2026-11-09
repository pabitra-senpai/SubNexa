package com.subnexa.app.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.subnexa.app.data.ProjectRepository
import com.subnexa.app.data.SettingsRepository
import com.subnexa.app.domain.exporter.SubtitleExporter
import com.subnexa.app.domain.model.ExportFormat
import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.SubtitleParser
import com.subnexa.app.domain.util.FileUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

sealed interface EditorSheetState {
  data object None : EditorSheetState
  data class Edit(val entry: SubtitleEntry?, val isNew: Boolean) : EditorSheetState
  data object GlobalSync : EditorSheetState
  data object Export : EditorSheetState
  data object Appearance : EditorSheetState
}

sealed interface EditorEvent {
  data class ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null) : EditorEvent
  data class ScrollToIndex(val index: Int) : EditorEvent
  data object OpenExportFolderPicker : EditorEvent
}

data class EditorUiState(
  val isLoading: Boolean = true,
  val project: Project? = null,
  val isPlaying: Boolean = false,
  val currentPositionMs: Long = 0L,
  val durationMs: Long = 0L,
  val playbackSpeed: Float = 1.0f,
  val activeEntry: SubtitleEntry? = null,
  val activeIndex: Int = -1,
  val sheetState: EditorSheetState = EditorSheetState.None,
  val isFullscreen: Boolean = false,
  val defaultExportFolderUri: String? = null,
  val defaultExportFormat: ExportFormat = ExportFormat.SRT,
  val playerError: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

  private val projectRepository = ProjectRepository.getInstance(application)
  private val settingsRepository = SettingsRepository.getInstance(application)

  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  private val _playbackPositionMs = MutableStateFlow(0L)
  val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

  private val _events = MutableSharedFlow<EditorEvent>()
  val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

  var exoPlayer: ExoPlayer? = null
    private set

  private var positionPollingJob: Job? = null
  private var autosaveJob: Job? = null
  private val idGenerator = AtomicLong(1000L)
  private val deletedEntriesStack = ArrayDeque<SubtitleEntry>()

  private val speedCycle = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

  fun initProject(projectId: String) {
    viewModelScope.launch {
      val project = projectRepository.getProject(projectId)
      if (project == null) {
        _uiState.update { it.copy(isLoading = false, playerError = "Project not found") }
        return@launch
      }

      val maxId = project.entries.maxOfOrNull { it.id } ?: 0L
      idGenerator.set(maxOf(maxId + 1L, 1000L))

      val defaultFolder = settingsRepository.defaultExportFolderUriFlow.first()
      val defaultFormat = settingsRepository.defaultExportFormatFlow.first()
      val startPos = project.lastPlaybackPositionMs

      _playbackPositionMs.value = startPos
      _uiState.update {
        it.copy(
          isLoading = false,
          project = project,
          defaultExportFolderUri = defaultFolder,
          defaultExportFormat = defaultFormat,
          currentPositionMs = startPos
        )
      }

      setupPlayer(project.videoUri, startPos)
    }
  }

  private fun setupPlayer(videoUriStr: String, startPositionMs: Long) {
    val context = getApplication<Application>()
    val player = ExoPlayer.Builder(context).build().apply {
      // Disable embedded text tracks
      trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setPreferredTextLanguage(null)
        .setDisabledTrackTypes(setOf(C.TRACK_TYPE_TEXT))
        .build()

      playWhenReady = false
      setMediaItem(MediaItem.fromUri(Uri.parse(videoUriStr)))
      prepare()
      if (startPositionMs > 0) {
        seekTo(startPositionMs)
      }

      addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          _uiState.update { it.copy(isPlaying = isPlaying) }
          if (isPlaying) {
            startPollingPosition()
          } else {
            stopPollingPosition()
            triggerDebouncedAutosave()
          }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_READY) {
            _uiState.update {
              it.copy(
                durationMs = maxOf(0L, duration),
                playerError = null
              )
            }
          }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
          _uiState.update {
            it.copy(
              playerError = "Cannot play video: ${error.localizedMessage ?: "unsupported format"}"
            )
          }
        }
      })
    }
    exoPlayer = player
    updateActiveEntry(startPositionMs)
  }

  private fun startPollingPosition() {
    positionPollingJob?.cancel()
    positionPollingJob = viewModelScope.launch {
      while (isActive) {
        val player = exoPlayer ?: break
        val pos = player.currentPosition
        val dur = maxOf(0L, player.duration)

        // Update the high-frequency playback position flow
        _playbackPositionMs.value = pos

        val entries = _uiState.value.project?.entries
        val activeIndex = if (entries != null) SubtitleParser.findActiveIndex(entries, pos) else -1
        val active = if (entries != null && activeIndex != -1) entries[activeIndex] else null

        // Stop updating currentPositionMs in EditorUiState on every tick.
        // Keep activeEntry and activeIndex in EditorUiState, updated only when they actually change.
        _uiState.update { current ->
          if (current.activeEntry == active &&
            current.activeIndex == activeIndex &&
            current.durationMs == dur
          ) {
            current
          } else {
            current.copy(
              activeEntry = active,
              activeIndex = activeIndex,
              durationMs = dur
            )
          }
        }
        delay(50L)
      }
    }
  }

  private fun stopPollingPosition() {
    positionPollingJob?.cancel()
    positionPollingJob = null
    val pos = exoPlayer?.currentPosition ?: _playbackPositionMs.value
    _playbackPositionMs.value = pos
    updatePositionAndActiveEntry(pos)
  }

  private fun updatePositionAndActiveEntry(positionMs: Long) {
    val entries = _uiState.value.project?.entries ?: return
    val index = SubtitleParser.findActiveIndex(entries, positionMs)
    val active = if (index != -1) entries[index] else null
    _uiState.update { current ->
      if (current.currentPositionMs == positionMs &&
        current.activeEntry == active &&
        current.activeIndex == index
      ) {
        current
      } else {
        current.copy(
          currentPositionMs = positionMs,
          activeEntry = active,
          activeIndex = index
        )
      }
    }
  }

  private fun updateActiveEntry(positionMs: Long) {
    val entries = _uiState.value.project?.entries ?: return
    val index = SubtitleParser.findActiveIndex(entries, positionMs)
    val active = if (index != -1) entries[index] else null
    _uiState.update { current ->
      if (current.activeEntry == active &&
        current.activeIndex == index
      ) {
        current
      } else {
        current.copy(
          activeEntry = active,
          activeIndex = index
        )
      }
    }
  }

  fun togglePlayPause() {
    val player = exoPlayer ?: return
    if (player.isPlaying) {
      player.pause()
    } else {
      player.play()
    }
  }

  fun pause() {
    exoPlayer?.pause()
    val pos = exoPlayer?.currentPosition ?: _playbackPositionMs.value
    _playbackPositionMs.value = pos
    updatePositionAndActiveEntry(pos)
  }

  fun seekTo(positionMs: Long) {
    val clamped = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(0L))
    exoPlayer?.seekTo(clamped)
    _playbackPositionMs.value = clamped
    updatePositionAndActiveEntry(clamped)
    triggerDebouncedAutosave()
  }

  fun cyclePlaybackSpeed() {
    val current = _uiState.value.playbackSpeed
    val currentIndex = speedCycle.indexOfFirst { kotlin.math.abs(it - current) < 0.05f }
    val nextIndex = (if (currentIndex == -1) 2 else currentIndex + 1) % speedCycle.size
    val nextSpeed = speedCycle[nextIndex]
    exoPlayer?.playbackParameters = PlaybackParameters(nextSpeed)
    _uiState.update { it.copy(playbackSpeed = nextSpeed) }
  }

  fun toggleFullscreen() {
    _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
  }

  fun openSheet(sheet: EditorSheetState) {
    pause()
    val pos = exoPlayer?.currentPosition ?: _playbackPositionMs.value
    _playbackPositionMs.value = pos
    _uiState.update {
      it.copy(
        sheetState = sheet,
        currentPositionMs = pos
      )
    }
    updateActiveEntry(pos)
  }

  fun closeSheet() {
    _uiState.update { it.copy(sheetState = EditorSheetState.None) }
  }

  fun addSubtitle(startMs: Long, endMs: Long, text: String) {
    val normalizedText = SubtitleExporter.normalizeText(text)
    if (endMs <= startMs || normalizedText.isEmpty()) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ShowSnackbar("Invalid timing or empty text"))
      }
      return
    }

    val project = _uiState.value.project ?: return
    val newEntry = SubtitleEntry(
      id = idGenerator.getAndIncrement(),
      startMs = startMs,
      endMs = endMs,
      text = normalizedText
    )
    val updatedEntries = (project.entries + newEntry).sortedBy { it.startMs }
    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update {
      it.copy(project = updatedProject, sheetState = EditorSheetState.None)
    }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)

    val savedIndex = updatedEntries.indexOf(newEntry)
    if (savedIndex != -1) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ScrollToIndex(savedIndex))
      }
    }
  }

  fun updateSubtitle(id: Long, startMs: Long, endMs: Long, text: String) {
    val normalizedText = SubtitleExporter.normalizeText(text)
    if (endMs <= startMs || normalizedText.isEmpty()) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ShowSnackbar("Invalid timing or empty text"))
      }
      return
    }

    val project = _uiState.value.project ?: return
    val updatedEntries = project.entries.map { entry ->
      if (entry.id == id) {
        entry.copy(startMs = startMs, endMs = endMs, text = normalizedText)
      } else entry
    }.sortedBy { it.startMs }

    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update {
      it.copy(project = updatedProject, sheetState = EditorSheetState.None)
    }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)

    val savedIndex = updatedEntries.indexOfFirst { it.id == id }
    if (savedIndex != -1) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ScrollToIndex(savedIndex))
      }
    }
  }

  fun deleteSubtitle(entry: SubtitleEntry) {
    val project = _uiState.value.project ?: return
    val updatedEntries = project.entries.filter { it.id != entry.id }
    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    deletedEntriesStack.addLast(entry)
    _uiState.update { it.copy(project = updatedProject) }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)

    viewModelScope.launch {
      _events.emit(
        EditorEvent.ShowSnackbar(
          message = "Subtitle deleted",
          actionLabel = "Undo",
          onAction = { undoDeleteSubtitle() }
        )
      )
    }
  }

  private fun undoDeleteSubtitle() {
    if (deletedEntriesStack.isEmpty()) return
    val entryToRestore = deletedEntriesStack.removeLast()
    val project = _uiState.value.project ?: return
    val updatedEntries = (project.entries + entryToRestore).sortedBy { it.startMs }
    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update { it.copy(project = updatedProject) }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)
  }

  fun duplicateSubtitle(entry: SubtitleEntry) {
    val project = _uiState.value.project ?: return
    val duration = (entry.endMs - entry.startMs).coerceAtLeast(500L)
    val newStart = entry.endMs + 50L
    val newEnd = newStart + duration
    val duplicated = SubtitleEntry(
      id = idGenerator.getAndIncrement(),
      startMs = newStart,
      endMs = newEnd,
      text = entry.text
    )
    val updatedEntries = (project.entries + duplicated).sortedBy { it.startMs }
    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update { it.copy(project = updatedProject) }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)

    val newIndex = updatedEntries.indexOf(duplicated)
    if (newIndex != -1) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ScrollToIndex(newIndex))
      }
    }
  }

  fun splitSubtitleAtPlayhead(entry: SubtitleEntry) {
    val playhead = exoPlayer?.currentPosition ?: _playbackPositionMs.value
    if (playhead <= entry.startMs || playhead >= entry.endMs) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ShowSnackbar("Playhead must be inside the subtitle to split"))
      }
      return
    }

    val project = _uiState.value.project ?: return
    val words = entry.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val midWordIndex = (words.size / 2).coerceAtLeast(1)
    val firstText = words.take(midWordIndex).joinToString(" ").ifEmpty { entry.text.trim() }
    val secondText = words.drop(midWordIndex).joinToString(" ").ifEmpty { "..." }

    val firstHalf = entry.copy(endMs = playhead, text = firstText)
    val secondHalf = SubtitleEntry(
      id = idGenerator.getAndIncrement(),
      startMs = playhead,
      endMs = entry.endMs,
      text = secondText
    )

    val updatedEntries = project.entries.map {
      if (it.id == entry.id) firstHalf else it
    }.plus(secondHalf).sortedBy { it.startMs }

    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update { it.copy(project = updatedProject) }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)
  }

  fun mergeWithNext(entry: SubtitleEntry) {
    val project = _uiState.value.project ?: return
    val sorted = project.entries.sortedBy { it.startMs }
    val currentIndex = sorted.indexOfFirst { it.id == entry.id }
    if (currentIndex == -1 || currentIndex >= sorted.size - 1) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ShowSnackbar("No next subtitle to merge with"))
      }
      return
    }

    val next = sorted[currentIndex + 1]
    val merged = entry.copy(
      endMs = maxOf(entry.endMs, next.endMs),
      text = "${entry.text.trim()}\n${next.text.trim()}"
    )

    val updatedEntries = sorted.filter { it.id != next.id }.map {
      if (it.id == entry.id) merged else it
    }

    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update { it.copy(project = updatedProject) }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)
  }

  fun applyGlobalSync(offsetSeconds: Double) {
    val offsetMs = (offsetSeconds * 1000.0).toLong()
    val project = _uiState.value.project ?: return

    val shiftedEntries = project.entries.map { entry ->
      val newStart = (entry.startMs + offsetMs).coerceAtLeast(0L)
      val duration = (entry.endMs - entry.startMs).coerceAtLeast(100L)
      val newEnd = (entry.endMs + offsetMs).coerceAtLeast(newStart + duration)
      entry.copy(startMs = newStart, endMs = newEnd)
    }.sortedBy { it.startMs }

    val updatedProject = project.copy(
      entries = shiftedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update {
      it.copy(project = updatedProject, sheetState = EditorSheetState.None)
    }
    updateActiveEntry(_playbackPositionMs.value)
    triggerImmediateSave(updatedProject)

    viewModelScope.launch {
      _events.emit(
        EditorEvent.ShowSnackbar("Shifted all ${shiftedEntries.size} subtitles by ${String.format("%.2f", offsetSeconds)}s")
      )
    }
  }

  fun updateAppearance(appearance: SubtitleAppearance) {
    val project = _uiState.value.project ?: return
    val updated = project.copy(
      appearance = appearance,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update { it.copy(project = updated) }
    triggerDebouncedAutosave()
  }

  fun importExternalSubtitles(subtitleUri: Uri) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      try {
        context.contentResolver.takePersistableUriPermission(
          subtitleUri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: Exception) {}

      val subtitleFileName = FileUtils.getFileName(context, subtitleUri, "Subtitles")

      val parseResult = try {
        context.contentResolver.openInputStream(subtitleUri)?.use { input ->
          SubtitleParser.parse(input, subtitleFileName)
        }
      } catch (_: Exception) {
        null
      }

      if (parseResult == null || parseResult.entries.isEmpty()) {
        _events.emit(EditorEvent.ShowSnackbar("Could not parse subtitles from selected file"))
        return@launch
      }

      val project = _uiState.value.project ?: return@launch
      // Re-assign IDs
      val newEntries = parseResult.entries.map {
        it.copy(id = idGenerator.getAndIncrement())
      }.sortedBy { it.startMs }

      val updated = project.copy(
        entries = newEntries,
        subtitleUri = subtitleUri.toString(),
        subtitleName = subtitleFileName,
        lastEditedTimeMs = System.currentTimeMillis()
      )
      _uiState.update { it.copy(project = updated) }
      updateActiveEntry(_playbackPositionMs.value)
      triggerImmediateSave(updated)

      val skipMsg = if (parseResult.skippedCount > 0) " (${parseResult.skippedCount} malformed blocks skipped)" else ""
      _events.emit(EditorEvent.ShowSnackbar("Loaded ${newEntries.size} subtitles$skipMsg"))
    }
  }

  fun onExportFolderSelected(folderUri: Uri) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      try {
        context.contentResolver.takePersistableUriPermission(
          folderUri,
          android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      } catch (_: Exception) {}

      settingsRepository.saveDefaultExportFolderUri(folderUri.toString())
      _uiState.update { it.copy(defaultExportFolderUri = folderUri.toString()) }
    }
  }

  fun exportSubtitles(filename: String, format: ExportFormat, folderUriStr: String) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val entries = _uiState.value.project?.entries ?: emptyList()
      if (entries.isEmpty()) {
        _events.emit(EditorEvent.ShowSnackbar("No subtitles to export"))
        return@launch
      }

      val folderUri = Uri.parse(folderUriStr)
      val result = SubtitleExporter.exportToFile(
        context = context,
        folderUri = folderUri,
        baseFileName = filename,
        format = format,
        entries = entries
      )

      result.onSuccess { savedFileName ->
        _uiState.update { it.copy(sheetState = EditorSheetState.None) }
        _events.emit(EditorEvent.ShowSnackbar("Saved as $savedFileName"))
      }.onFailure { error ->
        _events.emit(EditorEvent.ShowSnackbar("Export failed: ${error.localizedMessage ?: "Check permissions"}"))
      }
    }
  }

  private fun triggerDebouncedAutosave() {
    autosaveJob?.cancel()
    autosaveJob = viewModelScope.launch {
      delay(1000L)
      saveCurrentStateToDisk()
    }
  }

  private fun triggerImmediateSave(project: Project) {
    autosaveJob?.cancel()
    viewModelScope.launch {
      val pos = exoPlayer?.currentPosition ?: _playbackPositionMs.value
      val toSave = project.copy(lastPlaybackPositionMs = pos)
      projectRepository.saveProject(toSave)
    }
  }

  fun saveCurrentStateToDisk() {
    val project = _uiState.value.project ?: return
    val pos = exoPlayer?.currentPosition ?: _playbackPositionMs.value
    val toSave = project.copy(
      lastPlaybackPositionMs = pos,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    viewModelScope.launch {
      projectRepository.saveProject(toSave)
    }
  }

  override fun onCleared() {
    super.onCleared()
    saveCurrentStateToDisk()
    positionPollingJob?.cancel()
    autosaveJob?.cancel()
    exoPlayer?.release()
    exoPlayer = null
  }
}
