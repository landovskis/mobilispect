package com.mobilispect.backend.util

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.createTempDirectory

private const val BUFFER_SIZE = 1024

/**
 * An [ArchiveExtractor] that supports zip files.
 */
internal class ZipArchiveExtractor : ArchiveExtractor {
    private val logger = LoggerFactory.getLogger(ZipArchiveExtractor::class.java)

    override fun extract(archive: Path): Result<Path> {
        return try {
            val destDir = createTempDirectory()
            val archiveFile = archive.toFile()
            logger.info("Extracting ZIP archive {} (size: {} bytes) to {}", archive, archiveFile.length(), destDir)

            if (!archiveFile.exists()) {
                throw IOException("Archive file does not exist: $archive")
            }
            if (archiveFile.length() == 0L) {
                throw IOException("Archive file is empty: $archive")
            }

            val archiveInputStream = ZipInputStream(FileInputStream(archiveFile))
            var zipEntry = archiveInputStream.getNextEntry()
            logger.debug("First ZIP entry: {}", zipEntry?.name ?: "null")
            var fileCount = 0
            while (zipEntry != null) {
                val newFile = newFile(destDir.toFile(), zipEntry)
                logger.debug("Processing ZIP entry: {} (isDirectory: {})", zipEntry.name, zipEntry.isDirectory)

                if (zipEntry.isDirectory) {
                    // Create directory entries
                    if (!newFile.isDirectory && !newFile.mkdirs()) {
                        throw IOException("Failed to create directory $newFile")
                    }
                } else {
                    // Create parent directories for file entries
                    val parent = newFile.parentFile
                    if (!parent.isDirectory && !parent.mkdirs()) {
                        throw IOException("Failed to create directory $parent")
                    }

                    // Extract file
                    val out = FileOutputStream(newFile)
                    var len: Int
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (archiveInputStream.read(buffer).also { len = it } > 0) {
                        out.write(buffer, 0, len)
                    }
                    out.close()
                    fileCount++
                    logger.debug("Extracted file: {}", newFile.absolutePath)
                }

                zipEntry = archiveInputStream.getNextEntry()
            }
            archiveInputStream.close()

            logger.info("Successfully extracted {} files from archive to {}", fileCount, destDir)
            Result.success(destDir)
        } catch (e: IOException) {
            logger.error("Failed to extract archive: {}", archive, e)
            Result.failure(e)
        }
    }


    @Throws(IOException::class)
    // Prevent zip slip vulnerability by validating entry paths
    // Reference: https://security.snyk.io/research/zip-slip-vulnerability
    private fun newFile(destinationDir: File, zipEntry: ZipEntry): File {
        // Sanitize the entry name to prevent path traversal
        val sanitizedName = sanitizeZipEntryName(zipEntry.name)

        // Construct the destination file using the sanitized name
        val destFile = File(destinationDir, sanitizedName)

        // Validate that the canonical path is within the destination directory
        val destDirPath: String = destinationDir.canonicalPath
        val destFilePath: String = destFile.canonicalPath

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: ${zipEntry.name}")
        }

        return destFile
    }

    /**
     * Sanitizes a zip entry name to prevent path traversal attacks.
     * Removes any path components that attempt to navigate outside the extraction directory.
     */
    private fun sanitizeZipEntryName(entryName: String): String {
        // Normalize path separators to the system separator
        val normalized = entryName.replace('/', File.separatorChar)
            .replace('\\', File.separatorChar)

        // Split into path components and filter out dangerous elements
        val parts = normalized.split(File.separatorChar)
            .filter { it.isNotEmpty() && it != "." && it != ".." }

        if (parts.isEmpty()) {
            throw IOException("Invalid zip entry name: $entryName")
        }

        // Reconstruct the safe path
        return parts.joinToString(File.separator)
    }
}
