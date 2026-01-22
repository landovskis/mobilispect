package com.mobilispect.backend.agency.batch.import

import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component

@Component
@StepScope
class AgencyImportTasklet(private val agencyImportService: AgencyImportService) : Tasklet {
  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val stepExecution = chunkContext.stepContext.stepExecution
    val feedOnestopId =
      stepExecution.jobExecution.jobParameters.getString("feedOnestopId")
        ?: throw IllegalStateException("feedOnestopId job parameter is required")

    agencyImportService.execute(stepExecution, feedOnestopId)
    return RepeatStatus.FINISHED
  }
}
