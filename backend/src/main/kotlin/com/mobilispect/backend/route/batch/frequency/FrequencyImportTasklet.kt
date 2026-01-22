package com.mobilispect.backend.route.batch.frequency

import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component

@Component
@StepScope
class FrequencyImportTasklet(private val frequencyImportService: FrequencyImportService) : Tasklet {
  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val stepExecution = chunkContext.stepContext.stepExecution
    frequencyImportService.execute(stepExecution)
    return RepeatStatus.FINISHED
  }
}
