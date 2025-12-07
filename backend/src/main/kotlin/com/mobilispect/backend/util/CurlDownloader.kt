package com.mobilispect.backend.schedule.download

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/**
 * A [Downloader] that uses curl command-line tool.
 * This is used as a fallback when WebClient fails or for compatibility with servers
 * that have strict bot protection.
 */
@Component("curlDownloader")
internal class CurlDownloader : Downloader {
    private val logger = LoggerFactory.getLogger(CurlDownloader::class.java)

    override fun download(request: DownloadRequest): Result<Path> {
        val dest = createTempFile()
        return try {
            logger.info("Downloading URL with curl: {}", request.url)

            val command = buildList {
                add("curl")
                add("-L") // Follow redirects
                add("-f") // Fail on HTTP errors
                add("-s") // Silent mode
                add("-o")
                add(dest.toAbsolutePath().toString())
                // Add custom headers
                request.headers.forEach { (key, value) ->
                    add("-H")
                    add("$key: $value")
                }
                add(request.url)
            }

            val process = ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorOutput = process.errorStream.bufferedReader().readText()
                dest.deleteIfExists()
                throw IOException("curl failed with exit code $exitCode: $errorOutput")
            }

            if (!dest.exists() || dest.fileSize() == 0L) {
                dest.deleteIfExists()
                throw IOException("Downloaded file is empty or does not exist")
            }

            logger.info("Download complete via curl. File size: {} bytes. Path: {}", dest.fileSize(), dest)
            Result.success(dest)
        } catch (e: Exception) {
            logger.error("Download failed for URL: {}", request.url, e)
            dest.deleteIfExists()
            Result.failure(e)
        }
    }
}
