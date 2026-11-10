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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.font.FontManager
import com.subnexa.app.domain.font.SubtitleLanguageInfo
import com.subnexa.app.domain.font.SubtitleFontItem
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.DangerRed
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleFontSelector(
  selectedFontId: String,
  onFontSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
  /** Language + sample line of the loaded subtitle. Pass NONE (e.g. in Settings) when no subtitle is open. */
  subtitleInfo: SubtitleLanguageInfo = SubtitleLanguageInfo.NONE
) {
  val context = LocalContext.current
  var availableFonts by remember { mutableStateOf(FontManager.getAvailableFonts(context)) }
  var showFontPicker by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  // Refresh available fonts whenever picker is opened
  LaunchedEffect(showFontPicker) {
    if (showFontPicker) {
      availableFonts = FontManager.getAvailableFonts(context)
    }
  }

  val fontPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      val result = FontManager.importCustomFont(context, uri)
      result.onSuccess { newItem ->
        availableFonts = FontManager.getAvailableFonts(context)
        onFontSelected(newItem.id)
        showFontPicker = false
        errorMessage = null
        Toast.makeText(context, "Font imported: ${newItem.displayName}", Toast.LENGTH_SHORT).show()
      }.onFailure { error ->
        val msg = error.localizedMessage ?: "This file cannot be used as a font"
        errorMessage = msg
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
      }
    }
  }

  val selectedFontItem = remember(selectedFontId, availableFonts) {
    availableFonts.firstOrNull { it.id == selectedFontId }
      ?: FontManager.BUILT_IN_FONTS.firstOrNull { it.id == selectedFontId }
      ?: SubtitleFontItem(selectedFontId, selectedFontId)
  }

  val selectedFontFamily = remember(selectedFontId) {
    FontManager.resolveFontFamily(context, selectedFontId)
  }

  // Fonts are previewed with the user's real subtitle text when there is one, otherwise with
  // sample words in the scripts the font actually supports (never hard-coded Bengali/Hindi).
  val requiredScripts = subtitleInfo.requiredScripts
  fun previewFor(item: SubtitleFontItem): String = subtitleInfo.sampleLine ?: item.sampleText()

  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = "Subtitle font",
      style = MaterialTheme.typography.labelMedium,
      color = TextPrimary
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Single compact tappable row
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Surface)
        .border(
          width = 0.5.dp,
          color = Border,
          shape = RoundedCornerShape(8.dp)
        )
        .clickable { showFontPicker = true }
        .padding(horizontal = 14.dp, vertical = 10.dp)
        .testTag("subtitle_font_selector_trigger")
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = selectedFontItem.displayName,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.Normal,
              fontSize = 14.sp
            ),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = previewFor(selectedFontItem),
            fontFamily = selectedFontFamily,
            fontSize = 13.sp,
            color = Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
          imageVector = Icons.Outlined.ArrowDropDown,
          contentDescription = "Select font",
          tint = TextSecondary,
          modifier = Modifier.size(24.dp)
        )
      }
    }
  }

  // Material3 ModalBottomSheet for selecting fonts
  if (showFontPicker) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.70f

    ModalBottomSheet(
      onDismissRequest = { showFontPicker = false },
      sheetState = sheetState,
      containerColor = Surface,
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
      dragHandle = {
        Box(
          Modifier
            .padding(vertical = 10.dp)
            .width(36.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(Border)
        )
      }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = maxSheetHeight)
          .padding(horizontal = 20.dp)
          .navigationBarsPadding()
      ) {
        // 1. Header row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Select Subtitle Font",
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.SemiBold,
              fontSize = 16.sp
            ),
            color = TextPrimary
          )
          IconButton(
            onClick = { showFontPicker = false },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Outlined.Close,
              contentDescription = "Close",
              tint = TextSecondary,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Import font button at TOP of sheet (above font list)
        SecondarySubNexaButton(
          text = "Import font... (.ttf, .otf)",
          onClick = {
            fontPickerLauncher.launch(arrayOf("*/*"))
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("import_font_button")
        )

        if (errorMessage != null) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = errorMessage!!,
            style = MaterialTheme.typography.bodySmall.copy(color = DangerRed)
          )
        }

        // Language hint: tells the user what the subtitle is written in and which fonts can draw it.
        val primaryScript = subtitleInfo.primary
        if (requiredScripts.isNotEmpty() && primaryScript != null) {
          val needsImport = availableFonts.none { it.isCustom && it.supports(requiredScripts) }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Subtitle language: " + requiredScripts.joinToString(", ") { it.label },
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary
          )
          if (needsImport) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "No bundled font includes ${primaryScript.label}. \"System default\" works, " +
                "or import a ${primaryScript.label} font (.ttf / .otf / .ttc) for a stylish look.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary
            )
          } else {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Fonts that support ${primaryScript.label} are listed first.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary
            )
          }
        }

        // 10.dp gap between the Import font button (or error) and the font list
        Spacer(modifier = Modifier.height(10.dp))

        // 3. Scrollable list of fonts
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Stable sort: fonts that can draw the subtitle's language come first.
          val orderedFonts = availableFonts.sortedByDescending { it.supports(requiredScripts) }
          orderedFonts.forEach { item ->
            val isSelected = item.id == selectedFontId
            val supported = item.supports(requiredScripts)
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
                .clickable {
                  onFontSelected(item.id)
                  showFontPicker = false
                }
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
                    text = item.scriptSummary(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = previewFor(item),
                    fontFamily = fontSample,
                    fontSize = 15.sp,
                    color = when {
                      !supported -> TextMuted
                      isSelected -> Accent
                      else -> TextPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (!supported) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "Doesn't include " +
                        requiredScripts.filter { it !in item.scripts }.joinToString(", ") { it.label } +
                        " — device font is used instead",
                      style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                      color = DangerRed
                    )
                  }
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

          // Bottom padding inside scroll content so last item is never hidden under the navigation bar
          Spacer(modifier = Modifier.height(40.dp))
        }
      }
    }
  }
}
