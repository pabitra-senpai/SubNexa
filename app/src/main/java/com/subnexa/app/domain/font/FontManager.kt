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
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class SubtitleFontItem(
  val id: String,
  val displayName: String,
  val isCustom: Boolean = false
)

object FontManager {

  const val DEFAULT_FONT_ID = "hind_siliguri"
  private const val MAX_CUSTOM_FONTS = 10
  private const val MAX_FONT_SIZE_BYTES = 20 * 1024 * 1024L // 20 MB

  val BUILT_IN_FONTS = listOf(
    SubtitleFontItem("hind_siliguri", "Hind Siliguri (Bengali + Latin)"),
    SubtitleFontItem("noto_sans_bengali", "Noto Sans Bengali"),
    SubtitleFontItem("noto_serif_bengali", "Noto Serif Bengali"),
    SubtitleFontItem("noto_sans_devanagari", "Noto Sans Devanagari (Hindi)"),
    SubtitleFontItem("inter", "Inter (Latin)"),
    SubtitleFontItem("system", "System default")
  )

  private val customTypefaceCache = ConcurrentHashMap<String, FontFamily>()

  fun getFontsDirectory(context: Context): File {
    return File(context.filesDir, "fonts").apply { mkdirs() }
  }

  fun getAvailableFonts(context: Context): List<SubtitleFontItem> {
    val fonts = BUILT_IN_FONTS.toMutableList()
    val customDir = getFontsDirectory(context)
    val customFiles = customDir.listFiles { file ->
      file.isFile && (file.extension.equals("ttf", ignoreCase = true) || file.extension.equals("otf", ignoreCase = true))
    } ?: emptyArray()

    for (file in customFiles.sortedBy { it.name.lowercase() }) {
      fonts.add(
        SubtitleFontItem(
          id = "custom:${file.name}",
          displayName = file.name,
          isCustom = true
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
      val existingCustom = customDir.listFiles { file ->
        file.isFile && (file.extension.equals("ttf", ignoreCase = true) || file.extension.equals("otf", ignoreCase = true))
      } ?: emptyArray()

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
      if (ext != "ttf" && ext != "otf") {
        return Result.failure(IllegalArgumentException("This file cannot be used as a font (expected .ttf or .otf)"))
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

      val item = SubtitleFontItem(
        id = "custom:$finalName",
        displayName = finalName,
        isCustom = true
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
    val file = File(getFontsDirectory(context), fileName)
    return if (file.exists()) {
      file.delete()
    } else false
  }
}
