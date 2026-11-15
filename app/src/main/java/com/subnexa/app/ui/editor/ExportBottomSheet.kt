package com.subnexa.app.ui.editor

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.ExportFormat
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.components.SegmentedControl
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchGreen
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

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
