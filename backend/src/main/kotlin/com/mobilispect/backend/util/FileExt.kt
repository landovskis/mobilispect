package com.mobilispect.backend.util

import java.io.File

/**
 * Ensure that newlines are normalized to Unix-style and no leading/trailing whitespace.
 */
fun File.readTextAndNormalize(): String = readText()
    .replace("\r\n", "\n")
    .trim()
    .replace("\u00a0", "")
    .replace("\ufeff", "")
