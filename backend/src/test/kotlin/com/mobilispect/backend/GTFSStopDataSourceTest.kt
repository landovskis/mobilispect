package com.mobilispect.backend

import com.mobilispect.backend.infastructure.Stop
import com.mobilispect.backend.schedule.gtfs.GTFSStopDataSource
import com.mobilispect.backend.util.copyResourceTo
import java.io.IOException
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader

private const val VERSION = "v1"
private const val FEED_ID = "feed_id"

internal class GTFSStopDataSourceTest {
  private val resourceLoader = DefaultResourceLoader()

  private val stopIDDataSource =
    StubStopIDDataSource(
      mapOf(
        "71998" to "s-f256hrvf2g-1eboulevard~11eavenue",
        "71999" to "s-f256hrtws3-1eboulevard~11eavenue",
      )
    )
  private val subject = GTFSStopDataSource(stopIDDataSource)

  @Test
  fun fileNotFound(@TempDir root: Path) {
    val result = subject.stops(root, VERSION, FEED_ID).leftOrNull()!!

    assertThat(result.first()).isInstanceOf(IOException::class.java)
  }

  @Test
  fun corrupted(@TempDir root: Path) {
    resourceLoader.copyResourceTo(
      src = "classpath:citpi-stops-corrupt.txt",
      root = root,
      dst = "stops.txt",
    )

    val result = subject.stops(root, VERSION, FEED_ID).leftOrNull()!!

    assertThat(result.first()).isInstanceOf(SerializationException::class.java)
  }

  @Test
  fun importsSuccessfully(@TempDir root: Path) {
    resourceLoader.copyResourceTo(src = "classpath:citpi-stops.txt", root = root, dst = "stops.txt")

    val stops = subject.stops(root, VERSION, FEED_ID).getOrNull()!!

    assertThat(stops)
      .contains(
        Stop(
          uid = "s-f256hrvf2g-1eboulevard~11eavenue",
          name = "1e Boulevard / 11e Avenue",
          localID = "71998",
          versions = listOf(VERSION),
        )
      )
    assertThat(stops)
      .contains(
        Stop(
          uid = "s-f256hrtws3-1eboulevard~11eavenue",
          name = "1e Boulevard / 11e Avenue",
          localID = "71999",
          versions = listOf(VERSION),
        )
      )
  }
}
