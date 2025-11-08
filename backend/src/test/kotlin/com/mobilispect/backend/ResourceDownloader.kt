package com.mobilispect.backend

import com.mobilispect.backend.schedule.download.DownloadRequest
import com.mobilispect.backend.schedule.download.Downloader
import com.mobilispect.backend.util.copyResourceTo
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
class ResourceDownloader(private val resourceLoader: ResourceLoader) : Downloader {
    override fun download(request: DownloadRequest): Result<Path> {
        check(request.url.startsWith("classpath:"))
        val tempDir = kotlin.io.path.createTempDirectory()
        return Result.success(resourceLoader.copyResourceTo(request.url, root = tempDir, dst = "gtfs.zip"))
    }

}
