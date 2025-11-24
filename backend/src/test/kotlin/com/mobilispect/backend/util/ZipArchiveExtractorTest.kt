package com.mobilispect.backend.util

import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.reader
import kotlin.io.path.writeText
import kotlin.streams.toList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveExtractorTest {
    private val createdPaths = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        createdPaths.reversed().forEach { path ->
            if (Files.isDirectory(path)) {
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            } else {
                path.deleteIfExists()
            }
        }
        createdPaths.clear()
    }

    @Test
    fun `extracts zip entries to a temp directory`() {
        val archive = createTempFile("sample", ".zip").also { createdPaths.add(it) }
        ZipOutputStream(Files.newOutputStream(archive)).use { out ->
            out.putNextEntry(ZipEntry("hello.txt"))
            out.write("hi there".toByteArray())
            out.closeEntry()
        }

        val result = ZipArchiveExtractor().extract(archive)

        assertTrue(result.isSuccess)
        val destDir = result.getOrThrow()
        createdPaths.add(destDir)
        val extractedFile = destDir.resolve("hello.txt")
        assertTrue(extractedFile.exists())
        assertEquals("hi there", extractedFile.reader().use { it.readText() })
    }

    @Test
    fun `rejects zip entries that escape destination`() {
        val tmpRoot = Path.of(System.getProperty("java.io.tmpdir"))
        val existingBefore = Files.list(tmpRoot).use { it.toList() }
        val archive = createTempFile("zip-slip", ".zip").also { createdPaths.add(it) }
        ZipOutputStream(Files.newOutputStream(archive)).use { out ->
            out.putNextEntry(ZipEntry("../evil.txt"))
            out.write("oops".toByteArray())
            out.closeEntry()
        }

        val result = ZipArchiveExtractor().extract(archive)

        assertTrue(result.isFailure)
        result.exceptionOrNull()

        // Remove any temp directory that may have been created during extraction
        val after = Files.list(tmpRoot).use { it.toList() }
        val newDirs = after.minus(existingBefore.toSet()).filter { Files.isDirectory(it) }
        createdPaths.addAll(newDirs)
        newDirs.forEach { path -> assertFalse(Files.list(path).findAny().isPresent) }
    }
}
