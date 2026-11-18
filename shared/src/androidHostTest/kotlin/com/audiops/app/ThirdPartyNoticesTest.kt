package com.audiops.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app bundles full licence texts (Settings → Open-source licenses → Third-party notices).
 * Every file in licenses/ must appear there verbatim, so adding a licence without bundling it
 * fails the build.
 */
class ThirdPartyNoticesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "licenses").isDirectory }
    private val notices = File(root, "shared/src/commonMain/composeResources/files/third_party_notices.txt").readText()

    @Test fun everyLicenseFileIsBundledInFull() {
        val files = File(root, "licenses").listFiles()!!.filter { it.extension == "txt" }
        assertTrue("no licence files found", files.isNotEmpty())
        for (file in files) {
            assertTrue("${file.name} is missing from third_party_notices.txt", notices.contains(file.readText().trim('\n')))
            assertTrue("${file.name} is not referenced", notices.contains("[licenses/${file.name}]"))
        }
    }

    @Test fun projectLicenseAndNoticeExist() {
        assertTrue(File(root, "LICENSE").readText().contains("Apache License"))
        assertTrue(File(root, "NOTICE").readText().contains("Copyright 2026 Sayed Shaun"))
    }
}
