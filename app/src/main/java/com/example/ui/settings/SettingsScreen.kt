package com.example.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Output
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.BgMode
import com.example.domain.model.ExportFormat
import com.example.domain.model.SubtitlePosition
import com.example.ui.components.HairlineCard
import com.example.ui.components.SegmentedControl
import com.example.ui.theme.Accent
import com.example.ui.theme.Background
import com.example.ui.theme.Border
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.SwatchCyan
import com.example.ui.theme.SwatchGreen
import com.example.ui.theme.SwatchWhite
import com.example.ui.theme.SwatchYellow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onNavigateBack: () -> Unit
) {
  val appearance by viewModel.defaultAppearance.collectAsStateWithLifecycle()
  val exportFormat by viewModel.defaultExportFormat.collectAsStateWithLifecycle()

  BackHandler {
    onNavigateBack()
  }

  Scaffold(
    containerColor = Background,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.statusBars)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.size(40.dp).testTag("settings_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Settings",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
          ),
          color = TextPrimary
        )
      }
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(vertical = 16.dp)
    ) {
      // 1. Theme Setting
      item {
        HairlineCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Outlined.DarkMode,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Theme",
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                  ),
                  color = TextPrimary
                )
                Text(
                  text = "Always dark (AMOLED)",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary
                )
              }
            }
            Text(
              text = "Dark",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = Accent
            )
          }
        }
      }

      // 2. Default Subtitle Appearance Setting
      item {
        HairlineCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Default Subtitle Appearance",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp
                ),
                color = TextPrimary
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font size slider
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Default font size", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
              Text("${appearance.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Slider(
              value = appearance.fontSizeSp,
              onValueChange = {
                viewModel.updateAppearance(appearance.copy(fontSizeSp = it))
              },
              valueRange = 12f..28f,
              steps = 7,
              colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = SurfaceVariant
              )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Color Swatches
            Text("Default text color", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
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
                val isSelected = appearance.textColorHex == colorLong
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
                      viewModel.updateAppearance(appearance.copy(textColorHex = colorLong))
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

            // Background Mode
            Text("Default background", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedControl(
              items = listOf(BgMode.NONE, BgMode.DIM, BgMode.SOLID),
              selectedItem = appearance.bgMode,
              onItemSelected = {
                viewModel.updateAppearance(appearance.copy(bgMode = it))
              },
              label = { it.displayName }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Position
            Text("Default position", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            SegmentedControl(
              items = listOf(SubtitlePosition.TOP, SubtitlePosition.MIDDLE, SubtitlePosition.BOTTOM),
              selectedItem = appearance.position,
              onItemSelected = {
                viewModel.updateAppearance(appearance.copy(position = it))
              },
              label = { it.displayName }
            )
          }
        }
      }

      // 3. Default Export Format Setting
      item {
        HairlineCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Outlined.Output,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Default Export Format",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp
                ),
                color = TextPrimary
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SegmentedControl(
              items = listOf(ExportFormat.SRT, ExportFormat.VTT),
              selectedItem = exportFormat,
              onItemSelected = {
                viewModel.updateExportFormat(it)
              },
              label = { it.displayName }
            )
          }
        }
      }

      // 4. About SubNexa
      item {
        HairlineCard(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "About SubNexa",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp
                ),
                color = TextPrimary
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "SubNexa is an offline-first subtitle editor designed to edit, synchronize, and export SRT and VTT subtitles with local video playback reference.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Tagline: Edit. Sync. Export.\nYour video is only used as a playback reference and is never altered or exported.",
              style = MaterialTheme.typography.bodySmall,
              color = TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "App version",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
              )
              Text(
                text = "1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
              )
            }
          }
        }
      }
    }
  }
}
