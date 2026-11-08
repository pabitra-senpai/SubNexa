package com.subnexa.app.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.font.FontManager
import com.subnexa.app.domain.font.SubtitleFontItem
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.DangerRed
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

@Composable
fun SubtitleFontSelector(
  selectedFontId: String,
  onFontSelected: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var availableFonts by remember { mutableStateOf(FontManager.getAvailableFonts(context)) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val fontPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      val result = FontManager.importCustomFont(context, uri)
      result.onSuccess { newItem ->
        availableFonts = FontManager.getAvailableFonts(context)
        onFontSelected(newItem.id)
        errorMessage = null
        Toast.makeText(context, "Font imported: ${newItem.displayName}", Toast.LENGTH_SHORT).show()
      }.onFailure { error ->
        val msg = error.localizedMessage ?: "This file cannot be used as a font"
        errorMessage = msg
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
      }
    }
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = "Subtitle font",
      style = MaterialTheme.typography.labelMedium,
      color = TextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    // List of available fonts
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      availableFonts.forEach { item ->
        val isSelected = item.id == selectedFontId
        val fontSample = remember(item.id) {
          FontManager.resolveFontFamily(context, item.id)
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SurfaceVariant else Surface)
            .border(
              width = if (isSelected) 1.5.dp else 0.5.dp,
              color = if (isSelected) Accent else Border,
              shape = RoundedCornerShape(8.dp)
            )
            .clickable { onFontSelected(item.id) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("font_option_${item.id}")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                  fontSize = 14.sp
                ),
                color = if (isSelected) TextPrimary else TextSecondary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "বাংলা • English • हिंदी",
                fontFamily = fontSample,
                fontSize = 13.sp,
                color = if (isSelected) Accent else TextMuted
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              if (item.isCustom) {
                IconButton(
                  onClick = {
                    FontManager.deleteCustomFont(context, item.id)
                    availableFonts = FontManager.getAvailableFonts(context)
                    if (selectedFontId == item.id) {
                      onFontSelected(FontManager.DEFAULT_FONT_ID)
                    }
                  },
                  modifier = Modifier.size(32.dp)
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete font",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                  )
                }
                Spacer(modifier = Modifier.width(6.dp))
              }

              if (isSelected) {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = "Selected",
                  tint = Accent,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      }
    }

    if (errorMessage != null) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = errorMessage!!,
        style = MaterialTheme.typography.bodySmall.copy(color = DangerRed)
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Import font button
    SecondarySubNexaButton(
      text = "Import font... (.ttf, .otf)",
      onClick = {
        fontPickerLauncher.launch(arrayOf("*/*"))
      },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("import_font_button")
    )
  }
}
