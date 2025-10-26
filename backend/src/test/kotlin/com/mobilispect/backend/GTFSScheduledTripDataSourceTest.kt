package com.mobilispect.backend

import com.mobilispect.backend.schedule.ScheduledTrip
import com.mobilispect.backend.schedule.gtfs.GTFSScheduledTripDataSource
import com.mobilispect.backend.util.copyResourceTo
import kotlinx.serialization.SerializationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDate

private const val VERSION: String = "v1"
private const val FEED_ID: String = "feed_id"

@SpringBootTest
internal class GTFSScheduledTripDataSourceTest {
    @Autowired
    lateinit var resourceLoader: ResourceLoader

    private val routeIDDataSource = StubRouteIDDataSource(mapOf(
        "1" to "r-f2566-1",
        "T1" to "r-f2566-t1",
        "115" to "r-f2565-115"
    ))
    private val subject = GTFSScheduledTripDataSource(routeIDDataSource)

    @Test
    fun bothCalendarFilesNotFound(@TempDir root: Path) {
        resourceLoader.copyResourceTo(src = "classpath:gtfs/citpi-trips.txt", root = root, dst = "trips.txt")

        val result = subject.trips(root, VERSION, FEED_ID).exceptionOrNull()

        assertThat(result).isInstanceOf(IOException::class.java)
    }

    @Test
    fun corrupted(@TempDir root: Path) {
        resourceLoader.copyResourceTo(
            src = "classpath:gtfs/citpi-calendar-dates.txt",
            root = root,
            dst = "calendar_dates.txt"
        )
        resourceLoader.copyResourceTo(src = "classpath:gtfs/citpi-calendar.txt", root = root, dst = "calendar.txt")
        resourceLoader.copyResourceTo(src = "classpath:gtfs/citpi-trips-corrupt.txt", root = root, dst = "trips.txt")

        val result = subject.trips(root, VERSION, FEED_ID).exceptionOrNull()

        assertThat(result).isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun dayOfWeekNotFound(@TempDir root: Path) {
        resourceLoader.copyResourceTo(
            src = "classpath:gtfs/citpi-calendar-dates.txt",
            root = root,
            dst = "calendar_dates.txt"
        )
        resourceLoader.copyResourceTo(
            src = "classpath:gtfs/citpi-calendar-day-of-week-not-found.txt",
            root = root,
            dst = "calendar.txt"
        )
        resourceLoader.copyResourceTo(src = "classpath:gtfs/citpi-trips.txt", root = root, dst = "trips.txt")

        subject.trips(root, VERSION, FEED_ID).getOrNull()!!
    }

}
