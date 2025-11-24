package com.mobilispect.backend.feed.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedEnumsTest {
    @Test
    fun `converts feed spec types to and from db`() {
        assertEquals(FeedSpecType.GTFS, FeedSpecType.fromDb("gtfs"))
        assertEquals(FeedSpecType.GTFS_RT, FeedSpecType.fromDb("gtfs-rt"))
        assertNull(FeedSpecType.fromDb(null))

        val converter = FeedSpecTypeConverter()
        assertEquals("gtfs", converter.convertToDatabaseColumn(FeedSpecType.GTFS))
        assertEquals(FeedSpecType.GTFS_RT, converter.convertToEntityAttribute("gtfs-rt"))
    }

    @Test
    fun `converts feed status to and from db`() {
        assertEquals(FeedStatus.ERROR, FeedStatus.fromDb("error"))
        assertNull(FeedStatus.fromDb("unknown"))

        val converter = FeedStatusConverter()
        assertEquals("inactive", converter.convertToDatabaseColumn(FeedStatus.INACTIVE))
        assertEquals(FeedStatus.ACTIVE, converter.convertToEntityAttribute("active"))
    }

    @Test
    fun `converts admin role and auth types`() {
        val roleConverter = AdminRoleConverter()
        assertEquals("FEED_MANAGER", roleConverter.convertToDatabaseColumn(AdminRole.FEED_MANAGER))
        assertEquals(AdminRole.FEED_OPERATOR, roleConverter.convertToEntityAttribute("FEED_OPERATOR"))

        val authConverter = AuthTypeConverter()
        assertEquals("oauth2", authConverter.convertToDatabaseColumn(AuthType.OAUTH2))
        assertEquals(AuthType.API_KEY, authConverter.convertToEntityAttribute("api_key"))
    }

    @Test
    fun `converts import enums`() {
        val triggerConverter = ImportTriggerTypeConverter()
        assertEquals("automatic", triggerConverter.convertToDatabaseColumn(ImportTriggerType.AUTOMATIC))
        assertEquals(ImportTriggerType.MANUAL, triggerConverter.convertToEntityAttribute("manual"))

        val statusConverter = ImportStatusConverter()
        assertEquals("running", statusConverter.convertToDatabaseColumn(ImportStatus.RUNNING))
        assertEquals(ImportStatus.CANCELLED, statusConverter.convertToEntityAttribute("cancelled"))

        val logLevelConverter = LogLevelConverter()
        assertEquals("warn", logLevelConverter.convertToDatabaseColumn(LogLevel.WARN))
        assertEquals(LogLevel.ERROR, logLevelConverter.convertToEntityAttribute("error"))
    }
}
