package com.mobilispect.backend.util

import java.nio.file.Path

interface ArchiveExtractor {
  fun extract(archive: Path): Result<Path>
}
