package com.subnexa.app.ui.editor

import com.subnexa.app.domain.model.ExportFormat
import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.SubtitleEntry

sealed interface EditorSheetState {
  data object None : EditorSheetState
  data class Edit(val entry: SubtitleEntry?, val isNew: Boolean) : EditorSheetState
  data object GlobalSync : EditorSheetState
  data object Export : EditorSheetState
  data object Appearance : EditorSheetState
}

enum class NudgeMode { START, END, BOTH }

data class SearchState(
  val isOpen: Boolean = false,
  val query: String = "",
  val replacement: String = "",
  val matchCase: Boolean = false,
  val showReplace: Boolean = false,
  /** Position inside the list of matching lines (0-based). */
  val currentIndex: Int = 0
)

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
  val playerError: String? = null,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false,
  val search: SearchState = SearchState()
)
