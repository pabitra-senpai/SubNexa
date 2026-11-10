package com.subnexa.app.domain.font

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.Charset

/** What we learn about a font file by reading its tables: a readable name and the scripts it really covers. */
data class FontMeta(
  val name: String?,
  val scripts: Set<FontScript>
)

/**
 * Minimal, dependency-free TrueType/OpenType reader. It only reads the `cmap` (which characters
 * have glyphs) and `name` (family / full name) tables, so it is cheap even for 20 MB CJK fonts.
 *
 * Android's own `Paint.hasGlyph` cannot be used for this: a Typeface loaded from a file silently
 * falls back to system fonts, so it would report Japanese support for a Latin-only font.
 */
object FontParser {

  private const val MAX_TABLE_BYTES = 8 * 1024 * 1024

  interface Source {
    val size: Long
    fun read(offset: Long, length: Int): ByteArray?
  }

  private class ByteArraySource(private val data: ByteArray) : Source {
    override val size get() = data.size.toLong()
    override fun read(offset: Long, length: Int): ByteArray? {
      if (offset < 0 || length < 0 || offset + length > data.size) return null
      return data.copyOfRange(offset.toInt(), (offset + length).toInt())
    }
  }

  private class FileSource(private val raf: RandomAccessFile) : Source {
    override val size get() = raf.length()
    override fun read(offset: Long, length: Int): ByteArray? {
      if (offset < 0 || length < 0 || offset + length > raf.length()) return null
      val buf = ByteArray(length)
      raf.seek(offset)
      raf.readFully(buf)
      return buf
    }
  }

  fun parse(file: File): FontMeta? = try {
    RandomAccessFile(file, "r").use { parse(FileSource(it)) }
  } catch (_: Exception) {
    null
  }

  fun parse(bytes: ByteArray): FontMeta? = parse(ByteArraySource(bytes))

  fun parse(src: Source): FontMeta? = try {
    parseInternal(src)
  } catch (_: Exception) {
    null
  }

  private fun parseInternal(src: Source): FontMeta? {
    val head = src.read(0, 12) ?: return null
    var fontOffset = 0L
    if (String(head, 0, 4, Charsets.ISO_8859_1) == "ttcf") {
      // TrueType Collection: use the first font in it.
      val off = src.read(12, 4) ?: return null
      fontOffset = ByteBuffer.wrap(off).int.toLong() and 0xFFFFFFFFL
    }
    val dirHead = src.read(fontOffset, 12) ?: return null
    val numTables = ByteBuffer.wrap(dirHead).getShort(4).toInt() and 0xFFFF
    val dir = src.read(fontOffset + 12, numTables * 16) ?: return null
    val buf = ByteBuffer.wrap(dir)

    var cmapOff = -1L; var cmapLen = 0
    var nameOff = -1L; var nameLen = 0
    for (i in 0 until numTables) {
      val base = i * 16
      val tag = String(dir, base, 4, Charsets.ISO_8859_1)
      val off = buf.getInt(base + 8).toLong() and 0xFFFFFFFFL
      val len = buf.getInt(base + 12).toLong() and 0xFFFFFFFFL
      if (len > MAX_TABLE_BYTES) continue
      if (tag == "cmap") { cmapOff = off; cmapLen = len.toInt() }
      if (tag == "name") { nameOff = off; nameLen = len.toInt() }
    }
    if (cmapOff < 0) return null

    val cmap = src.read(cmapOff, cmapLen) ?: return null
    val lookups = readCmap(cmap)
    if (lookups.isEmpty()) return null

    val scripts = FontScript.entries.filter { script ->
      val cps = script.probe.codePoints().toArray()
      val hit = cps.count { cp -> lookups.any { it(cp) } }
      hit.toDouble() / cps.size >= FontScript.COVERAGE_THRESHOLD
    }.toSet()

    val name = if (nameOff >= 0) src.read(nameOff, nameLen)?.let { readName(it) } else null
    return FontMeta(name, scripts)
  }

  /** Returns one "has glyph for code point" predicate per usable cmap subtable. */
  private fun readCmap(cmap: ByteArray): List<(Int) -> Boolean> {
    val b = ByteBuffer.wrap(cmap)
    val count = b.getShort(2).toInt() and 0xFFFF
    val out = ArrayList<(Int) -> Boolean>()
    for (i in 0 until count) {
      val rec = 4 + i * 8
      if (rec + 8 > cmap.size) break
      val offset = (b.getInt(rec + 4).toLong() and 0xFFFFFFFFL).toInt()
      if (offset < 0 || offset + 4 > cmap.size) continue
      when (b.getShort(offset).toInt() and 0xFFFF) {
        4 -> format4(b, offset)?.let { out.add(it) }
        6 -> format6(b, offset)?.let { out.add(it) }
        12, 13 -> format12(b, offset)?.let { out.add(it) }
      }
    }
    return out
  }

  private fun u16(b: ByteBuffer, pos: Int) = b.getShort(pos).toInt() and 0xFFFF
  private fun u32(b: ByteBuffer, pos: Int) = b.getInt(pos).toLong() and 0xFFFFFFFFL

  private fun format4(b: ByteBuffer, o: Int): ((Int) -> Boolean)? {
    val segCount = u16(b, o + 6) / 2
    val endBase = o + 14
    val startBase = endBase + segCount * 2 + 2
    val deltaBase = startBase + segCount * 2
    val rangeBase = deltaBase + segCount * 2
    if (rangeBase + segCount * 2 > b.limit()) return null
    return fun(cp: Int): Boolean {
      if (cp > 0xFFFF) return false
      for (s in 0 until segCount) {
        if (u16(b, endBase + s * 2) < cp) continue
        if (u16(b, startBase + s * 2) > cp) return false
        val delta = u16(b, deltaBase + s * 2)
        val rangeOffsetPos = rangeBase + s * 2
        val rangeOffset = u16(b, rangeOffsetPos)
        if (rangeOffset == 0) return ((cp + delta) and 0xFFFF) != 0
        val glyphPos = rangeOffsetPos + rangeOffset + 2 * (cp - u16(b, startBase + s * 2))
        if (glyphPos + 2 > b.limit()) return false
        val glyph = u16(b, glyphPos)
        return glyph != 0 && ((glyph + delta) and 0xFFFF) != 0
      }
      return false
    }
  }

  private fun format6(b: ByteBuffer, o: Int): ((Int) -> Boolean)? {
    val first = u16(b, o + 6)
    val n = u16(b, o + 8)
    if (o + 10 + n * 2 > b.limit()) return null
    return { cp -> cp >= first && cp < first + n && u16(b, o + 10 + (cp - first) * 2) != 0 }
  }

  private fun format12(b: ByteBuffer, o: Int): ((Int) -> Boolean)? {
    val groups = u32(b, o + 12).toInt()
    if (groups < 0 || o + 16 + groups.toLong() * 12 > b.limit()) return null
    return fun(cp: Int): Boolean {
      var lo = 0
      var hi = groups - 1
      while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val g = o + 16 + mid * 12
        val start = u32(b, g)
        val end = u32(b, g + 4)
        when {
          cp < start -> hi = mid - 1
          cp > end -> lo = mid + 1
          else -> return true
        }
      }
      return false
    }
  }

  /** Full font name (nameID 4, else family nameID 1). Prefers English, then any Unicode / Windows name. */
  private fun readName(table: ByteArray): String? {
    val b = ByteBuffer.wrap(table)
    if (table.size < 6) return null
    val count = u16(b, 2)
    val strBase = u16(b, 4)
    var best: String? = null
    var bestScore = Int.MAX_VALUE
    for (i in 0 until count) {
      val r = 6 + i * 12
      if (r + 12 > table.size) break
      val platform = u16(b, r)
      val lang = u16(b, r + 4)
      val nameId = u16(b, r + 6)
      val len = u16(b, r + 8)
      val off = u16(b, r + 10)
      if (nameId != 4 && nameId != 1) continue
      val start = strBase + off
      if (start + len > table.size) continue
      val charset: Charset = when (platform) {
        0, 3 -> Charsets.UTF_16BE
        1 -> Charsets.ISO_8859_1
        else -> continue
      }
      val english = (platform == 3 && lang == 0x0409) || (platform == 1 && lang == 0)
      val score = (if (nameId == 4) 0 else 10) + (if (english) 0 else 5) + (if (platform == 3) 0 else 1)
      if (score < bestScore) {
        val s = String(table, start, len, charset).trim()
        if (s.isNotEmpty()) { best = s; bestScore = score }
      }
    }
    return best
  }
}
