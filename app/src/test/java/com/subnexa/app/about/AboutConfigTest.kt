package com.subnexa.app.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutConfigTest {

  @Test
  fun testSubNexaAboutConfigValues() {
    val config = subNexaAboutConfig()
    assertEquals("SubNexa", config.appName)
    assertEquals("Edit. Sync. Export.", config.tagline)
    assertEquals("Android", config.platform)
    assertEquals("Pabitra Senpai", config.developerName)
    assertEquals("pabitra-senpai", config.githubUsername)
    assertEquals("https://github.com/pabitra-senpai", config.githubUrl)
    assertEquals("https://github.com/pabitra-senpai/SubNexa/releases/latest/download/version.json", config.updateManifestUrl)
    assertTrue(config.currentVersionName.isNotEmpty())
    assertTrue(config.currentVersionCode > 0)
    assertTrue(config.description.contains("offline subtitle editor"))
  }

  @Test
  fun testUpdateUiStateErrorDisplayMessages() {
    val errNoConnection = UpdateUiState.Error(
      error = UpdateError.NO_CONNECTION,
      retryInfo = RetryInfo(action = RetryAction.CHECK)
    )
    assertTrue(errNoConnection.displayMessage.contains("No internet connection"))

    val errTimeout = UpdateUiState.Error(
      error = UpdateError.TIMEOUT,
      retryInfo = RetryInfo(action = RetryAction.CHECK)
    )
    assertTrue(errTimeout.displayMessage.contains("timed out"))

    val errVerification = UpdateUiState.Error(
      error = UpdateError.VERIFICATION_FAILED,
      retryInfo = RetryInfo(action = RetryAction.DOWNLOAD)
    )
    assertTrue(errVerification.displayMessage.contains("verification failed"))
  }
}
