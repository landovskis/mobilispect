package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.domain.model.ids.VariantHash
import java.security.MessageDigest
import org.springframework.stereotype.Component

/** Utility for generating SHA-256 hashes from ordered stop patterns. */
@Component
class VariantHashGenerator {
  fun fromStops(stopIds: List<String>): VariantHash {
    require(stopIds.size >= 2) { "Variant hash requires at least two stops" }
    val concatenated = stopIds.joinToString(separator = "|")
    val digest = MessageDigest.getInstance("SHA-256").digest(concatenated.toByteArray())
    val hex = digest.joinToString("") { "%02x".format(it) }
    return VariantHash(hex)
  }
}
