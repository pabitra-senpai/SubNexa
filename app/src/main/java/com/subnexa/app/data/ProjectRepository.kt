package com.subnexa.app.data

import android.content.Context
import android.util.Log
import com.subnexa.app.domain.model.BgMode
import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.ProjectSummary
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.model.SubtitlePosition
import com.subnexa.app.domain.model.subtitleSourceLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ProjectRepository(private val context: Context) {

  companion object {
    private const val TAG = "ProjectRepository"

    @Volatile
    private var instance: ProjectRepository? = null

    fun getInstance(context: Context): ProjectRepository {
      return instance ?: synchronized(this) {
        instance ?: ProjectRepository(context.applicationContext).also { instance = it }
      }
    }
  }

  private val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
  private val indexFile = File(projectsDir, "index.json")

  private val _summariesFlow = MutableStateFlow<List<ProjectSummary>>(emptyList())
  val summariesFlow: StateFlow<List<ProjectSummary>> = _summariesFlow.asStateFlow()

  init {
    loadSummaries()
  }

  private fun loadSummaries() {
    try {
      if (!indexFile.exists()) {
        _summariesFlow.value = emptyList()
        return
      }
      val jsonStr = indexFile.readText()
      val jsonArray = JSONArray(jsonStr)
      val list = mutableListOf<ProjectSummary>()
      var migrated = false
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        var summary = ProjectSummary(
          id = obj.getString("id"),
          videoName = obj.getString("videoName"),
          subtitleCount = obj.getInt("subtitleCount"),
          lastEditedTimeMs = obj.getLong("lastEditedTimeMs"),
          videoUri = obj.optString("videoUri", ""),
          sourceLabel = if (obj.has("sourceLabel") && !obj.isNull("sourceLabel")) obj.getString("sourceLabel") else null
        )
        // Index files written by older versions have no videoUri / sourceLabel: fill them in once
        // from the project file itself.
        if (!obj.has("videoUri")) {
          summary = fillMissingSummaryFields(summary)
          migrated = true
        }
        list.add(summary)
      }
      // Sort by last edited descending
      val sorted = list.sortedByDescending { it.lastEditedTimeMs }
      _summariesFlow.value = sorted
      if (migrated) saveSummaries(sorted)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load summaries from index.json", e)
      _summariesFlow.value = emptyList()
    }
  }

  private fun fillMissingSummaryFields(summary: ProjectSummary): ProjectSummary {
    return try {
      val file = File(projectsDir, "${summary.id}.json")
      if (!file.exists()) return summary
      val root = JSONObject(file.readText())
      val subtitleName = if (root.has("subtitleName") && !root.isNull("subtitleName")) root.getString("subtitleName") else null
      summary.copy(
        videoUri = root.optString("videoUri", ""),
        sourceLabel = subtitleSourceLabel(subtitleName, summary.subtitleCount)
      )
    } catch (e: Exception) {
      Log.w(TAG, "Could not read project ${summary.id} to complete its summary", e)
      summary
    }
  }

  private fun atomicWrite(targetFile: File, content: String) {
    val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
    try {
      FileOutputStream(tempFile).use { fos ->
        fos.write(content.toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.fd.sync()
      }
      try {
        Files.move(
          tempFile.toPath(),
          targetFile.toPath(),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING
        )
      } catch (_: Exception) {
        // Fallback: first retry Files.move(temp, target, REPLACE_EXISTING) without ATOMIC_MOVE
        try {
          Files.move(
            tempFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
          )
        } catch (_: Exception) {
          // Never delete the target file before the replacement succeeds.
          try {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
          } catch (copyEx: Exception) {
            Log.e(TAG, "Fallback copy of ${tempFile.name} to ${targetFile.name} failed", copyEx)
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed atomic write for ${targetFile.name}", e)
      if (tempFile.exists()) {
        tempFile.delete()
      }
    }
  }

  private fun saveSummaries(summaries: List<ProjectSummary>) {
    try {
      val jsonArray = JSONArray()
      for (summary in summaries) {
        val obj = JSONObject().apply {
          put("id", summary.id)
          put("videoName", summary.videoName)
          put("subtitleCount", summary.subtitleCount)
          put("lastEditedTimeMs", summary.lastEditedTimeMs)
          put("videoUri", summary.videoUri)
          put("sourceLabel", summary.sourceLabel)
        }
        jsonArray.put(obj)
      }
      atomicWrite(indexFile, jsonArray.toString())
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save summaries to index.json", e)
    }
  }

  private fun parseProjectJson(jsonStr: String): Project {
    val root = JSONObject(jsonStr)
    val id = root.getString("id")
    val videoUri = root.getString("videoUri")
    val videoName = root.getString("videoName")
    val subtitleUri = if (root.has("subtitleUri") && !root.isNull("subtitleUri")) root.getString("subtitleUri") else null
    val subtitleName = if (root.has("subtitleName") && !root.isNull("subtitleName")) root.getString("subtitleName") else null
    val lastPlaybackPositionMs = root.optLong("lastPlaybackPositionMs", 0L)
    val lastSelectedEntryId = if (root.has("lastSelectedEntryId") && !root.isNull("lastSelectedEntryId")) root.getLong("lastSelectedEntryId") else null
    val lastEditedTimeMs = root.optLong("lastEditedTimeMs", System.currentTimeMillis())

    // Appearance
    val appObj = root.optJSONObject("appearance")
    val appearance = if (appObj != null) {
      SubtitleAppearance(
        fontSizeSp = appObj.optDouble("fontSizeSp", 16.0).toFloat(),
        textColorHex = appObj.optLong("textColorHex", 0xFFFFFFFFL),
        bgMode = try { BgMode.valueOf(appObj.optString("bgMode", "DIM")) } catch (_: Exception) { BgMode.DIM },
        position = try { SubtitlePosition.valueOf(appObj.optString("position", "BOTTOM")) } catch (_: Exception) { SubtitlePosition.BOTTOM },
        fontId = appObj.optString("fontId", "hind_siliguri")
      )
    } else {
      SubtitleAppearance()
    }

    // Entries
    val entriesArray = root.optJSONArray("entries") ?: JSONArray()
    val entries = mutableListOf<SubtitleEntry>()
    for (i in 0 until entriesArray.length()) {
      val entryObj = entriesArray.getJSONObject(i)
      entries.add(
        SubtitleEntry(
          id = entryObj.getLong("id"),
          startMs = entryObj.getLong("startMs"),
          endMs = entryObj.getLong("endMs"),
          text = entryObj.getString("text")
        )
      )
    }

    return Project(
      id = id,
      videoUri = videoUri,
      videoName = videoName,
      subtitleUri = subtitleUri,
      subtitleName = subtitleName,
      entries = entries.sortedBy { it.startMs },
      lastPlaybackPositionMs = lastPlaybackPositionMs,
      lastSelectedEntryId = lastSelectedEntryId,
      appearance = appearance,
      lastEditedTimeMs = lastEditedTimeMs
    )
  }

  suspend fun getProject(projectId: String): Project? = withContext(Dispatchers.IO) {
    val file = File(projectsDir, "$projectId.json")
    val tempFile = File(projectsDir, "$projectId.json.tmp")

    // Try reading primary file first
    if (file.exists()) {
      try {
        return@withContext parseProjectJson(file.readText())
      } catch (e: Exception) {
        Log.e(TAG, "Failed to parse primary project file for $projectId, checking tmp file", e)
      }
    }

    // If <id>.json is missing or fails to parse, but <id>.json.tmp exists and parses, use it and rename it into place
    if (tempFile.exists()) {
      try {
        val project = parseProjectJson(tempFile.readText())
        try {
          Files.move(
            tempFile.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING
          )
        } catch (_: Exception) {
          if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
          }
        }
        return@withContext project
      } catch (e: Exception) {
        Log.e(TAG, "Failed to parse tmp project file for $projectId", e)
      }
    }

    null
  }

  suspend fun saveProject(project: Project) = withContext(Dispatchers.IO) {
    try {
      val file = File(projectsDir, "${project.id}.json")
      val root = JSONObject().apply {
        put("id", project.id)
        put("videoUri", project.videoUri)
        put("videoName", project.videoName)
        put("subtitleUri", project.subtitleUri)
        put("subtitleName", project.subtitleName)
        put("lastPlaybackPositionMs", project.lastPlaybackPositionMs)
        put("lastSelectedEntryId", project.lastSelectedEntryId)
        put("lastEditedTimeMs", project.lastEditedTimeMs)

        // Appearance
        val appObj = JSONObject().apply {
          put("fontSizeSp", project.appearance.fontSizeSp.toDouble())
          put("textColorHex", project.appearance.textColorHex)
          put("bgMode", project.appearance.bgMode.name)
          put("position", project.appearance.position.name)
          put("fontId", project.appearance.fontId)
        }
        put("appearance", appObj)

        // Entries
        val entriesArray = JSONArray()
        for (entry in project.entries) {
          val entryObj = JSONObject().apply {
            put("id", entry.id)
            put("startMs", entry.startMs)
            put("endMs", entry.endMs)
            put("text", entry.text)
          }
          entriesArray.put(entryObj)
        }
        put("entries", entriesArray)
      }

      atomicWrite(file, root.toString())

      // Update summaries
      val currentSummaries = _summariesFlow.value.filter { it.id != project.id }.toMutableList()
      currentSummaries.add(
        0,
        ProjectSummary(
          id = project.id,
          videoName = project.videoName,
          subtitleCount = project.entries.size,
          lastEditedTimeMs = project.lastEditedTimeMs,
          videoUri = project.videoUri,
          sourceLabel = subtitleSourceLabel(project.subtitleName, project.entries.size)
        )
      )
      val sorted = currentSummaries.sortedByDescending { it.lastEditedTimeMs }
      _summariesFlow.value = sorted
      saveSummaries(sorted)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save project ${project.id}", e)
    }
  }

  suspend fun deleteProject(projectId: String): Project? = withContext(Dispatchers.IO) {
    val project = getProject(projectId)
    try {
      val file = File(projectsDir, "$projectId.json")
      if (file.exists()) {
        file.delete()
      }
      val updated = _summariesFlow.value.filter { it.id != projectId }
      _summariesFlow.value = updated
      saveSummaries(updated)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete project $projectId", e)
    }
    project
  }

  /** Changes the display name of a project. Does not change its "last edited" time. */
  suspend fun renameProject(projectId: String, newName: String) = withContext(Dispatchers.IO) {
    val project = getProject(projectId) ?: return@withContext
    saveProject(project.copy(videoName = newName))
  }

  suspend fun restoreProject(project: Project) = withContext(Dispatchers.IO) {
    saveProject(project)
  }
}
