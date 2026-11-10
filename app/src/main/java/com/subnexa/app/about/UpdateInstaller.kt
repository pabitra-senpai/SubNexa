package com.subnexa.app.about

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

object UpdateInstaller {

  suspend fun downloadAndVerifyApk(
    context: Context,
    updateInfo: AppUpdateInfo,
    currentVersionCode: Long,
    onProgress: (Float) -> Unit
  ): File = withContext(Dispatchers.IO) {
    val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
    val targetFile = File(updatesDir, "update_${updateInfo.versionCode}.apk")
    val partFile = File(updatesDir, "update_${updateInfo.versionCode}.apk.part")

    if (partFile.exists()) {
      partFile.delete()
    }
    if (targetFile.exists()) {
      targetFile.delete()
    }

    var connection: HttpURLConnection? = null
    try {
      val url = URL(updateInfo.apkUrl)
      connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept-Encoding", "identity")
      }

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        throw UpdateException(UpdateError.SERVER, "Download server returned HTTP $responseCode")
      }

      val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: updateInfo.apkSize ?: -1L
      var downloadedBytes = 0L

      connection.inputStream.use { input ->
        FileOutputStream(partFile).use { output ->
          val buffer = ByteArray(8192)
          var bytesRead: Int

          while (input.read(buffer).also { bytesRead = it } != -1) {
            coroutineContext.ensureActive()
            output.write(buffer, 0, bytesRead)
            downloadedBytes += bytesRead

            if (totalBytes > 0) {
              val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
              onProgress(progress)
            } else {
              onProgress(-1f)
            }
          }
          output.flush()
        }
      }

      if (!partFile.renameTo(targetFile)) {
        partFile.copyTo(targetFile, overwrite = true)
        partFile.delete()
      }

      // 1. Verify SHA-256 if provided
      val expectedSha256 = updateInfo.sha256?.trim()
      if (!expectedSha256.isNullOrBlank()) {
        val calculatedSha256 = computeSha256(targetFile)
        if (!calculatedSha256.equals(expectedSha256, ignoreCase = true)) {
          targetFile.delete()
          throw UpdateException(UpdateError.VERIFICATION_FAILED, "SHA-256 mismatch")
        }
      }

      // 2. Verify package name and version code via PackageManager
      val pm = context.packageManager
      val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(targetFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(targetFile.absolutePath, 0)
      }

      if (archiveInfo == null) {
        targetFile.delete()
        throw UpdateException(UpdateError.VERIFICATION_FAILED, "Could not parse downloaded APK")
      }

      val archivePackage = archiveInfo.packageName
      val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        archiveInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        archiveInfo.versionCode.toLong()
      }

      if (archivePackage != context.packageName) {
        targetFile.delete()
        throw UpdateException(
          UpdateError.VERIFICATION_FAILED,
          "Package name mismatch: expected ${context.packageName}, found $archivePackage"
        )
      }

      if (archiveVersionCode <= currentVersionCode) {
        targetFile.delete()
        throw UpdateException(
          UpdateError.VERIFICATION_FAILED,
          "Version code $archiveVersionCode is not newer than installed $currentVersionCode"
        )
      }

      targetFile
    } catch (e: CancellationException) {
      partFile.delete()
      targetFile.delete()
      throw e
    } catch (e: UpdateException) {
      partFile.delete()
      targetFile.delete()
      throw e
    } catch (e: UnknownHostException) {
      partFile.delete()
      targetFile.delete()
      throw UpdateException(UpdateError.NO_CONNECTION, "No connection to download server", e)
    } catch (e: ConnectException) {
      partFile.delete()
      targetFile.delete()
      throw UpdateException(UpdateError.NO_CONNECTION, "Failed to connect to download server", e)
    } catch (e: SocketTimeoutException) {
      partFile.delete()
      targetFile.delete()
      throw UpdateException(UpdateError.TIMEOUT, "Download timed out", e)
    } catch (e: Exception) {
      partFile.delete()
      targetFile.delete()
      throw UpdateException(UpdateError.DOWNLOAD_FAILED, e.message ?: "Failed to download update", e)
    } finally {
      connection?.disconnect()
    }
  }

  private fun computeSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (input.read(buffer).also { bytesRead = it } != -1) {
        digest.update(buffer, 0, bytesRead)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  fun canRequestPackageInstalls(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  fun startInstall(context: Context, apkFile: File): Boolean {
    if (!apkFile.exists()) {
      throw UpdateException(UpdateError.INSTALL_FAILED, "APK file not found")
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if (!context.packageManager.canRequestPackageInstalls()) {
        try {
          val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
          ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(settingsIntent)
        } catch (_: Exception) {
          val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(fallbackIntent)
        }
        return false
      }
    }

    try {
      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
      )

      val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(installIntent)
      return true
    } catch (e: Exception) {
      throw UpdateException(UpdateError.INSTALL_FAILED, "Failed to launch installer", e)
    }
  }
}
