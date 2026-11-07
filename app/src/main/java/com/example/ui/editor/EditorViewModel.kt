package com.example.ui.editor

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.ProjectRepository
import com.example.data.SettingsRepository
import com.example.domain.exporter.SubtitleExporter
import com.example.domain.model.ExportFormat
import com.example.domain.model.Project
import com.example.domain.model.SubtitleAppearance
import com.example.domain.model.SubtitleEntry
import com.example.domain.parser.SubtitleParser
import com.example.domain.parser.TimeUtils
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

  private val projectRepository = ProjectRepository(application)
  private val settingsRepository = SettingsRepository(application)

  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<EditorEvent>()
  val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

  var exoPlayer: ExoPlayer? = null
    private set

  private var positionPollingJob: Job? = null
  private var autosaveJob: Job? = null
  private val idGenerator = AtomicLong(1000L)
  private var lastDeletedEntry: Pair<SubtitleEntry, Int>? = null

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

      _uiState.update {
        it.copy(
          isLoading = false,
          project = project,
          defaultExportFolderUri = defaultFolder,
          defaultExportFormat = defaultFormat,
          currentPositionMs = project.lastPlaybackPositionMs
        )
      }

      setupPlayer(project.videoUri, project.lastPlaybackPositionMs)
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
        _uiState.update { it.copy(currentPositionMs = pos, durationMs = dur) }
        updateActiveEntry(pos)
        delay(40L)
      }
    }
  }

  private fun stopPollingPosition() {
    positionPollingJob?.cancel()
    positionPollingJob = null
    val pos = exoPlayer?.currentPosition ?: 0L
    _uiState.update { it.copy(currentPositionMs = pos) }
    updateActiveEntry(pos)
  }

  private fun updateActiveEntry(positionMs: Long) {
    val entries = _uiState.value.project?.entries ?: return
    val active = SubtitleParser.findActiveEntry(entries, positionMs)
    val index = if (active != null) entries.indexOf(active) else -1
    _uiState.update { it.copy(activeEntry = active, activeIndex = index) }
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
  }

  fun seekTo(positionMs: Long) {
    val clamped = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(0L))
    exoPlayer?.seekTo(clamped)
    _uiState.update { it.copy(currentPositionMs = clamped) }
    updateActiveEntry(clamped)
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
    _uiState.update { it.copy(sheetState = sheet) }
  }

  fun closeSheet() {
    _uiState.update { it.copy(sheetState = EditorSheetState.None) }
  }

  fun addSubtitle(startMs: Long, endMs: Long, text: String) {
    val project = _uiState.value.project ?: return
    val newEntry = SubtitleEntry(
      id = idGenerator.getAndIncrement(),
      startMs = startMs,
      endMs = endMs,
      text = text.trim()
    )
    val updatedEntries = (project.entries + newEntry).sortedBy { it.startMs }
    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update {
      it.copy(project = updatedProject, sheetState = EditorSheetState.None)
    }
    updateActiveEntry(_uiState.value.currentPositionMs)
    triggerImmediateSave(updatedProject)

    val savedIndex = updatedEntries.indexOf(newEntry)
    if (savedIndex != -1) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ScrollToIndex(savedIndex))
      }
    }
  }

  fun updateSubtitle(id: Long, startMs: Long, endMs: Long, text: String) {
    val project = _uiState.value.project ?: return
    val updatedEntries = project.entries.map { entry ->
      if (entry.id == id) {
        entry.copy(startMs = startMs, endMs = endMs, text = text.trim())
      } else entry
    }.sortedBy { it.startMs }

    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    _uiState.update {
      it.copy(project = updatedProject, sheetState = EditorSheetState.None)
    }
    updateActiveEntry(_uiState.value.currentPositionMs)
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
    val originalIndex = project.entries.indexOf(entry)
    val updatedEntries = project.entries.filter { it.id != entry.id }
    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    lastDeletedEntry = entry to originalIndex
    _uiState.update { it.copy(project = updatedProject) }
    updateActiveEntry(_uiState.value.currentPositionMs)
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
    val (entryToRestore, _) = lastDeletedEntry ?: return
    val project = _uiState.value.project ?: return
    val updatedEntries = (project.entries + entryToRestore).sortedBy { it.startMs }
    val updatedProject = project.copy(
      entries = updatedEntries,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    lastDeletedEntry = null
    _uiState.update { it.copy(project = updatedProject) }
    updateActiveEntry(_uiState.value.currentPositionMs)
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
    updateActiveEntry(_uiState.value.currentPositionMs)
    triggerImmediateSave(updatedProject)

    val newIndex = updatedEntries.indexOf(duplicated)
    if (newIndex != -1) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ScrollToIndex(newIndex))
      }
    }
  }

  fun splitSubtitleAtPlayhead(entry: SubtitleEntry) {
    val playhead = _uiState.value.currentPositionMs
    if (playhead <= entry.startMs || playhead >= entry.endMs) {
      viewModelScope.launch {
        _events.emit(EditorEvent.ShowSnackbar("Playhead must be inside the subtitle to split"))
      }
      return
    }

    val project = _uiState.value.project ?: return
    val words = entry.text.split(" ")
    val midWordIndex = (words.size / 2).coerceAtLeast(1)
    val firstText = words.take(midWordIndex).joinToString(" ").ifEmpty { entry.text }
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
    updateActiveEntry(_uiState.value.currentPositionMs)
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
    updateActiveEntry(_uiState.value.currentPositionMs)
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
    updateActiveEntry(_uiState.value.currentPositionMs)
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

      val parseResult = try {
        context.contentResolver.openInputStream(subtitleUri)?.use { input ->
          SubtitleParser.parse(input)
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
        subtitleName = subtitleUri.lastPathSegment,
        lastEditedTimeMs = System.currentTimeMillis()
      )
      _uiState.update { it.copy(project = updated) }
      updateActiveEntry(_uiState.value.currentPositionMs)
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
      val pos = exoPlayer?.currentPosition ?: project.lastPlaybackPositionMs
      val toSave = project.copy(lastPlaybackPositionMs = pos)
      projectRepository.saveProject(toSave)
    }
  }

  fun saveCurrentStateToDisk() {
    val project = _uiState.value.project ?: return
    val pos = exoPlayer?.currentPosition ?: project.lastPlaybackPositionMs
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
