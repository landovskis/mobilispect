package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.agency.batch.import.AgencyImportTasklet
import com.mobilispect.backend.feed.api.GTFSData
import com.mobilispect.backend.route.batch.frequency.FrequencyImportTasklet
import com.mobilispect.backend.route.batch.import.RouteImportTasklet
import com.mobilispect.backend.route.batch.spacing.StopSpacingImportTasklet
import com.mobilispect.backend.route.batch.variant.RouteVariantImportTasklet
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class FeedImportJobConfig(
  private val jobRepository: JobRepository,
  private val transactionManager: PlatformTransactionManager,
  private val feedImportReader: GTFSFeedReader,
  private val feedImportWriter: FeedImportWriter,
  private val agencyImportTasklet: AgencyImportTasklet,
  private val routeImportTasklet: RouteImportTasklet,
  private val routeVariantImportTasklet: RouteVariantImportTasklet,
  private val stopSpacingImportTasklet: StopSpacingImportTasklet,
  private val frequencyImportTasklet: FrequencyImportTasklet,
  private val stepExecutionListener: FeedImportStepExecutionListener,
  private val jobExecutionListener: FeedImportJobExecutionListener,
) {

  @Bean
  fun feedImportJob(): Job =
    JobBuilder("feedImportJob", jobRepository)
      .preventRestart()
      .start(feedImportStep())
      .next(agencyProcessingStep())
      .next(routeVariantProcessingStep())
      .next(stopSpacingProcessingStep())
      .next(frequencyProcessingStep())
      .listener(jobExecutionListener)
      .build()

  @Bean
  fun feedImportStep(): Step =
    StepBuilder("feedImportStep", jobRepository)
      .chunk<GTFSData, GTFSData>(1)
      .reader(feedImportReader)
      .writer(feedImportWriter)
      .listener(stepExecutionListener)
      .transactionManager(transactionManager)
      .build()

  @Bean
  fun agencyProcessingStep(): Step =
    StepBuilder("agencyProcessingStep", jobRepository)
      .tasklet(agencyImportTasklet, transactionManager)
      .listener(stepExecutionListener)
      .build()

  @Bean
  fun routeProcessingStep(): Step =
    StepBuilder("routeProcessingStep", jobRepository)
      .tasklet(routeImportTasklet, transactionManager)
      .listener(stepExecutionListener)
      .build()

  @Bean
  fun routeVariantProcessingStep(): Step =
    StepBuilder("routeVariantProcessingStep", jobRepository)
      .tasklet(routeVariantImportTasklet, transactionManager)
      .listener(stepExecutionListener)
      .build()

  @Bean
  fun stopSpacingProcessingStep(): Step =
    StepBuilder("stopSpacingProcessingStep", jobRepository)
      .tasklet(stopSpacingImportTasklet, transactionManager)
      .listener(stepExecutionListener)
      .build()

  @Bean
  fun frequencyProcessingStep(): Step =
    StepBuilder("frequencyProcessingStep", jobRepository)
      .tasklet(frequencyImportTasklet, transactionManager)
      .listener(stepExecutionListener)
      .build()
}
