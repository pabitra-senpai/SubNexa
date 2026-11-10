package com.subnexa.app.about

import java.io.File

data class AppUpdateInfo(
  val versionCode: Long,
  val versionName: String,
  val apkUrl: String,
  val sha256: String? = null,
  val apkSize: Long? = null
)

enum class UpdateError {
  NO_CONNECTION,
  TIMEOUT,
  SERVER,
  INVALID_RESPONSE,
  DOWNLOAD_FAILED,
  VERIFICATION_FAILED,
  INSTALL_FAILED,
  UNKNOWN
}

class UpdateException(
  val error: UpdateError,
  message: String? = null,
  cause: Throwable? = null
) : Exception(message ?: error.name, cause)

enum class RetryAction {
  CHECK,
  DOWNLOAD
}

data class RetryInfo(
  val action: RetryAction,
  val updateInfo: AppUpdateInfo? = null
)

sealed interface UpdateUiState {
  data object Idle : UpdateUiState
  data object Checking : UpdateUiState
  data class UpToDate(val currentVersion: String) : UpdateUiState
  data class Available(val updateInfo: AppUpdateInfo, val currentVersion: String) : UpdateUiState
  data class Downloading(val updateInfo: AppUpdateInfo) : UpdateUiState
  data class ReadyToInstall(
    val updateInfo: AppUpdateInfo,
    val apkFile: File,
    val needsInstallPermission: Boolean
  ) : UpdateUiState
  data class Error(
    val error: UpdateError,
    val retryInfo: RetryInfo,
    val customMessage: String? = null
  ) : UpdateUiState {
    val displayMessage: String
      get() = customMessage ?: when (error) {
        UpdateError.NO_CONNECTION -> "No internet connection. Please check your network."
        UpdateError.TIMEOUT -> "Connection timed out. Please try again."
        UpdateError.SERVER -> "Server unreachable or returned an error."
        UpdateError.INVALID_RESPONSE -> "Invalid response from update server."
        UpdateError.DOWNLOAD_FAILED -> "Download failed. Please check your connection."
        UpdateError.VERIFICATION_FAILED -> "Package verification failed."
        UpdateError.INSTALL_FAILED -> "Installer unavailable or installation failed."
        UpdateError.UNKNOWN -> "An unexpected error occurred."
      }
  }
}
