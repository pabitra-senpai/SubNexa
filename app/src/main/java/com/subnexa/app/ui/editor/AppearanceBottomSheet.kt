package com.subnexa.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.subnexa.app.domain.font.FontManager
import com.subnexa.app.domain.font.SubtitleLanguageInfo
import com.subnexa.app.ui.components.SubtitleFontSelector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.model.BgMode
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitlePosition
import com.subnexa.app.ui.components.TextColorPicker
import com.subnexa.app.ui.components.SegmentedControl
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Background
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceBottomSheet(
  initialAppearance: SubtitleAppearance,
  subtitleInfo: SubtitleLanguageInfo = SubtitleLanguageInfo.NONE,
  onDismiss: () -> Unit,
  onAppearanceChanged: (SubtitleAppearance) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val context = LocalContext.current
  val scrollState = rememberScrollState()

  var fontSize by remember { mutableFloatStateOf(initialAppearance.fontSizeSp) }
  var textColorHex by remember { mutableLongStateOf(initialAppearance.textColorHex) }
  var bgMode by remember { mutableStateOf(initialAppearance.bgMode) }
  var position by remember { mutableStateOf(initialAppearance.position) }
  var fontId by remember { mutableStateOf(initialAppearance.fontId) }

  val previewFont = remember(fontId) {
    FontManager.resolveFontFamily(context, fontId)
  }
  // Preview uses the real subtitle text; without a subtitle, sample words in the font's own scripts.
  val previewSample = remember(fontId) {
    FontManager.getAvailableFonts(context).firstOrNull { it.id == fontId }?.sampleText() ?: "Aa Bb Cc 123"
  }

  fun notifyChange() {
    onAppearanceChanged(
      SubtitleAppearance(
        fontSizeSp = fontSize,
        textColorHex = textColorHex,
        bgMode = bgMode,
        position = position,
        fontId = fontId
      )
    )
  }

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
        .verticalScroll(scrollState)
    ) {
      Text(
        text = "Subtitle appearance",
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = TextPrimary
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Live Preview Strip
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(80.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color.Black)
          .border(0.5.dp, Border, RoundedCornerShape(8.dp))
          .padding(8.dp),
        contentAlignment = when (position) {
          SubtitlePosition.TOP -> Alignment.TopCenter
          SubtitlePosition.MIDDLE -> Alignment.Center
          SubtitlePosition.BOTTOM -> Alignment.BottomCenter
        }
      ) {
        val previewBg = when (bgMode) {
          BgMode.NONE -> Color.Transparent
          BgMode.DIM -> Color(0x99000000)
          BgMode.SOLID -> Color(0xFF000000)
        }
        Text(
          text = subtitleInfo.sampleLine ?: previewSample,
          color = Color(textColorHex),
          fontSize = fontSize.sp,
          fontFamily = previewFont,
          fontWeight = FontWeight.Medium,
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(previewBg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Font size slider
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Font size", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
        Text("${fontSize.toInt()} sp", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
      }
      Slider(
        value = fontSize,
        onValueChange = {
          fontSize = it
          notifyChange()
        },
        valueRange = 12f..28f,
        steps = 7,
        colors = SliderDefaults.colors(
          thumbColor = Accent,
          activeTrackColor = Accent,
          inactiveTrackColor = SurfaceVariant
        )
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Text Color Swatches
      Text("Text color", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      Spacer(modifier = Modifier.height(8.dp))
      TextColorPicker(
        selectedColor = textColorHex,
        onColorSelected = {
          textColorHex = it
          notifyChange()
        }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Background segmented control
      Text("Background", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      Spacer(modifier = Modifier.height(6.dp))
      SegmentedControl(
        items = listOf(BgMode.NONE, BgMode.DIM, BgMode.SOLID),
        selectedItem = bgMode,
        onItemSelected = {
          bgMode = it
          notifyChange()
        },
        label = { it.displayName }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Position segmented control
      Text("Position", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
      Spacer(modifier = Modifier.height(6.dp))
      SegmentedControl(
        items = listOf(SubtitlePosition.TOP, SubtitlePosition.MIDDLE, SubtitlePosition.BOTTOM),
        selectedItem = position,
        onItemSelected = {
          position = it
          notifyChange()
        },
        label = { it.displayName }
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Subtitle font selector
      SubtitleFontSelector(
        subtitleInfo = subtitleInfo,
        selectedFontId = fontId,
        onFontSelected = {
          fontId = it
          notifyChange()
        }
      )

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
