package com.mobilispect.backend.route.batch.variant

import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component

@Component
@StepScope
class RouteVariantImportTasklet(private val routeVariantImportService: RouteVariantImportService) :
  Tasklet {
  override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
    val stepExecution = chunkContext.stepContext.stepExecution
    routeVariantImportService.execute(stepExecution)
    return RepeatStatus.FINISHED
  }
}
