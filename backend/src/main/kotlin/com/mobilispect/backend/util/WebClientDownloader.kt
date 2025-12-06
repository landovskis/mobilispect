package com.mobilispect.backend.schedule.download

import org.springframework.context.annotation.Primary
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpStatus
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import reactor.util.retry.Retry
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration

private const val RETRY_ATTEMPTS = 3L

/**
 * A [Downloader] that uses Spring [WebClient].
 */
@Component
@Primary
internal class WebClientDownloader(webClientBuilder: WebClient.Builder) : Downloader {
    private var webClient: WebClient = webClientBuilder
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create().followRedirect(true)
            )
        )
        .build()

    override fun download(request: DownloadRequest): Result<Path> {
        val dest = kotlin.io.path.createTempFile()
        return try {
            var builder = webClient.get()
                .uri(request.url)
            for (header in request.headers) {
                builder = builder.header(header.key, header.value)
            }
            val dataBuffer = builder
                .retrieve()
                .bodyToFlux(DataBuffer::class.java)
                .retryWhen(
                    Retry.backoff(RETRY_ATTEMPTS, Duration.ofSeconds(1))
                        .filter { error ->
                            val responseError = error as? WebClientResponseException
                            val serverError = responseError?.statusCode?.is5xxServerError ?: false
                            val rateLimitedError =
                                responseError?.statusCode?.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS) ?: false
                            return@filter serverError || rateLimitedError
                        })

            DataBufferUtils.write(
                dataBuffer, dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            ).then().block()

            Result.success(dest)
        } catch (e: WebClientException) {
            Result.failure(e)
        }
    }
}
