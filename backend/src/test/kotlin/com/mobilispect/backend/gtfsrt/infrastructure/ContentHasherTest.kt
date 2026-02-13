package com.mobilispect.backend.gtfsrt.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ContentHasherTest {

  @Test
  fun `hash returns consistent SHA-256 hex string for same input`() {
    val content = "test content".toByteArray()

    val hash1 = ContentHasher.hash(content)
    val hash2 = ContentHasher.hash(content)

    assertEquals(hash1, hash2)
    assertEquals(64, hash1.length) // SHA-256 produces 32 bytes = 64 hex chars
  }

  @Test
  fun `hash returns different values for different inputs`() {
    val content1 = "content 1".toByteArray()
    val content2 = "content 2".toByteArray()

    val hash1 = ContentHasher.hash(content1)
    val hash2 = ContentHasher.hash(content2)

    assertNotEquals(hash1, hash2)
  }

  @Test
  fun `hash returns lowercase hex string`() {
    val content = "ABC123".toByteArray()

    val hash = ContentHasher.hash(content)

    assertEquals(hash, hash.lowercase())
  }

  @Test
  fun `hash handles empty byte array`() {
    val empty = ByteArray(0)

    val hash = ContentHasher.hash(empty)

    // SHA-256 of empty input is a well-known constant
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
  }

  @Test
  fun `hash is thread-safe`() {
    val content = "concurrent test".toByteArray()
    val results = mutableListOf<String>()

    // Run multiple hashes concurrently
    val threads =
      (1..10).map {
        Thread {
          repeat(100) { results.add(ContentHasher.hash(content)) }
        }
      }

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    // All results should be identical
    assertEquals(1, results.distinct().size)
  }
}
