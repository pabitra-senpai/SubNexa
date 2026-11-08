package com.subnexa.app.data

import android.content.Context
import com.subnexa.app.domain.model.BgMode
import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.ProjectSummary
import com.subnexa.app.domain.model.SubtitleAppearance
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.model.SubtitlePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectRepository(private val context: Context) {

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
      for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        list.add(
          ProjectSummary(
            id = obj.getString("id"),
            videoName = obj.getString("videoName"),
            subtitleCount = obj.getInt("subtitleCount"),
            lastEditedTimeMs = obj.getLong("lastEditedTimeMs")
          )
        )
      }
      // Sort by last edited descending
      _summariesFlow.value = list.sortedByDescending { it.lastEditedTimeMs }
    } catch (_: Exception) {
      _summariesFlow.value = emptyList()
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
        }
        jsonArray.put(obj)
      }
      indexFile.writeText(jsonArray.toString(2))
    } catch (_: Exception) {
      // Ignore write errors to index
    }
  }

  suspend fun getProject(projectId: String): Project? = withContext(Dispatchers.IO) {
    try {
      val file = File(projectsDir, "$projectId.json")
      if (!file.exists()) return@withContext null

      val root = JSONObject(file.readText())
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

      Project(
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
    } catch (_: Exception) {
      null
    }
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

      file.writeText(root.toString(2))

      // Update summaries
      val currentSummaries = _summariesFlow.value.filter { it.id != project.id }.toMutableList()
      currentSummaries.add(
        0,
        ProjectSummary(
          id = project.id,
          videoName = project.videoName,
          subtitleCount = project.entries.size,
          lastEditedTimeMs = project.lastEditedTimeMs
        )
      )
      val sorted = currentSummaries.sortedByDescending { it.lastEditedTimeMs }
      _summariesFlow.value = sorted
      saveSummaries(sorted)
    } catch (_: Exception) {
      // Save failure handled gracefully
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
    } catch (_: Exception) {
      // Ignore delete errors
    }
    project
  }

  suspend fun restoreProject(project: Project) = withContext(Dispatchers.IO) {
    saveProject(project)
  }
}
