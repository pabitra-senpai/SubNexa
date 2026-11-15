package com.subnexa.app.ui.editor

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.ui.components.SegmentedControl
import com.subnexa.app.ui.components.pressScale
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

private val NUDGE_STEPS_MS = listOf(-500L, -100L, 100L, 500L)

/**
 * Quick timing nudges for the line the playhead is on (or was last on), usable while the
 * video plays.
 */
@Composable
fun EditorNudgeBar(
  entry: SubtitleEntry?,
  lineNumber: Int,
  mode: NudgeMode,
  onModeChange: (NudgeMode) -> Unit,
  onNudge: (deltaMs: Long) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Surface)
      .border(0.5.dp, Border)
      .padding(12.dp)
  ) {
    Text(
      text = if (entry != null) {
        "Line $lineNumber  ·  ${TimeUtils.formatTimestamp(entry.startMs, '.')}  →  " +
          TimeUtils.formatTimestamp(entry.endMs, '.')
      } else {
        "Play over a line, then nudge its timing here"
      },
      style = MonospaceTimestampStyle.copy(
        color = if (entry != null) TextSecondary else TextMuted,
        fontSize = 11.5.sp
      )
    )

    Spacer(modifier = Modifier.height(8.dp))

    SegmentedControl(
      items = NudgeMode.values().toList(),
      selectedItem = mode,
      onItemSelected = onModeChange,
      label = {
        when (it) {
          NudgeMode.START -> "Start"
          NudgeMode.END -> "End"
          NudgeMode.BOTH -> "Both"
        }
      }
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      NUDGE_STEPS_MS.forEach { delta ->
        NudgeButton(
          label = if (delta > 0) "+${delta}ms" else "${delta}ms",
          enabled = entry != null,
          onClick = { onNudge(delta) },
          modifier = Modifier.weight(1f),
          testTag = "nudge_$delta"
        )
      }
    }
  }
}

@Composable
private fun NudgeButton(
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier
) {
  val shape = RoundedCornerShape(8.dp)
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .pressScale(interactionSource)
      .clip(shape)
      .background(SurfaceVariant)
      .border(0.5.dp, Border, shape)
      .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick
      )
      .padding(vertical = 12.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
      ),
      color = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.5f)
    )
  }
}
