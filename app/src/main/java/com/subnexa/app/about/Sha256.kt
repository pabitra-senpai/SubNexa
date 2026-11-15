package com.subnexa.app.about

import java.io.File
import java.security.MessageDigest
import java.util.Locale

private val SHA256_HEX = Regex("[0-9a-f]{64}")

/**
 * Returns [raw] trimmed and lowercased when it is a valid SHA-256 hex digest
 * (64 hex characters), otherwise null.
 */
fun normalizeSha256(raw: String?): String? {
  val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
  return if (SHA256_HEX.matches(value)) value else null
}

/** Streams [file] through SHA-256 and returns the lowercase hex digest. */
fun computeSha256(file: File): String {
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
