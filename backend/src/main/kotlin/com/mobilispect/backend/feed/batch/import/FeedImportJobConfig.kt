package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.feed.api.GTFSData
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
  private val stepExecutionListener: FeedImportStepExecutionListener,
  private val jobExecutionListener: FeedImportJobExecutionListener,
) {

  @Bean
  fun feedImportJob(): Job =
    JobBuilder("feedImportJob", jobRepository)
      .preventRestart()
      .start(feedImportStep())
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
}
