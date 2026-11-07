package com.example.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.SubtitleEntry
import com.example.domain.parser.TimeUtils
import com.example.ui.components.HairlineCard
import com.example.ui.components.PrimaryAccentButton
import com.example.ui.theme.Accent
import com.example.ui.theme.ActiveRowTint
import com.example.ui.theme.Background
import com.example.ui.theme.Border
import com.example.ui.theme.MonospaceTimestampStyle
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
  projectId: String,
  viewModel: EditorViewModel,
  onNavigateBack: () -> Unit
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val listState = rememberLazyListState()

  // SAF Folder picker for export
  val folderPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.onExportFolderSelected(uri)
    }
  }

  // SAF Subtitle file picker for re-import
  val importSubtitleLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.importExternalSubtitles(uri)
    }
  }

  LaunchedEffect(projectId) {
    viewModel.initProject(projectId)
  }

  // Autosave and back handling
  BackHandler {
    if (uiState.isFullscreen) {
      viewModel.toggleFullscreen()
    } else {
      viewModel.saveCurrentStateToDisk()
      onNavigateBack()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      viewModel.saveCurrentStateToDisk()
    }
  }

  // Auto-scroll to active index during playback if not actively dragging
  LaunchedEffect(uiState.activeIndex, uiState.isPlaying) {
    if (uiState.isPlaying && uiState.activeIndex >= 0 && !listState.isScrollInProgress) {
      listState.animateScrollToItem(uiState.activeIndex)
    }
  }

  LaunchedEffect(Unit) {
    viewModel.events.collectLatest { event ->
      when (event) {
        is EditorEvent.ShowSnackbar -> {
          val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = event.actionLabel,
            duration = SnackbarDuration.Short
          )
          if (result == SnackbarResult.ActionPerformed) {
            event.onAction?.invoke()
          }
        }
        is EditorEvent.ScrollToIndex -> {
          if (event.index in 0 until (uiState.project?.entries?.size ?: 0)) {
            listState.animateScrollToItem(event.index)
          }
        }
        is EditorEvent.OpenExportFolderPicker -> {
          folderPickerLauncher.launch(null)
        }
      }
    }
  }

  // Fullscreen layout branch
  if (uiState.isFullscreen) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
      VideoPlayerSection(
        exoPlayer = viewModel.exoPlayer,
        activeEntry = uiState.activeEntry,
        appearance = uiState.project?.appearance ?: com.example.domain.model.SubtitleAppearance(),
        isPlaying = uiState.isPlaying,
        currentPositionMs = uiState.currentPositionMs,
        durationMs = uiState.durationMs,
        playbackSpeed = uiState.playbackSpeed,
        isFullscreen = true,
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSeekTo = { viewModel.seekTo(it) },
        onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
        onOpenAppearance = { viewModel.openSheet(EditorSheetState.Appearance) },
        onToggleFullscreen = { viewModel.toggleFullscreen() },
        modifier = Modifier.fillMaxSize()
      )
    }
  } else {
    Scaffold(
      containerColor = Background,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        // 1. Compact Top Bar: back arrow and video name
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              viewModel.saveCurrentStateToDisk()
              onNavigateBack()
            },
            modifier = Modifier.size(40.dp).testTag("editor_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = TextPrimary
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Text(
            text = uiState.project?.videoName ?: "Video Editor",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.SemiBold,
              fontSize = 16.sp
            ),
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
          )
        }
      }
    ) { innerPadding ->
      if (uiState.isLoading) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(color = Accent)
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          // 2. Video Player Section (16:9)
          VideoPlayerSection(
            exoPlayer = viewModel.exoPlayer,
            activeEntry = uiState.activeEntry,
            appearance = uiState.project?.appearance ?: com.example.domain.model.SubtitleAppearance(),
            isPlaying = uiState.isPlaying,
            currentPositionMs = uiState.currentPositionMs,
            durationMs = uiState.durationMs,
            playbackSpeed = uiState.playbackSpeed,
            isFullscreen = false,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
            onOpenAppearance = { viewModel.openSheet(EditorSheetState.Appearance) },
            onToggleFullscreen = { viewModel.toggleFullscreen() },
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(16f / 9f)
          )

          // 3. Four Equal Actions Row: Import, Add, Sync, Export
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(Surface)
              .border(0.5.dp, Border)
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            EditorActionButton(
              icon = Icons.Outlined.FileUpload,
              label = "Import",
              onClick = {
                importSubtitleLauncher.launch(arrayOf("*/*"))
              },
              modifier = Modifier.weight(1f),
              testTag = "action_import"
            )

            EditorActionButton(
              icon = Icons.Outlined.Add,
              label = "Add",
              onClick = {
                viewModel.openSheet(
                  EditorSheetState.Edit(entry = null, isNew = true)
                )
              },
              modifier = Modifier.weight(1f),
              testTag = "action_add"
            )

            EditorActionButton(
              icon = Icons.Outlined.Sync,
              label = "Sync",
              onClick = {
                viewModel.openSheet(EditorSheetState.GlobalSync)
              },
              modifier = Modifier.weight(1f),
              testTag = "action_sync"
            )

            EditorActionButton(
              icon = Icons.Outlined.FileDownload,
              label = "Export",
              onClick = {
                viewModel.openSheet(EditorSheetState.Export)
              },
              modifier = Modifier.weight(1f),
              testTag = "action_export"
            )
          }

          // 4. Subtitle List Area
          val entries = uiState.project?.entries ?: emptyList()

          // Count Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${entries.size} subtitles",
              style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
              ),
              color = TextSecondary
            )
          }

          if (entries.isEmpty()) {
            // 5. Empty State (no subtitles)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                Icon(
                  imageVector = Icons.Outlined.Subtitles,
                  contentDescription = null,
                  tint = TextMuted,
                  modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "No subtitles yet",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Pause the video where a line starts, then add it.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextMuted
                )
                Spacer(modifier = Modifier.height(18.dp))
                PrimaryAccentButton(
                  text = "Add your first subtitle",
                  onClick = {
                    viewModel.openSheet(EditorSheetState.Edit(entry = null, isNew = true))
                  },
                  testTag = "add_first_subtitle_button"
                )
              }
            }
          } else {
            LazyColumn(
              state = listState,
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              contentPadding = PaddingValues(bottom = 24.dp)
            ) {
              itemsIndexed(
                items = entries,
                key = { _, entry -> entry.id }
              ) { index, entry ->
                val isActive = index == uiState.activeIndex

                SubtitleRowItem(
                  index = index + 1,
                  entry = entry,
                  isActive = isActive,
                  onSeek = {
                    viewModel.seekTo(entry.startMs)
                  },
                  onEdit = {
                    viewModel.openSheet(EditorSheetState.Edit(entry = entry, isNew = false))
                  },
                  onDelete = {
                    viewModel.deleteSubtitle(entry)
                  },
                  onDuplicate = {
                    viewModel.duplicateSubtitle(entry)
                  },
                  onSplitAtPlayhead = {
                    viewModel.splitSubtitleAtPlayhead(entry)
                  },
                  onMergeWithNext = {
                    viewModel.mergeWithNext(entry)
                  }
                )
              }
            }
          }
        }
      }
    }
  }

  // Bottom Sheets
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
        subtitleCount = uiState.project?.entries?.size ?: 0,
        onDismiss = { viewModel.closeSheet() },
        onApply = { offsetSeconds ->
          viewModel.applyGlobalSync(offsetSeconds)
        }
      )
    }

    is EditorSheetState.Export -> {
      ExportBottomSheet(
        initialFilename = uiState.project?.videoName ?: "Subtitles",
        defaultFormat = uiState.defaultExportFormat,
        defaultFolderUriStr = uiState.defaultExportFolderUri,
        onDismiss = { viewModel.closeSheet() },
        onPickFolder = { folderPickerLauncher.launch(null) },
        onExport = { filename, format, folderUriStr ->
          viewModel.exportSubtitles(filename, format, folderUriStr)
        }
      )
    }

    is EditorSheetState.Appearance -> {
      AppearanceBottomSheet(
        initialAppearance = uiState.project?.appearance ?: com.example.domain.model.SubtitleAppearance(),
        onDismiss = { viewModel.closeSheet() },
        onAppearanceChanged = { newAppearance ->
          viewModel.updateAppearance(newAppearance)
        }
      )
    }
  }
}

@Composable
private fun EditorActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = TextPrimary,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.height(3.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.Medium,
          fontSize = 11.5.sp
        ),
        color = TextSecondary
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtitleRowItem(
  index: Int,
  entry: SubtitleEntry,
  isActive: Boolean,
  onSeek: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDuplicate: () -> Unit,
  onSplitAtPlayhead: () -> Unit,
  onMergeWithNext: () -> Unit
) {
  var showMenu by remember { mutableStateOf(false) }

  val rowBackground = if (isActive) ActiveRowTint else Color.Transparent

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(rowBackground)
      .combinedClickable(
        onClick = onSeek,
        onLongClick = { showMenu = true }
      )
      .padding(horizontal = 16.dp, vertical = 10.dp)
      .testTag("subtitle_row_$index")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Red left indicator line for active row
      Box(
        modifier = Modifier
          .width(3.dp)
          .height(36.dp)
          .background(if (isActive) Accent else Color.Transparent)
      )

      Spacer(modifier = Modifier.width(10.dp))

      // 1-based index number
      Text(
        text = "$index",
        style = MonospaceTimestampStyle.copy(
          color = if (isActive) Accent else TextMuted,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        ),
        modifier = Modifier.width(36.dp)
      )

      // Content: text + timestamps
      Column(
        modifier = Modifier.weight(1f)
      ) {
        Text(
          text = entry.text,
          style = MaterialTheme.typography.bodyMedium.copy(
            color = if (isActive) TextPrimary else TextPrimary.copy(alpha = 0.9f),
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            fontSize = 14.sp
          ),
          maxLines = 2
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "${TimeUtils.formatTimestamp(entry.startMs, '.')}  →  ${TimeUtils.formatTimestamp(entry.endMs, '.')}",
          style = MonospaceTimestampStyle.copy(
            color = if (isActive) Accent else TextSecondary,
            fontSize = 11.5.sp
          )
        )
      }

      // Actions: Edit and Delete
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onEdit,
          modifier = Modifier.size(36.dp).testTag("edit_subtitle_$index")
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit",
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(36.dp).testTag("delete_subtitle_$index")
        ) {
          Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "Delete",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // Long press dropdown menu: Duplicate, Split, Merge
    DropdownMenu(
      expanded = showMenu,
      onDismissRequest = { showMenu = false },
      modifier = Modifier
        .background(Surface)
        .border(0.5.dp, Border, RoundedCornerShape(8.dp))
    ) {
      DropdownMenuItem(
        text = { Text("Duplicate", color = TextPrimary) },
        leadingIcon = {
          Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = Accent)
        },
        onClick = {
          showMenu = false
          onDuplicate()
        }
      )
      DropdownMenuItem(
        text = { Text("Split at playhead", color = TextPrimary) },
        leadingIcon = {
          Icon(Icons.Outlined.CallSplit, contentDescription = null, tint = Accent)
        },
        onClick = {
          showMenu = false
          onSplitAtPlayhead()
        }
      )
      DropdownMenuItem(
        text = { Text("Merge with next", color = TextPrimary) },
        leadingIcon = {
          Icon(Icons.Outlined.Merge, contentDescription = null, tint = Accent)
        },
        onClick = {
          showMenu = false
          onMergeWithNext()
        }
      )
    }
  }
}
