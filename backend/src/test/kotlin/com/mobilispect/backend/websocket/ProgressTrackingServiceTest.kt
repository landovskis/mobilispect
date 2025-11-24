package com.mobilispect.backend.websocket

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.messaging.simp.SimpMessagingTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

class ProgressTrackingServiceTest {
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val service = ProgressTrackingService(messagingTemplate)

    @Test
    fun `updates progress and tracks active imports`() {
        val importId = "import-1"
        var sentUpdate: Any? = null
        every { messagingTemplate.convertAndSend(any<String>(), any<Any>()) } answers {
            sentUpdate = secondArg()
            Unit
        }

        service.updateProgress(
            importId = importId,
            feedOnestopId = "f-feed",
            progressPercentage = 25,
            currentStep = "Downloading",
            currentStepNumber = 1,
            totalSteps = 4,
            startedAt = Instant.parse("2024-01-01T00:00:00Z"),
            estimatedTimeRemainingSeconds = 10,
            processingRate = 2.5
        )

        assertTrue(service.isActive(importId))
        assertEquals(listOf(importId), service.getActiveImportIds())
        val progress = service.getProgress(importId)
        assertNotNull(progress)
        assertEquals(25, progress.progressPercentage)

        verify {
            messagingTemplate.convertAndSend(
                "/topic/import/progress/$importId",
                any<ProgressUpdate>()
            )
        }
        assertTrue((sentUpdate as ProgressUpdate).progress?.feedOnestopId == "f-feed")
    }

    @Test
    fun `marks imports complete and failed`() {
        val importId = "import-2"
        val captured = mutableListOf<Any>()
        every { messagingTemplate.convertAndSend(any<String>(), any<Any>()) } answers {
            captured.add(secondArg())
            Unit
        }

        service.updateProgress(
            importId = importId,
            feedOnestopId = "f-feed",
            progressPercentage = 50,
            currentStep = "Processing",
            currentStepNumber = 2,
            totalSteps = 4,
            startedAt = Instant.now()
        )

        service.markCompleted(importId)
        assertFalse(service.isActive(importId))

        service.updateProgress(
            importId = importId,
            feedOnestopId = "f-feed",
            progressPercentage = 75,
            currentStep = "Processing",
            currentStepNumber = 3,
            totalSteps = 4,
            startedAt = Instant.now()
        )
        service.markFailed(importId, "boom")
        assertFalse(service.isActive(importId))

        verify {
            messagingTemplate.convertAndSend("/topic/import/progress/$importId", ProgressUpdate(completed = true))
            messagingTemplate.convertAndSend("/topic/import/progress/$importId", ProgressUpdate(error = "boom"))
        }
        val finalUpdate = captured.last() as ProgressUpdate
        assertEquals("boom", finalUpdate.error)
    }
}
