package com.mobilispect.backend.feed.model

import com.mobilispect.backend.persistence.PostgreSqlEnumConverter
import jakarta.persistence.Converter

enum class FeedSpecType(val dbValue: String) {
    GTFS("gtfs"),
    GTFS_RT("gtfs-rt");

    companion object {
        private val byDbValue = values().associateBy { it.dbValue }
        fun fromDb(value: String?): FeedSpecType? = value?.let(byDbValue::get)
    }
}

enum class FeedStatus(val dbValue: String) {
    ACTIVE("active"),
    INACTIVE("inactive"),
    ERROR("error");

    companion object {
        private val byDbValue = values().associateBy { it.dbValue }
        fun fromDb(value: String?): FeedStatus? = value?.let(byDbValue::get)
    }
}

enum class AuthType(val dbValue: String) {
    NONE("none"),
    API_KEY("api_key"),
    OAUTH2("oauth2");

    companion object {
        private val byDbValue = values().associateBy { it.dbValue }
        fun fromDb(value: String?): AuthType? = value?.let(byDbValue::get)
    }
}

enum class AdminRole(val dbValue: String) {
    FEED_VIEWER("FEED_VIEWER"),
    FEED_OPERATOR("FEED_OPERATOR"),
    FEED_MANAGER("FEED_MANAGER");

    companion object {
        private val byDbValue = values().associateBy { it.dbValue }
        fun fromDb(value: String?): AdminRole? = value?.let(byDbValue::get)
    }
}

enum class ImportTriggerType(val dbValue: String) {
    MANUAL("manual"),
    AUTOMATIC("automatic");

    companion object {
        private val byDbValue = values().associateBy { it.dbValue }
        fun fromDb(value: String?): ImportTriggerType? = value?.let(byDbValue::get)
    }
}

enum class ImportStatus(val dbValue: String) {
    PENDING("pending"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        private val byDbValue = values().associateBy { it.dbValue }
        fun fromDb(value: String?): ImportStatus? = value?.let(byDbValue::get)
    }
}

enum class LogLevel(val dbValue: String) {
    INFO("info"),
    WARN("warn"),
    ERROR("error");

    companion object {
        private val byDbValue = values().associateBy { it.dbValue }
        fun fromDb(value: String?): LogLevel? = value?.let(byDbValue::get)
    }
}

@Converter(autoApply = true)
class FeedSpecTypeConverter :
    PostgreSqlEnumConverter<FeedSpecType>(
        toDbValue = { it.dbValue },
        fromDbValue = { FeedSpecType.fromDb(it) }
    )

@Converter(autoApply = true)
class FeedStatusConverter :
    PostgreSqlEnumConverter<FeedStatus>(
        toDbValue = { it.dbValue },
        fromDbValue = { FeedStatus.fromDb(it) }
    )

@Converter(autoApply = true)
class AuthTypeConverter :
    PostgreSqlEnumConverter<AuthType>(
        toDbValue = { it.dbValue },
        fromDbValue = { AuthType.fromDb(it) }
    )

@Converter(autoApply = true)
class AdminRoleConverter :
    PostgreSqlEnumConverter<AdminRole>(
        toDbValue = { it.dbValue },
        fromDbValue = { AdminRole.fromDb(it) }
    )

@Converter(autoApply = true)
class ImportTriggerTypeConverter :
    PostgreSqlEnumConverter<ImportTriggerType>(
        toDbValue = { it.dbValue },
        fromDbValue = { ImportTriggerType.fromDb(it) }
    )

@Converter(autoApply = true)
class ImportStatusConverter :
    PostgreSqlEnumConverter<ImportStatus>(
        toDbValue = { it.dbValue },
        fromDbValue = { ImportStatus.fromDb(it) }
    )

@Converter(autoApply = true)
class LogLevelConverter :
    PostgreSqlEnumConverter<LogLevel>(
        toDbValue = { it.dbValue },
        fromDbValue = { LogLevel.fromDb(it) }
    )
