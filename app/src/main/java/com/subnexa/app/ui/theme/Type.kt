package com.subnexa.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.subnexa.app.R

val PlusJakartaSansFamily = FontFamily(
  Font(R.font.plus_jakarta_sans, FontWeight.Normal),
  Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val HindSiliguriFamily = FontFamily(
  Font(R.font.hind_siliguri_regular, FontWeight.Normal),
  Font(R.font.hind_siliguri_medium, FontWeight.Medium),
  Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
  Font(R.font.hind_siliguri_bold, FontWeight.Bold)
)

val NotoSansBengaliFamily = FontFamily(
  Font(R.font.noto_sans_bengali_regular, FontWeight.Normal),
  Font(R.font.noto_sans_bengali_bold, FontWeight.Bold)
)

val NotoSerifBengaliFamily = FontFamily(
  Font(R.font.noto_serif_bengali_regular, FontWeight.Normal),
  Font(R.font.noto_serif_bengali_bold, FontWeight.Bold)
)

val NotoSansDevanagariFamily = FontFamily(
  Font(R.font.noto_sans_devanagari_regular, FontWeight.Normal),
  Font(R.font.noto_sans_devanagari_bold, FontWeight.Bold)
)

val InterFamily = FontFamily(
  Font(R.font.inter_regular, FontWeight.Normal)
)

val JetBrainsMonoFamily = FontFamily(
  Font(R.font.jetbrains_mono, FontWeight.Normal),
  Font(R.font.jetbrains_mono, FontWeight.Bold)
)

// Default subtitle text font family
val SubtitleFontFamily = HindSiliguriFamily

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    color = TextPrimary
  ),
  headlineMedium = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    color = TextPrimary
  ),
  titleLarge = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    color = TextPrimary
  ),
  titleMedium = TextStyle(
    fontFamily = PlusJakartaSansFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    color = TextPrimary
  ),
  bodyLarge = TextStyle(
    fontFamily = HindSiliguriFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    color = TextPrimary
  ),
  bodyMedium = TextStyle(
    fontFamily = HindSiliguriFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = TextSecondary
  ),
  bodySmall = TextStyle(
    fontFamily = HindSiliguriFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = TextMuted
  ),
  labelLarge = TextStyle(
    fontFamily = HindSiliguriFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = TextPrimary
  ),
  labelSmall = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    color = TextSecondary
  )
)

val MonospaceTimestampStyle = TextStyle(
  fontFamily = JetBrainsMonoFamily,
  fontWeight = FontWeight.Medium,
  fontSize = 12.sp,
  lineHeight = 16.sp,
  color = TextSecondary
)
