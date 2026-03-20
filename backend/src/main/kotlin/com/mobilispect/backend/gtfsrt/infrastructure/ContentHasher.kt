package com.mobilispect.backend.gtfsrt.infrastructure

import java.security.MessageDigest

/** Utility for computing SHA-256 content hashes for deduplication. */
object ContentHasher {

  private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

  /**
   * Compute SHA-256 hash of content.
   *
   * @param content The byte array to hash
   * @return Lowercase hex string representation of the hash
   */
  fun hash(content: ByteArray): String {
    val md = digest.get()
    md.reset()
    md.update(content)
    return md.digest().toHexString()
  }

  private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
