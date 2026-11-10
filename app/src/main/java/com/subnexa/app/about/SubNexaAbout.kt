package com.subnexa.app.about

import com.subnexa.app.BuildConfig

fun subNexaAboutConfig(): AboutConfig {
  return AboutConfig(
    appName = "SubNexa",
    description = "An offline subtitle editor to edit, sync and export SRT and VTT subtitles. Your video is only a playback reference and is never changed.",
    tagline = "Edit. Sync. Export.",
    platform = "Android",
    developerName = "Pabitra Senpai",
    githubUsername = "pabitra-senpai",
    githubUrl = "https://github.com/pabitra-senpai",
    currentVersionName = BuildConfig.VERSION_NAME,
    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
    updateManifestUrl = "https://github.com/pabitra-senpai/SubNexa/releases/latest/download/version.json"
  )
}
