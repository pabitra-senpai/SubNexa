package com.subnexa.app.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AboutViewModel(
  application: Application,
  private val config: AboutConfig
) : AndroidViewModel(application) {

  private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
  val state: StateFlow<UpdateUiState> = _state.asStateFlow()

  private val _progress = MutableStateFlow(-1f)
  val progress: StateFlow<Float> = _progress.asStateFlow()

  fun checkForUpdates() {
    val current = _state.value
    if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) {
      return
    }

    _state.value = UpdateUiState.Checking
    viewModelScope.launch {
      try {
        val updateInfo = UpdateRepository.fetchUpdateInfo(config.updateManifestUrl)
        if (updateInfo.versionCode > config.currentVersionCode) {
          _state.value = UpdateUiState.Available(
            updateInfo = updateInfo,
            currentVersion = config.currentVersionName
          )
        } else {
          _state.value = UpdateUiState.UpToDate(currentVersion = config.currentVersionName)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: UpdateException) {
        _state.value = UpdateUiState.Error(
          error = e.error,
          retryInfo = RetryInfo(action = RetryAction.CHECK)
        )
      } catch (e: Exception) {
        _state.value = UpdateUiState.Error(
          error = UpdateError.UNKNOWN,
          retryInfo = RetryInfo(action = RetryAction.CHECK),
          customMessage = e.message
        )
      }
    }
  }

  fun downloadUpdate(updateInfo: AppUpdateInfo) {
    val current = _state.value
    if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) {
      return
    }

    _state.value = UpdateUiState.Downloading(updateInfo)
    _progress.value = -1f

    viewModelScope.launch {
      try {
        val apkFile = UpdateInstaller.downloadAndVerifyApk(
          context = getApplication(),
          updateInfo = updateInfo,
          currentVersionCode = config.currentVersionCode,
          onProgress = { p -> _progress.value = p }
        )
        val needsPermission = !UpdateInstaller.canRequestPackageInstalls(getApplication())
        _state.value = UpdateUiState.ReadyToInstall(
          updateInfo = updateInfo,
          apkFile = apkFile,
          needsInstallPermission = needsPermission
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: UpdateException) {
        _state.value = UpdateUiState.Error(
          error = e.error,
          retryInfo = RetryInfo(action = RetryAction.DOWNLOAD, updateInfo = updateInfo)
        )
      } catch (e: Exception) {
        _state.value = UpdateUiState.Error(
          error = UpdateError.DOWNLOAD_FAILED,
          retryInfo = RetryInfo(action = RetryAction.DOWNLOAD, updateInfo = updateInfo),
          customMessage = e.message
        )
      }
    }
  }

  fun installUpdate() {
    val current = _state.value
    if (current !is UpdateUiState.ReadyToInstall) return

    try {
      val started = UpdateInstaller.startInstall(getApplication(), current.apkFile)
      if (!started) {
        _state.value = current.copy(needsInstallPermission = true)
      }
    } catch (e: UpdateException) {
      _state.value = UpdateUiState.Error(
        error = e.error,
        retryInfo = RetryInfo(action = RetryAction.DOWNLOAD, updateInfo = current.updateInfo)
      )
    }
  }

  fun onAppResumed() {
    val current = _state.value
    if (current is UpdateUiState.ReadyToInstall && current.needsInstallPermission) {
      if (UpdateInstaller.canRequestPackageInstalls(getApplication())) {
        _state.value = current.copy(needsInstallPermission = false)
      }
    }
  }

  fun retry() {
    val current = _state.value
    if (current is UpdateUiState.Error) {
      if (current.retryInfo.action == RetryAction.DOWNLOAD && current.retryInfo.updateInfo != null) {
        downloadUpdate(current.retryInfo.updateInfo)
      } else {
        checkForUpdates()
      }
    }
  }

  class Factory(
    private val application: Application,
    private val config: AboutConfig
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(AboutViewModel::class.java)) {
        return AboutViewModel(application, config) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
  }
}
