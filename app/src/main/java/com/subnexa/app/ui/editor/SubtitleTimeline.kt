package com.subnexa.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.domain.edit.TimelineEditing
import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.parser.SubtitleParser
import com.subnexa.app.domain.parser.TimeUtils
import com.subnexa.app.domain.validation.IssueType
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.SwatchYellow
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils

/** Which view fills the area under the action row. */
internal enum class EditorViewMode { LIST, TIMELINE }

private enum class DragKind { SCRUB, TRIM_START, TRIM_END, MOVE }

private const val MIN_DP_PER_SEC = 12f
private const val MAX_DP_PER_SEC = 600f
private const val DEFAULT_DP_PER_SEC = 80f
private const val ZOOM_BUTTON_FACTOR = 1.5f

private const val LANES = 4

private val RulerHeight = 22.dp
private val LaneTop = 28.dp
private val LaneHeight = 30.dp
private val LaneGap = 3.dp
private val TimelineBottomPad = 6.dp

private fun timelineHeight(): Dp =
  LaneTop + LaneHeight * LANES + LaneGap * (LANES - 1) + TimelineBottomPad
private val BlockColor = Color(0xFF3F3F46)

// ------------------------------------------------------------------------------------------
// View switch
// ------------------------------------------------------------------------------------------

/** Small two-option switch: List | Timeline. */
@Composable
internal fun EditorViewToggle(
  mode: EditorViewMode,
  onModeChange: (EditorViewMode) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(Surface)
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    ToggleOption(
      label = "List",
      selected = mode == EditorViewMode.LIST,
      onClick = { onModeChange(EditorViewMode.LIST) },
      tag = "view_toggle_list",
      modifier = Modifier.weight(1f)
    )
    ToggleOption(
      label = "Timeline",
      selected = mode == EditorViewMode.TIMELINE,
      onClick = { onModeChange(EditorViewMode.TIMELINE) },
      tag = "view_toggle_timeline",
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun ToggleOption(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(if (selected) Accent else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(vertical = 7.dp)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
      ),
      color = if (selected) Color.White else TextSecondary
    )
  }
}

// ------------------------------------------------------------------------------------------
// Timeline
// ------------------------------------------------------------------------------------------

/**
 * Video-editor style track. The playhead stays in the middle and the lines move under it.
 *
 * - 4 rows. Lines that overlap in time (duplicates too) are placed in different rows.
 * - Press a line and drag: select it and move it, sideways in time and up/down between rows.
 *   Drag the edges of the selected line to trim.
 * - Tap a line: select. Tap empty space in the rows: clear the selection. Tap the ruler: move the playhead.
 * - Drag on empty space: scrub the video.
 * - Pinch (or the zoom buttons): zoom in and out.
 *
 * Edges snap to neighbouring lines and to the playhead.
 */
@Composable
internal fun SubtitleTimeline(
  entries: List<SubtitleEntry>,
  issues: Map<Long, List<IssueType>>,
  selectedId: Long?,
  positionMs: State<Long>,
  isPlaying: Boolean,
  playbackSpeed: Float,
  durationMs: Long,
  onSelect: (Long?) -> Unit,
  onSeek: (Long) -> Unit,
  onScrubStart: () -> Unit,
  onDragStart: () -> Unit,
  onDrag: (id: Long, startMs: Long, endMs: Long) -> Unit,
  onDragEnd: () -> Unit,
  modifier: Modifier = Modifier
) {
  var dpPerSec by rememberSaveable { mutableStateOf(DEFAULT_DP_PER_SEC) }

  // Rows. A line keeps the row it was given or dragged to. New lines (split, duplicate, add)
  // are placed in the first row where they overlap nothing.
  val laneMap = remember { HashMap<Long, Int>() }
  val laneVersion = remember { mutableIntStateOf(0) }
  if (entries.any { !laneMap.containsKey(it.id) }) {
    laneMap.putAll(TimelineEditing.fillLanes(entries, laneMap, LANES))
  }

  // Smooth playhead. The player reports its position about every 50 ms, which would make the
  // track step 20 times a second. Between reports the position is estimated on every screen
  // frame and gently corrected towards each new report.
  val displayPos = remember { mutableFloatStateOf(positionMs.value.toFloat()) }
  val speedState = rememberUpdatedState(playbackSpeed)
  LaunchedEffect(isPlaying) {
    if (!isPlaying) {
      displayPos.floatValue = positionMs.value.toFloat()
      snapshotFlow { positionMs.value }.collect { displayPos.floatValue = it.toFloat() }
    } else {
      var lastFrame = withFrameNanos { it }
      var reported = positionMs.value
      var reportedAt = lastFrame
      displayPos.floatValue = reported.toFloat()
      while (true) {
        val now = withFrameNanos { it }
        val current = positionMs.value
        if (current != reported) {
          reported = current
          reportedAt = now
        }
        val frameMs = (now - lastFrame) / 1_000_000f
        lastFrame = now
        val speed = speedState.value
        val predicted = displayPos.floatValue + frameMs * speed
        val target = reported + (now - reportedAt) / 1_000_000f * speed
        displayPos.floatValue =
          if (abs(target - predicted) > 400f) target else predicted + (target - predicted) * 0.15f
      }
    }
  }

  // The gesture code lives as long as the composable, so it reads the latest values through these.
  val entriesState = rememberUpdatedState(entries)
  val selectedState = rememberUpdatedState(selectedId)
  val durationState = rememberUpdatedState(durationMs)
  val onSelectState = rememberUpdatedState(onSelect)
  val onSeekState = rememberUpdatedState(onSeek)
  val onScrubStartState = rememberUpdatedState(onScrubStart)
  val onDragStartState = rememberUpdatedState(onDragStart)
  val onDragState = rememberUpdatedState(onDrag)
  val onDragEndState = rememberUpdatedState(onDragEnd)

  // Plain Android paints: no Compose text layout runs while drawing, so drawing cannot throw
  // from text measuring, and it stays cheap while the timeline scrolls during playback.
  val labelPaint = remember { TextPaint(Paint.ANTI_ALIAS_FLAG) }
  val rulerPaint = remember { TextPaint(Paint.ANTI_ALIAS_FLAG) }
  // Cleaned-up text per line, so tags are not stripped again on every frame.
  val labelCache = remember { HashMap<String, String>() }

  Column(modifier = modifier.fillMaxWidth()) {
    // Time readout and zoom buttons
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TimeReadout(positionMs = positionMs, modifier = Modifier.weight(1f))
      IconButton(
        onClick = { dpPerSec = (dpPerSec / ZOOM_BUTTON_FACTOR).coerceIn(MIN_DP_PER_SEC, MAX_DP_PER_SEC) },
        modifier = Modifier.size(36.dp).testTag("timeline_zoom_out")
      ) {
        Icon(Icons.Outlined.ZoomOut, contentDescription = "Zoom out", tint = TextSecondary)
      }
      IconButton(
        onClick = { dpPerSec = (dpPerSec * ZOOM_BUTTON_FACTOR).coerceIn(MIN_DP_PER_SEC, MAX_DP_PER_SEC) },
        modifier = Modifier.size(36.dp).testTag("timeline_zoom_in")
      ) {
        Icon(Icons.Outlined.ZoomIn, contentDescription = "Zoom in", tint = TextSecondary)
      }
    }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(timelineHeight())
        .clipToBounds()
        .background(Surface)
        .testTag("subtitle_timeline")
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downX = down.position.x
            val downY = down.position.y
            val centerX = size.width / 2f
            val startPos = displayPos.floatValue
            val startPxPerMs = dpPerSec * density / 1000f
            if (startPxPerMs <= 0f) return@awaitEachGesture

            // A little more forgiving than the system value, so a tap with a slightly moving finger is still a tap.
            val slop = viewConfiguration.touchSlop * 1.5f
            val handleZonePx = 28.dp.toPx()
            val minTouchPx = 28.dp.toPx()
            val snapThresholdMs = (8.dp.toPx() / startPxPerMs).toLong()

            val list = entriesState.value
            val laneTopPx = LaneTop.toPx()
            val laneHeightPx = LaneHeight.toPx()
            val laneStepPx = laneHeightPx + LaneGap.toPx()

            // The ruler (top strip) moves the playhead. Under it are the rows for the lines.
            val inRows = downY >= laneTopPx
            val downLane = if (inRows) {
              val row = ((downY - laneTopPx) / laneStepPx).toInt()
              val inRow = (downY - laneTopPx) - row * laneStepPx <= laneHeightPx
              if (row < LANES && inRow) row else -1
            } else -1

            fun laneOf(entry: SubtitleEntry) = laneMap[entry.id] ?: 0
            fun xStart(entry: SubtitleEntry) = centerX + (entry.startMs - startPos) * startPxPerMs
            fun xEnd(entry: SubtitleEntry) = centerX + (entry.endMs - startPos) * startPxPerMs

            // The line under the finger. Very short lines get a wider touch area.
            fun lineAtDown(): SubtitleEntry? {
              if (downLane < 0) return null
              for (i in list.indices.reversed()) {
                val entry = list[i]
                if (laneOf(entry) != downLane) continue
                val xs = xStart(entry)
                val xe = xEnd(entry)
                val pad = ((minTouchPx - (xe - xs)) / 2f).coerceAtLeast(0f)
                if (downX >= xs - pad && downX <= xe + pad) return entry
              }
              return null
            }

            // What does this touch do if the finger starts moving?
            //  - edge of the selected line: trim, anywhere else on a line: move it (also up and
            //    down between rows), empty space: scrub.
            var kind = DragKind.SCRUB
            var target: SubtitleEntry? = null
            val selected = list.firstOrNull { it.id == selectedState.value }
            if (selected != null && downLane >= 0 && laneOf(selected) == downLane) {
              val xs = xStart(selected)
              val xe = xEnd(selected)
              val zone = minOf(handleZonePx, (xe - xs) / 3f)
              val nearStart = abs(downX - xs)
              val nearEnd = abs(downX - xe)
              if (nearStart <= zone && nearStart <= nearEnd) {
                kind = DragKind.TRIM_START
                target = selected
              } else if (nearEnd <= zone) {
                kind = DragKind.TRIM_END
                target = selected
              }
            }
            if (kind == DragKind.SCRUB) {
              val hit = lineAtDown()
              if (hit != null) {
                kind = DragKind.MOVE
                target = hit
              }
            }
            val grabLane = target?.let { laneOf(it) } ?: 0

            var passedSlop = false
            var pinching = false
            var previousDistance = 0f
            var editing = false
            var targets = LongArray(0)

            try {
              while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break

                // Two fingers: zoom. This ends any single-finger action for the rest of the touch.
                if (pressed.size >= 2) {
                  val distance = (pressed[0].position - pressed[1].position).getDistance()
                  if (!pinching) {
                    pinching = true
                  } else if (previousDistance > 0f && distance > 0f) {
                    dpPerSec = (dpPerSec * distance / previousDistance)
                      .coerceIn(MIN_DP_PER_SEC, MAX_DP_PER_SEC)
                  }
                  previousDistance = distance
                  pressed.forEach { it.consume() }
                  continue
                }
                if (pinching) {
                  pressed.forEach { it.consume() }
                  continue
                }

                val change = pressed[0]
                val dx = change.position.x - downX
                val dy = change.position.y - downY

                if (!passedSlop) {
                  // Moving a line can start with an up/down move; trimming and scrubbing are sideways only.
                  val moved = if (kind == DragKind.MOVE) abs(dx) > slop || abs(dy) > slop else abs(dx) > slop
                  if (!moved) continue
                  passedSlop = true
                  val grabbed = target
                  if (kind == DragKind.SCRUB || grabbed == null) {
                    kind = DragKind.SCRUB
                    onScrubStartState.value()
                  } else {
                    editing = true
                    if (grabbed.id != selectedState.value) onSelectState.value(grabbed.id)
                    targets = TimelineEditing.snapTargets(list, grabbed.id, startPos.toLong())
                    onDragStartState.value()
                  }
                }

                val deltaMs = (dx / startPxPerMs).toLong()
                val line = target
                when (kind) {
                  DragKind.SCRUB -> {
                    val newPos = (startPos - deltaMs).coerceAtLeast(0f)
                    displayPos.floatValue = newPos
                    onSeekState.value(newPos.toLong())
                  }
                  DragKind.TRIM_START -> if (line != null) {
                    val span = TimelineEditing.trimStart(
                      line, line.startMs + deltaMs, targets, snapThresholdMs
                    )
                    onDragState.value(line.id, span.startMs, span.endMs)
                  }
                  DragKind.TRIM_END -> if (line != null) {
                    val span = TimelineEditing.trimEnd(
                      line, line.endMs + deltaMs, targets, snapThresholdMs, durationState.value
                    )
                    onDragState.value(line.id, span.startMs, span.endMs)
                  }
                  DragKind.MOVE -> if (line != null) {
                    val span = TimelineEditing.move(
                      line, line.startMs + deltaMs, targets, snapThresholdMs, durationState.value
                    )
                    onDragState.value(line.id, span.startMs, span.endMs)
                    // Up and down: the line follows the finger to another row.
                    val newLane = (grabLane + (dy / laneStepPx).roundToInt()).coerceIn(0, LANES - 1)
                    if (laneMap[line.id] != newLane) {
                      laneMap[line.id] = newLane
                      laneVersion.intValue += 1
                    }
                  }
                }
                change.consume()
              }

              // Finger lifted without moving.
              if (!passedSlop && !pinching) {
                if (inRows) {
                  // Tap on a line selects it; tap on empty space clears the selection.
                  onSelectState.value(lineAtDown()?.id)
                } else {
                  // Tap on the ruler moves the playhead there.
                  val tapMs = (startPos + (downX - centerX) / startPxPerMs).toLong().coerceAtLeast(0L)
                  displayPos.floatValue = tapMs.toFloat()
                  onSeekState.value(tapMs)
                }
              }
            } finally {
              if (editing) onDragEndState.value()
            }
          }
        }
    ) {
      if (laneVersion.intValue < 0) return@Canvas // read so a row change redraws the canvas
      val pos = displayPos.floatValue
      val pxPerMs = dpPerSec * density / 1000f
      if (pxPerMs <= 0f) return@Canvas

      val width = size.width
      val height = size.height
      val centerX = width / 2f
      val laneTopPx = LaneTop.toPx()
      val laneHeightPx = LaneHeight.toPx()
      val laneStepPx = laneHeightPx + LaneGap.toPx()
      val radius = CornerRadius(6.dp.toPx())

      // Ruler
      val steps = longArrayOf(100, 250, 500, 1_000, 2_000, 5_000, 10_000, 30_000, 60_000, 300_000, 600_000)
      val labelGapPx = 64.dp.toPx()
      val major = steps.firstOrNull { it * pxPerMs >= labelGapPx } ?: steps.last()
      val minor = major / 5
      val visibleStartMs = pos - centerX / pxPerMs
      val visibleEndMs = pos + centerX / pxPerMs
      var tick = ((visibleStartMs.toLong() / minor) * minor).coerceAtLeast(0L)
      rulerPaint.textSize = 10.sp.toPx()
      rulerPaint.color = TextMuted.toArgb()
      while (tick <= visibleEndMs) {
        val x = centerX + (tick - pos) * pxPerMs
        val isMajor = tick % major == 0L
        val tickLength = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
        drawLine(
          color = Border,
          start = Offset(x, RulerHeight.toPx() - tickLength),
          end = Offset(x, RulerHeight.toPx()),
          strokeWidth = 1.dp.toPx()
        )
        if (isMajor) {
          drawLabel(
            paint = rulerPaint,
            text = formatTick(tick, major),
            x = x + 3.dp.toPx(),
            baseline = 12.dp.toPx(),
            maxWidth = width - x - 3.dp.toPx()
          )
        }
        tick += minor
      }
      drawLine(
        color = Border,
        start = Offset(0f, RulerHeight.toPx()),
        end = Offset(width, RulerHeight.toPx()),
        strokeWidth = 1.dp.toPx()
      )

      // Faint stripes show where each row is
      for (lane in 0 until LANES) {
        drawRect(
          color = SurfaceVariant.copy(alpha = 0.35f),
          topLeft = Offset(0f, laneTopPx + lane * laneStepPx),
          size = Size(width, laneHeightPx)
        )
      }

      // Subtitle blocks. The selected one is drawn last so it stays on top.
      labelPaint.textSize = 12.sp.toPx()
      labelPaint.color = TextPrimary.toArgb()
      val minBlockPx = 2.dp.toPx()
      if (labelCache.size > 4000) labelCache.clear()
      var selectedEntry: SubtitleEntry? = null
      for (entry in entries) {
        if (entry.id == selectedId) {
          selectedEntry = entry
          continue
        }
        val xs = centerX + (entry.startMs - pos) * pxPerMs
        val xe = centerX + (entry.endMs - pos) * pxPerMs
        if (xe < 0f || xs > width) continue
        val top = laneTopPx + (laneMap[entry.id] ?: 0) * laneStepPx
        val left = xs.coerceAtLeast(-radius.x)
        val right = maxOf(xe.coerceAtMost(width + radius.x), left + minBlockPx)
        val isActive = pos >= entry.startMs && pos < entry.endMs
        drawRoundRect(
          color = if (isActive) Accent.copy(alpha = 0.5f) else BlockColor,
          topLeft = Offset(left, top),
          size = Size(right - left, laneHeightPx),
          cornerRadius = radius
        )
        if (issues[entry.id].orEmpty().isNotEmpty()) {
          drawRoundRect(
            color = SwatchYellow,
            topLeft = Offset(left, top),
            size = Size(right - left, laneHeightPx),
            cornerRadius = radius,
            style = Stroke(width = 1.5.dp.toPx())
          )
        }
        drawLabel(
          paint = labelPaint,
          text = labelCache.getOrPut(entry.text) { blockLabel(entry) },
          x = left + 6.dp.toPx(),
          baseline = top + laneHeightPx / 2f + 4.dp.toPx(),
          maxWidth = right - left - 12.dp.toPx()
        )
      }

      selectedEntry?.let { entry ->
        val xs = centerX + (entry.startMs - pos) * pxPerMs
        val xe = centerX + (entry.endMs - pos) * pxPerMs
        if (xe >= 0f && xs <= width) {
          val top = laneTopPx + (laneMap[entry.id] ?: 0) * laneStepPx
          val left = xs.coerceAtLeast(-radius.x)
          val right = maxOf(xe.coerceAtMost(width + radius.x), left + minBlockPx)
          drawRoundRect(
            color = Accent,
            topLeft = Offset(left, top),
            size = Size(right - left, laneHeightPx),
            cornerRadius = radius
          )
          drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(right - left, laneHeightPx),
            cornerRadius = radius,
            style = Stroke(width = 2.dp.toPx())
          )
          // Grab handles on both edges
          val handleWidth = 4.dp.toPx()
          val handleHeight = 16.dp.toPx()
          val handleTop = top + (laneHeightPx - handleHeight) / 2f
          val handleSize = Size(handleWidth, handleHeight)
          val handleRadius = CornerRadius(2.dp.toPx())
          drawRoundRect(Color.White, Offset(xs + 3.dp.toPx(), handleTop), handleSize, handleRadius)
          drawRoundRect(Color.White, Offset(xe - 3.dp.toPx() - handleWidth, handleTop), handleSize, handleRadius)

          drawLabel(
            paint = labelPaint,
            text = labelCache.getOrPut(entry.text) { blockLabel(entry) },
            x = left + 14.dp.toPx(),
            baseline = top + laneHeightPx / 2f + 4.dp.toPx(),
            maxWidth = right - left - 28.dp.toPx()
          )
        }
      }

      // Playhead
      drawLine(
        color = Color.White,
        start = Offset(centerX, 0f),
        end = Offset(centerX, height),
        strokeWidth = 2.dp.toPx()
      )
      drawCircle(color = Accent, radius = 5.dp.toPx(), center = Offset(centerX, 5.dp.toPx()))
    }
  }
}

/** Current playback time. Kept in its own function so only this small text redraws while playing. */
@Composable
private fun TimeReadout(positionMs: State<Long>, modifier: Modifier = Modifier) {
  Text(
    text = TimeUtils.formatTimestamp(positionMs.value),
    modifier = modifier,
    style = MaterialTheme.typography.labelLarge.copy(
      fontWeight = FontWeight.SemiBold,
      fontSize = 13.sp
    ),
    color = TextSecondary
  )
}

// ------------------------------------------------------------------------------------------
// Selected line
// ------------------------------------------------------------------------------------------

/** Details and quick actions for the line selected on the timeline. */
@Composable
internal fun SelectedLinePanel(
  entry: SubtitleEntry?,
  lineNumber: Int,
  onEdit: () -> Unit,
  onSplit: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 10.dp)
  ) {
    if (entry == null) {
      Text(
        text = "Press a line and drag to move it. Select it and drag its edges to trim. " +
          "Drag empty space to scrub, pinch to zoom.",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted
      )
      return@Column
    }

    Text(
      text = "Line $lineNumber  \u2022  ${TimeUtils.formatTimestamp(entry.startMs)} \u2192 " +
        TimeUtils.formatTimestamp(entry.endMs),
      style = MaterialTheme.typography.labelMedium,
      color = TextMuted
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = blockLabel(entry),
      style = MaterialTheme.typography.bodyMedium,
      color = TextPrimary,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      PanelAction(Icons.Outlined.Edit, "Edit", onEdit, "timeline_edit", Modifier.weight(1f))
      PanelAction(Icons.Outlined.ContentCut, "Split", onSplit, "timeline_split", Modifier.weight(1f))
      PanelAction(Icons.Outlined.ContentCopy, "Duplicate", onDuplicate, "timeline_duplicate", Modifier.weight(1f))
      PanelAction(Icons.Outlined.Delete, "Delete", onDelete, "timeline_delete", Modifier.weight(1f))
    }
  }
}

@Composable
private fun PanelAction(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  tag: String,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(SurfaceVariant)
      .clickable(onClick = onClick)
      .padding(vertical = 8.dp)
      .testTag(tag),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(20.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
      color = TextSecondary,
      maxLines = 1,
      softWrap = false
    )
  }
}

// ------------------------------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------------------------------

/** Draws one line of text, cut with "..." to [maxWidth]. Does nothing when there is no room. */
private fun DrawScope.drawLabel(paint: TextPaint, text: String, x: Float, baseline: Float, maxWidth: Float) {
  if (maxWidth < 16f || text.isEmpty()) return
  val shown = TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END)
  drawIntoCanvas { canvas ->
    canvas.nativeCanvas.drawText(shown, 0, shown.length, x, baseline, paint)
  }
}

/** One-line preview of a line's text without style tags, kept short so drawing stays cheap. */
private fun blockLabel(entry: SubtitleEntry): String =
  SubtitleParser.stripTags(entry.text).replace("\r", "").replace("\n", " ").trim().take(80)

/** Ruler label such as 1:05, or 1:05.5 when the ruler is zoomed in far enough for tenths. */
private fun formatTick(ms: Long, stepMs: Long): String {
  val totalSeconds = ms / 1000
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return when {
    stepMs < 1000L -> String.format(Locale.US, "%d:%02d.%d", totalSeconds / 60, seconds, (ms % 1000) / 100)
    hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else -> String.format(Locale.US, "%d:%02d", minutes, seconds)
  }
}
