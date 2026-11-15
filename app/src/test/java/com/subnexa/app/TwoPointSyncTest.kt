package com.subnexa.app

import com.subnexa.app.domain.model.SubtitleEntry
import com.subnexa.app.domain.sync.TwoPointSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPointSyncTest {

  private val entries = listOf(
    SubtitleEntry(id = 1, startMs = 10_000L, endMs = 12_000L, text = "a"),
    SubtitleEntry(id = 2, startMs = 20_000L, endMs = 22_000L, text = "b"),
    SubtitleEntry(id = 3, startMs = 110_000L, endMs = 112_000L, text = "c")
  )

  @Test
  fun anchorsLandExactlyOnNewTimes() {
    val result = TwoPointSync.apply(entries, 1, 11_000L, 3, 121_000L)
    assertNotNull(result)
    val byId = result!!.entries.associateBy { it.id }
    assertEquals(11_000L, byId.getValue(1).startMs)
    assertEquals(121_000L, byId.getValue(3).startMs)
    // Scale is 110000 / 100000 = 1.1, so the middle line moves from 20s to 11s + 11s
    assertEquals(22_000L, byId.getValue(2).startMs)
    assertEquals(1.1, result.scale, 1e-9)
    assertEquals(0, result.clampedCount)
  }

  @Test
  fun equalSpanIsAPlainShift() {
    val result = TwoPointSync.apply(entries, 1, 9_000L, 3, 109_000L)
    assertNotNull(result)
    result!!.entries.zip(entries).forEach { (moved, original) ->
      assertEquals(original.startMs - 1_000L, moved.startMs)
      assertEquals(original.endMs - 1_000L, moved.endMs)
    }
    assertEquals(1.0, result.scale, 1e-9)
  }

  @Test
  fun linesBeforeZeroAreHeldAtZeroAndCounted() {
    val result = TwoPointSync.apply(entries, 2, 5_000L, 3, 95_000L)
    assertNotNull(result)
    val first = result!!.entries.first()
    assertEquals(1L, first.id)
    assertEquals(0L, first.startMs)
    assertEquals(1, result.clampedCount)
    assertTrue(first.endMs > first.startMs)
  }

  @Test
  fun unusableAnchorsReturnNull() {
    // Same line twice
    assertNull(TwoPointSync.apply(entries, 1, 1_000L, 1, 5_000L))
    // New times in the wrong order
    assertNull(TwoPointSync.apply(entries, 1, 50_000L, 3, 40_000L))
    // Unknown line id
    assertNull(TwoPointSync.apply(entries, 1, 1_000L, 99, 5_000L))
    // Negative first time
    assertNull(TwoPointSync.apply(entries, 1, -1L, 3, 5_000L))
  }
}
