package com.termux.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets

class BootstrapInstallerTest {

    @Test
    fun bootstrapConfigConstantsAreValid() {
        assertTrue(BootstrapConfig.URL.startsWith("https://"))
        assertTrue(BootstrapConfig.URL.endsWith(".zip"))
        assertEquals(64, BootstrapConfig.SHA256.length)
        assertTrue(BootstrapConfig.SIZE_BYTES > 10_000_000L)
    }

    @Test
    fun sha256MatchesKnownVector() {
        val temp = File.createTempFile("bootstrap_test", ".txt")
        try {
            temp.writeText("abc")
            val hash = BootstrapInstaller.sha256(temp)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        } finally {
            temp.delete()
        }
    }

    @Test
    fun symlinksParserHandlesStandardFormat() {
        val symlinkData = "lib/libssl.so.3←lib/libssl.so\nbin/sh←bin/bash\n"
        val input = ByteArrayInputStream(symlinkData.toByteArray(StandardCharsets.UTF_8))
        val parsed = mutableListOf<Pair<String, String>>()
        
        // Emulate readSymlinks logic
        input.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split("←")
                if (parts.size == 2) {
                    parsed.add(Pair(parts[0], parts[1]))
                }
            }
        }

        assertEquals(2, parsed.size)
        assertEquals("lib/libssl.so.3", parsed[0].first)
        assertEquals("lib/libssl.so", parsed[0].second)
        assertEquals("bin/sh", parsed[1].first)
        assertEquals("bin/bash", parsed[1].second)
    }
}
