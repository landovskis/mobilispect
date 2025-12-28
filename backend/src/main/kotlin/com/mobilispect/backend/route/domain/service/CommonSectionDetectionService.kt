package com.mobilispect.backend.route.domain.service

import com.mobilispect.backend.route.domain.model.CommonSection
import com.mobilispect.backend.route.domain.model.CommonSectionVariant
import com.mobilispect.backend.route.domain.model.RouteVariant
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * Service for detecting common sections where multiple routes/variants overlap.
 *
 * A common section is a sequence of 3 or more consecutive stops shared between multiple route
 * variants.
 */
interface CommonSectionDetectionService {
  fun detectCommonSections(
    variants: List<RouteVariant>
  ): Pair<List<CommonSection>, List<CommonSectionVariant>>
}

@Service
class CommonSectionDetectionServiceImpl : CommonSectionDetectionService {
  override fun detectCommonSections(
    variants: List<RouteVariant>
  ): Pair<List<CommonSection>, List<CommonSectionVariant>> {
    if (variants.size < 2) return emptyList<CommonSection>() to emptyList()

    val sections = mutableListOf<CommonSection>()
    val sectionVariants = mutableListOf<CommonSectionVariant>()

    variants.forEachIndexed { index, a ->
      val stopsA = a.stopPattern.split("|")
      variants.drop(index + 1).forEach { b ->
        val stopsB = b.stopPattern.split("|")
        val common = longestCommonSubsequence(stopsA, stopsB).filter { it.size >= 3 }
        common.forEach { seq ->
          val startA = stopsA.indexOf(seq.first())
          val endA = stopsA.indexOf(seq.last())
          val startB = stopsB.indexOf(seq.first())
          val endB = stopsB.indexOf(seq.last())
          if (startA == -1 || endA == -1 || startB == -1 || endB == -1) return@forEach

          val section =
            CommonSection(
              id = UUID.randomUUID(),
              stopPattern = seq.joinToString("|"),
              stopCount = seq.size,
              firstStopId = seq.first(),
              lastStopId = seq.last(),
              createdAt = Instant.now(),
              updatedAt = Instant.now(),
            )
          sections += section
          sectionVariants +=
            CommonSectionVariant(
              id = UUID.randomUUID(),
              commonSection = section,
              variantId = a.id.value,
              startSequence = startA,
              endSequence = endA,
            )
          sectionVariants +=
            CommonSectionVariant(
              id = UUID.randomUUID(),
              commonSection = section,
              variantId = b.id.value,
              startSequence = startB,
              endSequence = endB,
            )
        }
      }
    }

    return sections.distinctBy { it.stopPattern } to sectionVariants
  }

  private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<List<String>> {
    val dp = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
      for (j in b.indices.reversed()) {
        dp[i][j] = if (a[i] == b[j]) 1 + dp[i + 1][j + 1] else maxOf(dp[i + 1][j], dp[i][j + 1])
      }
    }

    val results = mutableListOf<List<String>>()
    fun backtrack(i: Int, j: Int, path: MutableList<String>) {
      var x = i
      var y = j
      while (x < a.size && y < b.size) {
        when {
          a[x] == b[y] -> {
            path += a[x]
            x++
            y++
          }

          dp[x + 1][y] >= dp[x][y + 1] -> x++
          else -> y++
        }
      }
      if (path.isNotEmpty()) {
        results += path.toList()
      }
    }

    backtrack(0, 0, mutableListOf())
    return results
  }
}
