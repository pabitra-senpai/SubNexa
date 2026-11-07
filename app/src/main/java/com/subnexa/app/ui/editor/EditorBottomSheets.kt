package com.subnexa.app.ui.editor

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.BgMode
import com.subnexa.app.domain.model.ExportFormat
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.model.SubtitlePosition
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.components.SegmentedControl
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Background
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.DangerRed
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchCyan
import com.subnexa.app.ui.theme.SwatchGreen
import com.subnexa.app.ui.theme.SwatchWhite
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
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

      // Inline validation error or overlap warning
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSyncBottomSheet(
  subtitleCount: Int,
  onDismiss: () -> Unit,
  onApply: (offsetSeconds: Double) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var offsetSeconds by remember { mutableStateOf("0.0") }

  val parsedOffset = offsetSeconds.toDoubleOrNull()
  val isValid = parsedOffset != null

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
    ) {
      Text(
        text = "Global synchronization",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(6.dp))
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
  onDismiss: () -> Unit,
  onAppearanceChanged: (SubtitleAppearance) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var fontSize by remember { mutableFloatStateOf(initialAppearance.fontSizeSp) }
  var textColorHex by remember { mutableLongStateOf(initialAppearance.textColorHex) }
  var bgMode by remember { mutableStateOf(initialAppearance.bgMode) }
  var position by remember { mutableStateOf(initialAppearance.position) }

  fun notifyChange() {
    onAppearanceChanged(
      SubtitleAppearance(
        fontSizeSp = fontSize,
        textColorHex = textColorHex,
        bgMode = bgMode,
        position = position
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
          text = "Preview: Subtitle caption styling",
          color = Color(textColorHex),
          fontSize = fontSize.sp,
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
      val swatches = listOf(
        0xFFFAFAFA to SwatchWhite,
        0xFFFACC15 to SwatchYellow,
        0xFF22D3EE to SwatchCyan,
        0xFF4ADE80 to SwatchGreen
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        swatches.forEach { (colorLong, composeColor) ->
          val isSelected = textColorHex == colorLong
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(composeColor)
              .border(
                width = if (isSelected) 2.5.dp else 0.5.dp,
                color = if (isSelected) Accent else Border,
                shape = CircleShape
              )
              .clickable {
                textColorHex = colorLong
                notifyChange()
              },
            contentAlignment = Alignment.Center
          ) {
            if (isSelected) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(Color.Black)
              )
            }
          }
        }
      }

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

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
