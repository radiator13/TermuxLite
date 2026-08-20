package com.termux.lite

import com.termux.app.TermuxOpenReceiver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPathTest {
    @Test
    fun underAllowsRootAndChildrenNotSiblings() {
        val root = "/data/data/com.termux/files"
        assertTrue(TermuxOpenReceiver.under(root, root))
        assertTrue(TermuxOpenReceiver.under("$root/home/x", root))
        assertFalse(TermuxOpenReceiver.under("${root}-evil/x", root))
        assertFalse(TermuxOpenReceiver.under("/tmp/x", root))
    }
}
