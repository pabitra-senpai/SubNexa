package com.subnexa.app

import com.subnexa.app.domain.font.FontManager
import com.subnexa.app.domain.font.SubtitleFontItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontManagerTest {

  @Test
  fun testBuiltInFontsCountAndDefault() {
    val builtIn = FontManager.BUILT_IN_FONTS
    assertEquals(6, builtIn.size)
    assertEquals("hind_siliguri", FontManager.DEFAULT_FONT_ID)

    val ids = builtIn.map { it.id }
    assertTrue(ids.contains("hind_siliguri"))
    assertTrue(ids.contains("noto_sans_bengali"))
    assertTrue(ids.contains("noto_serif_bengali"))
    assertTrue(ids.contains("noto_sans_devanagari"))
    assertTrue(ids.contains("inter"))
    assertTrue(ids.contains("system"))

    // Built-in fonts should not be custom
    builtIn.forEach {
      assertFalse(it.isCustom)
    }
  }

  @Test
  fun testFontListWithManyCustomFonts() {
    val builtIn = FontManager.BUILT_IN_FONTS
    val customList = mutableListOf<SubtitleFontItem>()
    for (i in 1..12) {
      customList.add(SubtitleFontItem("custom:font_$i.ttf", "Font $i", isCustom = true))
    }
    val combined = builtIn + customList
    assertEquals(18, combined.size)
    assertEquals(6, combined.count { !it.isCustom })
    assertEquals(12, combined.count { it.isCustom })
  }
}
