package com.subnexa.app.domain.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour helpers that work on opaque ARGB values stored as Long (0xFFRRGGBB), the same format
 * used by SubtitleAppearance.textColorHex. Pure Kotlin so it can be unit-tested on the JVM.
 */
object ColorUtils {

  /** Relative luminance (0..1) below which text becomes hard to read on dark video. */
  private const val VERY_DARK_LUMINANCE = 0.03f

  fun rgb(argb: Long): Triple<Int, Int, Int> =
    Triple(((argb shr 16) and 0xFF).toInt(), ((argb shr 8) and 0xFF).toInt(), (argb and 0xFF).toInt())

  fun fromRgb(r: Int, g: Int, b: Int): Long =
    0xFF000000L or (r.coerceIn(0, 255).toLong() shl 16) or
      (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong()

  /** Returns [hue 0..360, saturation 0..1, value 0..1]. */
  fun toHsv(argb: Long): FloatArray {
    val (ri, gi, bi) = rgb(argb)
    val r = ri / 255f
    val g = gi / 255f
    val b = bi / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC

    val hue = when {
      delta == 0f -> 0f
      maxC == r -> 60f * (((g - b) / delta) % 6f)
      maxC == g -> 60f * (((b - r) / delta) + 2f)
      else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (maxC == 0f) 0f else delta / maxC
    return floatArrayOf(hue, saturation, maxC)
  }

  fun fromHsv(hue: Float, saturation: Float, value: Float): Long {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
      h < 60f -> Triple(c, x, 0f)
      h < 120f -> Triple(x, c, 0f)
      h < 180f -> Triple(0f, c, x)
      h < 240f -> Triple(0f, x, c)
      h < 300f -> Triple(x, 0f, c)
      else -> Triple(c, 0f, x)
    }
    return fromRgb(((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt())
  }

  /** "RRGGBB" in upper case, without '#'. */
  fun toHex(argb: Long): String = "%06X".format(argb and 0xFFFFFFL)

  /** Accepts "RRGGBB", "#RRGGBB", "RGB" and "#RGB". Returns null if the text is not a colour. */
  fun parseHex(text: String): Long? {
    val clean = text.trim().removePrefix("#")
    val full = when (clean.length) {
      6 -> clean
      3 -> clean.map { "$it$it" }.joinToString("")
      else -> return null
    }
    if (!full.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return 0xFF000000L or full.toLong(16)
  }

  /** WCAG relative luminance, 0 (black) .. 1 (white). */
  fun luminance(argb: Long): Float {
    val (r, g, b) = rgb(argb)
    fun channel(v: Int): Double {
      val c = v / 255.0
      return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return (0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)).toFloat()
  }

  fun isVeryDark(argb: Long): Boolean = luminance(argb) < VERY_DARK_LUMINANCE
}
