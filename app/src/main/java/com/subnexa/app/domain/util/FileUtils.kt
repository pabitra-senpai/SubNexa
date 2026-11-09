package com.subnexa.app.domain.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object FileUtils {

  /**
   * Resolves the user-facing display name for a content URI via OpenableColumns,
   * falling back to uri.lastPathSegment or the provided default name.
   */
  fun getFileName(context: Context, uri: Uri, defaultName: String = "File"): String {
    var name = defaultName
    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
          name = cursor.getString(nameIndex) ?: name
        }
      }
    } catch (_: Exception) {
      name = uri.lastPathSegment ?: defaultName
    }
    return name
  }
}
