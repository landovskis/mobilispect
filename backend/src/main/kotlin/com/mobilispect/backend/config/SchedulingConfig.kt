package com.mobilispect.backend.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.util.concurrent.Executor

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    value = ["feeds.scheduler.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class SchedulingConfig : SchedulingConfigurer {

    private val logger = LoggerFactory.getLogger(SchedulingConfig::class.java)

    @Bean
    fun taskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.setPoolSize(5)
        scheduler.setThreadNamePrefix("feed-scheduler-")
        scheduler.setWaitForTasksToCompleteOnShutdown(true)
        scheduler.setAwaitTerminationSeconds(60)
        scheduler.setRejectedExecutionHandler { runnable, executor ->
            logger.warn("Scheduled task rejected: {}", runnable.toString())
        }
        scheduler.initialize()

        logger.info("Feed management task scheduler initialized with pool size: {}", scheduler.poolSize)
        return scheduler
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor())
    }

    @Bean
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskScheduler()
        executor.setPoolSize(3)
        executor.setThreadNamePrefix("feed-task-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()

        logger.info("Feed management task executor initialized with pool size: {}", executor.poolSize)
        return executor
    }
}
