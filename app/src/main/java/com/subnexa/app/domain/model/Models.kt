package com.subnexa.app.domain.model

enum class BgMode(val displayName: String) {
  NONE("None"),
  DIM("Dim"),
  SOLID("Solid")
}

enum class SubtitlePosition(val displayName: String) {
  TOP("Top"),
  MIDDLE("Middle"),
  BOTTOM("Bottom")
}

enum class ExportFormat(val extension: String, val displayName: String) {
  SRT("srt", "SRT"),
  VTT("vtt", "VTT")
}

data class SubtitleAppearance(
  val fontSizeSp: Float = 16f,
  val textColorHex: Long = 0xFFFFFFFF,
  val bgMode: BgMode = BgMode.DIM,
  val position: SubtitlePosition = SubtitlePosition.BOTTOM
)

data class SubtitleEntry(
  val id: Long,
  val startMs: Long,
  val endMs: Long,
  val text: String
)

data class Project(
  val id: String,
  val videoUri: String,
  val videoName: String,
  val subtitleUri: String? = null,
  val subtitleName: String? = null,
  val entries: List<SubtitleEntry> = emptyList(),
  val lastPlaybackPositionMs: Long = 0L,
  val lastSelectedEntryId: Long? = null,
  val appearance: SubtitleAppearance = SubtitleAppearance(),
  val lastEditedTimeMs: Long = System.currentTimeMillis()
)

data class ProjectSummary(
  val id: String,
  val videoName: String,
  val subtitleCount: Int,
  val lastEditedTimeMs: Long
)
