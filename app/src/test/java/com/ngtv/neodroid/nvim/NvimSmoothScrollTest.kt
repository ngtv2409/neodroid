package com.ngtv.neodroid.nvim

import com.ngtv.neodroid.core.events.NvimUiEvent
import com.ngtv.neodroid.core.events.TextCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NvimSmoothScrollTest {
    @Test
    fun `scroll buffer keeps rows that leave the viewport`() {
        val actor = NvimStateActor()
        val height = 5

        actor.apply(NvimUiEvent.GridResize(1, 20, 10))
        actor.apply(
            NvimUiEvent.WinPos(
                grid = 2,
                window = 100,
                startRow = 0,
                startCol = 0,
                width = 10,
                height = height.toLong(),
            ),
        )
        for (row in 0 until height) {
            actor.apply(line(row, "row$row"))
        }
        actor.apply(NvimUiEvent.Flush)

        scroll(actor, grid = 2, rows = 1, height = height)
        actor.apply(line(height - 1, "new"))
        actor.apply(NvimUiEvent.WinViewport(2, 100, 1, 6, 1, 0, null, 1))
        actor.apply(NvimUiEvent.Flush)

        val layer = actor.state.value.layer(2) ?: error("window layer missing")
        assertEquals(1, layer.scrollDelta)
        assertEquals(height, layer.scrollOrigin)
        assertEquals(height, layer.scrollMargin)
        assertEquals(height + 2 * height, layer.scrollRows.size)
        assertTrue(layer.scrollGeneration > 0)
        assertEquals(1L, layer.scrollTotal)

        // At the start of the animation the renderer looks one row above the
        // new viewport, which is the row that just scrolled out.
        assertEquals("row0", layer.scrollRows[layer.scrollOrigin - 1].textBetween(0, 10).trim())
        assertEquals("row1", layer.scrollRows[layer.scrollOrigin].textBetween(0, 10).trim())
        assertEquals(
            "new",
            layer.scrollRows[layer.scrollOrigin + height - 1].textBetween(0, 10).trim(),
        )

        // Scrolling back up must keep the previous viewport below the new one.
        scroll(actor, grid = 2, rows = -1, height = height)
        actor.apply(line(0, "top"))
        actor.apply(NvimUiEvent.WinViewport(2, 100, 0, 5, 0, 0, null, -1))
        actor.apply(NvimUiEvent.Flush)

        val scrolledUp = actor.state.value.layer(2) ?: error("window layer missing")
        assertEquals(-1, scrolledUp.scrollDelta)
        assertEquals(0L, scrolledUp.scrollTotal)
        assertEquals(
            "top",
            scrolledUp.scrollRows[scrolledUp.scrollOrigin].textBetween(0, 10).trim(),
        )
        assertEquals(
            "row1",
            scrolledUp.scrollRows[scrolledUp.scrollOrigin + 1].textBetween(0, 10).trim(),
        )
    }

    @Test
    fun `grid scroll without viewport still animates`() {
        val actor = NvimStateActor()

        actor.apply(NvimUiEvent.GridResize(1, 20, 10))
        actor.apply(
            NvimUiEvent.WinPos(
                grid = 2,
                window = 100,
                startRow = 0,
                startCol = 0,
                width = 10,
                height = 4,
            ),
        )
        for (row in 0 until 4) {
            actor.apply(line(row, "row$row"))
        }
        actor.apply(NvimUiEvent.Flush)

        scroll(actor, grid = 2, rows = 1, height = 4)
        actor.apply(line(3, "new"))
        actor.apply(NvimUiEvent.Flush)

        val layer = actor.state.value.layer(2) ?: error("window layer missing")
        assertEquals(1, layer.scrollDelta)
    }

    private fun scroll(actor: NvimStateActor, grid: Long, rows: Long, height: Int) {
        actor.apply(
            NvimUiEvent.GridScroll(
                grid = grid,
                top = 0,
                bot = height.toLong(),
                left = 0,
                right = 10,
                rows = rows,
                cols = 0,
            ),
        )
    }

    private fun line(row: Int, text: String): NvimUiEvent.GridLine = NvimUiEvent.GridLine(
        grid = 2,
        row = row.toLong(),
        col_start = 0,
        cells = text.map { TextCell(it.toString(), 0, null) },
        wrap = false,
    )
}
