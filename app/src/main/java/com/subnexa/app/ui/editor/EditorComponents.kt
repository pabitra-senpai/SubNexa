package com.subnexa.app.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.pressScale
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

/** Compact top bar: back arrow, video name and undo / redo. */
@Composable
internal fun EditorTopBar(
  title: String,
  canUndo: Boolean,
  canRedo: Boolean,
  onBack: () -> Unit,
  onUndo: () -> Unit,
  onRedo: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(
      onClick = onBack,
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
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
      ),
      color = TextPrimary,
      maxLines = 1,
      modifier = Modifier.weight(1f)
    )

    val undoTint by animateColorAsState(
      targetValue = if (canUndo) TextPrimary else TextMuted.copy(alpha = 0.5f),
      animationSpec = tween(150),
      label = "undo_tint"
    )
    val redoTint by animateColorAsState(
      targetValue = if (canRedo) TextPrimary else TextMuted.copy(alpha = 0.5f),
      animationSpec = tween(150),
      label = "redo_tint"
    )

    IconButton(
      onClick = onUndo,
      enabled = canUndo,
      modifier = Modifier.size(40.dp).testTag("editor_undo_button")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Undo,
        contentDescription = "Undo",
        tint = undoTint
      )
    }

    IconButton(
      onClick = onRedo,
      enabled = canRedo,
      modifier = Modifier.size(40.dp).testTag("editor_redo_button")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Redo,
        contentDescription = "Redo",
        tint = redoTint
      )
    }
  }
}

/** Four equal actions: Import, Add, Sync, Export. */
@Composable
internal fun EditorActionsRow(
  onImport: () -> Unit,
  onAdd: () -> Unit,
  onSync: () -> Unit,
  onExport: () -> Unit
) {
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
      onClick = onImport,
      modifier = Modifier.weight(1f),
      testTag = "action_import"
    )

    EditorActionButton(
      icon = Icons.Outlined.Add,
      label = "Add",
      onClick = onAdd,
      modifier = Modifier.weight(1f),
      testTag = "action_add"
    )

    EditorActionButton(
      icon = Icons.Outlined.Sync,
      label = "Sync",
      onClick = onSync,
      modifier = Modifier.weight(1f),
      testTag = "action_sync"
    )

    EditorActionButton(
      icon = Icons.Outlined.FileDownload,
      label = "Export",
      onClick = onExport,
      modifier = Modifier.weight(1f),
      testTag = "action_export"
    )
  }
}

@Composable
internal fun EditorActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .pressScale(interactionSource)
      .clip(RoundedCornerShape(8.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick
      )
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

/** Count header with the problem chip, nudge toggle and find toggle. */
@Composable
internal fun SubtitleListHeader(
  subtitleCount: Int,
  issueCount: Int,
  showNudge: Boolean,
  searchOpen: Boolean,
  onIssueClick: () -> Unit,
  onToggleNudge: () -> Unit,
  onToggleSearch: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "$subtitleCount subtitles",
      style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
      ),
      color = TextSecondary
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      if (issueCount > 0) {
        IssueChip(
          count = issueCount,
          onClick = onIssueClick
        )
        Spacer(modifier = Modifier.width(4.dp))
      }

      IconButton(
        onClick = onToggleNudge,
        modifier = Modifier.size(40.dp).testTag("toggle_nudge_button")
      ) {
        Icon(
          imageVector = Icons.Outlined.Tune,
          contentDescription = "Nudge timing",
          tint = if (showNudge) Accent else TextSecondary
        )
      }

      IconButton(
        onClick = onToggleSearch,
        modifier = Modifier.size(40.dp).testTag("toggle_search_button")
      ) {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = "Find and replace",
          tint = if (searchOpen) Accent else TextSecondary
        )
      }
    }
  }
}

@Composable
internal fun IssueChip(count: Int, onClick: () -> Unit) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier = Modifier
      .clip(shape)
      .background(SwatchYellow.copy(alpha = 0.14f))
      .border(0.5.dp, SwatchYellow.copy(alpha = 0.5f), shape)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 5.dp)
      .testTag("issues_chip"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.Outlined.WarningAmber,
      contentDescription = null,
      tint = SwatchYellow,
      modifier = Modifier.size(14.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = if (count == 1) "1 problem" else "$count problems",
      style = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
      ),
      color = SwatchYellow
    )
  }
}

/** Slim bar shown only while some lines repeat an earlier line's exact timing and text. */
@Composable
internal fun DuplicatesBanner(count: Int, onRemove: () -> Unit) {
  val shape = RoundedCornerShape(12.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .clip(shape)
      .background(Surface)
      .border(0.5.dp, Border, shape)
      .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
      .testTag("duplicates_banner"),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.Outlined.ContentCopy,
      contentDescription = null,
      tint = TextSecondary,
      modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = if (count == 1) "1 duplicate line" else "$count duplicate lines",
      style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
      color = TextSecondary,
      modifier = Modifier.weight(1f)
    )
    TextButton(
      onClick = onRemove,
      modifier = Modifier.testTag("remove_duplicates_button")
    ) {
      Text(
        text = "Remove duplicates",
        style = MaterialTheme.typography.labelLarge.copy(
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold
        ),
        color = Accent
      )
    }
  }
}

/** Shown instead of the list when the project has no subtitle lines yet. */
@Composable
internal fun EmptySubtitlesContent(onAddFirst: () -> Unit) {
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
      onClick = onAddFirst,
      testTag = "add_first_subtitle_button"
    )
  }
}
