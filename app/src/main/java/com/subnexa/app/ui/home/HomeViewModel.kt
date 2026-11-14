package com.subnexa.app.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subnexa.app.data.ProjectRepository
import com.subnexa.app.data.SettingsRepository
import com.subnexa.app.domain.embedded.EmbeddedSubtitleExtractor
import com.subnexa.app.domain.embedded.EmbeddedTrack
import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.ProjectSummary
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.SubtitleParser
import com.subnexa.app.domain.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface HomeEvent {
  data class NavigateToEditor(val projectId: String) : HomeEvent
  data class PromptPickVideoForSubtitle(val subtitleUri: Uri, val subtitleName: String) : HomeEvent
  /** Open the subtitle file picker for the video the user just opened. */
  data object PickSubtitleFileForVideo : HomeEvent
  data class ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null) : HomeEvent
}

/** A video that was picked and is waiting for the user to decide where its subtitles come from. */
data class PendingVideo(val uri: Uri, val name: String)

/** Steps of the "use the subtitles stored inside the video" flow shown on the home screen. */
sealed interface EmbeddedFlowState {
  data object Idle : EmbeddedFlowState
  /** Quickly reading the video header to see which subtitle tracks it has. */
  data object Scanning : EmbeddedFlowState
  /** Tracks found: ask "import a subtitle file" or "use the video's subtitles". */
  data class Choice(val video: PendingVideo, val tracks: List<EmbeddedTrack>) : EmbeddedFlowState
  /** Show how many tracks exist and let the user pick one. */
  data class TrackList(val video: PendingVideo, val tracks: List<EmbeddedTrack>) : EmbeddedFlowState
  /** Reading the chosen track out of the video (can take a while for large files). */
  data class Loading(
    val video: PendingVideo,
    val tracks: List<EmbeddedTrack>,
    val track: EmbeddedTrack,
    val progress: Float?
  ) : EmbeddedFlowState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

  val projectRepository = ProjectRepository.getInstance(application)
  val settingsRepository = SettingsRepository.getInstance(application)

  val summaries: StateFlow<List<ProjectSummary>> = projectRepository.summariesFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _events = MutableSharedFlow<HomeEvent>()
  val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

  private val _embeddedState = MutableStateFlow<EmbeddedFlowState>(EmbeddedFlowState.Idle)
  val embeddedState: StateFlow<EmbeddedFlowState> = _embeddedState.asStateFlow()

  private var pendingSubtitleUri: Uri? = null
  private var pendingSubtitleName: String? = null
  private var pendingVideoForFile: PendingVideo? = null
  private var extractionJob: Job? = null
  private var lastDeletedProject: Project? = null

  fun onVideoSelected(videoUri: Uri) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      try {
        context.contentResolver.takePersistableUriPermission(
          videoUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: Exception) {
        // Some providers do not support persistable URIs, continue anyway
      }

      val videoName = FileUtils.getFileName(context, videoUri, "Video")

      // Existing flow: a subtitle file was chosen first, now its video arrived.
      if (pendingSubtitleUri != null) {
        val sUri = pendingSubtitleUri!!
        val sName = pendingSubtitleName ?: FileUtils.getFileName(context, sUri, "Subtitles")
        pendingSubtitleUri = null
        pendingSubtitleName = null

        val entries = parseSubtitleFile(sUri, sName)
        createProjectAndOpen(videoUri, videoName, entries, sUri.toString(), sName)
        return@launch
      }

      // New: look inside the video for subtitle tracks.
      _embeddedState.value = EmbeddedFlowState.Scanning
      val tracks = try {
        EmbeddedSubtitleExtractor.listTracks(context, videoUri)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        emptyList()
      }

      if (tracks.any { it.isEditable }) {
        _embeddedState.value = EmbeddedFlowState.Choice(PendingVideo(videoUri, videoName), tracks)
      } else {
        // No readable subtitle track: behave exactly as before.
        _embeddedState.value = EmbeddedFlowState.Idle
        createProjectAndOpen(videoUri, videoName, emptyList(), null, null)
      }
    }
  }

  fun onSubtitleSelected(subtitleUri: Uri) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      try {
        context.contentResolver.takePersistableUriPermission(
          subtitleUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: Exception) {
        // Ignore
      }

      val subtitleName = FileUtils.getFileName(context, subtitleUri, "Subtitles")
      pendingSubtitleUri = subtitleUri
      pendingSubtitleName = subtitleName

      _events.emit(HomeEvent.PromptPickVideoForSubtitle(subtitleUri, subtitleName))
    }
  }

  // ---------------------------------------------------------------------------------------
  // Embedded subtitle flow
  // ---------------------------------------------------------------------------------------

  /** Choice dialog -> "Use video's subtitles". */
  fun chooseUseVideoSubtitles() {
    val state = _embeddedState.value as? EmbeddedFlowState.Choice ?: return
    _embeddedState.value = EmbeddedFlowState.TrackList(state.video, state.tracks)
  }

  /** Choice dialog -> "Import subtitle file". */
  fun chooseImportSubtitleFile() {
    val state = _embeddedState.value as? EmbeddedFlowState.Choice ?: return
    pendingVideoForFile = state.video
    _embeddedState.value = EmbeddedFlowState.Idle
    viewModelScope.launch { _events.emit(HomeEvent.PickSubtitleFileForVideo) }
  }

  /** Track list -> back to the two-option choice. */
  fun backToChoice() {
    val state = _embeddedState.value as? EmbeddedFlowState.TrackList ?: return
    _embeddedState.value = EmbeddedFlowState.Choice(state.video, state.tracks)
  }

  /** Close the flow without opening the video. */
  fun dismissEmbeddedFlow() {
    extractionJob?.cancel()
    extractionJob = null
    _embeddedState.value = EmbeddedFlowState.Idle
  }

  fun onSubtitleFileForVideoSelected(subtitleUri: Uri) {
    val video = pendingVideoForFile ?: return
    pendingVideoForFile = null
    viewModelScope.launch {
      val context = getApplication<Application>()
      try {
        context.contentResolver.takePersistableUriPermission(
          subtitleUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: Exception) {
        // Ignore
      }
      val subtitleName = FileUtils.getFileName(context, subtitleUri, "Subtitles")
      val entries = parseSubtitleFile(subtitleUri, subtitleName)
      createProjectAndOpen(video.uri, video.name, entries, subtitleUri.toString(), subtitleName)
    }
  }

  /** The subtitle picker was closed without choosing a file: open the video with no subtitles. */
  fun onSubtitleFileForVideoCancelled() {
    val video = pendingVideoForFile ?: return
    pendingVideoForFile = null
    viewModelScope.launch {
      createProjectAndOpen(video.uri, video.name, emptyList(), null, null)
    }
  }

  fun selectEmbeddedTrack(track: EmbeddedTrack) {
    val state = _embeddedState.value as? EmbeddedFlowState.TrackList ?: return
    if (!track.isEditable) {
      viewModelScope.launch {
        _events.emit(HomeEvent.ShowSnackbar("This track is image-based and can't be edited."))
      }
      return
    }

    val context = getApplication<Application>()
    _embeddedState.value = EmbeddedFlowState.Loading(state.video, state.tracks, track, null)

    extractionJob?.cancel()
    extractionJob = viewModelScope.launch {
      try {
        val entries = EmbeddedSubtitleExtractor.extractTrack(context, state.video.uri, track) { p ->
          _embeddedState.update { if (it is EmbeddedFlowState.Loading) it.copy(progress = p) else it }
        }

        if (entries.isEmpty()) {
          _embeddedState.value = EmbeddedFlowState.TrackList(state.video, state.tracks)
          _events.emit(HomeEvent.ShowSnackbar("No subtitle lines found in this track."))
          return@launch
        }

        _embeddedState.value = EmbeddedFlowState.Idle
        createProjectAndOpen(
          videoUri = state.video.uri,
          videoName = state.video.name,
          entries = entries,
          subtitleUri = null,
          subtitleName = "Embedded: ${track.displayName} (${track.formatName})"
        )
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _embeddedState.value = EmbeddedFlowState.TrackList(state.video, state.tracks)
        _events.emit(HomeEvent.ShowSnackbar("Could not read this subtitle track."))
      }
    }
  }

  /** Loading dialog -> Cancel. Returns to the track list. */
  fun cancelEmbeddedLoading() {
    val state = _embeddedState.value as? EmbeddedFlowState.Loading ?: return
    extractionJob?.cancel()
    extractionJob = null
    _embeddedState.value = EmbeddedFlowState.TrackList(state.video, state.tracks)
  }

  // ---------------------------------------------------------------------------------------

  private fun parseSubtitleFile(subtitleUri: Uri, subtitleName: String): List<SubtitleEntry> {
    val context = getApplication<Application>()
    val parseResult = try {
      context.contentResolver.openInputStream(subtitleUri)?.use { input ->
        SubtitleParser.parse(input, subtitleName)
      }
    } catch (e: Exception) {
      null
    }
    return parseResult?.entries ?: emptyList()
  }

  private suspend fun createProjectAndOpen(
    videoUri: Uri,
    videoName: String,
    entries: List<SubtitleEntry>,
    subtitleUri: String?,
    subtitleName: String?
  ) {
    val defaultAppearance = settingsRepository.defaultAppearanceFlow.first()
    val newProject = Project(
      id = UUID.randomUUID().toString(),
      videoUri = videoUri.toString(),
      videoName = videoName,
      subtitleUri = subtitleUri,
      subtitleName = subtitleName,
      entries = entries,
      appearance = defaultAppearance,
      lastEditedTimeMs = System.currentTimeMillis()
    )
    projectRepository.saveProject(newProject)
    _events.emit(HomeEvent.NavigateToEditor(newProject.id))
  }

  fun renameProject(summary: ProjectSummary, newName: String) {
    val name = newName.trim()
    if (name.isEmpty() || name == summary.videoName) return
    viewModelScope.launch {
      projectRepository.renameProject(summary.id, name)
    }
  }

  fun deleteProject(summary: ProjectSummary) {
    viewModelScope.launch {
      val deleted = projectRepository.deleteProject(summary.id)
      if (deleted != null) {
        lastDeletedProject = deleted
        _events.emit(
          HomeEvent.ShowSnackbar(
            message = "Project deleted",
            actionLabel = "Undo",
            onAction = {
              undoDelete()
            }
          )
        )
      }
    }
  }

  private fun undoDelete() {
    viewModelScope.launch {
      val projectToRestore = lastDeletedProject ?: return@launch
      projectRepository.restoreProject(projectToRestore)
      lastDeletedProject = null
    }
  }
}
