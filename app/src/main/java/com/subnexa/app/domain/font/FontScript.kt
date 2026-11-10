package com.subnexa.app.domain.font

/**
 * Writing systems SubNexa knows about. Each script has a human label, a short
 * sample word (rendered in the font itself in the font picker) and a set of
 * probe characters used to decide whether a font file really contains glyphs
 * for that script.
 */
enum class FontScript(
  val label: String,
  val sample: String,
  val probe: String
) {
  BENGALI("Bengali", "বাংলা", "অআইউএওকখগঘচজটডণতনপবমযরলশসহ"),
  DEVANAGARI("Devanagari", "हिंदी", "अआइउएओकखगघचजटडणतनपबमयरलवशसह"),
  JAPANESE("Japanese", "日本語", "あいうえおかきくけこアイウエオカキクケコ日本語人大"),
  KOREAN("Korean", "한국어", "가나다라마바사아자차한국어"),
  CHINESE("Chinese", "中文", "中文字汉语们这个来说对为"),
  ARABIC("Arabic", "العربية", "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"),
  THAI("Thai", "ภาษาไทย", "กขคงจฉชซดตถทนบปผพมยรลวสหอ"),
  CYRILLIC("Cyrillic", "Русский", "АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩабвгдежзиклмнопр"),
  LATIN("Latin", "English", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");

  /** Fraction of probe characters that must exist for the font to count as supporting the script. */
  companion object {
    const val COVERAGE_THRESHOLD = 0.8

    /** Script of a single code point, or null for digits, punctuation, spaces, emoji, etc. */
    private fun classify(cp: Int): ScriptClass? = when (cp) {
      in 0x0980..0x09FF -> ScriptClass.BENGALI
      in 0x0900..0x097F -> ScriptClass.DEVANAGARI
      in 0x3040..0x30FF, in 0x31F0..0x31FF, in 0xFF66..0xFF9F -> ScriptClass.KANA
      in 0x4E00..0x9FFF, in 0x3400..0x4DBF, in 0x20000..0x2A6DF -> ScriptClass.HAN
      in 0xAC00..0xD7AF, in 0x1100..0x11FF, in 0x3130..0x318F -> ScriptClass.HANGUL
      in 0x0600..0x06FF, in 0x0750..0x077F, in 0xFB50..0xFDFF, in 0xFE70..0xFEFF -> ScriptClass.ARABIC
      in 0x0E00..0x0E7F -> ScriptClass.THAI
      in 0x0400..0x04FF, in 0x0500..0x052F -> ScriptClass.CYRILLIC
      in 0x0041..0x005A, in 0x0061..0x007A, in 0x00C0..0x024F -> ScriptClass.LATIN
      else -> null
    }

    private enum class ScriptClass { BENGALI, DEVANAGARI, KANA, HAN, HANGUL, ARABIC, THAI, CYRILLIC, LATIN }

    /**
     * Languages found in [texts], most frequent first. Han characters count as Japanese when the
     * text also contains kana, otherwise as Chinese (Japanese subtitles almost always have kana).
     */
    fun detect(texts: List<String>): List<FontScript> {
      val counts = IntArray(ScriptClass.entries.size)
      for (text in texts) {
        var i = 0
        while (i < text.length) {
          val cp = text.codePointAt(i)
          i += Character.charCount(cp)
          classify(cp)?.let { counts[it.ordinal]++ }
        }
      }
      fun c(s: ScriptClass) = counts[s.ordinal]
      val han = c(ScriptClass.HAN)
      val kana = c(ScriptClass.KANA)
      val japanese = kana + if (kana > 0) han else 0
      val chinese = if (kana == 0) han else 0
      val result = listOf(
        BENGALI to c(ScriptClass.BENGALI),
        DEVANAGARI to c(ScriptClass.DEVANAGARI),
        JAPANESE to japanese,
        CHINESE to chinese,
        KOREAN to c(ScriptClass.HANGUL),
        ARABIC to c(ScriptClass.ARABIC),
        THAI to c(ScriptClass.THAI),
        CYRILLIC to c(ScriptClass.CYRILLIC),
        LATIN to c(ScriptClass.LATIN)
      )
      val total = result.sumOf { it.second }
      if (total == 0) return emptyList()
      // Ignore scripts that are only a stray mention (< 5 % of letters).
      return result
        .filter { it.second > 0 && it.second * 20 >= total }
        .sortedByDescending { it.second }
        .map { it.first }
    }
  }
}

/** What the loaded subtitle file is written in, plus one representative line to preview fonts with. */
data class SubtitleLanguageInfo(
  val scripts: List<FontScript>,
  val sampleLine: String?
) {
  /** Scripts a font must cover. Latin is ignored when another script is present (any font handles a stray word). */
  val requiredScripts: Set<FontScript>
    get() {
      val nonLatin = scripts.filter { it != FontScript.LATIN }
      return if (nonLatin.isNotEmpty()) nonLatin.toSet() else emptySet()
    }

  val primary: FontScript? get() = scripts.firstOrNull { it != FontScript.LATIN } ?: scripts.firstOrNull()

  companion object {
    val NONE = SubtitleLanguageInfo(emptyList(), null)

    fun from(texts: List<String>, maxEntries: Int = 80): SubtitleLanguageInfo {
      val cleaned = texts.asSequence()
        .map { it.replace(Regex("<[^>]*>"), "").trim() }
        .filter { it.isNotEmpty() }
        .take(maxEntries)
        .toList()
      if (cleaned.isEmpty()) return NONE
      val scripts = FontScript.detect(cleaned)
      val primary = scripts.firstOrNull { it != FontScript.LATIN } ?: scripts.firstOrNull()
      val line = cleaned
        .firstOrNull { primary != null && FontScript.detect(listOf(it)).firstOrNull() == primary }
        ?: cleaned.first()
      val oneLine = line.lineSequence().first().take(48)
      return SubtitleLanguageInfo(scripts, oneLine)
    }
  }
}
