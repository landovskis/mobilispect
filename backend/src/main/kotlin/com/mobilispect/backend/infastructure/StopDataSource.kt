package com.mobilispect.backend.schedule.stop

import arrow.core.Ior
import com.mobilispect.backend.infastructure.Stop
import java.nio.file.Path

interface StopDataSource {
  fun stops(
    root: Path,
    version: String,
    feedID: String,
  ): Ior<Collection<Throwable>, Collection<Stop>>
}
