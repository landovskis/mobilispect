package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.feed.batch.import.FeedImportTasklet
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.step.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class FeedImportJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val feedImportTasklet: FeedImportTasklet
) {

    @Bean
    fun feedImportJob(): Job = JobBuilder("feedImportJob", jobRepository)
        .start(feedImportStep())
        .build()

    @Bean
    fun feedImportStep(): Step = StepBuilder("feedImportStep", jobRepository)
        .tasklet(feedImportTasklet, transactionManager)
        .build()
}
