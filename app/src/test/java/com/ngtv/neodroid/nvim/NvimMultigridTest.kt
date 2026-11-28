package com.ngtv.neodroid.nvim

import com.ngtv.neodroid.core.events.NvimUiEvent
import com.ngtv.neodroid.core.events.TextCell
import com.ngtv.neodroid.core.events.WindowAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NvimMultigridTest {
    @Test
    fun `window grid is positioned and receives mouse hits`() {
        val actor = NvimStateActor()

        actor.apply(NvimUiEvent.GridResize(1, 20, 10))
        actor.apply(NvimUiEvent.GridResize(2, 10, 5))
        actor.apply(
            NvimUiEvent.WinPos(
                grid = 2,
                window = 100,
                startRow = 2,
                startCol = 3,
                width = 10,
                height = 5,
            ),
        )
        actor.apply(line(grid = 2, row = 1, text = "hello"))
        actor.apply(NvimUiEvent.GridCursorGoto(2, 1, 2))
        actor.apply(NvimUiEvent.Flush)

        val frame = actor.state.value
        val window = frame.layer(2) ?: error("window layer missing")
        assertEquals(NvimLayerKind.Window, window.kind)
        assertEquals(2, window.startRow)
        assertEquals(3, window.startCol)
        assertEquals("hello", window.rows[1].textBetween(0, 5))
        assertEquals(1, window.cursor?.row)
        assertEquals(2, window.cursor?.col)

        val hit = frame.hitAt(screenRow = 3, screenColumn = 5) ?: error("expected a hit")
        assertEquals(2, hit.grid)
        assertEquals(1, hit.row)
        assertEquals(2, hit.col)
        assertEquals(1L, frame.hitAt(screenRow = 9, screenColumn = 19)?.grid)
        assertNull(frame.hitAt(screenRow = 20, screenColumn = 30))
    }

    @Test
    fun `grid resize preserves existing window content`() {
        val actor = NvimStateActor()

        actor.apply(NvimUiEvent.GridResize(1, 20, 10))
        actor.apply(NvimUiEvent.GridResize(2, 10, 4))
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
        actor.apply(line(grid = 2, row = 0, text = "hello"))
        actor.apply(line(grid = 2, row = 1, text = "world"))
        actor.apply(NvimUiEvent.Flush)

        actor.apply(
            NvimUiEvent.WinPos(
                grid = 2,
                window = 100,
                startRow = 0,
                startCol = 0,
                width = 10,
                height = 8,
            ),
        )
        actor.apply(NvimUiEvent.GridResize(2, 10, 8))
        actor.apply(NvimUiEvent.Flush)

        val grown = actor.state.value.layer(2) ?: error("window layer missing")
        assertEquals(8, grown.rows.size)
        assertEquals("hello", grown.rows[0].textBetween(0, 5))
        assertEquals("world", grown.rows[1].textBetween(0, 5))
        assertEquals("", grown.rows[7].textBetween(0, 10).trim())

        actor.apply(
            NvimUiEvent.WinPos(
                grid = 2,
                window = 100,
                startRow = 0,
                startCol = 0,
                width = 4,
                height = 8,
            ),
        )
        actor.apply(NvimUiEvent.GridResize(2, 4, 8))
        actor.apply(NvimUiEvent.Flush)

        val shrunk = actor.state.value.layer(2) ?: error("window layer missing")
        assertEquals(4, shrunk.rows[0].columns)
        assertEquals("hell", shrunk.rows[0].textBetween(0, 4))
    }

    @Test
    fun `floating windows respect anchors and hide state`() {
        val actor = NvimStateActor()

        actor.apply(NvimUiEvent.GridResize(1, 20, 10))
        actor.apply(NvimUiEvent.GridResize(2, 4, 3))
        actor.apply(
            NvimUiEvent.WinFloatPos(
                grid = 2,
                window = 100,
                anchor = WindowAnchor.SE,
                anchorGrid = 1,
                anchorRow = 10.0,
                anchorCol = 20.0,
                mouseEnabled = true,
                zIndex = 50,
                compIndex = 10,
                screenRow = null,
                screenCol = null,
            ),
        )
        actor.apply(NvimUiEvent.Flush)

        val floating = actor.state.value.layer(2) ?: error("floating layer missing")
        assertEquals(NvimLayerKind.Floating, floating.kind)
        assertEquals(7, floating.startRow)
        assertEquals(16, floating.startCol)
        assertEquals(1010, floating.zOrder)

        actor.apply(NvimUiEvent.WinHide(2))
        actor.apply(NvimUiEvent.Flush)

        assertNull(actor.state.value.layer(2))
    }

    private fun line(grid: Long, row: Long, text: String): NvimUiEvent.GridLine =
        NvimUiEvent.GridLine(
            grid = grid,
            row = row,
            col_start = 0,
            cells = text.map { TextCell(it.toString(), 0, null) },
            wrap = false,
        )
}
