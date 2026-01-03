package com.mobilispect.backend.schedule.download

import java.nio.file.Path

class DownloadRequest(val url: String, val headers: Map<String, String> = emptyMap())

interface Downloader {
  fun download(request: DownloadRequest): Result<Path>
}
