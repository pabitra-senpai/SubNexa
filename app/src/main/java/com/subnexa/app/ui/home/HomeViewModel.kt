package com.subnexa.app.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subnexa.app.data.ProjectRepository
import com.subnexa.app.data.SettingsRepository
import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.ProjectSummary
import com.subnexa.app.domain.parser.SubtitleParser
import com.subnexa.app.domain.util.FileUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface HomeEvent {
  data class NavigateToEditor(val projectId: String) : HomeEvent
  data class PromptPickVideoForSubtitle(val subtitleUri: Uri, val subtitleName: String) : HomeEvent
  data class ShowSnackbar(val message: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null) : HomeEvent
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

  val projectRepository = ProjectRepository.getInstance(application)
  val settingsRepository = SettingsRepository.getInstance(application)

  val summaries: StateFlow<List<ProjectSummary>> = projectRepository.summariesFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _events = MutableSharedFlow<HomeEvent>()
  val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

  private var pendingSubtitleUri: Uri? = null
  private var pendingSubtitleName: String? = null
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
      val defaultAppearance = settingsRepository.defaultAppearanceFlow.first()

      val (entries, subUri, subName) = if (pendingSubtitleUri != null) {
        val sUri = pendingSubtitleUri!!
        val sName = pendingSubtitleName ?: FileUtils.getFileName(context, sUri, "Subtitles")
        pendingSubtitleUri = null
        pendingSubtitleName = null

        val parseResult = try {
          context.contentResolver.openInputStream(sUri)?.use { input ->
            SubtitleParser.parse(input, sName)
          }
        } catch (e: Exception) {
          null
        }

        Triple(parseResult?.entries ?: emptyList(), sUri.toString(), sName)
      } else {
        Triple(emptyList(), null, null)
      }

      val newProject = Project(
        id = UUID.randomUUID().toString(),
        videoUri = videoUri.toString(),
        videoName = videoName,
        subtitleUri = subUri,
        subtitleName = subName,
        entries = entries,
        appearance = defaultAppearance,
        lastEditedTimeMs = System.currentTimeMillis()
      )

      projectRepository.saveProject(newProject)
      _events.emit(HomeEvent.NavigateToEditor(newProject.id))
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
