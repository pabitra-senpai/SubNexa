package com.subnexa.app.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import com.subnexa.app.domain.font.FontManager
import com.subnexa.app.domain.font.SubtitleLanguageInfo
import com.subnexa.app.ui.components.SubtitleFontSelector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.BgMode
import com.subnexa.app.domain.model.ExportFormat
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.model.SubtitlePosition
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.domain.parser.SubtitleParser
import com.subnexa.app.domain.sync.TwoPointSync
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.components.TextColorPicker
import com.subnexa.app.ui.components.SegmentedControl
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Background
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.DangerRed
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.SubtitleFontFamily
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchCyan
import com.subnexa.app.ui.theme.SwatchGreen
import com.subnexa.app.ui.theme.SwatchWhite
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

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

private enum class SyncMode(val label: String) {
  SHIFT("Shift all"),
  TWO_POINT("Two-point")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSyncBottomSheet(
  entries: List<SubtitleEntry>,
  playbackPositionFlow: StateFlow<Long>,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  onDismiss: () -> Unit,
  onApply: (offsetSeconds: Double) -> Unit,
  onApplyTwoPoint: (firstId: Long, firstNewStartMs: Long, lastId: Long, lastNewStartMs: Long) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var mode by remember { mutableStateOf(SyncMode.SHIFT) }
  var offsetSeconds by remember { mutableStateOf("0.0") }

  val subtitleCount = entries.size
  val startTimesMs = remember(entries) { entries.map { it.startMs } }

  val parsedOffset = offsetSeconds.toDoubleOrNull()
  val isValid = parsedOffset != null

  // Earliest-start info, used to warn when an earlier shift would push lines before 00:00.
  val earliestStartMs = remember(startTimesMs) { startTimesMs.minOrNull() ?: 0L }
  val offsetMsForWarning = ((parsedOffset ?: 0.0) * 1000.0).toLong()
  val clampedCount = if (offsetMsForWarning < 0L) {
    startTimesMs.count { it + offsetMsForWarning < 0L }
  } else 0
  // Largest earlier shift (in seconds, 2 decimals, rounded down) that moves nothing below 0.
  val maxSafeEarlierSeconds = Math.floor(earliestStartMs / 10.0) / 100.0

  fun adjustOffset(delta: Double) {
    val cur = offsetSeconds.toDoubleOrNull() ?: 0.0
    val next = cur + delta
    offsetSeconds = String.format(Locale.US, "%.2f", next)
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
        .verticalScroll(rememberScrollState())
    ) {
      Text(
        text = "Global synchronization",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(12.dp))

      SegmentedControl(
        items = SyncMode.values().toList(),
        selectedItem = mode,
        onItemSelected = { mode = it },
        label = { it.label }
      )

      Spacer(modifier = Modifier.height(12.dp))

      AnimatedContent(
        targetState = mode,
        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
        label = "sync_mode_transition"
      ) { currentMode ->
        Column(modifier = Modifier.fillMaxWidth()) {
          if (currentMode == SyncMode.TWO_POINT) {
            TwoPointSyncSection(
              entries = entries,
              playbackPositionFlow = playbackPositionFlow,
              isPlaying = isPlaying,
              onTogglePlayPause = onTogglePlayPause,
              onDismiss = onDismiss,
              onApplyTwoPoint = onApplyTwoPoint
            )
          } else {
            Text(
              text = "Shifts all $subtitleCount subtitles. Start never goes below 00:00:00.000.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick chips row: -1s, -0.5s, +0.5s, +1s
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(-1.0, -0.5, 0.5, 1.0).forEach { delta ->
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariant)
                    .border(0.5.dp, Border, RoundedCornerShape(8.dp))
                    .clickable { adjustOffset(delta) }
                    .padding(vertical = 10.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = if (delta > 0) "+${delta}s" else "${delta}s",
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 13.sp
                    ),
                    color = TextPrimary
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
              value = offsetSeconds,
              onValueChange = { offsetSeconds = it },
              label = { Text("Offset (seconds, signed decimal)") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("offset_seconds_input"),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = InputBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent
              ),
              shape = RoundedCornerShape(8.dp),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true
            )

            Column(modifier = Modifier.fillMaxWidth().animateContentSize(tween(150))) {
              if (clampedCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
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
                  Column {
                    Text(
                      text = "$clampedCount subtitle(s) would go before 00:00:00.000 and stop at 0, " +
                        "so their spacing will change. Safest earlier shift: " +
                        "${String.format(Locale.US, "%.2f", maxSafeEarlierSeconds)}s.",
                      style = MaterialTheme.typography.bodySmall.copy(color = SwatchYellow)
                    )
                    if (maxSafeEarlierSeconds > 0.0) {
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(
                        text = "Use safe shift",
                        style = MaterialTheme.typography.labelMedium.copy(
                          fontWeight = FontWeight.SemiBold,
                          color = Accent
                        ),
                        modifier = Modifier
                          .clickable {
                            offsetSeconds = String.format(Locale.US, "%.2f", -maxSafeEarlierSeconds)
                          }
                          .testTag("use_safe_shift")
                      )
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              SecondarySubNexaButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                testTag = "cancel_sync_button"
              )

              PrimaryAccentButton(
                text = "Apply",
                onClick = {
                  if (isValid) {
                    onApply(parsedOffset!!)
                  }
                },
                enabled = isValid,
                modifier = Modifier.weight(1f),
                testTag = "apply_sync_button"
              )
            }

            Spacer(modifier = Modifier.height(24.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun TwoPointSyncSection(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
  initialFilename: String,
  defaultFormat: ExportFormat,
  defaultFolderUriStr: String?,
  onDismiss: () -> Unit,
  onPickFolder: () -> Unit,
  onExport: (filename: String, format: ExportFormat, folderUriStr: String) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var filename by remember { mutableStateOf(initialFilename.removeSuffix(".srt").removeSuffix(".vtt")) }
  var format by remember { mutableStateOf(defaultFormat) }

  val canExport = filename.trim().isNotEmpty() && !defaultFolderUriStr.isNullOrEmpty()

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
    ) {
      Text(
        text = "Export subtitles",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Filename field
      OutlinedTextField(
        value = filename,
        onValueChange = { filename = it },
        label = { Text("Filename (no extension)") },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("export_filename_input"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = Accent,
          unfocusedBorderColor = InputBorder,
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          cursorColor = Accent
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = true
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Format segmented control (SRT / VTT)
      SegmentedControl(
        items = listOf(ExportFormat.SRT, ExportFormat.VTT),
        selectedItem = format,
        onItemSelected = { format = it },
        label = { it.displayName }
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Save folder row
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceVariant)
          .border(0.5.dp, Border, RoundedCornerShape(8.dp))
          .clickable(onClick = onPickFolder)
          .padding(14.dp)
          .testTag("choose_export_folder")
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
            Icon(
              imageVector = Icons.Outlined.FolderOpen,
              contentDescription = null,
              tint = Accent,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Save folder",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
              )
              Text(
                text = if (defaultFolderUriStr.isNullOrEmpty()) "Tap to choose save directory" else "Folder selected",
                style = MaterialTheme.typography.bodySmall,
                color = if (defaultFolderUriStr.isNullOrEmpty()) SwatchYellow else TextSecondary
              )
            }
          }
          Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = if (!defaultFolderUriStr.isNullOrEmpty()) SwatchGreen else Color.Transparent,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        SecondarySubNexaButton(
          text = "Cancel",
          onClick = onDismiss,
          modifier = Modifier.weight(1f),
          testTag = "cancel_export_button"
        )

        PrimaryAccentButton(
          text = "Export",
          onClick = {
            if (canExport) {
              onExport(filename.trim(), format, defaultFolderUriStr!!)
            }
          },
          enabled = canExport,
          modifier = Modifier.weight(1f),
          testTag = "confirm_export_button"
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceBottomSheet(
  initialAppearance: SubtitleAppearance,
  subtitleInfo: SubtitleLanguageInfo = SubtitleLanguageInfo.NONE,
  onDismiss: () -> Unit,
  onAppearanceChanged: (SubtitleAppearance) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val context = LocalContext.current
  val scrollState = rememberScrollState()

  var fontSize by remember { mutableFloatStateOf(initialAppearance.fontSizeSp) }
  var textColorHex by remember { mutableLongStateOf(initialAppearance.textColorHex) }
  var bgMode by remember { mutableStateOf(initialAppearance.bgMode) }
  var position by remember { mutableStateOf(initialAppearance.position) }
  var fontId by remember { mutableStateOf(initialAppearance.fontId) }

  val previewFont = remember(fontId) {
    FontManager.resolveFontFamily(context, fontId)
  }
  // Preview uses the real subtitle text; without a subtitle, sample words in the font's own scripts.
  val previewSample = remember(fontId) {
    FontManager.getAvailableFonts(context).firstOrNull { it.id == fontId }?.sampleText() ?: "Aa Bb Cc 123"
  }

  fun notifyChange() {
    onAppearanceChanged(
      SubtitleAppearance(
        fontSizeSp = fontSize,
        textColorHex = textColorHex,
        bgMode = bgMode,
        position = position,
        fontId = fontId
      )
    )
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
      Text(
        text = "Subtitle appearance",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Live Preview Strip
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(80.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color.Black)
          .border(0.5.dp, Border, RoundedCornerShape(8.dp))
          .padding(8.dp),
        contentAlignment = when (position) {
          SubtitlePosition.TOP -> Alignment.TopCenter
          SubtitlePosition.MIDDLE -> Alignment.Center
          SubtitlePosition.BOTTOM -> Alignment.BottomCenter
        }
      ) {
        val previewBg = when (bgMode) {
          BgMode.NONE -> Color.Transparent
          BgMode.DIM -> Color(0x99000000)
          BgMode.SOLID -> Color(0xFF000000)
        }
        Text(
          text = subtitleInfo.sampleLine ?: previewSample,
          color = Color(textColorHex),
          fontSize = fontSize.sp,
          fontFamily = previewFont,
          fontWeight = FontWeight.Medium,
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(previewBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Font size slider
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Font size", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
      Slider(
        value = fontSize,
        onValueChange = {
          fontSize = it
          notifyChange()
        },
        valueRange = 12f..28f,
        steps = 7,
        colors = SliderDefaults.colors(
          thumbColor = Accent,
          activeTrackColor = Accent,
          inactiveTrackColor = SurfaceVariant
        )
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Text Color Swatches
      Text("Text color", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      Spacer(modifier = Modifier.height(8.dp))
      TextColorPicker(
        selectedColor = textColorHex,
        onColorSelected = {
          textColorHex = it
          notifyChange()
        }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Background segmented control
      Text("Background", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      Spacer(modifier = Modifier.height(6.dp))
      SegmentedControl(
        items = listOf(BgMode.NONE, BgMode.DIM, BgMode.SOLID),
        selectedItem = bgMode,
        onItemSelected = {
          bgMode = it
          notifyChange()
        },
        label = { it.displayName }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Position segmented control
      Text("Position", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      Spacer(modifier = Modifier.height(6.dp))
      SegmentedControl(
        items = listOf(SubtitlePosition.TOP, SubtitlePosition.MIDDLE, SubtitlePosition.BOTTOM),
        selectedItem = position,
        onItemSelected = {
          position = it
          notifyChange()
        },
        label = { it.displayName }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Subtitle font selector
      SubtitleFontSelector(
        subtitleInfo = subtitleInfo,
        selectedFontId = fontId,
        onFontSelected = {
          fontId = it
          notifyChange()
        }
      )

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
