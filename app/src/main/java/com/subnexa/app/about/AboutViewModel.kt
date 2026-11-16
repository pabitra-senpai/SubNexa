package com.subnexa.app.about

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutViewModel(
  application: Application,
  private val config: AboutConfig
) : AndroidViewModel(application) {

  private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
  val state: StateFlow<UpdateUiState> = _state.asStateFlow()

  private val _progress = MutableStateFlow(-1f)
  val progress: StateFlow<Float> = _progress.asStateFlow()

  private var refreshJob: Job? = null

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
        val (finalInfo, apkFile) = downloadWithRecovery(updateInfo)
        val needsPermission = !UpdateInstaller.canRequestPackageInstalls(getApplication())
        _state.value = UpdateUiState.ReadyToInstall(
          updateInfo = finalInfo,
          apkFile = apkFile,
          needsInstallPermission = needsPermission
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: UpdateException) {
        _state.value = errorStateFor(e, updateInfo)
      } catch (e: Exception) {
        _state.value = UpdateUiState.Error(
          error = UpdateError.DOWNLOAD_FAILED,
          retryInfo = RetryInfo(action = RetryAction.DOWNLOAD, updateInfo = updateInfo),
          customMessage = e.message
        )
      }
    }
  }

  /**
   * Downloads and verifies the APK. If the checksum does not match, the update that was
   * published may have changed since the manifest was read (for example a newer build was
   * released while this screen was open). In that case the manifest is read again and, if it
   * now describes a different build, that build is downloaded once instead.
   */
  private suspend fun downloadWithRecovery(updateInfo: AppUpdateInfo): Pair<AppUpdateInfo, java.io.File> {
    try {
      return updateInfo to download(updateInfo)
    } catch (e: UpdateException) {
      if (e.error != UpdateError.VERIFICATION_FAILED || e.message != UpdateInstaller.SHA_MISMATCH_MESSAGE) {
        throw e
      }
      val fresh = UpdateRepository.fetchUpdateInfo(config.updateManifestUrl)
      val manifestChanged = fresh.sha256 != updateInfo.sha256 || fresh.versionCode != updateInfo.versionCode
      if (!manifestChanged || fresh.versionCode <= config.currentVersionCode) {
        throw e
      }
      _progress.value = -1f
      _state.value = UpdateUiState.Downloading(fresh)
      return fresh to download(fresh)
    }
  }

  private suspend fun download(info: AppUpdateInfo): java.io.File =
    UpdateInstaller.downloadAndVerifyApk(
      context = getApplication(),
      updateInfo = info,
      currentVersionCode = config.currentVersionCode,
      onProgress = { p -> _progress.value = p }
    )

  private fun errorStateFor(e: UpdateException, updateInfo: AppUpdateInfo): UpdateUiState.Error {
    if (e.error == UpdateError.VERIFICATION_FAILED) {
      return if (e.message == UpdateInstaller.SHA_MISMATCH_MESSAGE) {
        // The published file changed after the version was read: a fresh check fixes it.
        UpdateUiState.Error(
          error = e.error,
          retryInfo = RetryInfo(action = RetryAction.CHECK),
          customMessage = "The update file changed while downloading. Please check for updates again."
        )
      } else {
        UpdateUiState.Error(
          error = e.error,
          retryInfo = RetryInfo(action = RetryAction.DOWNLOAD, updateInfo = updateInfo),
          customMessage = "Package verification failed (${e.message ?: "unknown reason"})."
        )
      }
    }
    return UpdateUiState.Error(
      error = e.error,
      retryInfo = RetryInfo(action = RetryAction.DOWNLOAD, updateInfo = updateInfo)
    )
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
    refreshStaleUpdate()
  }

  /**
   * An update that was found (or downloaded) earlier can go out of date: a newer build may be
   * published later, or the user may already have installed it. When this screen comes back
   * on screen, quietly read the manifest again and move to the newest state. Network problems
   * are ignored here so that nothing changes on the screen.
   */
  private fun refreshStaleUpdate() {
    val shown = shownUpdate(_state.value) ?: return
    if (refreshJob?.isActive == true) return

    refreshJob = viewModelScope.launch {
      val latest = try {
        UpdateRepository.fetchUpdateInfo(config.updateManifestUrl)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        return@launch
      }

      val now = _state.value
      val currentShown = shownUpdate(now) ?: return@launch

      if (latest.versionCode <= config.currentVersionCode) {
        // Already installed (or nothing newer is published any more).
        deleteDownloadedApk(now)
        _state.value = UpdateUiState.UpToDate(currentVersion = config.currentVersionName)
      } else if (latest.versionCode > currentShown.versionCode) {
        // A newer build than the one on screen is available.
        deleteDownloadedApk(now)
        _state.value = UpdateUiState.Available(
          updateInfo = latest,
          currentVersion = config.currentVersionName
        )
      }
    }
  }

  private fun shownUpdate(state: UpdateUiState): AppUpdateInfo? = when (state) {
    is UpdateUiState.Available -> state.updateInfo
    is UpdateUiState.ReadyToInstall -> state.updateInfo
    else -> null
  }

  private suspend fun deleteDownloadedApk(state: UpdateUiState) {
    if (state is UpdateUiState.ReadyToInstall) {
      withContext(Dispatchers.IO) { runCatching { state.apkFile.delete() } }
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
