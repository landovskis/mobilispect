package com.mobilispect.backend.agency.batch.import

import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.agency.AgencyId
import com.mobilispect.backend.feed.api.GTFSAgency
import com.mobilispect.backend.feed.domain.model.ids.FeedId
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@StepScope
class AgencyProcessor : ItemProcessor<GTFSAgency, Agency> {

  @Value("#{jobParameters['feedOnestopId']}") lateinit var feedId: String

  override fun process(item: GTFSAgency): Agency {
    return Agency(
      agencyId = AgencyId(FeedId(feedId), item.agencyId),
      feedId = FeedId(feedId),
      name = item.name,
    )
  }
}
