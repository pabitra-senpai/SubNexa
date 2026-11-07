package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.domain.model.ExportFormat
import com.example.domain.model.SubtitleAppearance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

  private val settingsRepository = SettingsRepository(application)

  val defaultAppearance: StateFlow<SubtitleAppearance> = settingsRepository.defaultAppearanceFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubtitleAppearance())

  val defaultExportFormat: StateFlow<ExportFormat> = settingsRepository.defaultExportFormatFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExportFormat.SRT)

  fun updateAppearance(appearance: SubtitleAppearance) {
    viewModelScope.launch {
      settingsRepository.saveDefaultAppearance(appearance)
    }
  }

  fun updateExportFormat(format: ExportFormat) {
    viewModelScope.launch {
      settingsRepository.saveDefaultExportFormat(format)
    }
  }
}
