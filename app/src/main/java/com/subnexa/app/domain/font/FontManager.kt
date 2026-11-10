package com.subnexa.app.domain.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.text.font.FontFamily
import com.subnexa.app.ui.theme.HindSiliguriFamily
import com.subnexa.app.ui.theme.InterFamily
import com.subnexa.app.ui.theme.NotoSansBengaliFamily
import com.subnexa.app.ui.theme.NotoSansDevanagariFamily
import com.subnexa.app.ui.theme.NotoSerifBengaliFamily
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class SubtitleFontItem(
  val id: String,
  val displayName: String,
  val isCustom: Boolean = false,
  /** Scripts this font really has glyphs for. */
  val scripts: Set<FontScript> = emptySet(),
  /** True for the device font: it falls back per character, so it can show every language. */
  val coversAllLanguages: Boolean = false
) {
  /** Can this font draw text written in all of [required] scripts? */
  fun supports(required: Set<FontScript>): Boolean =
    required.isEmpty() || coversAllLanguages || scripts.containsAll(required)

  /** Short sample words shown in this font's own typeface, e.g. "日本語 • English". */
  fun sampleText(): String {
    if (coversAllLanguages) return "English • বাংলা • 日本語 • 한국어"
    val order = listOf(
      FontScript.JAPANESE, FontScript.KOREAN, FontScript.CHINESE, FontScript.BENGALI,
      FontScript.DEVANAGARI, FontScript.ARABIC, FontScript.THAI, FontScript.CYRILLIC, FontScript.LATIN
    )
    val words = order.filter { it in scripts }.take(3).map { it.sample }
    return if (words.isEmpty()) "Aa Bb Cc 123" else words.joinToString(" • ")
  }

  /** "Bengali · Latin" style summary for the badge line. */
  fun scriptSummary(): String = when {
    coversAllLanguages -> "All languages (device fonts)"
    scripts.isEmpty() -> "Language support unknown"
    else -> scripts.sortedBy { it.ordinal }.joinToString(" · ") { it.label }
  }
}

object FontManager {

  const val DEFAULT_FONT_ID = "hind_siliguri"
  private const val MAX_CUSTOM_FONTS = 10
  private const val MAX_FONT_SIZE_BYTES = 20 * 1024 * 1024L // 20 MB

  // Script coverage below was verified against the bundled .ttf cmap tables (see FontManagerTest).
  val BUILT_IN_FONTS = listOf(
    SubtitleFontItem("hind_siliguri", "Hind Siliguri", scripts = setOf(FontScript.BENGALI, FontScript.LATIN)),
    SubtitleFontItem("noto_sans_bengali", "Noto Sans Bengali", scripts = setOf(FontScript.BENGALI)),
    SubtitleFontItem("noto_serif_bengali", "Noto Serif Bengali", scripts = setOf(FontScript.BENGALI)),
    SubtitleFontItem("noto_sans_devanagari", "Noto Sans Devanagari", scripts = setOf(FontScript.DEVANAGARI)),
    SubtitleFontItem("inter", "Inter", scripts = setOf(FontScript.LATIN, FontScript.CYRILLIC)),
    SubtitleFontItem("system", "System default", scripts = FontScript.entries.toSet(), coversAllLanguages = true)
  )

  private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "otc")

  private val customTypefaceCache = ConcurrentHashMap<String, FontFamily>()
  private val customMetaCache = ConcurrentHashMap<String, Pair<Long, FontMeta?>>()

  private fun isFontFile(file: File) = file.isFile && file.extension.lowercase() in FONT_EXTENSIONS

  /** Font name + supported scripts read from the file itself (cached until the file changes). */
  fun customFontMeta(file: File): FontMeta? {
    val cached = customMetaCache[file.name]
    if (cached != null && cached.first == file.lastModified()) return cached.second
    val meta = FontParser.parse(file)
    customMetaCache[file.name] = file.lastModified() to meta
    return meta
  }

  fun getFontsDirectory(context: Context): File {
    return File(context.filesDir, "fonts").apply { mkdirs() }
  }

  fun getAvailableFonts(context: Context): List<SubtitleFontItem> {
    val fonts = BUILT_IN_FONTS.toMutableList()
    val customDir = getFontsDirectory(context)
    val customFiles = customDir.listFiles { file -> isFontFile(file) } ?: emptyArray()

    for (file in customFiles.sortedBy { it.name.lowercase() }) {
      val meta = customFontMeta(file)
      fonts.add(
        SubtitleFontItem(
          id = "custom:${file.name}",
          displayName = meta?.name ?: file.nameWithoutExtension,
          isCustom = true,
          scripts = meta?.scripts ?: emptySet()
        )
      )
    }
    return fonts
  }

  fun resolveFontFamily(context: Context, fontId: String): FontFamily {
    return when (fontId) {
      "hind_siliguri" -> HindSiliguriFamily
      "noto_sans_bengali" -> NotoSansBengaliFamily
      "noto_serif_bengali" -> NotoSerifBengaliFamily
      "noto_sans_devanagari" -> NotoSansDevanagariFamily
      "inter" -> InterFamily
      "system" -> FontFamily.Default
      else -> {
        if (fontId.startsWith("custom:")) {
          val fileName = fontId.removePrefix("custom:")
          val file = File(getFontsDirectory(context), fileName)
          if (file.exists() && file.isFile) {
            customTypefaceCache.getOrPut(fileName) {
              try {
                val typeface = Typeface.createFromFile(file)
                if (typeface != null) FontFamily(typeface) else HindSiliguriFamily
              } catch (_: Exception) {
                HindSiliguriFamily
              }
            }
          } else {
            HindSiliguriFamily
          }
        } else {
          HindSiliguriFamily
        }
      }
    }
  }

  fun importCustomFont(context: Context, uri: Uri): Result<SubtitleFontItem> {
    return try {
      val customDir = getFontsDirectory(context)
      val existingCustom = customDir.listFiles { file -> isFontFile(file) } ?: emptyArray()

      if (existingCustom.size >= MAX_CUSTOM_FONTS) {
        return Result.failure(IllegalStateException("Maximum of $MAX_CUSTOM_FONTS custom fonts reached. Please delete one first."))
      }

      // Query display name
      var displayName = "custom_font.ttf"
      try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIdx != -1 && cursor.moveToFirst()) {
            displayName = cursor.getString(nameIdx) ?: displayName
          }
        }
      } catch (_: Exception) {
        displayName = uri.lastPathSegment ?: displayName
      }

      val ext = displayName.substringAfterLast('.', "").lowercase()
      if (ext !in FONT_EXTENSIONS) {
        return Result.failure(IllegalArgumentException("This file cannot be used as a font (expected .ttf, .otf or .ttc)"))
      }

      // Check file size & copy to cache for validation
      val cacheFile = File(context.cacheDir, "temp_font_${System.currentTimeMillis()}.$ext")
      var bytesCopied = 0L

      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(cacheFile).use { output ->
          val buffer = ByteArray(8192)
          var bytesRead: Int
          while (input.read(buffer).also { bytesRead = it } != -1) {
            bytesCopied += bytesRead
            if (bytesCopied > MAX_FONT_SIZE_BYTES) {
              cacheFile.delete()
              return Result.failure(IllegalArgumentException("Font file is too large (maximum allowed size is 20 MB)"))
            }
            output.write(buffer, 0, bytesRead)
          }
          output.flush()
        }
      } ?: return Result.failure(IllegalStateException("Cannot open input stream for selected font"))

      // Check first 4 bytes: TrueType (00 01 00 00), OpenType ("OTTO"), "true", or "ttcf"
      val header = ByteArray(4)
      val readCount = FileInputStream(cacheFile).use { it.read(header) }
      val isValidMagic = readCount == 4 && (
        (header[0] == 0x00.toByte() && header[1] == 0x01.toByte() && header[2] == 0x00.toByte() && header[3] == 0x00.toByte()) ||
        (header[0] == 'O'.code.toByte() && header[1] == 'T'.code.toByte() && header[2] == 'T'.code.toByte() && header[3] == 'O'.code.toByte()) ||
        (header[0] == 't'.code.toByte() && header[1] == 'r'.code.toByte() && header[2] == 'u'.code.toByte() && header[3] == 'e'.code.toByte()) ||
        (header[0] == 't'.code.toByte() && header[1] == 't'.code.toByte() && header[2] == 'c'.code.toByte() && header[3] == 'f'.code.toByte())
      )

      if (!isValidMagic) {
        cacheFile.delete()
        return Result.failure(IllegalArgumentException("This file cannot be used as a font"))
      }

      // Validate font loading with Typeface
      val testTypeface = try {
        Typeface.createFromFile(cacheFile)
      } catch (_: Exception) {
        null
      }

      if (testTypeface == null) {
        cacheFile.delete()
        return Result.failure(IllegalArgumentException("This file cannot be used as a font"))
      }

      // Clean file name
      val baseName = displayName.substringBeforeLast('.').replace(Regex("[^a-zA-Z0-9._-]"), "_")
      var finalName = "$baseName.$ext"
      var destFile = File(customDir, finalName)
      var counter = 1
      while (destFile.exists()) {
        finalName = "${baseName}_$counter.$ext"
        destFile = File(customDir, finalName)
        counter++
      }

      if (!cacheFile.renameTo(destFile)) {
        cacheFile.copyTo(destFile, overwrite = true)
        cacheFile.delete()
      }

      val meta = customFontMeta(destFile)
      val item = SubtitleFontItem(
        id = "custom:$finalName",
        displayName = meta?.name ?: finalName.substringBeforeLast('.'),
        isCustom = true,
        scripts = meta?.scripts ?: emptySet()
      )
      Result.success(item)
    } catch (e: Exception) {
      Result.failure(IllegalArgumentException("This file cannot be used as a font: ${e.localizedMessage ?: "Unknown error"}"))
    }
  }

  fun deleteCustomFont(context: Context, fontId: String): Boolean {
    if (!fontId.startsWith("custom:")) return false
    val fileName = fontId.removePrefix("custom:")
    customTypefaceCache.remove(fileName)
    customMetaCache.remove(fileName)
    val file = File(getFontsDirectory(context), fileName)
    return if (file.exists()) {
      file.delete()
    } else false
  }
}
