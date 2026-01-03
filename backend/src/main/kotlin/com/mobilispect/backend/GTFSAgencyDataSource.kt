package com.mobilispect.backend

import com.mobilispect.backend.util.readTextAndNormalize
import java.io.IOException
import java.nio.file.Path
import kotlin.time.measureTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.decodeFromString
import org.springframework.stereotype.Component

/** An [AgencyDataSource] that uses a GTFS feed as a source. */
@OptIn(ExperimentalSerializationApi::class)
@Component
internal class GTFSAgencyDataSource(private val agencyIDDataSource: AgencyIDDataSource) :
  AgencyDataSource {
  private val logger = org.slf4j.LoggerFactory.getLogger(GTFSAgencyDataSource::class.java)

  override fun agencies(root: Path, version: String, feedID: String): Result<Collection<Agency>> {
    logger.debug("Looking up agency IDs for feedID=$feedID")
    val agencyIDRes = agencyIDDataSource.agencyIDs(feedID)
    if (agencyIDRes.isFailure) {
      return Result.failure(Exception("Missing agency IDs"))
    }
    val agencyIDs = agencyIDRes.getOrNull()!!

    return try {
      val input = root.resolve("agency.txt").toFile().readTextAndNormalize()
      val csv = Csv {
        hasHeaderRecord = true
        ignoreUnknownColumns = true
      }
      val agencies: List<Agency>
      val decodingTime = measureTime {
        agencies =
          csv.decodeFromString<Collection<GTFSAgency>>(input).mapNotNull { agency ->
            val id = agencyIDs[agency.agency_id] ?: return@mapNotNull null
            Agency(
              uid = id,
              localID = agency.agency_id,
              name = agency.agency_name,
              versions = listOf(version),
            )
          }
      }
      logger.debug("Decoded {} agencies in {}", agencies.size, decodingTime)

      Result.success(agencies)
    } catch (e: IOException) {
      Result.failure(e)
    } catch (e: SerializationException) {
      Result.failure(e)
    }
  }
}
