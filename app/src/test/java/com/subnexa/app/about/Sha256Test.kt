package com.subnexa.app.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256Test {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val abcDigest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

  @Test
  fun normalizeSha256_acceptsValidDigest() {
    assertEquals(abcDigest, normalizeSha256(abcDigest))
  }

  @Test
  fun normalizeSha256_trimsAndLowercases() {
    assertEquals(abcDigest, normalizeSha256("  " + abcDigest.uppercase() + "\n"))
  }

  @Test
  fun normalizeSha256_rejectsMissingOrBlank() {
    assertNull(normalizeSha256(null))
    assertNull(normalizeSha256(""))
    assertNull(normalizeSha256("   "))
  }

  @Test
  fun normalizeSha256_rejectsWrongLengthOrNonHex() {
    assertNull(normalizeSha256(abcDigest.dropLast(1)))
    assertNull(normalizeSha256(abcDigest + "0"))
    assertNull(normalizeSha256("z" + abcDigest.drop(1)))
    assertNull(normalizeSha256("null"))
  }

  @Test
  fun computeSha256_matchesKnownDigest() {
    val file = tempFolder.newFile("abc.bin")
    file.writeBytes("abc".toByteArray(Charsets.US_ASCII))
    assertEquals(abcDigest, computeSha256(file))
  }

  @Test
  fun computeSha256_emptyFile() {
    val file = tempFolder.newFile("empty.bin")
    assertEquals(
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      computeSha256(file)
    )
  }
}
