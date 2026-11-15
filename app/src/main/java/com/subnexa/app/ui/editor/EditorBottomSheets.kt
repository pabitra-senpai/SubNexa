package com.subnexa.app.ui.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.DangerRed
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditSubtitleBottomSheet(
  entry: SubtitleEntry?,
  isNew: Boolean,
  currentPlayheadMs: Long,
  isPlaying: Boolean,
  allEntries: List<SubtitleEntry>,
  onTogglePlayPause: () -> Unit,
  onDismiss: () -> Unit,
  onSave: (startMs: Long, endMs: Long, text: String) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scrollState = rememberScrollState()

  val initialStartMs = entry?.startMs ?: currentPlayheadMs
  val initialEndMs = entry?.endMs ?: (initialStartMs + 2000L)

  var textValue by remember { mutableStateOf(entry?.text ?: "") }
  var startStr by remember { mutableStateOf(TimeUtils.formatTimestamp(initialStartMs, '.')) }
  var endStr by remember { mutableStateOf(TimeUtils.formatTimestamp(initialEndMs, '.')) }

  val parsedStart = TimeUtils.parseTimestamp(startStr)
  val parsedEnd = TimeUtils.parseTimestamp(endStr)

  val isStartValid = parsedStart != null && parsedStart >= 0L
  val isEndValid = parsedEnd != null && parsedEnd > (parsedStart ?: 0L)
  val isTextValid = textValue.trim().isNotEmpty()

  val canSave = isStartValid && isEndValid && isTextValid

  // Overlap warning detection
  val overlapWarning = remember(parsedStart, parsedEnd, entry) {
    if (parsedStart == null || parsedEnd == null) null
    else {
      val neighbor = allEntries.firstOrNull { other ->
        other.id != (entry?.id ?: -1L) && (
          (parsedStart in other.startMs until other.endMs) ||
          (parsedEnd in (other.startMs + 1)..other.endMs)
        )
      }
      if (neighbor != null) "Overlaps with another subtitle (${TimeUtils.formatTimestamp(neighbor.startMs, '.')} - ${TimeUtils.formatTimestamp(neighbor.endMs, '.')})" else null
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = Surface,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .navigationBarsPadding()
        .imePadding()
        .verticalScroll(scrollState)
    ) {
      // Mini Player Preview Header pinned at top of sheet
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceVariant)
          .border(0.5.dp, Border, RoundedCornerShape(8.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(32.dp).testTag("mini_player_play")
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
            text = TimeUtils.formatTimestamp(currentPlayheadMs, '.'),
            style = MonospaceTimestampStyle.copy(color = TextPrimary, fontSize = 12.sp)
          )
        }
        Text(
          text = if (textValue.isNotEmpty()) textValue.take(24) else "Preview",
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
          maxLines = 1
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = if (isNew) "New subtitle" else "Edit subtitle",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Subtitle text field
      OutlinedTextField(
        value = textValue,
        onValueChange = { textValue = it },
        label = { Text("Subtitle text") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("subtitle_text_input"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = Accent,
          unfocusedBorderColor = InputBorder,
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          focusedLabelColor = Accent,
          unfocusedLabelColor = TextSecondary,
          cursorColor = Accent
        ),
        shape = RoundedCornerShape(8.dp),
        minLines = 3,
        maxLines = 6
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Start Time Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = startStr,
          onValueChange = { startStr = it },
          label = { Text("Start (HH:MM:SS.mmm)") },
          isError = !isStartValid,
          textStyle = MonospaceTimestampStyle.copy(color = TextPrimary, fontSize = 13.sp),
          modifier = Modifier
            .weight(1f)
            .testTag("start_time_input"),
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
          onClick = {
            startStr = TimeUtils.formatTimestamp(currentPlayheadMs, '.')
          },
          modifier = Modifier.testTag("set_start_now_button")
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // End Time Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = endStr,
          onValueChange = { endStr = it },
          label = { Text("End (HH:MM:SS.mmm)") },
          isError = !isEndValid,
          textStyle = MonospaceTimestampStyle.copy(color = TextPrimary, fontSize = 13.sp),
          modifier = Modifier
            .weight(1f)
            .testTag("end_time_input"),
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
          onClick = {
            endStr = TimeUtils.formatTimestamp(currentPlayheadMs, '.')
          },
          modifier = Modifier.testTag("set_end_now_button")
        )
      }

      // Inline validation error or overlap warning (height changes smoothly)
      Column(modifier = Modifier.fillMaxWidth().animateContentSize(tween(150))) {
        if (!isEndValid && parsedStart != null && parsedEnd != null && parsedEnd <= parsedStart) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "End time must be greater than start time",
            style = MaterialTheme.typography.bodySmall.copy(color = DangerRed)
          )
        } else if (overlapWarning != null) {
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Outlined.WarningAmber,
              contentDescription = null,
              tint = SwatchYellow,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = overlapWarning,
              style = MaterialTheme.typography.bodySmall.copy(color = SwatchYellow)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Action Buttons: Cancel and Save (Save is single red accent button)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        SecondarySubNexaButton(
          text = "Cancel",
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
          testTag = "cancel_edit_button"
        )

        PrimaryAccentButton(
          text = "Save",
          onClick = {
            if (canSave) {
              onSave(parsedStart!!, parsedEnd!!, textValue)
            }
          },
          enabled = canSave,
          modifier = Modifier.weight(1f),
          testTag = "save_edit_button"
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
