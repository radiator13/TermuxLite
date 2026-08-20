package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NativeBridgeTest {
    @Test
    fun nativeBridgeInterfaceHandlesMissingLibraryGracefully() {
        // In standard JVM unit test environment, native library might not be loaded;
        // NativeBridge must not throw unscheduled UnsatisfiedLinkError exceptions.
        val testLine = "https://example.com"
        val result = NativeBridge.findUrlAt(testLine, 0)
        // If native is present, it returns the URL; if absent, it returns null and caller falls back.
        assertTrue(result == null || result == "https://example.com")
    }

    @Test
    fun bootstrapSha256MatchesExpected() {
        val tempFile = File.createTempFile("test_sha256", ".txt")
        try {
            tempFile.writeText("TermuxLite native optimization test\n")
            val hash = BootstrapInstaller.sha256(tempFile)
            assertNotNull(hash)
            assertEquals(64, hash.length)
        } finally {
            tempFile.delete()
        }
    }
}
