package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SubNexaColorScheme = darkColorScheme(
  primary = Accent,
  onPrimary = TextPrimary,
  primaryContainer = SurfaceVariant,
  onPrimaryContainer = TextPrimary,
  secondary = TextSecondary,
  onSecondary = Background,
  secondaryContainer = SurfaceVariant,
  onSecondaryContainer = TextPrimary,
  tertiary = AccentHover,
  onTertiary = TextPrimary,
  background = Background,
  onBackground = TextPrimary,
  surface = Surface,
  onSurface = TextPrimary,
  surfaceVariant = SurfaceVariant,
  onSurfaceVariant = TextSecondary,
  outline = Border,
  outlineVariant = InputBorder,
  error = DangerRed,
  onError = TextPrimary,
  errorContainer = DangerFill,
  onErrorContainer = TextPrimary
)

val SubNexaShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(10.dp), // 10dp buttons
  large = RoundedCornerShape(12.dp),  // 12dp cards
  extraLarge = RoundedCornerShape(20.dp) // 20dp sheet top corners
)

@Composable
fun SubNexaTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = SubNexaColorScheme,
    typography = Typography,
    shapes = SubNexaShapes,
    content = content
  )
}
