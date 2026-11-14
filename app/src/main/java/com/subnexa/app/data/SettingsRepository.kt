package com.subnexa.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.subnexa.app.domain.model.BgMode
import com.subnexa.app.domain.model.ExportFormat
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitlePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "subnexa_settings")

class SettingsRepository(private val context: Context) {

  companion object {
    private const val MAX_CUSTOM_COLORS = 8

    @Volatile
    private var instance: SettingsRepository? = null

    fun getInstance(context: Context): SettingsRepository {
      return instance ?: synchronized(this) {
        instance ?: SettingsRepository(context.applicationContext).also { instance = it }
      }
    }
  }

  private object PrefKeys {
    val FONT_SIZE = floatPreferencesKey("pref_font_size")
    val TEXT_COLOR = longPreferencesKey("pref_text_color")
    val BG_MODE = stringPreferencesKey("pref_bg_mode")
    val POSITION = stringPreferencesKey("pref_position")
    val SUBTITLE_FONT_ID = stringPreferencesKey("pref_subtitle_font_id")
    val EXPORT_FORMAT = stringPreferencesKey("pref_export_format")
    val EXPORT_FOLDER_URI = stringPreferencesKey("pref_export_folder_uri")
    val CUSTOM_TEXT_COLORS = stringPreferencesKey("pref_custom_text_colors")
  }

  val defaultAppearanceFlow: Flow<SubtitleAppearance> = context.dataStore.data.map { prefs ->
    val fontSize = prefs[PrefKeys.FONT_SIZE] ?: 16f
    val colorHex = prefs[PrefKeys.TEXT_COLOR] ?: 0xFFFFFFFFL
    val bgModeStr = prefs[PrefKeys.BG_MODE] ?: BgMode.DIM.name
    val posStr = prefs[PrefKeys.POSITION] ?: SubtitlePosition.BOTTOM.name
    val fontId = prefs[PrefKeys.SUBTITLE_FONT_ID] ?: "hind_siliguri"

    val bgMode = try { BgMode.valueOf(bgModeStr) } catch (_: Exception) { BgMode.DIM }
    val pos = try { SubtitlePosition.valueOf(posStr) } catch (_: Exception) { SubtitlePosition.BOTTOM }

    SubtitleAppearance(
      fontSizeSp = fontSize,
      textColorHex = colorHex,
      bgMode = bgMode,
      position = pos,
      fontId = fontId
    )
  }

  val defaultExportFormatFlow: Flow<ExportFormat> = context.dataStore.data.map { prefs ->
    val formatStr = prefs[PrefKeys.EXPORT_FORMAT] ?: ExportFormat.SRT.name
    try { ExportFormat.valueOf(formatStr) } catch (_: Exception) { ExportFormat.SRT }
  }

  val defaultExportFolderUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
    prefs[PrefKeys.EXPORT_FOLDER_URI]?.ifEmpty { null }
  }

  /** Recently used custom subtitle colours (most recent first), as opaque 0xFFRRGGBB values. */
  val customTextColorsFlow: Flow<List<Long>> = context.dataStore.data.map { prefs ->
    parseCustomColors(prefs[PrefKeys.CUSTOM_TEXT_COLORS])
  }

  /** Remembers a custom colour at the front of the list. Keeps only the latest few. */
  suspend fun addCustomTextColor(color: Long) {
    context.dataStore.edit { prefs ->
      val updated = (listOf(color) + parseCustomColors(prefs[PrefKeys.CUSTOM_TEXT_COLORS]))
        .distinct()
        .take(MAX_CUSTOM_COLORS)
      prefs[PrefKeys.CUSTOM_TEXT_COLORS] = updated.joinToString(",")
    }
  }

  private fun parseCustomColors(raw: String?): List<Long> =
    raw.orEmpty()
      .split(',')
      .mapNotNull { it.trim().toLongOrNull() }
      .filter { it in 0xFF000000L..0xFFFFFFFFL }

  suspend fun saveDefaultAppearance(appearance: SubtitleAppearance) {
    context.dataStore.edit { prefs ->
      prefs[PrefKeys.FONT_SIZE] = appearance.fontSizeSp
      prefs[PrefKeys.TEXT_COLOR] = appearance.textColorHex
      prefs[PrefKeys.BG_MODE] = appearance.bgMode.name
      prefs[PrefKeys.POSITION] = appearance.position.name
      prefs[PrefKeys.SUBTITLE_FONT_ID] = appearance.fontId
    }
  }

  suspend fun saveDefaultExportFormat(format: ExportFormat) {
    context.dataStore.edit { prefs ->
      prefs[PrefKeys.EXPORT_FORMAT] = format.name
    }
  }

  suspend fun saveDefaultExportFolderUri(uriString: String) {
    context.dataStore.edit { prefs ->
      prefs[PrefKeys.EXPORT_FOLDER_URI] = uriString
    }
  }
}
