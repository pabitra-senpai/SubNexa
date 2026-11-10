package com.subnexa.app.about

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

object UpdateRepository {

  suspend fun fetchUpdateInfo(manifestUrl: String): AppUpdateInfo = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
      val url = URL(manifestUrl)
      connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("Accept", "application/json")
      }

      val responseCode = connection.responseCode
      if (responseCode !in 200..299) {
        throw UpdateException(UpdateError.SERVER, "Server returned HTTP $responseCode")
      }

      val jsonString = connection.inputStream.use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
      }

      val json = try {
        JSONObject(jsonString)
      } catch (e: JSONException) {
        throw UpdateException(UpdateError.INVALID_RESPONSE, "Malformed JSON response", e)
      }

      val versionCode = json.optLong("versionCode", -1L)
      val versionName = json.optString("versionName", "")
      val apkUrl = json.optString("apkUrl", "")
      val sha256 = if (json.has("sha256") && !json.isNull("sha256")) json.optString("sha256") else null
      val apkSize = if (json.has("apkSize") && !json.isNull("apkSize")) json.optLong("apkSize") else null

      if (versionCode <= 0 || versionName.isBlank() || apkUrl.isBlank()) {
        throw UpdateException(UpdateError.INVALID_RESPONSE, "Missing required version fields in manifest")
      }

      if (!apkUrl.startsWith("https://", ignoreCase = true)) {
        throw UpdateException(UpdateError.INVALID_RESPONSE, "APK URL must use HTTPS: $apkUrl")
      }

      AppUpdateInfo(
        versionCode = versionCode,
        versionName = versionName,
        apkUrl = apkUrl,
        sha256 = sha256?.takeIf { it.isNotBlank() },
        apkSize = apkSize?.takeIf { it > 0 }
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: UpdateException) {
      throw e
    } catch (e: UnknownHostException) {
      throw UpdateException(UpdateError.NO_CONNECTION, "Host not found", e)
    } catch (e: ConnectException) {
      throw UpdateException(UpdateError.NO_CONNECTION, "Failed to connect", e)
    } catch (e: SocketTimeoutException) {
      throw UpdateException(UpdateError.TIMEOUT, "Connection timed out", e)
    } catch (e: JSONException) {
      throw UpdateException(UpdateError.INVALID_RESPONSE, "Invalid JSON structure", e)
    } catch (e: Exception) {
      throw UpdateException(UpdateError.SERVER, e.message ?: "Failed to fetch update manifest", e)
    } finally {
      connection?.disconnect()
    }
  }
}
