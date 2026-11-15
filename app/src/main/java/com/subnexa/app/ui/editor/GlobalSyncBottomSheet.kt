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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.components.SegmentedControl
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

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
