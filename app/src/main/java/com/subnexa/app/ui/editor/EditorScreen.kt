package com.subnexa.app.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subnexa.app.domain.edit.SubtitleEditing
import com.subnexa.app.domain.search.SubtitleSearch
import com.subnexa.app.domain.validation.SubtitleIssues
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Background
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

  var showNudge by rememberSaveable { mutableStateOf(false) }
  var nudgeMode by rememberSaveable { mutableStateOf(NudgeMode.BOTH) }
  var lastActiveId by remember { mutableStateOf<Long?>(null) }
  var viewMode by rememberSaveable { mutableStateOf(EditorViewMode.LIST) }
  var selectedLineId by rememberSaveable { mutableStateOf<Long?>(null) }
  val uiScope = rememberCoroutineScope()

  // The line the nudge buttons act on: the one under the playhead, or the last one that was.
  LaunchedEffect(uiState.activeEntry?.id) {
    uiState.activeEntry?.id?.let { lastActiveId = it }
  }

  // Autosave and back handling
  BackHandler {
    if (uiState.isFullscreen) {
      viewModel.toggleFullscreen()
    } else if (uiState.search.isOpen) {
      viewModel.closeSearch()
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
        appearance = uiState.project?.appearance ?: com.subnexa.app.domain.model.SubtitleAppearance(),
        isPlaying = uiState.isPlaying,
        playbackPositionFlow = viewModel.playbackPositionMs,
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
        EditorTopBar(
          title = uiState.project?.videoName ?: "Video Editor",
          canUndo = uiState.canUndo,
          canRedo = uiState.canRedo,
          onBack = {
            viewModel.saveCurrentStateToDisk()
            onNavigateBack()
          },
          onUndo = { viewModel.undo() },
          onRedo = { viewModel.redo() }
        )
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
            appearance = uiState.project?.appearance ?: com.subnexa.app.domain.model.SubtitleAppearance(),
            isPlaying = uiState.isPlaying,
            playbackPositionFlow = viewModel.playbackPositionMs,
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
          EditorActionsRow(
            onImport = { importSubtitleLauncher.launch(arrayOf("*/*")) },
            onAdd = {
              viewModel.openSheet(EditorSheetState.Edit(entry = null, isNew = true))
            },
            onSync = { viewModel.openSheet(EditorSheetState.GlobalSync) },
            onExport = { viewModel.openSheet(EditorSheetState.Export) }
          )

          // 4. Subtitle List Area
          val entries = uiState.project?.entries ?: emptyList()
          val search = uiState.search
          val issues = remember(entries) { SubtitleIssues.analyze(entries) }
          val issueIndexes = remember(entries, issues) {
            entries.indices.filter { entries[it].id in issues }
          }
          val matchIds = remember(entries, search.isOpen, search.query, search.matchCase) {
            if (search.isOpen) {
              SubtitleSearch.findMatchIds(entries, search.query, search.matchCase)
            } else emptyList()
          }
          val currentMatchId = matchIds.getOrNull(
            search.currentIndex.coerceIn(0, (matchIds.size - 1).coerceAtLeast(0))
          )

          // List | Timeline switch
          EditorViewToggle(
            mode = viewMode,
            onModeChange = { viewMode = it }
          )

          // Count header with problem chip, nudge toggle and find toggle
          SubtitleListHeader(
            subtitleCount = entries.size,
            issueCount = issueIndexes.size,
            showNudge = showNudge,
            searchOpen = search.isOpen,
            onIssueClick = {
              if (viewMode == EditorViewMode.TIMELINE) {
                // Timeline: jump the playhead to the next problem line after the current time.
                val now = viewModel.playbackPositionMs.value
                val problemLines = entries.filter { it.id in issues }.sortedBy { it.startMs }
                val target = problemLines.firstOrNull { it.startMs > now + 1L } ?: problemLines.firstOrNull()
                if (target != null) {
                  selectedLineId = target.id
                  viewModel.seekTo(target.startMs)
                }
              } else {
                // Each tap moves on to the next problem below the first visible line.
                val reference = listState.firstVisibleItemIndex
                val target = issueIndexes.firstOrNull { it > reference } ?: issueIndexes.first()
                uiScope.launch { listState.animateScrollToItem(target) }
              }
            },
            onToggleNudge = { showNudge = !showNudge },
            onToggleSearch = {
              if (search.isOpen) viewModel.closeSearch() else viewModel.openSearch()
            }
          )

          val duplicateCount = remember(entries) { SubtitleEditing.removeDuplicates(entries).removedCount }
          if (duplicateCount > 0) {
            DuplicatesBanner(
              count = duplicateCount,
              onRemove = { viewModel.removeDuplicates() }
            )
          }

          AnimatedVisibility(
            visible = search.isOpen,
            enter = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
          ) {
            EditorSearchBar(
              state = search,
              matchCount = matchIds.size,
              onQueryChange = { viewModel.setSearchQuery(it) },
              onReplacementChange = { viewModel.setSearchReplacement(it) },
              onToggleMatchCase = { viewModel.toggleSearchMatchCase() },
              onToggleReplace = { viewModel.toggleSearchReplace() },
              onPrevious = { viewModel.stepSearchMatch(forward = false) },
              onNext = { viewModel.stepSearchMatch(forward = true) },
              onReplaceCurrent = { viewModel.replaceCurrentMatch() },
              onReplaceAll = { viewModel.replaceAllMatches() },
              onClose = { viewModel.closeSearch() }
            )
          }

          AnimatedVisibility(
            visible = showNudge,
            enter = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
          ) {
            val nudgeEntry = entries.firstOrNull { it.id == lastActiveId }
            EditorNudgeBar(
              entry = nudgeEntry,
              lineNumber = entries.indexOfFirst { it.id == lastActiveId } + 1,
              mode = nudgeMode,
              onModeChange = { nudgeMode = it },
              onNudge = { delta ->
                nudgeEntry?.let { viewModel.nudgeSubtitle(it.id, nudgeMode, delta) }
              }
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
              EmptySubtitlesContent(
                onAddFirst = {
                  viewModel.openSheet(EditorSheetState.Edit(entry = null, isNew = true))
                }
              )
            }
          } else if (viewMode == EditorViewMode.TIMELINE) {
            val positionState = viewModel.playbackPositionMs.collectAsStateWithLifecycle()
            val selectedEntry = entries.firstOrNull { it.id == selectedLineId }
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
            ) {
              SubtitleTimeline(
                entries = entries,
                issues = issues,
                selectedId = selectedEntry?.id,
                positionMs = positionState,
                durationMs = uiState.durationMs,
                onSelect = { selectedLineId = it },
                onSeek = { viewModel.seekTo(it) },
                onScrubStart = { viewModel.pause() },
                onDragStart = {
                  viewModel.pause()
                  viewModel.beginTimingDrag()
                },
                onDrag = { id, start, end -> viewModel.dragSubtitleTiming(id, start, end) },
                onDragEnd = { viewModel.endTimingDrag() }
              )
              SelectedLinePanel(
                entry = selectedEntry,
                lineNumber = entries.indexOfFirst { it.id == selectedEntry?.id } + 1,
                onEdit = {
                  selectedEntry?.let {
                    viewModel.openSheet(EditorSheetState.Edit(entry = it, isNew = false))
                  }
                },
                onSplit = { selectedEntry?.let { viewModel.splitSubtitleAtPlayhead(it) } },
                onDuplicate = { selectedEntry?.let { viewModel.duplicateSubtitle(it) } },
                onDelete = {
                  selectedEntry?.let {
                    viewModel.deleteSubtitle(it)
                    selectedLineId = null
                  }
                }
              )
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
                  modifier = Modifier.animateItem(),
                  index = index + 1,
                  entry = entry,
                  isActive = isActive,
                  issues = issues[entry.id].orEmpty(),
                  highlight = if (search.isOpen) search.query else "",
                  highlightIgnoreCase = !search.matchCase,
                  isCurrentMatch = entry.id == currentMatchId,
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
  EditorSheetHost(
    uiState = uiState,
    viewModel = viewModel,
    onPickFolder = { folderPickerLauncher.launch(null) }
  )
}
