package com.subnexa.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Renders whichever bottom sheet [EditorUiState.sheetState] asks for. */
@Composable
internal fun EditorSheetHost(
  uiState: EditorUiState,
  viewModel: EditorViewModel,
  onPickFolder: () -> Unit
) {
  when (val sheet = uiState.sheetState) {
    is EditorSheetState.None -> Unit

    is EditorSheetState.Edit -> {
      EditSubtitleBottomSheet(
        entry = sheet.entry,
        isNew = sheet.isNew,
        currentPlayheadMs = uiState.currentPositionMs,
        isPlaying = uiState.isPlaying,
        allEntries = uiState.project?.entries ?: emptyList(),
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onDismiss = { viewModel.closeSheet() },
        onSave = { startMs, endMs, text ->
          if (sheet.isNew || sheet.entry == null) {
            viewModel.addSubtitle(startMs, endMs, text)
          } else {
            viewModel.updateSubtitle(sheet.entry.id, startMs, endMs, text)
          }
        }
      )
    }

    is EditorSheetState.GlobalSync -> {
      GlobalSyncBottomSheet(
        entries = uiState.project?.entries ?: emptyList(),
        playbackPositionFlow = viewModel.playbackPositionMs,
        isPlaying = uiState.isPlaying,
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onDismiss = { viewModel.closeSheet() },
        onApply = { offsetSeconds ->
          viewModel.applyGlobalSync(offsetSeconds)
        },
        onApplyTwoPoint = { firstId, firstMs, lastId, lastMs ->
          viewModel.applyTwoPointSync(firstId, firstMs, lastId, lastMs)
        }
      )
    }

    is EditorSheetState.Export -> {
      ExportBottomSheet(
        initialFilename = uiState.project?.videoName ?: "Subtitles",
        defaultFormat = uiState.defaultExportFormat,
        defaultFolderUriStr = uiState.defaultExportFolderUri,
        onDismiss = { viewModel.closeSheet() },
        onPickFolder = onPickFolder,
        onExport = { filename, format, folderUriStr ->
          viewModel.exportSubtitles(filename, format, folderUriStr)
        }
      )
    }

    is EditorSheetState.Appearance -> {
      val entries = uiState.project?.entries
      val subtitleInfo = androidx.compose.runtime.remember(entries) {
        com.subnexa.app.domain.font.SubtitleLanguageInfo.from(entries?.map { it.text } ?: emptyList())
      }
      AppearanceBottomSheet(
        initialAppearance = uiState.project?.appearance ?: com.subnexa.app.domain.model.SubtitleAppearance(),
        subtitleInfo = subtitleInfo,
        onDismiss = { viewModel.closeSheet() },
        onAppearanceChanged = { newAppearance ->
          viewModel.updateAppearance(newAppearance)
        }
      )
    }
  }
}
