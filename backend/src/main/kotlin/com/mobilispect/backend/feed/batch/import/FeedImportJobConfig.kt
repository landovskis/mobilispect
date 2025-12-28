package com.mobilispect.backend.feed.batch.import

import com.mobilispect.backend.agency.batch.import.AgencyProcessor
import com.mobilispect.backend.agency.batch.import.AgencyReader
import com.mobilispect.backend.agency.batch.import.AgencyWriter
import com.mobilispect.backend.agency.domain.model.Agency
import com.mobilispect.backend.feed.gtfs.ParsedAgency
import com.mobilispect.backend.feed.gtfs.ParsedGtfsData
import com.mobilispect.backend.feed.batch.import.GTFSFeedReader
import com.mobilispect.backend.route.batch.import.RouteBatch
import com.mobilispect.backend.route.batch.import.RouteInput
import com.mobilispect.backend.route.batch.import.RouteProcessor
import com.mobilispect.backend.route.batch.import.RouteReader
import com.mobilispect.backend.route.batch.import.RouteWriter
import com.mobilispect.backend.route.batch.frequency.FrequencyBatch
import com.mobilispect.backend.route.batch.frequency.FrequencyInput
import com.mobilispect.backend.route.batch.frequency.FrequencyProcessor
import com.mobilispect.backend.route.batch.frequency.FrequencyReader
import com.mobilispect.backend.route.batch.frequency.FrequencyWriter
import com.mobilispect.backend.route.batch.spacing.StopSpacingBatch
import com.mobilispect.backend.route.batch.spacing.StopSpacingInput
import com.mobilispect.backend.route.batch.spacing.StopSpacingProcessor
import com.mobilispect.backend.route.batch.spacing.StopSpacingReader
import com.mobilispect.backend.route.batch.spacing.StopSpacingWriter
import com.mobilispect.backend.route.batch.variant.RouteVariantBatch
import com.mobilispect.backend.route.batch.variant.RouteVariantInput
import com.mobilispect.backend.route.batch.variant.RouteVariantProcessor
import com.mobilispect.backend.route.batch.variant.RouteVariantReader
import com.mobilispect.backend.route.batch.variant.RouteVariantWriter
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
    private val feedImportReader: GTFSFeedReader,
    private val feedImportWriter: FeedImportWriter,
    private val agencyReader: AgencyReader,
    private val agencyProcessor: AgencyProcessor,
    private val agencyWriter: AgencyWriter,
    private val routeReader: RouteReader,
    private val routeProcessor: RouteProcessor,
    private val routeWriter: RouteWriter,
    private val routeVariantReader: RouteVariantReader,
    private val routeVariantProcessor: RouteVariantProcessor,
    private val routeVariantWriter: RouteVariantWriter,
    private val stopSpacingReader: StopSpacingReader,
    private val stopSpacingProcessor: StopSpacingProcessor,
    private val stopSpacingWriter: StopSpacingWriter,
    private val frequencyReader: FrequencyReader,
    private val frequencyProcessor: FrequencyProcessor,
    private val frequencyWriter: FrequencyWriter,
    private val stepExecutionListener: FeedImportStepExecutionListener
) {

    @Bean
    fun feedImportJob(): Job = JobBuilder("feedImportJob", jobRepository)
        .start(feedImportStep())
        .next(agencyProcessingStep())
        .next(routeProcessingStep())
        .next(routeVariantProcessingStep())
        .next(stopSpacingProcessingStep())
        .next(frequencyProcessingStep())
        .build()

    @Bean
    fun feedImportStep(): Step = StepBuilder("feedImportStep", jobRepository)
        .chunk<ParsedGtfsData, ParsedGtfsData>(1, transactionManager)
        .reader(feedImportReader)
        .writer(feedImportWriter)
        .listener(stepExecutionListener)
        .build()

    @Bean
    fun agencyProcessingStep(): Step = StepBuilder("agencyProcessingStep", jobRepository)
        .chunk<ParsedAgency, Agency>(50, transactionManager)
        .reader(agencyReader)
        .processor(agencyProcessor)
        .writer(agencyWriter)
        .listener(stepExecutionListener)
        .build()

    @Bean
    fun routeProcessingStep(): Step = StepBuilder("routeProcessingStep", jobRepository)
        .chunk<RouteInput, RouteBatch>(50, transactionManager)
        .reader(routeReader)
        .processor(routeProcessor)
        .writer(routeWriter)
        .listener(stepExecutionListener)
        .build()

    @Bean
    fun routeVariantProcessingStep(): Step = StepBuilder("routeVariantProcessingStep", jobRepository)
        .chunk<RouteVariantInput, RouteVariantBatch>(10, transactionManager)
        .reader(routeVariantReader)
        .processor(routeVariantProcessor)
        .writer(routeVariantWriter)
        .listener(stepExecutionListener)
        .build()

    @Bean
    fun stopSpacingProcessingStep(): Step = StepBuilder("stopSpacingProcessingStep", jobRepository)
        .chunk<StopSpacingInput, StopSpacingBatch>(20, transactionManager)
        .reader(stopSpacingReader)
        .processor(stopSpacingProcessor)
        .writer(stopSpacingWriter)
        .listener(stepExecutionListener)
        .build()

    @Bean
    fun frequencyProcessingStep(): Step = StepBuilder("frequencyProcessingStep", jobRepository)
        .chunk<FrequencyInput, FrequencyBatch>(10, transactionManager)
        .reader(frequencyReader)
        .processor(frequencyProcessor)
        .writer(frequencyWriter)
        .listener(stepExecutionListener)
        .build()
}
