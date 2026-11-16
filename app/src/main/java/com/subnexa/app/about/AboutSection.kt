package com.subnexa.app.about

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subnexa.app.R
import com.subnexa.app.ui.components.HairlineCard
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Background
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchGreen
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

fun LazyListScope.aboutSection(
  config: AboutConfig,
  viewModel: AboutViewModel
) {
  item {
    AboutCard(config = config, viewModel = viewModel)
  }
}

/**
 * One compact card: app name, version and developer, a short description, and a row of
 * actions (GitHub + update). The update area changes with the update state.
 */
@Composable
private fun AboutCard(config: AboutConfig, viewModel: AboutViewModel) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()
  val uriHandler = LocalUriHandler.current
  val openGithub: () -> Unit = {
    runCatching { uriHandler.openUri(config.githubUrl) }
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.onAppResumed()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  HairlineCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(tween(200))
    ) {
      // Header: logo tile, name + version, status pill
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .border(0.5.dp, Border, RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_subnexa_mark),
            contentDescription = null
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = config.appName,
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            ),
            color = TextPrimary
          )
          Text(
            text = "Version ${config.currentVersionName} · by ${config.developerName}",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
          )
        }

        val pill = statusPill(state)
        if (pill != null) {
          Spacer(modifier = Modifier.width(8.dp))
          StatusPill(text = pill.first, color = pill.second)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = config.description,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )

      Spacer(modifier = Modifier.height(14.dp))

      AnimatedContent(
        targetState = state,
        transitionSpec = {
          fadeIn(tween(200)) togetherWith fadeOut(tween(150))
        },
        label = "update_state_transition"
      ) { targetState ->
        when (targetState) {
          is UpdateUiState.Idle -> {
            ActionRow(
              onGithub = openGithub,
              primaryText = "Check updates",
              onPrimary = { viewModel.checkForUpdates() },
              primaryTag = "check_updates_button"
            )
          }

          is UpdateUiState.Checking -> {
            ActionRow(
              onGithub = openGithub,
              primaryText = "Checking…",
              onPrimary = {},
              primaryTag = "check_updates_button",
              primaryEnabled = false
            )
          }

          is UpdateUiState.UpToDate -> {
            ActionRow(
              onGithub = openGithub,
              primaryText = "Check updates",
              onPrimary = { viewModel.checkForUpdates() },
              primaryTag = "check_again_button"
            )
          }

          is UpdateUiState.Available -> {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "Update available: v${targetState.currentVersion} → v${targetState.updateInfo.versionName}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
              )
              Spacer(modifier = Modifier.height(10.dp))
              ActionRow(
                onGithub = openGithub,
                primaryText = "Download",
                onPrimary = { viewModel.downloadUpdate(targetState.updateInfo) },
                primaryTag = "download_update_button"
              )
            }
          }

          is UpdateUiState.Downloading -> {
            Column(modifier = Modifier.fillMaxWidth()) {
              val progressText = if (progress >= 0f) {
                "Downloading ${targetState.updateInfo.versionName}… ${(progress * 100).toInt()}%"
              } else {
                "Downloading ${targetState.updateInfo.versionName}…"
              }
              Text(
                text = progressText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary
              )
              Spacer(modifier = Modifier.height(8.dp))
              if (progress >= 0f) {
                LinearProgressIndicator(
                  progress = { progress },
                  color = Accent,
                  trackColor = SurfaceVariant,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                )
              } else {
                LinearProgressIndicator(
                  color = Accent,
                  trackColor = SurfaceVariant,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                )
              }
            }
          }

          is UpdateUiState.ReadyToInstall -> {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "Version ${targetState.updateInfo.versionName} is ready",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Android will ask you to confirm the installation.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
              )
              if (targetState.needsInstallPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Please allow installs from SubNexa in Android Settings, then tap Install Update again.",
                  style = MaterialTheme.typography.bodySmall.copy(color = Accent)
                )
              }
              Spacer(modifier = Modifier.height(14.dp))
              PrimaryAccentButton(
                text = "Install Update",
                onClick = { viewModel.installUpdate() },
                modifier = Modifier.fillMaxWidth(),
                testTag = "install_update_button"
              )
            }
          }

          is UpdateUiState.Error -> {
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.ErrorOutline,
                  contentDescription = null,
                  tint = Accent,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Update Failed",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = TextPrimary
                )
              }
              Text(
                text = targetState.displayMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
              )
              Spacer(modifier = Modifier.height(12.dp))
              ActionRow(
                onGithub = openGithub,
                primaryText = "Retry",
                onPrimary = { viewModel.retry() },
                primaryTag = "retry_update_button"
              )
            }
          }
        }
      }
    }
  }
}

/** Short text and color for the pill next to the app name, or null when there is nothing to say. */
private fun statusPill(state: UpdateUiState): Pair<String, Color>? = when (state) {
  is UpdateUiState.Idle -> null
  is UpdateUiState.Checking -> "Checking…" to TextMuted
  is UpdateUiState.UpToDate -> "Up to date" to SwatchGreen
  is UpdateUiState.Available -> "v${state.updateInfo.versionName} available" to Accent
  is UpdateUiState.Downloading -> "Downloading" to TextSecondary
  is UpdateUiState.ReadyToInstall -> "Ready to install" to Accent
  is UpdateUiState.Error -> "Failed" to Accent
}

@Composable
private fun StatusPill(text: String, color: Color) {
  Box(
    modifier = Modifier
      .clip(CircleShape)
      .background(color.copy(alpha = 0.12f))
      .padding(horizontal = 10.dp, vertical = 4.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp
      ),
      color = color
    )
  }
}

/** GitHub (outlined) on the left, the main update action (accent) on the right. */
@Composable
private fun ActionRow(
  onGithub: () -> Unit,
  primaryText: String,
  onPrimary: () -> Unit,
  primaryTag: String,
  primaryEnabled: Boolean = true
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    SecondarySubNexaButton(
      text = "GitHub",
      onClick = onGithub,
      modifier = Modifier.weight(0.85f),
      testTag = "github_link_row"
    )
    PrimaryAccentButton(
      text = primaryText,
      onClick = onPrimary,
      modifier = Modifier.weight(1.15f),
      enabled = primaryEnabled,
      testTag = primaryTag
    )
  }
}
