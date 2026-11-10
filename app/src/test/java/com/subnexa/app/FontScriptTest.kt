package com.subnexa.app

import com.subnexa.app.domain.font.FontManager
import com.subnexa.app.domain.font.FontParser
import com.subnexa.app.domain.font.FontScript
import com.subnexa.app.domain.font.SubtitleLanguageInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FontScriptTest {

  @Test
  fun detectsJapaneseWhenKanaPresent() {
    val info = SubtitleLanguageInfo.from(listOf("こんにちは、世界", "今日はいい天気ですね"))
    assertEquals(FontScript.JAPANESE, info.primary)
    assertEquals(setOf(FontScript.JAPANESE), info.requiredScripts)
    assertEquals("こんにちは、世界", info.sampleLine)
  }

  @Test
  fun hanOnlyTextIsChinese() {
    val info = SubtitleLanguageInfo.from(listOf("你好，我们走吧"))
    assertEquals(FontScript.CHINESE, info.primary)
  }

  @Test
  fun detectsBengaliKoreanAndLatin() {
    assertEquals(FontScript.BENGALI, SubtitleLanguageInfo.from(listOf("আমি ভালো আছি")).primary)
    assertEquals(FontScript.KOREAN, SubtitleLanguageInfo.from(listOf("안녕하세요")).primary)
    val latin = SubtitleLanguageInfo.from(listOf("Hello <i>world</i>"))
    assertEquals(FontScript.LATIN, latin.primary)
    assertTrue(latin.requiredScripts.isEmpty())
  }

  @Test
  fun strayLatinWordDoesNotRequireLatin() {
    val info = SubtitleLanguageInfo.from(listOf("これはOKです。日本語の字幕"))
    assertEquals(setOf(FontScript.JAPANESE), info.requiredScripts)
  }

  @Test
  fun builtInFontsDoNotSupportJapaneseButSystemDoes() {
    val required = setOf(FontScript.JAPANESE)
    FontManager.BUILT_IN_FONTS.filter { it.id != "system" }.forEach {
      assertFalse("${it.id} must not claim Japanese", it.supports(required))
    }
    assertTrue(FontManager.BUILT_IN_FONTS.first { it.id == "system" }.supports(required))
  }

  @Test
  fun sampleTextUsesFontsOwnScripts() {
    val bengali = FontManager.BUILT_IN_FONTS.first { it.id == "hind_siliguri" }
    assertEquals("বাংলা • English", bengali.sampleText())
    val jp = bengali.copy(scripts = setOf(FontScript.JAPANESE, FontScript.LATIN))
    assertEquals("日本語 • English", jp.sampleText())
  }

  /** The hard-coded built-in script map must match what the real font files contain. */
  @Test
  fun builtInScriptMapMatchesBundledFontFiles() {
    val dir = listOf(File("src/main/res/font"), File("app/src/main/res/font")).firstOrNull { it.isDirectory }
    if (dir == null) return // not runnable from this working directory
    val files = mapOf(
      "hind_siliguri" to "hind_siliguri_regular.ttf",
      "noto_sans_bengali" to "noto_sans_bengali_regular.ttf",
      "noto_serif_bengali" to "noto_serif_bengali_regular.ttf",
      "noto_sans_devanagari" to "noto_sans_devanagari_regular.ttf",
      "inter" to "inter_regular.ttf"
    )
    for ((id, fileName) in files) {
      val meta = FontParser.parse(File(dir, fileName))
      assertNotNull("cannot parse $fileName", meta)
      val expected = FontManager.BUILT_IN_FONTS.first { it.id == id }.scripts
      assertEquals("scripts for $id", expected, meta!!.scripts)
    }
  }
}
