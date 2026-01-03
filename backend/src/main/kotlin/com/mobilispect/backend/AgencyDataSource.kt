package com.mobilispect.backend

import java.nio.file.Path

interface AgencyDataSource {
  fun agencies(root: Path, version: String, feedID: String): Result<Collection<Agency>>
}
