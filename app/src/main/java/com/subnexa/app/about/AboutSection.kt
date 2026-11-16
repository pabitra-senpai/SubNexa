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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
  // Card 1: About SubNexa (version, developer, GitHub)
  item {
    AboutAppCard(config = config)
  }

  // Card 2: App updates
  item {
    AppUpdatesCard(viewModel = viewModel)
  }
}

/** Tappable card header with an optional one-line status and an expand / collapse chevron. */
@Composable
private fun AccordionHeader(
  icon: ImageVector,
  title: String,
  expanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  subtitleColor: Color = TextMuted
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .clickable(
        onClickLabel = if (expanded) "Collapse" else "Expand",
        role = Role.Button,
        onClick = onToggle
      ),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = Accent,
      modifier = Modifier.size(22.dp)
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 15.sp
        ),
        color = TextPrimary
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = subtitleColor
        )
      }
    }
    Icon(
      imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
      contentDescription = null,
      tint = TextMuted,
      modifier = Modifier.size(22.dp)
    )
  }
}

@Composable
private fun AboutAppCard(config: AboutConfig) {
  val uriHandler = LocalUriHandler.current
  var expanded by rememberSaveable { mutableStateOf(true) }

  HairlineCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(tween(200))
    ) {
      AccordionHeader(
        icon = Icons.Outlined.Info,
        title = "About ${config.appName}",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = Modifier.testTag("about_header")
      )

      if (expanded) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = config.description,
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        MetaRow(label = "Version", value = config.currentVersionName)
        MetaRow(label = "Developer", value = config.developerName)

        Spacer(modifier = Modifier.height(6.dp))

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
            .heightIn(min = 48.dp)
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
}

@Composable
private fun MetaRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
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

  var expanded by rememberSaveable { mutableStateOf(false) }

  // Open the card automatically when there is something to act on.
  val needsAttention = state !is UpdateUiState.Idle && state !is UpdateUiState.UpToDate
  LaunchedEffect(needsAttention) {
    if (needsAttention) expanded = true
  }

  val (statusText, statusColor) = when (val s = state) {
    is UpdateUiState.Idle -> "Check for new versions" to TextMuted
    is UpdateUiState.Checking -> "Checking…" to TextMuted
    is UpdateUiState.UpToDate -> "You're up to date" to SwatchGreen
    is UpdateUiState.Available -> "Update available · v${s.updateInfo.versionName}" to Accent
    is UpdateUiState.Downloading -> "Downloading v${s.updateInfo.versionName}…" to TextSecondary
    is UpdateUiState.ReadyToInstall -> "Ready to install" to Accent
    is UpdateUiState.Error -> "Update failed" to Accent
  }

  HairlineCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .animateContentSize(tween(200))
    ) {
      AccordionHeader(
        icon = Icons.Outlined.Refresh,
        title = "App updates",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = Modifier.testTag("updates_header"),
        subtitle = statusText,
        subtitleColor = statusColor
      )

      if (expanded) {
        Spacer(modifier = Modifier.height(10.dp))

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
}
