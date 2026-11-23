package com.ngtv.neodroid.nvim

import org.junit.Assert.assertEquals
import org.junit.Test

class TextRowSelectionTest {
    @Test
    fun `textBetween returns plain text for a full row`() {
        val row = row(
            TextSpan(startCol = 0, columns = 2, text = "你", hlId = 0, wide = true),
            TextSpan(startCol = 2, columns = 2, text = "ab", hlId = 0),
        )

        assertEquals("你ab", row.textBetween(0, 4))
    }

    @Test
    fun `partial wide character is replaced with a blank cell`() {
        val row = row(
            TextSpan(startCol = 0, columns = 2, text = "你", hlId = 0, wide = true),
            TextSpan(startCol = 2, columns = 1, text = "a", hlId = 0),
        )

        assertEquals(" a", row.textBetween(1, 3))
    }

    @Test
    fun `out of range selection is padded with spaces`() {
        val row = row(TextSpan(startCol = 0, columns = 2, text = "ab", hlId = 0))

        assertEquals("ab   ", row.textBetween(0, 5))
    }

    private fun row(vararg spans: TextSpan): TextRow = TextRow(spans.toList())
}
