package com.subnexa.app.about

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
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
  // Card 1: About App
  item {
    AboutAppCard(config = config)
  }

  // Card 2: Developer & Open Source
  item {
    DeveloperCard(config = config)
  }

  // Card 3: App Updates
  item {
    AppUpdatesCard(viewModel = viewModel)
  }
}

@Composable
private fun AboutAppCard(config: AboutConfig) {
  HairlineCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Header
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = Accent,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "About ${config.appName}",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
          ),
          color = TextPrimary
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = config.description,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Rows separated by 0.5dp Border dividers
      HorizontalDivider(thickness = 0.5.dp, color = Border)
      MetaRow(label = "Tagline", value = config.tagline)

      HorizontalDivider(thickness = 0.5.dp, color = Border)
      MetaRow(label = "App version", value = config.currentVersionName)

      HorizontalDivider(thickness = 0.5.dp, color = Border)
      MetaRow(label = "Developer", value = config.developerName)

      HorizontalDivider(thickness = 0.5.dp, color = Border)
      MetaRow(label = "Platform", value = config.platform)
    }
  }
}

@Composable
private fun MetaRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = TextPrimary
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = TextMuted
    )
  }
}

@Composable
private fun DeveloperCard(config: AboutConfig) {
  val uriHandler = LocalUriHandler.current

  HairlineCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Header
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Outlined.Code,
          contentDescription = null,
          tint = Accent,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Developer & Open Source",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
          ),
          color = TextPrimary
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Developer row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Developer",
          style = MaterialTheme.typography.labelMedium,
          color = TextPrimary
        )
        Text(
          text = config.developerName,
          style = MaterialTheme.typography.bodySmall,
          color = TextMuted
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Clickable rounded row with SurfaceVariant background
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(SurfaceVariant)
          .border(0.5.dp, Border, RoundedCornerShape(8.dp))
          .clickable {
            runCatching { uriHandler.openUri(config.githubUrl) }
          }
          .padding(horizontal = 14.dp, vertical = 12.dp)
          .testTag("github_link_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_github),
            contentDescription = "GitHub",
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "GitHub",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Medium,
              fontSize = 14.sp
            ),
            color = TextPrimary
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = config.githubUsername,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun AppUpdatesCard(viewModel: AboutViewModel) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()

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
      // Header
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Outlined.Refresh,
          contentDescription = null,
          tint = Accent,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "App Updates",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
          ),
          color = TextPrimary
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Check whether a newer version is available.",
        style = MaterialTheme.typography.bodySmall,
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
            PrimaryAccentButton(
              text = "Check for Updates",
              onClick = { viewModel.checkForUpdates() },
              modifier = Modifier.fillMaxWidth(),
              testTag = "check_updates_button"
            )
          }

          is UpdateUiState.Checking -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                color = Accent,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Checking for updates…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
              )
            }
          }

          is UpdateUiState.UpToDate -> {
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
              ) {
                Icon(
                  imageVector = Icons.Outlined.CheckCircle,
                  contentDescription = null,
                  tint = SwatchGreen,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "You're up to date",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "• v${targetState.currentVersion}",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextMuted
                )
              }
              SecondarySubNexaButton(
                text = "Check for Updates",
                onClick = { viewModel.checkForUpdates() },
                modifier = Modifier.fillMaxWidth(),
                testTag = "check_again_button"
              )
            }
          }

          is UpdateUiState.Available -> {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(
                text = "Update Available",
                style = MaterialTheme.typography.titleSmall.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = Accent
                )
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Current version",
                  style = MaterialTheme.typography.labelMedium,
                  color = TextPrimary
                )
                Text(
                  text = "v${targetState.currentVersion}",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextMuted
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Latest version",
                  style = MaterialTheme.typography.labelMedium,
                  color = TextPrimary
                )
                Text(
                  text = "v${targetState.updateInfo.versionName}",
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                  color = Accent
                )
              }
              Spacer(modifier = Modifier.height(14.dp))
              PrimaryAccentButton(
                text = "Download Update",
                onClick = { viewModel.downloadUpdate(targetState.updateInfo) },
                modifier = Modifier.fillMaxWidth(),
                testTag = "download_update_button"
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
                  text = "Update Check Failed",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  color = TextPrimary
                )
              }
              Text(
                text = targetState.displayMessage,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
              )
              Spacer(modifier = Modifier.height(14.dp))
              SecondarySubNexaButton(
                text = "Retry",
                onClick = { viewModel.retry() },
                modifier = Modifier.fillMaxWidth(),
                testTag = "retry_update_button"
              )
            }
          }
        }
      }
    }
  }
}
