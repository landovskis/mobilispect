package com.mobilispect.backend.util

import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File

class FileExtTest {
    @Test
    fun `normalizes newlines trims whitespace and removes odd characters`() {
        val tempFile = File.createTempFile("normalize", ".txt")
        tempFile.writeText("\uFEFF First line \r\nSecond line\u00a0 \r\n")

        val normalized = tempFile.readTextAndNormalize()

        assertEquals("First line \nSecond line", normalized)
        tempFile.delete()
    }
}
