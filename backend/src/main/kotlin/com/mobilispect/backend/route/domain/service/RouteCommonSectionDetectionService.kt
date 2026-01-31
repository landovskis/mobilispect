package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.domain.model.RouteVariant
import org.springframework.stereotype.Service

/**
 * Service for detecting the longest continuous section of stops shared by ALL variants
 * in a given direction.
 *
 * Unlike the general CommonSectionDetectionService which finds pairwise overlaps,
 * this service finds the single longest sequence of consecutive stops that appears
 * in ALL provided variants.
 */
interface RouteCommonSectionDetectionService {
  /**
   * Detects the longest continuous section of stops shared by all variants.
   *
   * @param variants List of route variants to analyze (should all be from same route/direction)
   * @return CommonSectionResult containing the stop IDs and names, or null if no common section found
   */
  fun detectCommonSection(variants: List<RouteVariant>): CommonSectionResult?
}

/**
 * Result of common section detection containing stop information.
 *
 * @property stopIds Pipe-separated list of stop IDs (e.g., "stop1|stop2|stop3")
 * @property stopNames List of stop names in order
 */
data class CommonSectionResult(
  val stopIds: String,
  val stopNames: List<String>,
)

@Service
class RouteCommonSectionDetectionServiceImpl : RouteCommonSectionDetectionService {

  override fun detectCommonSection(variants: List<RouteVariant>): CommonSectionResult? {
    // Need at least 2 variants to find a common section
    if (variants.size < 2) return null

    // Parse all stop patterns into lists
    val stopSequences = variants.map { it.stopPattern.split("|") }
    val stopNameSequences = variants.map { it.stopNamePattern?.split("|") ?: emptyList() }

    // Find the longest continuous sequence that appears in all variants
    val longestCommon = findLongestCommonContinuousSequence(stopSequences)
    if (longestCommon.isEmpty()) return null

    // Get corresponding stop names from the first variant (they should be consistent)
    val stopNames = findStopNamesForSequence(longestCommon, stopSequences[0], stopNameSequences[0])

    return CommonSectionResult(
      stopIds = longestCommon.joinToString("|"),
      stopNames = stopNames,
    )
  }

  /**
   * Finds the longest continuous sequence of stops that appears in ALL stop sequences.
   */
  private fun findLongestCommonContinuousSequence(sequences: List<List<String>>): List<String> {
    if (sequences.isEmpty()) return emptyList()

    // Start with the first sequence and find all possible continuous subsequences
    val firstSequence = sequences[0]
    var longestCommon = emptyList<String>()

    // Try all possible continuous subsequences from the first sequence
    // Start with longest possible subsequences
    for (length in firstSequence.size downTo 1) {
      for (start in 0..firstSequence.size - length) {
        val subsequence = firstSequence.subList(start, start + length)

        // Check if this subsequence appears in all other sequences
        if (sequences.all { containsContinuousSequence(it, subsequence) }) {
          // Found a longer common sequence
          if (subsequence.size > longestCommon.size) {
            longestCommon = subsequence
            // Since we're searching from longest to shortest, we can break once we find one
            break
          }
        }
      }
      // If we found a common sequence at this length, no need to check shorter lengths
      if (longestCommon.isNotEmpty()) break
    }

    return longestCommon
  }

  /**
   * Checks if a sequence contains a continuous subsequence.
   */
  private fun containsContinuousSequence(sequence: List<String>, subsequence: List<String>): Boolean {
    if (subsequence.size > sequence.size) return false
    if (subsequence.isEmpty()) return true

    // Use a sliding window to check if subsequence appears continuously
    for (i in 0..sequence.size - subsequence.size) {
      if (sequence.subList(i, i + subsequence.size) == subsequence) {
        return true
      }
    }
    return false
  }

  /**
   * Finds the stop names corresponding to a sequence of stop IDs.
   */
  private fun findStopNamesForSequence(
    stopIds: List<String>,
    fullStopSequence: List<String>,
    fullStopNameSequence: List<String>
  ): List<String> {
    if (fullStopNameSequence.isEmpty()) {
      return stopIds // Fallback to stop IDs if names not available
    }

    // Find where this sequence appears in the full sequence
    for (i in 0..fullStopSequence.size - stopIds.size) {
      if (fullStopSequence.subList(i, i + stopIds.size) == stopIds) {
        // Found the position, extract corresponding names
        return fullStopNameSequence.subList(i, i + stopIds.size)
      }
    }

    return stopIds // Fallback if not found
  }
}
