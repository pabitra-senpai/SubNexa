package com.subnexa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subnexa.app.data.SettingsRepository
import com.subnexa.app.domain.util.ColorUtils
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SwatchCyan
import com.subnexa.app.ui.theme.SwatchGreen
import com.subnexa.app.ui.theme.SwatchWhite
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PRESET_COLORS: List<Pair<Long, Color>> = listOf(
  0xFFFAFAFAL to SwatchWhite,
  0xFFFACC15L to SwatchYellow,
  0xFF22D3EEL to SwatchCyan,
  0xFF4ADE80L to SwatchGreen
)

/**
 * Subtitle text colour chooser: four quick presets, the user's recent custom colours and a "+"
 * button that opens a full picker (hue / saturation / brightness sliders and a hex field).
 * Colours are opaque ARGB Longs (0xFFRRGGBB), the same format SubtitleAppearance already stores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextColorPicker(
  selectedColor: Long,
  onColorSelected: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current.applicationContext
  val settings = remember { SettingsRepository.getInstance(context) }
  val recentCustom by settings.customTextColorsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()
  var showPicker by remember { mutableStateOf(false) }

  val presetValues = PRESET_COLORS.map { it.first }
  // The app's default text colour is pure white; treat it as the "white" preset.
  val shownColor = if (selectedColor == 0xFFFFFFFFL) 0xFFFAFAFAL else selectedColor
  // The current colour is always shown, even if it came from an old project and is not saved.
  val customColors = (recentCustom + shownColor)
    .filter { it !in presetValues }
    .distinct()

  FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    PRESET_COLORS.forEach { (value, color) ->
      ColorDot(
        color = color,
        selected = shownColor == value,
        onClick = { onColorSelected(value) }
      )
    }
    customColors.forEach { value ->
      ColorDot(
        color = Color(value),
        selected = shownColor == value,
        onClick = { onColorSelected(value) }
      )
    }
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .border(1.dp, InputBorder, CircleShape)
        .clickable { showPicker = true }
        .testTag("custom_color_add"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "Choose custom color",
        tint = TextSecondary,
        modifier = Modifier.size(20.dp)
      )
    }
  }

  if (showPicker) {
    CustomColorDialog(
      initialColor = selectedColor,
      onConfirm = { chosen ->
        showPicker = false
        onColorSelected(chosen)
        if (chosen !in presetValues) {
          scope.launch { settings.addCustomTextColor(chosen) }
        }
      },
      onDismiss = { showPicker = false }
    )
  }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .size(36.dp)
      .clip(CircleShape)
      .background(color)
      .border(
        width = if (selected) 2.5.dp else 0.5.dp,
        color = if (selected) Accent else Border,
        shape = CircleShape
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    if (selected) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(Color.Black)
      )
    }
  }
}

@Composable
private fun CustomColorDialog(
  initialColor: Long,
  onConfirm: (Long) -> Unit,
  onDismiss: () -> Unit
) {
  val initialHsv = remember(initialColor) { ColorUtils.toHsv(initialColor) }
  var hue by remember { mutableFloatStateOf(initialHsv[0]) }
  var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
  var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
  var hexText by remember { mutableStateOf(ColorUtils.toHex(initialColor)) }

  val current = ColorUtils.fromHsv(hue, saturation, brightness)
  val currentColor = Color(current)

  fun syncHexFromSliders() {
    hexText = ColorUtils.toHex(ColorUtils.fromHsv(hue, saturation, brightness))
  }

  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(Surface)
        .border(0.5.dp, Border, RoundedCornerShape(16.dp))
        .padding(20.dp)
    ) {
      Text(
        text = "Custom text color",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 17.sp
        ),
        color = TextPrimary
      )
      Spacer(modifier = Modifier.height(14.dp))

      // Live preview on a dark, video-like background
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(72.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFF111114))
          .border(0.5.dp, Border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Sample subtitle \u2022 \u09A8\u09AE\u09C1\u09A8\u09BE",
          color = currentColor,
          fontSize = 18.sp,
          fontWeight = FontWeight.Medium,
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      SliderLabel("Color")
      GradientSlider(
        value = hue / 360f,
        onValueChange = {
          hue = it * 360f
          syncHexFromSliders()
        },
        gradient = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color(ColorUtils.fromHsv(it, 1f, 1f)) },
        thumbColor = Color(ColorUtils.fromHsv(hue, 1f, 1f)),
        testTag = "color_hue_slider"
      )

      Spacer(modifier = Modifier.height(10.dp))
      SliderLabel("Saturation")
      GradientSlider(
        value = saturation,
        onValueChange = {
          saturation = it
          syncHexFromSliders()
        },
        gradient = listOf(
          Color(ColorUtils.fromHsv(hue, 0f, brightness)),
          Color(ColorUtils.fromHsv(hue, 1f, brightness))
        ),
        thumbColor = currentColor,
        testTag = "color_saturation_slider"
      )

      Spacer(modifier = Modifier.height(10.dp))
      SliderLabel("Brightness")
      GradientSlider(
        value = brightness,
        onValueChange = {
          brightness = it
          syncHexFromSliders()
        },
        gradient = listOf(
          Color.Black,
          Color(ColorUtils.fromHsv(hue, saturation, 1f))
        ),
        thumbColor = currentColor,
        testTag = "color_brightness_slider"
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = hexText,
        onValueChange = { raw ->
          val cleaned = raw.removePrefix("#").uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
          hexText = cleaned
          if (cleaned.length == 6) {
            ColorUtils.parseHex(cleaned)?.let { parsed ->
              val hsv = ColorUtils.toHsv(parsed)
              hue = hsv[0]
              saturation = hsv[1]
              brightness = hsv[2]
            }
          }
        },
        prefix = { Text("#", color = TextSecondary) },
        label = { Text("Hex code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("color_hex_field"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          focusedBorderColor = Accent,
          unfocusedBorderColor = Border,
          focusedLabelColor = Accent,
          unfocusedLabelColor = TextSecondary,
          cursorColor = Accent
        )
      )

      if (ColorUtils.isVeryDark(current)) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "This color is very dark and may be hard to read on video.",
          style = MaterialTheme.typography.bodySmall,
          color = TextMuted
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        SecondarySubNexaButton(
          text = "Cancel",
          onClick = onDismiss,
          modifier = Modifier.weight(1f)
        )
        PrimaryAccentButton(
          text = "Use color",
          onClick = { onConfirm(current) },
          modifier = Modifier.weight(1f),
          testTag = "color_use_button"
        )
      }
    }
  }
}

@Composable
private fun SliderLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = TextSecondary
  )
  Spacer(modifier = Modifier.height(4.dp))
}

/** A horizontal slider whose track is a colour gradient. [value] is 0..1. */
@Composable
private fun GradientSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  gradient: List<Color>,
  thumbColor: Color,
  testTag: String,
  modifier: Modifier = Modifier
) {
  val latestOnChange by rememberUpdatedState(onValueChange)
  var widthPx by remember { mutableFloatStateOf(1f) }
  val density = LocalDensity.current
  val thumbSize = 26.dp
  val thumbSizePx = with(density) { thumbSize.toPx() }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(thumbSize)
      .testTag(testTag)
      .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
      .pointerInput(Unit) {
        // The thumb centre travels from half a thumb to (width - half a thumb).
        val half = thumbSize.toPx() / 2f
        detectTapGestures { offset ->
          latestOnChange(((offset.x - half) / (size.width - 2f * half).coerceAtLeast(1f)).coerceIn(0f, 1f))
        }
      }
      .pointerInput(Unit) {
        val half = thumbSize.toPx() / 2f
        detectHorizontalDragGestures { change, _ ->
          change.consume()
          latestOnChange(((change.position.x - half) / (size.width - 2f * half).coerceAtLeast(1f)).coerceIn(0f, 1f))
        }
      }
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth()
        .padding(horizontal = thumbSize / 2)
        .height(14.dp)
        .clip(RoundedCornerShape(7.dp))
        .background(Brush.horizontalGradient(gradient))
        .border(0.5.dp, Border, RoundedCornerShape(7.dp))
    )
    // Thumb: centre follows the value, kept inside the track ends.
    val travel = (widthPx - thumbSizePx).coerceAtLeast(0f)
    Box(
      modifier = Modifier
        .align(Alignment.CenterStart)
        .offset { androidx.compose.ui.unit.IntOffset((value.coerceIn(0f, 1f) * travel).roundToInt(), 0) }
        .size(thumbSize)
        .clip(CircleShape)
        .background(thumbColor)
        .border(3.dp, Color.White, CircleShape)
    )
  }
}
