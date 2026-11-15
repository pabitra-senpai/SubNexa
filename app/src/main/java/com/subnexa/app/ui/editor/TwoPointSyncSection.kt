package com.subnexa.app.ui.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.domain.parser.SubtitleParser
import com.subnexa.app.domain.sync.TwoPointSync
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.DangerRed
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@Composable
internal fun TwoPointSyncSection(
  entries: List<SubtitleEntry>,
  playbackPositionFlow: StateFlow<Long>,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  onDismiss: () -> Unit,
  onApplyTwoPoint: (firstId: Long, firstNewStartMs: Long, lastId: Long, lastNewStartMs: Long) -> Unit
) {
  val sorted = remember(entries) { entries.sortedBy { it.startMs } }

  if (sorted.size < 2) {
    Text(
      text = "Add at least 2 subtitles to use two-point sync.",
      style = MaterialTheme.typography.bodySmall,
      color = TextSecondary
    )
    Spacer(modifier = Modifier.height(20.dp))
    SecondarySubNexaButton(
      text = "Close",
      onClick = onDismiss,
      modifier = Modifier.fillMaxWidth(),
      testTag = "cancel_two_point_button"
    )
    Spacer(modifier = Modifier.height(24.dp))
  } else {
    TwoPointSyncForm(
      sorted = sorted,
      playbackPositionFlow = playbackPositionFlow,
      isPlaying = isPlaying,
      onTogglePlayPause = onTogglePlayPause,
      onDismiss = onDismiss,
      onApplyTwoPoint = onApplyTwoPoint
    )
  }
}

@Composable
private fun TwoPointSyncForm(
  sorted: List<SubtitleEntry>,
  playbackPositionFlow: StateFlow<Long>,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  onDismiss: () -> Unit,
  onApplyTwoPoint: (firstId: Long, firstNewStartMs: Long, lastId: Long, lastNewStartMs: Long) -> Unit
) {
  val playheadMs by playbackPositionFlow.collectAsStateWithLifecycle()

  var firstIdx by remember { mutableStateOf(0) }
  var lastIdx by remember { mutableStateOf(sorted.lastIndex) }
  var firstStr by remember { mutableStateOf(TimeUtils.formatTimestamp(sorted.first().startMs, '.')) }
  var lastStr by remember { mutableStateOf(TimeUtils.formatTimestamp(sorted.last().startMs, '.')) }

  val firstEntry = sorted[firstIdx]
  val lastEntry = sorted[lastIdx]
  val p1 = TimeUtils.parseTimestamp(firstStr)
  val p2 = TimeUtils.parseTimestamp(lastStr)

  val isFirstTimeValid = p1 != null && p1 >= 0L
  val isLastTimeValid = p2 != null && p2 >= 0L
  val sameOldStart = lastEntry.startMs <= firstEntry.startMs
  val wrongOrder = p1 != null && p2 != null && p2 <= p1

  val preview = remember(firstIdx, lastIdx, firstStr, lastStr, sorted) {
    if (isFirstTimeValid && isLastTimeValid && !sameOldStart && !wrongOrder) {
      TwoPointSync.apply(sorted, firstEntry.id, p1!!, lastEntry.id, p2!!)
    } else null
  }

  Text(
    text = "Fixes subtitles that start in sync but drift later. Pick one line near the start " +
      "and one near the end, then say when each should start. Everything else is " +
      "shifted and stretched to match.",
    style = MaterialTheme.typography.bodySmall,
    color = TextSecondary
  )

  Spacer(modifier = Modifier.height(12.dp))

  // Mini player: lets the user find the real moment a line is spoken.
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(SurfaceVariant)
      .border(0.5.dp, Border, RoundedCornerShape(8.dp))
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(
      onClick = onTogglePlayPause,
      modifier = Modifier.size(32.dp).testTag("two_point_play")
    ) {
      Icon(
        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
        tint = Accent,
        modifier = Modifier.size(20.dp)
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = TimeUtils.formatTimestamp(playheadMs, '.'),
      style = MonospaceTimestampStyle.copy(color = TextPrimary, fontSize = 12.sp)
    )
  }

  Spacer(modifier = Modifier.height(12.dp))

  SyncAnchorCard(
    title = "First line",
    entry = firstEntry,
    position = firstIdx + 1,
    total = sorted.size,
    canPrev = firstIdx > 0,
    canNext = firstIdx < lastIdx - 1,
    onPrev = {
      firstIdx -= 1
      firstStr = TimeUtils.formatTimestamp(sorted[firstIdx].startMs, '.')
    },
    onNext = {
      firstIdx += 1
      firstStr = TimeUtils.formatTimestamp(sorted[firstIdx].startMs, '.')
    },
    timeStr = firstStr,
    onTimeChange = { firstStr = it },
    isTimeValid = isFirstTimeValid,
    onSetNow = { firstStr = TimeUtils.formatTimestamp(playheadMs, '.') },
    tagPrefix = "first_point"
  )

  Spacer(modifier = Modifier.height(10.dp))

  SyncAnchorCard(
    title = "Last line",
    entry = lastEntry,
    position = lastIdx + 1,
    total = sorted.size,
    canPrev = lastIdx > firstIdx + 1,
    canNext = lastIdx < sorted.lastIndex,
    onPrev = {
      lastIdx -= 1
      lastStr = TimeUtils.formatTimestamp(sorted[lastIdx].startMs, '.')
    },
    onNext = {
      lastIdx += 1
      lastStr = TimeUtils.formatTimestamp(sorted[lastIdx].startMs, '.')
    },
    timeStr = lastStr,
    onTimeChange = { lastStr = it },
    isTimeValid = isLastTimeValid,
    onSetNow = { lastStr = TimeUtils.formatTimestamp(playheadMs, '.') },
    tagPrefix = "last_point"
  )

  Column(modifier = Modifier.fillMaxWidth().animateContentSize(tween(150))) {
    if (sameOldStart) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "These two lines start at the same time. Pick lines that start at different times.",
        style = MaterialTheme.typography.bodySmall.copy(color = DangerRed)
      )
    } else if (wrongOrder) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "The last line must start after the first line.",
        style = MaterialTheme.typography.bodySmall.copy(color = DangerRed)
      )
    } else if (preview != null) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "First line ${signedSeconds(p1!! - firstEntry.startMs)}s, " +
          "last line ${signedSeconds(p2!! - lastEntry.startMs)}s, " +
          "speed x${String.format(Locale.US, "%.5f", preview.scale)}",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted
      )
      if (preview.clampedCount > 0) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Top) {
          Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = SwatchYellow,
            modifier = Modifier
              .padding(top = 2.dp)
              .size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${preview.clampedCount} subtitle(s) would go before 00:00:00.000 and stop at 0.",
            style = MaterialTheme.typography.bodySmall.copy(color = SwatchYellow)
          )
        }
      }
    }
  }

  Spacer(modifier = Modifier.height(20.dp))

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    SecondarySubNexaButton(
      text = "Cancel",
      onClick = onDismiss,
      modifier = Modifier.weight(1f),
      testTag = "cancel_two_point_button"
    )

    PrimaryAccentButton(
      text = "Apply",
      onClick = {
        if (preview != null) {
          onApplyTwoPoint(firstEntry.id, p1!!, lastEntry.id, p2!!)
        }
      },
      enabled = preview != null,
      modifier = Modifier.weight(1f),
      testTag = "apply_two_point_button"
    )
  }

  Spacer(modifier = Modifier.height(24.dp))
}

private fun signedSeconds(deltaMs: Long): String =
  String.format(Locale.US, "%+.2f", deltaMs / 1000.0)

@Composable
private fun SyncAnchorCard(
  title: String,
  entry: SubtitleEntry,
  position: Int,
  total: Int,
  canPrev: Boolean,
  canNext: Boolean,
  onPrev: () -> Unit,
  onNext: () -> Unit,
  timeStr: String,
  onTimeChange: (String) -> Unit,
  isTimeValid: Boolean,
  onSetNow: () -> Unit,
  tagPrefix: String
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(SurfaceVariant)
      .border(0.5.dp, Border, RoundedCornerShape(12.dp))
      .padding(12.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 14.sp
        ),
        color = TextPrimary
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onPrev,
          enabled = canPrev,
          modifier = Modifier.size(32.dp).testTag("${tagPrefix}_prev")
        ) {
          Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = "Previous line",
            tint = if (canPrev) TextPrimary else TextMuted.copy(alpha = 0.5f)
          )
        }
        Text(
          text = "$position / $total",
          style = MonospaceTimestampStyle.copy(color = TextSecondary, fontSize = 12.sp)
        )
        IconButton(
          onClick = onNext,
          enabled = canNext,
          modifier = Modifier.size(32.dp).testTag("${tagPrefix}_next")
        ) {
          Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Next line",
            tint = if (canNext) TextPrimary else TextMuted.copy(alpha = 0.5f)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = SubtitleParser.stripTags(entry.text).replace("\n", " "),
      style = MaterialTheme.typography.bodySmall,
      color = TextSecondary,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )

    Spacer(modifier = Modifier.height(2.dp))

    Text(
      text = "Now starts at ${TimeUtils.formatTimestamp(entry.startMs, '.')}",
      style = MaterialTheme.typography.bodySmall,
      color = TextMuted
    )

    Spacer(modifier = Modifier.height(10.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = timeStr,
        onValueChange = onTimeChange,
        label = { Text("Should start at (HH:MM:SS.mmm)") },
        isError = !isTimeValid,
        textStyle = MonospaceTimestampStyle.copy(color = TextPrimary, fontSize = 13.sp),
        modifier = Modifier
          .weight(1f)
          .testTag("${tagPrefix}_time_input"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = Accent,
          unfocusedBorderColor = InputBorder,
          errorBorderColor = DangerRed,
          cursorColor = Accent
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = true
      )

      SecondarySubNexaButton(
        text = "Set to now",
        onClick = onSetNow,
        testTag = "${tagPrefix}_now_button"
      )
    }
  }
}
