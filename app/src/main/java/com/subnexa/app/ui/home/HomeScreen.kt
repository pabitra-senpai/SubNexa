package com.subnexa.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subnexa.app.domain.model.ProjectSummary
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.ui.components.HairlineCard
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Background
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.DangerFill
import com.subnexa.app.ui.theme.DangerRed
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: HomeViewModel,
  onNavigateToEditor: (String) -> Unit,
  onNavigateToSettings: () -> Unit
) {
  val summaries by viewModel.summaries.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val embeddedState by viewModel.embeddedState.collectAsStateWithLifecycle()
  var renameTarget by remember { mutableStateOf<ProjectSummary?>(null) }

  // File pickers
  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.onVideoSelected(uri)
    }
  }

  val subtitlePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.onSubtitleSelected(uri)
    }
  }

  // Subtitle file picker used when the video also has embedded tracks but the user prefers a file
  val subtitleForVideoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.onSubtitleFileForVideoSelected(uri)
    } else {
      viewModel.onSubtitleFileForVideoCancelled()
    }
  }

  LaunchedEffect(Unit) {
    viewModel.events.collectLatest { event ->
      when (event) {
        is HomeEvent.NavigateToEditor -> {
          onNavigateToEditor(event.projectId)
        }
        is HomeEvent.PromptPickVideoForSubtitle -> {
          // Open video picker to select the matching video
          videoPickerLauncher.launch(arrayOf("video/*"))
        }
        is HomeEvent.PickSubtitleFileForVideo -> {
          subtitleForVideoLauncher.launch(arrayOf("*/*"))
        }
        is HomeEvent.ShowSnackbar -> {
          val result = snackbarHostState.showSnackbar(
            message = event.message,
            actionLabel = event.actionLabel,
            duration = SnackbarDuration.Short
          )
          if (result == SnackbarResult.ActionPerformed) {
            event.onAction?.invoke()
          }
        }
      }
    }
  }

  Scaffold(
    containerColor = Background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.statusBars)
          .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // SubNexa icon mark
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Surface)
              .border(0.5.dp, Border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.Subtitles,
              contentDescription = "SubNexa",
              tint = Accent,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "SubNexa",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 0.5.sp
              ),
              color = TextPrimary
            )
            Text(
              text = "Edit. Sync. Export.",
              style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp
              ),
              color = TextSecondary
            )
          }
        }

        IconButton(
          onClick = onNavigateToSettings,
          modifier = Modifier
            .size(44.dp)
            .testTag("settings_button")
        ) {
          Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Settings",
            tint = TextSecondary
          )
        }
      }
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(8.dp))
        // Two main action buttons
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          PrimaryAccentButton(
            text = "Open video",
            onClick = {
              videoPickerLauncher.launch(arrayOf("video/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            testTag = "open_video_button"
          )

          SecondarySubNexaButton(
            text = "Import subtitle",
            onClick = {
              // Accepts */* to support any mime type sent by devices
              subtitlePickerLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            testTag = "import_subtitle_button"
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Recent projects",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
          ),
          color = TextPrimary
        )
      }

      if (summaries.isEmpty()) {
        item {
          HairlineCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceVariant.copy(alpha = 0.3f)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.Movie,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "No recent projects yet",
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Medium
                ),
                color = TextSecondary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Open a video to start editing subtitles.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
              )
            }
          }
        }
      } else {
        items(
          items = summaries,
          key = { it.id }
        ) { summary ->
          val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { dismissValue ->
              if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                viewModel.deleteProject(summary)
                true
              } else false
            }
          )

          SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            backgroundContent = {
              val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) DangerFill else Color.Transparent,
                label = "delete_background"
              )
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .clip(RoundedCornerShape(12.dp))
                  .background(color)
                  .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
              ) {
                Icon(
                  imageVector = Icons.Outlined.Delete,
                  contentDescription = "Delete",
                  tint = DangerRed
                )
              }
            }
          ) {
            ProjectCard(
              summary = summary,
              onClick = { onNavigateToEditor(summary.id) },
              onRename = { renameTarget = summary },
              onDelete = { viewModel.deleteProject(summary) }
            )
          }
        }
      }
    }
  }

  EmbeddedSubtitleDialogs(
    state = embeddedState,
    onUseVideoSubtitles = viewModel::chooseUseVideoSubtitles,
    onImportFile = viewModel::chooseImportSubtitleFile,
    onDismiss = viewModel::dismissEmbeddedFlow,
    onBackToChoice = viewModel::backToChoice,
    onSelectTrack = viewModel::selectEmbeddedTrack,
    onCancelLoading = viewModel::cancelEmbeddedLoading
  )

  renameTarget?.let { target ->
    RenameProjectDialog(
      currentName = target.videoName,
      onConfirm = { newName ->
        viewModel.renameProject(target, newName)
        renameTarget = null
      },
      onDismiss = { renameTarget = null }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
  summary: ProjectSummary,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit
) {
  val isEmpty = summary.subtitleCount == 0
  val shape = RoundedCornerShape(12.dp)

  HairlineCard(
    // Tap opens the project, long-press renames it.
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .combinedClickable(onClick = onClick, onLongClick = onRename),
    borderColor = if (isEmpty) Accent.copy(alpha = 0.45f) else Border,
    backgroundColor = if (isEmpty) Accent.copy(alpha = 0.07f) else Surface
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VideoThumbnail(
          videoUri = summary.videoUri,
          modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = summary.videoName,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.SemiBold,
              fontSize = 15.sp
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(4.dp))
          if (isEmpty) {
            Text(
              text = "No subtitles yet \u2022 Open to import",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
              color = Accent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          } else {
            val label = summary.sourceLabel
            Text(
              text = if (label.isNullOrBlank()) {
                "${summary.subtitleCount} subtitles"
              } else {
                "${summary.subtitleCount} subtitles \u2022 $label"
              },
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
              color = TextSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = TimeUtils.timeAgo(summary.lastEditedTimeMs),
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 1
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onRename,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Rename project",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = "Delete project",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
