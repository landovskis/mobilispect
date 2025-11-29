package com.mobilispect.backend.transitanalysis.domain.model

/**
 * Enum representing the lifecycle states of GTFS feed imports.
 *
 * Tracks the progression of an import job from initiation through completion
 * or failure, enabling monitoring and recovery of import processes.
 *
 * State Transitions:
 * - PENDING -> IN_PROGRESS -> COMPLETED
 * - PENDING -> IN_PROGRESS -> FAILED
 *
 * @property value Database enum string for persistence
 */
enum class ImportStatus(val value: String) {
    /**
     * Import job queued but not yet started.
     * Job has been created but no processing has begun.
     */
    PENDING("PENDING"),

    /**
     * Import job actively processing.
     * Feed is being downloaded, parsed, and routes/variants identified.
     */
    IN_PROGRESS("IN_PROGRESS"),

    /**
     * Import job successfully completed.
     * All routes and variants have been processed and persisted.
     */
    COMPLETED("COMPLETED"),

    /**
     * Import job failed.
     * Processing was interrupted due to error. Check errorMessage field for details.
     */
    FAILED("FAILED");

    companion object {
        /**
         * Retrieves an ImportStatus by its database value string.
         *
         * @param value The database enum string
         * @return The matching ImportStatus
         * @throws IllegalArgumentException if value does not match any status
         */
        fun fromValue(value: String): ImportStatus {
            return entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown ImportStatus value: $value. Valid values: ${entries.joinToString { it.value }}")
        }

        /**
         * Checks if a status represents a terminal state (no further transitions possible).
         *
         * @return true if this status is COMPLETED or FAILED, false otherwise
         */
        fun ImportStatus.isTerminal(): Boolean = this == COMPLETED || this == FAILED

        /**
         * Checks if a status represents an active processing state.
         *
         * @return true if this status is IN_PROGRESS, false otherwise
         */
        fun ImportStatus.isActive(): Boolean = this == IN_PROGRESS
    }
}
