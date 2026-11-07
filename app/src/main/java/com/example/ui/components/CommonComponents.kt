package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Accent
import com.example.ui.theme.Border
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PrimaryAccentButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  testTag: String = "primary_button"
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .testTag(testTag),
    shape = RoundedCornerShape(10.dp),
    colors = ButtonDefaults.buttonColors(
      containerColor = Accent,
      contentColor = TextPrimary,
      disabledContainerColor = Accent.copy(alpha = 0.4f),
      disabledContentColor = TextPrimary.copy(alpha = 0.5f)
    ),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
      )
    )
  }
}

@Composable
fun SecondarySubNexaButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  testTag: String = "secondary_button"
) {
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .testTag(testTag),
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(0.5.dp, Border),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = Surface,
      contentColor = TextPrimary,
      disabledContainerColor = Surface.copy(alpha = 0.5f),
      disabledContentColor = TextSecondary.copy(alpha = 0.5f)
    ),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
      )
    )
  }
}

@Composable
fun HairlineCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  borderColor: Color = Border,
  backgroundColor: Color = Surface,
  content: @Composable () -> Unit
) {
  val shape = RoundedCornerShape(12.dp)
  val clickableModifier = if (onClick != null) {
    Modifier.clickable(onClick = onClick)
  } else Modifier

  Box(
    modifier = modifier
      .clip(shape)
      .background(backgroundColor)
      .border(0.5.dp, borderColor, shape)
      .then(clickableModifier)
      .padding(16.dp)
  ) {
    content()
  }
}

@Composable
fun <T> SegmentedControl(
  items: List<T>,
  selectedItem: T,
  onItemSelected: (T) -> Unit,
  modifier: Modifier = Modifier,
  label: (T) -> String
) {
  val shape = RoundedCornerShape(8.dp)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(44.dp)
      .clip(shape)
      .background(SurfaceVariant)
      .border(0.5.dp, Border, shape)
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    items.forEach { item ->
      val isSelected = item == selectedItem
      Box(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(6.dp))
          .background(if (isSelected) Accent else Color.Transparent)
          .clickable { onItemSelected(item) }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = label(item),
          style = MaterialTheme.typography.labelLarge.copy(
            color = if (isSelected) TextPrimary else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp
          ),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
