package com.subnexa.app.about

data class AboutConfig(
  val appName: String,
  val description: String,
  val tagline: String,
  val platform: String,
  val developerName: String,
  val githubUsername: String,
  val githubUrl: String,
  val currentVersionName: String,
  val currentVersionCode: Long,
  val updateManifestUrl: String
)
