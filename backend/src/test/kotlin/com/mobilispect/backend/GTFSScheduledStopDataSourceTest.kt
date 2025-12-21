package com.mobilispect.backend

import com.mobilispect.backend.schedule.ScheduledStop
import com.mobilispect.backend.schedule.gtfs.GTFSScheduledStopDataSource
import com.mobilispect.backend.util.DateTimeOffset
import com.mobilispect.backend.util.copyResourceTo
import kotlinx.serialization.SerializationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import java.io.IOException
import java.nio.file.Path

private const val VERSION = "v1"

class GTFSScheduledStopDataSourceTest {
    private val resourceLoader = DefaultResourceLoader()

    private val subject = GTFSScheduledStopDataSource()

    @Test
    fun fileNotFound(@TempDir root: Path) {
        val result = subject.scheduledStops(root, VERSION).exceptionOrNull()!!

        assertThat(result).isInstanceOf(IOException::class.java)
    }

    @Test
    fun corrupted(@TempDir root: Path) {
        resourceLoader.copyResourceTo(
            src = "classpath:citpi-stop-times-corrupt.txt",
            root = root,
            dst = "stop_times.txt"
        )

        val result = subject.scheduledStops(root, VERSION).exceptionOrNull()

        assertThat(result).isInstanceOf(SerializationException::class.java)
    }

    @Test
    @Suppress("LongMethod")
    fun importsSuccessfully(@TempDir root: Path) {
        resourceLoader.copyResourceTo(src = "classpath:citpi-stop-times.txt", root = root, dst = "stop_times.txt")

        val scheduledStops = subject.scheduledStops(root, VERSION).getOrNull()!!

        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72986",
                departsAt = DateTimeOffset(hours = 6, minutes = 37),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37),
                stopSequence = 1,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72748",
                departsAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 28),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 28),
                stopSequence = 2,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72960",
                departsAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 38),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 38),
                stopSequence = 3,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72711",
                departsAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 51),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 37, seconds = 51),
                stopSequence = 4,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72618",
                departsAt = DateTimeOffset(hours = 6, minutes = 38, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 38, seconds = 0),
                stopSequence = 5,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72828",
                departsAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 0),
                stopSequence = 6,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72698",
                departsAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 26),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 26),
                stopSequence = 7,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72702",
                departsAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 55),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 42, seconds = 55),
                stopSequence = 8,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72704",
                departsAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 26),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 26),
                stopSequence = 9,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72696",
                departsAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 57),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 43, seconds = 57),
                stopSequence = 10,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72705",
                departsAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 15),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 15),
                stopSequence = 11,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72707",
                departsAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 55),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 44, seconds = 55),
                stopSequence = 12,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72710",
                departsAt = DateTimeOffset(hours = 6, minutes = 45, seconds = 42),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 45, seconds = 42),
                stopSequence = 13,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72916",
                departsAt = DateTimeOffset(hours = 6, minutes = 46, seconds = 4),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 46, seconds = 4),
                stopSequence = 14,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72901",
                departsAt = DateTimeOffset(hours = 6, minutes = 47, seconds = 25),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 47, seconds = 25),
                stopSequence = 15,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72577",
                departsAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 0),
                stopSequence = 16,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72579",
                departsAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 11),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 11),
                stopSequence = 17,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72581",
                departsAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 36),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 48, seconds = 36),
                stopSequence = 18,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72567",
                departsAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 1),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 1),
                stopSequence = 19,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72568",
                departsAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 30),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 30),
                stopSequence = 20,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72570",
                departsAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 42),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 49, seconds = 42),
                stopSequence = 21,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72626",
                departsAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 8),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 8),
                stopSequence = 22,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72722",
                departsAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 36),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 50, seconds = 36),
                stopSequence = 23,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72720",
                departsAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 6),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 6),
                stopSequence = 24,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72789",
                departsAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 26),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 51, seconds = 26),
                stopSequence = 25,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72571",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 0),
                stopSequence = 26,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72919",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 20),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 20),
                stopSequence = 27,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72801",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 35),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 35),
                stopSequence = 28,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72585",
                departsAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 54),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 52, seconds = 54),
                stopSequence = 29,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72819",
                departsAt = DateTimeOffset(hours = 6, minutes = 53, seconds = 48),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 53, seconds = 48),
                stopSequence = 30,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72728",
                departsAt = DateTimeOffset(hours = 6, minutes = 54, seconds = 57),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 54, seconds = 57),
                stopSequence = 31,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72825",
                departsAt = DateTimeOffset(hours = 6, minutes = 55, seconds = 23),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 55, seconds = 23),
                stopSequence = 32,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72968",
                departsAt = DateTimeOffset(hours = 6, minutes = 56, seconds = 7),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 56, seconds = 7),
                stopSequence = 33,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72914",
                departsAt = DateTimeOffset(hours = 6, minutes = 57, seconds = 33),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 57, seconds = 33),
                stopSequence = 34,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72617",
                departsAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 0),
                stopSequence = 35,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72712",
                departsAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 59),
                arrivesAt = DateTimeOffset(hours = 6, minutes = 58, seconds = 59),
                stopSequence = 36,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72961",
                departsAt = DateTimeOffset(hours = 7, minutes = 0, seconds = 19),
                arrivesAt = DateTimeOffset(hours = 7, minutes = 0, seconds = 19),
                stopSequence = 37,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72750",
                departsAt = DateTimeOffset(hours = 7, minutes = 1, seconds = 3),
                arrivesAt = DateTimeOffset(hours = 7, minutes = 1, seconds = 3),
                stopSequence = 38,
                versions = listOf(VERSION)
            )
        )
        assertThat(scheduledStops).contains(
            ScheduledStop(
                tripID = "3281905-PI-A22-PI_GTFS-Semaine-01",
                stopID = "72986",
                departsAt = DateTimeOffset(hours = 7, minutes = 3, seconds = 0),
                arrivesAt = DateTimeOffset(hours = 7, minutes = 3, seconds = 0),
                stopSequence = 39,
                versions = listOf(VERSION)
            )
        )
    }
}
