package com.mobilispect.backend.agency.batch.import

import com.mobilispect.backend.AgencyResultItem
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.domain.model.ids.AgencyId
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import com.mobilispect.backend.feed.api.ParsedAgency
import com.mobilispect.backend.schedule.transit_land.TransitLandAPI
import com.mobilispect.backend.schedule.transit_land.api.TransitLandCredentialsRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@StepScope
class AgencyProcessor(
    private val transitLandAPI: TransitLandAPI,
    private val credentialsRepository: TransitLandCredentialsRepository
) : ItemProcessor<ParsedAgency, Agency> {

    private val logger = LoggerFactory.getLogger(AgencyProcessor::class.java)

    @Value("#{jobParameters['feedOnestopId']}")
    lateinit var feedOnestopId: String

    private var agencyIdLookup: Map<String, String>? = null

    override fun process(item: ParsedAgency): Agency {
        val onestopId = resolveOnestopId(item.agencyId)
        return Agency(
            agencyOnestopId = AgencyId(onestopId),
            feedId = FeedId(feedOnestopId),
            gtfsAgencyId = item.agencyId,
            name = item.name,
            website = item.url,
            phone = item.phone
        )
    }

    private fun resolveOnestopId(gtfsAgencyId: String): String {
        val lookup = agencyIdLookup ?: loadAgencyLookup().also { agencyIdLookup = it }
        return lookup[gtfsAgencyId]
            ?: throw IllegalStateException(
                "No Transitland agency onestop ID for GTFS agency '$gtfsAgencyId' (feed=$feedOnestopId)"
            )
    }

    private fun loadAgencyLookup(): Map<String, String> {
        val apiKey = credentialsRepository.get()
            ?: throw IllegalStateException("Missing Transitland API key")
        val feedId = feedOnestopId.ifBlank {
            throw IllegalArgumentException("feedOnestopId job parameter is required")
        }
        val result = transitLandAPI.agencies(apiKey = apiKey, feedID = feedId)
            .getOrElse { throw IllegalStateException("Transitland agency lookup failed for feed=$feedId", it) }

        val lookup = result.agencies
            .filter { it.agencyID != null }
            .associate { item: AgencyResultItem -> item.agencyID!! to item.id }

        logger.info("Loaded {} agency onestop IDs from Transitland for feed {}", lookup.size, feedId)
        return lookup
    }
}
