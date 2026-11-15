package com.subnexa.app.ui.editor

import com.subnexa.app.domain.model.Project
import com.subnexa.app.domain.model.SubtitleEntry

/** Undo / redo stacks for the subtitle lines of a project. */
internal class EditHistory(private val maxSize: Int = 100) {

  data class Snapshot(
    val entries: List<SubtitleEntry>,
    val subtitleUri: String?,
    val subtitleName: String?
  )

  private val undoStack = ArrayDeque<Snapshot>()
  private val redoStack = ArrayDeque<Snapshot>()

  val canUndo: Boolean get() = undoStack.isNotEmpty()
  val canRedo: Boolean get() = redoStack.isNotEmpty()

  /** Call right before a change to the subtitle lines, with the project as it is now. */
  fun record(project: Project) {
    undoStack.addLast(snapshotOf(project))
    while (undoStack.size > maxSize) undoStack.removeFirst()
    redoStack.clear()
  }

  /** Takes the latest undo step and remembers [current] for redo. Null if there is none. */
  fun undo(current: Project): Snapshot? = step(from = undoStack, to = redoStack, current = current)

  /** Takes the latest redo step and remembers [current] for undo. Null if there is none. */
  fun redo(current: Project): Snapshot? = step(from = redoStack, to = undoStack, current = current)

  private fun step(
    from: ArrayDeque<Snapshot>,
    to: ArrayDeque<Snapshot>,
    current: Project
  ): Snapshot? {
    val snapshot = from.removeLastOrNull() ?: return null
    to.addLast(snapshotOf(current))
    return snapshot
  }

  private fun snapshotOf(project: Project) =
    Snapshot(project.entries, project.subtitleUri, project.subtitleName)
}
