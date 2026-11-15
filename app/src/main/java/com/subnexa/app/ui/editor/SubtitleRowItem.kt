package com.subnexa.app.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.validation.IssueType
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.ActiveRowTint
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubtitleRowItem(
  index: Int,
  entry: SubtitleEntry,
  isActive: Boolean,
  onSeek: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onDuplicate: () -> Unit,
  onSplitAtPlayhead: () -> Unit,
  onMergeWithNext: () -> Unit,
  issues: List<IssueType> = emptyList(),
  highlight: String = "",
  highlightIgnoreCase: Boolean = true,
  isCurrentMatch: Boolean = false,
  modifier: Modifier = Modifier
) {
  var showMenu by remember { mutableStateOf(false) }

  val rowBackground by animateColorAsState(
    targetValue = if (isActive) ActiveRowTint else ActiveRowTint.copy(alpha = 0f),
    animationSpec = tween(150),
    label = "row_background"
  )
  val indicatorColor by animateColorAsState(
    targetValue = if (isActive) Accent else Accent.copy(alpha = 0f),
    animationSpec = tween(150),
    label = "row_indicator"
  )
  val indexColor by animateColorAsState(
    targetValue = if (isActive) Accent else TextMuted,
    animationSpec = tween(150),
    label = "row_index"
  )
  val timeColor by animateColorAsState(
    targetValue = if (isActive) Accent else TextSecondary,
    animationSpec = tween(150),
    label = "row_time"
  )
  val highlightColor = SwatchYellow.copy(alpha = 0.35f)
  val displayText = remember(entry.text, highlight, highlightIgnoreCase) {
    buildHighlightedText(entry.text, highlight, highlightIgnoreCase, highlightColor)
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(rowBackground)
      .then(if (isCurrentMatch) Modifier.border(1.dp, Accent.copy(alpha = 0.8f)) else Modifier)
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
          .background(indicatorColor)
      )

      Spacer(modifier = Modifier.width(10.dp))

      // 1-based index number
      Text(
        text = "$index",
        style = MonospaceTimestampStyle.copy(
          color = indexColor,
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
          text = displayText,
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
            color = timeColor,
            fontSize = 11.5.sp
          )
        )
        if (issues.isNotEmpty()) {
          Spacer(modifier = Modifier.height(3.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = SwatchYellow,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = issues.joinToString("  ·  ") { it.label },
              style = MaterialTheme.typography.bodySmall.copy(
                color = SwatchYellow,
                fontSize = 11.sp
              )
            )
          }
        }
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

private fun buildHighlightedText(
  text: String,
  query: String,
  ignoreCase: Boolean,
  color: Color
): AnnotatedString {
  if (query.isEmpty()) return AnnotatedString(text)
  return buildAnnotatedString {
    append(text)
    var from = 0
    while (true) {
      val at = text.indexOf(query, from, ignoreCase)
      if (at < 0) break
      addStyle(SpanStyle(background = color), at, at + query.length)
      from = at + query.length
    }
  }
}
