package com.ngtv.neodroid.sshUtils

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuoteTest {
    @Test
    fun `plain value is wrapped in single quotes`() {
        assertEquals("'/tmp/nvim.sock'", shellQuote("/tmp/nvim.sock"))
    }

    @Test
    fun `single quotes are escaped`() {
        assertEquals("'a'\\''b'", shellQuote("a'b"))
    }

    @Test
    fun `shell metacharacters are quoted literally`() {
        assertEquals(
            """'UNIX-CONNECT:/tmp/a b;rm -rf ~`id`$(whoami)'""",
            shellQuote("""UNIX-CONNECT:/tmp/a b;rm -rf ~`id`$(whoami)"""),
        )
    }
}
