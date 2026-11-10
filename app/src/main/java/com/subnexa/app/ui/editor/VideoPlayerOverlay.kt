package com.subnexa.app.ui.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.subnexa.app.R
import com.subnexa.app.domain.font.FontManager
import com.subnexa.app.domain.model.BgMode
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.model.SubtitlePosition
import com.subnexa.app.domain.parser.SubtitleParser
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.SubtitleFontFamily
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerSection(
  exoPlayer: ExoPlayer?,
  activeEntry: SubtitleEntry?,
  appearance: SubtitleAppearance,
  isPlaying: Boolean,
  playbackPositionFlow: StateFlow<Long>,
  durationMs: Long,
  playbackSpeed: Float,
  isFullscreen: Boolean,
  onTogglePlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onCycleSpeed: () -> Unit,
  onOpenAppearance: () -> Unit,
  onToggleFullscreen: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val subtitleFont = remember(appearance.fontId) {
    FontManager.resolveFontFamily(context, appearance.fontId)
  }

  // Controls stay visible (no auto-hide, no fade) until the user taps the close (X) button.
  // While they are hidden, tapping the video brings them back.
  var areControlsVisible by remember { mutableStateOf(true) }

  Box(
    modifier = modifier
      .background(Color.Black)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        if (!areControlsVisible) {
          areControlsVisible = true
        }
      }
  ) {
    // ExoPlayer View
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        // Inflated from XML so the video uses a TextureView (animates with the screen on exit).
        (LayoutInflater.from(ctx).inflate(R.layout.view_player_texture, null, false) as PlayerView).apply {
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          useController = false
          subtitleView?.visibility = android.view.View.GONE
          player = exoPlayer
        }
      },
      update = { playerView ->
        if (playerView.player != exoPlayer) {
          playerView.player = exoPlayer
        }
      }
    )

    // Subtitle Text Overlay
    if (activeEntry != null) {
      val strippedText = SubtitleParser.stripTags(activeEntry.text)
      if (strippedText.isNotEmpty()) {
        val alignment = when (appearance.position) {
          SubtitlePosition.TOP -> Alignment.TopCenter
          SubtitlePosition.MIDDLE -> Alignment.Center
          SubtitlePosition.BOTTOM -> Alignment.BottomCenter
        }

        val paddingBottom = if (appearance.position == SubtitlePosition.BOTTOM) {
          if (areControlsVisible) 56.dp else 16.dp
        } else 16.dp
        val paddingTop = 16.dp

        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = paddingTop, bottom = paddingBottom),
          contentAlignment = alignment
        ) {
          val bgColor = when (appearance.bgMode) {
            BgMode.NONE -> Color.Transparent
            BgMode.DIM -> Color(0x99000000)
            BgMode.SOLID -> Color(0xFF000000)
          }

          Text(
            text = strippedText,
            color = Color(appearance.textColorHex),
            fontSize = appearance.fontSizeSp.sp,
            lineHeight = (appearance.fontSizeSp * 1.35f).sp,
            fontFamily = subtitleFont,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(bgColor)
              .padding(horizontal = 10.dp, vertical = 6.dp)
          )
        }
      }
    }

    // Top Left: close (X) button - hides all controls until the video is tapped again
    if (areControlsVisible) {
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(8.dp)
          .size(36.dp)
          .clip(CircleShape)
          .background(Color(0x88000000)),
        contentAlignment = Alignment.Center
      ) {
        IconButton(
          onClick = { areControlsVisible = false },
          modifier = Modifier.size(36.dp).testTag("close_controls_button")
        ) {
          Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Hide controls",
            tint = TextPrimary,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    // Top Right Controls Overlay
    if (areControlsVisible) {
      Row(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x88000000)),
          contentAlignment = Alignment.Center
        ) {
          IconButton(
            onClick = onOpenAppearance,
            modifier = Modifier.size(36.dp).testTag("appearance_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.Tune,
              contentDescription = "Subtitle appearance",
              tint = TextPrimary,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x88000000)),
          contentAlignment = Alignment.Center
        ) {
          IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier.size(36.dp).testTag("fullscreen_button")
          ) {
            Icon(
              imageVector = if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
              contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
              tint = TextPrimary,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }

    // Bottom Controls Bar Overlay - only this composable collects playbackPositionFlow
    if (areControlsVisible) {
      VideoPlayerControlsBottomBar(
        playbackPositionFlow = playbackPositionFlow,
        durationMs = durationMs,
        isPlaying = isPlaying,
        playbackSpeed = playbackSpeed,
        onSeekTo = onSeekTo,
        onTogglePlayPause = onTogglePlayPause,
        onCycleSpeed = onCycleSpeed,
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }
  }
}

@Composable
private fun VideoPlayerControlsBottomBar(
  playbackPositionFlow: StateFlow<Long>,
  durationMs: Long,
  isPlaying: Boolean,
  playbackSpeed: Float,
  onSeekTo: (Long) -> Unit,
  onTogglePlayPause: () -> Unit,
  onCycleSpeed: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentPositionMs by playbackPositionFlow.collectAsState()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Color(0xAA09090B))
      .padding(horizontal = 12.dp, vertical = 4.dp)
  ) {
    // Seekbar Scrubber
    val sliderValue = if (durationMs > 0) {
      (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Slider(
      value = sliderValue,
      onValueChange = { frac ->
        if (durationMs > 0) {
          onSeekTo((frac * durationMs).toLong())
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(24.dp)
        .testTag("video_slider"),
      colors = SliderDefaults.colors(
        thumbColor = Accent,
        activeTrackColor = Accent,
        inactiveTrackColor = Color(0x55FAFAFA)
      )
    )

    // Play/Pause, Timestamp, Speed Chip
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onTogglePlayPause,
          modifier = Modifier.size(36.dp).testTag("play_pause_button")
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Accent,
            modifier = Modifier.size(24.dp)
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
          text = TimeUtils.formatDuration(currentPositionMs, durationMs),
          style = MonospaceTimestampStyle.copy(
            color = TextPrimary,
            fontSize = 11.5.sp
          )
        )
      }

      // Speed chip
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(Surface)
          .border(0.5.dp, Border, RoundedCornerShape(6.dp))
          .clickable(onClick = onCycleSpeed)
          .padding(horizontal = 8.dp, vertical = 4.dp)
          .testTag("speed_chip")
      ) {
        Text(
          text = "${playbackSpeed}x",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp
          ),
          color = TextPrimary
        )
      }
    }
  }
}
