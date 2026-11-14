package com.subnexa.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subnexa.app.domain.embedded.EmbeddedTrack
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

/**
 * All dialogs of the "use the subtitles stored inside the video" flow.
 * Renders nothing while the flow is [EmbeddedFlowState.Idle].
 */
@Composable
fun EmbeddedSubtitleDialogs(
  state: EmbeddedFlowState,
  onUseVideoSubtitles: () -> Unit,
  onImportFile: () -> Unit,
  onDismiss: () -> Unit,
  onBackToChoice: () -> Unit,
  onSelectTrack: (EmbeddedTrack) -> Unit,
  onCancelLoading: () -> Unit
) {
  when (state) {
    EmbeddedFlowState.Idle -> Unit

    EmbeddedFlowState.Scanning -> {
      Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
      ) {
        DialogCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Accent,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
              text = "Checking video for subtitles…",
              style = MaterialTheme.typography.bodyMedium,
              color = TextPrimary
            )
          }
        }
      }
    }

    is EmbeddedFlowState.Choice -> {
      val count = state.tracks.size
      Dialog(onDismissRequest = onDismiss) {
        DialogCard {
          DialogTitle("Subtitles found in this video")
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = state.video.name,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = if (count == 1) {
              "This video has 1 subtitle track. Do you want to use it, or import a subtitle file?"
            } else {
              "This video has $count subtitle tracks. Do you want to use one of them, or import a subtitle file?"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
          )
          Spacer(modifier = Modifier.size(20.dp))
          PrimaryAccentButton(
            text = "Use video's subtitles",
            onClick = onUseVideoSubtitles,
            modifier = Modifier.fillMaxWidth(),
            testTag = "use_video_subtitles_button"
          )
          Spacer(modifier = Modifier.size(10.dp))
          SecondarySubNexaButton(
            text = "Import subtitle file",
            onClick = onImportFile,
            modifier = Modifier.fillMaxWidth(),
            testTag = "import_file_for_video_button"
          )
          Spacer(modifier = Modifier.size(4.dp))
          TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cancel", color = TextSecondary)
          }
        }
      }
    }

    is EmbeddedFlowState.TrackList -> {
      val count = state.tracks.size
      Dialog(onDismissRequest = onBackToChoice) {
        DialogCard {
          DialogTitle("Choose a subtitle track")
          Spacer(modifier = Modifier.size(6.dp))
          Text(
            text = if (count == 1) "1 track found in this video" else "$count tracks found in this video",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
          )
          Spacer(modifier = Modifier.size(14.dp))
          LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(items = state.tracks, key = { it.trackId }) { track ->
              TrackRow(track = track, onClick = { onSelectTrack(track) })
            }
          }
          Spacer(modifier = Modifier.size(8.dp))
          TextButton(onClick = onBackToChoice, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Back", color = TextSecondary)
          }
        }
      }
    }

    is EmbeddedFlowState.Loading -> {
      Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
      ) {
        DialogCard {
          DialogTitle("Loading subtitles…")
          Spacer(modifier = Modifier.size(6.dp))
          Text(
            text = "${state.track.displayName} · ${state.track.formatName}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.size(16.dp))
          val progress = state.progress
          if (progress != null) {
            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier.fillMaxWidth(),
              color = Accent,
              trackColor = SurfaceVariant
            )
          } else {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth(),
              color = Accent,
              trackColor = SurfaceVariant
            )
          }
          Spacer(modifier = Modifier.size(8.dp))
          Text(
            text = if (progress != null) "${(progress * 100).toInt()}%  ·  Large videos can take a little while."
            else "Large videos can take a little while.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
          )
          Spacer(modifier = Modifier.size(16.dp))
          SecondarySubNexaButton(
            text = "Cancel",
            onClick = onCancelLoading,
            modifier = Modifier.fillMaxWidth(),
            testTag = "cancel_embedded_loading_button"
          )
        }
      }
    }
  }
}

@Composable
private fun DialogCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  val shape = RoundedCornerShape(16.dp)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(Surface)
      .border(0.5.dp, Border, shape)
      .padding(20.dp),
    content = content
  )
}

@Composable
private fun DialogTitle(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium.copy(
      fontWeight = FontWeight.SemiBold,
      fontSize = 17.sp
    ),
    color = TextPrimary
  )
}

@Composable
private fun TrackRow(track: EmbeddedTrack, onClick: () -> Unit) {
  val shape = RoundedCornerShape(10.dp)
  val tags = buildList {
    add(track.formatName)
    if (track.isDefault) add("Default")
    if (track.isForced) add("Forced")
    if (!track.isEditable) add("can't be edited")
  }.joinToString(" · ")

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .alpha(if (track.isEditable) 1f else 0.45f)
      .clip(shape)
      .background(SurfaceVariant.copy(alpha = 0.5f))
      .border(0.5.dp, Border, shape)
      .clickable(enabled = track.isEditable, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 12.dp)
      .testTag("embedded_track_${track.trackId}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(28.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Surface),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = track.number.toString(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = if (track.isEditable) Accent else TextMuted
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      // Language on its own line, then the full track title (wraps up to 3 lines) so that
      // tracks with similar long names can be told apart.
      Text(
        text = track.languageName,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      if (!track.title.isNullOrBlank()) {
        Text(
          text = track.title!!.trim(),
          style = MaterialTheme.typography.bodySmall,
          color = TextPrimary.copy(alpha = 0.85f),
          maxLines = 3,
          overflow = TextOverflow.Ellipsis
        )
      }
      Text(
        text = tags,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
