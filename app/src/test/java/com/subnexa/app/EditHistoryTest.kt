package com.subnexa.app

import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.ui.editor.EditHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

  private fun project(vararg ids: Long) = Project(
    id = "p",
    videoUri = "content://video",
    videoName = "video.mp4",
    entries = ids.map { SubtitleEntry(id = it, startMs = it * 1000, endMs = it * 1000 + 500, text = "t$it") }
  )

  @Test
  fun startsEmpty() {
    val history = EditHistory()
    assertFalse(history.canUndo)
    assertFalse(history.canRedo)
    assertNull(history.undo(project(1)))
    assertNull(history.redo(project(1)))
  }

  @Test
  fun undoThenRedoRestoresBothStates() {
    val history = EditHistory()
    history.record(project(1))
    assertTrue(history.canUndo)
    assertFalse(history.canRedo)

    val undone = history.undo(project(1, 2))
    assertNotNull(undone)
    assertEquals(project(1).entries, undone!!.entries)
    assertFalse(history.canUndo)
    assertTrue(history.canRedo)

    val redone = history.redo(project(1))
    assertNotNull(redone)
    assertEquals(project(1, 2).entries, redone!!.entries)
    assertTrue(history.canUndo)
    assertFalse(history.canRedo)
  }

  @Test
  fun recordingANewChangeClearsRedo() {
    val history = EditHistory()
    history.record(project(1))
    history.undo(project(1, 2))
    assertTrue(history.canRedo)

    history.record(project(1, 3))
    assertFalse(history.canRedo)
  }

  @Test
  fun oldestStepsAreDroppedBeyondMaxSize() {
    val history = EditHistory(maxSize = 2)
    history.record(project(1))
    history.record(project(2))
    history.record(project(3))

    assertEquals(project(3).entries, history.undo(project(4))!!.entries)
    assertEquals(project(2).entries, history.undo(project(3))!!.entries)
    assertNull(history.undo(project(2)))
  }

  @Test
  fun snapshotKeepsSubtitleSource() {
    val history = EditHistory()
    history.record(project(1).copy(subtitleUri = "content://a.srt", subtitleName = "a.srt"))

    val snapshot = history.undo(project(1))!!
    assertEquals("content://a.srt", snapshot.subtitleUri)
    assertEquals("a.srt", snapshot.subtitleName)
  }
}
