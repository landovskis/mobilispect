package com.mobilispect.backend.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ZipArchiveExtractor with focus on zip slip vulnerability prevention.
 * Constitutional requirement: TDD with security validation
 */
class ZipArchiveExtractorTest {

    private val extractor = ZipArchiveExtractor()

    @Test
    fun `should extract valid zip archive successfully`(@TempDir tempDir: Path) {
        // Create a valid zip file
        val zipFile = tempDir.resolve("test.zip").toFile()
        createZipFile(zipFile, listOf(
            "file1.txt" to "content1",
            "subdir/file2.txt" to "content2"
        ))

        // Extract the archive
        val result = extractor.extract(zipFile.toPath())

        // Verify extraction succeeded
        assertTrue(result.isSuccess)
        val extractedDir = result.getOrThrow()
        assertTrue(extractedDir.toFile().exists())
        assertTrue(extractedDir.resolve("file1.txt").toFile().exists())
        assertTrue(extractedDir.resolve("subdir/file2.txt").toFile().exists())
    }

    @Test
    fun `should reject zip entry with path traversal using dot-dot`(@TempDir tempDir: Path) {
        // Create a malicious zip file with path traversal
        val zipFile = tempDir.resolve("malicious.zip").toFile()
        createZipFile(zipFile, listOf(
            "../../etc/passwd" to "malicious content"
        ))

        // Extract the archive
        val result = extractor.extract(zipFile.toPath())

        // Verify extraction succeeded but sanitized the path
        assertTrue(result.isSuccess)
        val extractedDir = result.getOrThrow()

        // The malicious path should be sanitized to "etc/passwd" (without ..)
        assertTrue(extractedDir.resolve("etc/passwd").toFile().exists())

        // Verify the file was NOT created outside the extraction directory
        val parentDir = extractedDir.parent.toFile()
        assertFalse(parentDir.resolve("etc/passwd").exists())
    }

    @Test
    fun `should reject zip entry with absolute path`(@TempDir tempDir: Path) {
        // Create a zip file with absolute path
        val zipFile = tempDir.resolve("absolute.zip").toFile()
        createZipFile(zipFile, listOf(
            "/tmp/malicious.txt" to "malicious content"
        ))

        // Extract the archive
        val result = extractor.extract(zipFile.toPath())

        // Verify extraction succeeded with sanitized path
        assertTrue(result.isSuccess)
        val extractedDir = result.getOrThrow()

        // The absolute path should be sanitized to "tmp/malicious.txt"
        assertTrue(extractedDir.resolve("tmp/malicious.txt").toFile().exists())

        // Verify the file was NOT created at /tmp
        assertFalse(File("/tmp/malicious.txt").exists())
    }

    @Test
    fun `should handle empty zip archive`(@TempDir tempDir: Path) {
        // Create an empty zip file
        val zipFile = tempDir.resolve("empty.zip").toFile()
        createZipFile(zipFile, emptyList())

        // Extract the archive
        val result = extractor.extract(zipFile.toPath())

        // Verify extraction succeeded even for empty archive
        assertTrue(result.isSuccess)
        val extractedDir = result.getOrThrow()
        assertTrue(extractedDir.toFile().exists())
    }

    @Test
    fun `should reject non-existent archive`(@TempDir tempDir: Path) {
        val nonExistentFile = tempDir.resolve("nonexistent.zip")

        // Extract non-existent archive
        val result = extractor.extract(nonExistentFile)

        // Verify extraction failed
        assertTrue(result.isFailure)
    }

    @Test
    fun `should handle zip with directory entries`(@TempDir tempDir: Path) {
        // Create a zip file with explicit directory entries
        val zipFile = tempDir.resolve("dirs.zip").toFile()
        val zipOut = ZipOutputStream(FileOutputStream(zipFile))

        // Add directory entry
        zipOut.putNextEntry(ZipEntry("mydir/"))
        zipOut.closeEntry()

        // Add file in directory
        zipOut.putNextEntry(ZipEntry("mydir/file.txt"))
        zipOut.write("content".toByteArray())
        zipOut.closeEntry()

        zipOut.close()

        // Extract the archive
        val result = extractor.extract(zipFile.toPath())

        // Verify extraction succeeded
        assertTrue(result.isSuccess)
        val extractedDir = result.getOrThrow()
        assertTrue(extractedDir.resolve("mydir").toFile().isDirectory)
        assertTrue(extractedDir.resolve("mydir/file.txt").toFile().exists())
    }

    @Test
    fun `should sanitize Windows-style path separators`(@TempDir tempDir: Path) {
        // Create a zip with Windows-style paths
        val zipFile = tempDir.resolve("windows.zip").toFile()
        createZipFile(zipFile, listOf(
            "dir\\subdir\\file.txt" to "content"
        ))

        // Extract the archive
        val result = extractor.extract(zipFile.toPath())

        // Verify extraction succeeded
        assertTrue(result.isSuccess)
        val extractedDir = result.getOrThrow()

        // The path should be normalized to platform separator
        assertTrue(
            extractedDir.resolve("dir${File.separator}subdir${File.separator}file.txt").toFile().exists() ||
            extractedDir.resolve("dir/subdir/file.txt").toFile().exists()
        )
    }

    @Test
    fun `should handle GTFS zip structure`(@TempDir tempDir: Path) {
        // Create a GTFS-like zip file
        val zipFile = tempDir.resolve("gtfs.zip").toFile()
        createZipFile(zipFile, listOf(
            "agency.txt" to "agency_id,agency_name,agency_url,agency_timezone",
            "routes.txt" to "route_id,route_short_name,route_long_name",
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon",
            "trips.txt" to "trip_id,route_id,service_id"
        ))

        // Extract the archive
        val result = extractor.extract(zipFile.toPath())

        // Verify all GTFS files were extracted
        assertTrue(result.isSuccess)
        val extractedDir = result.getOrThrow()
        assertTrue(extractedDir.resolve("agency.txt").toFile().exists())
        assertTrue(extractedDir.resolve("routes.txt").toFile().exists())
        assertTrue(extractedDir.resolve("stops.txt").toFile().exists())
        assertTrue(extractedDir.resolve("trips.txt").toFile().exists())

        // Verify content
        val agencyContent = extractedDir.resolve("agency.txt").toFile().readText()
        assertEquals("agency_id,agency_name,agency_url,agency_timezone", agencyContent)
    }

    /**
     * Helper function to create a zip file with specified entries
     */
    private fun createZipFile(zipFile: File, entries: List<Pair<String, String>>) {
        val zipOut = ZipOutputStream(FileOutputStream(zipFile))
        for ((name, content) in entries) {
            zipOut.putNextEntry(ZipEntry(name))
            zipOut.write(content.toByteArray())
            zipOut.closeEntry()
        }
        zipOut.close()
    }
}
