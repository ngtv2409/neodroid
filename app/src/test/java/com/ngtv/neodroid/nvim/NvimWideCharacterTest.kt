package com.ngtv.neodroid.nvim

import com.ngtv.neodroid.core.events.NvimUiEvent
import com.ngtv.neodroid.core.events.TextCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NvimWideCharacterTest {
    @Test
    fun `double width cell and empty filler become one wide span`() {
        val actor = actor(width = 8)

        actor.apply(line(cell("你"), cell("")))
        actor.apply(NvimUiEvent.Flush)

        val spans = actor.state.value.rows[0].spans
        assertEquals("你", spans[0].text)
        assertEquals(0, spans[0].startCol)
        assertEquals(2, spans[0].columns)
        assertTrue(spans[0].wide)
        assertEquals(8, actor.state.value.rows[0].columns)
    }

    @Test
    fun `wide char in seperate grid_line batch is widen`() {
        val actor = actor(width = 8)

        actor.apply(line(cell("a"), cell("b"), cell("你")))
        actor.apply(NvimUiEvent.Flush)

        actor.apply(lineAt(3, cell("")))
        actor.apply(NvimUiEvent.Flush)

        val spans = actor.state.value.rows[0].spans
        assertEquals("ab", spans[0].text)
        assertFalse(spans[0].wide)
        assertEquals("你", spans[1].text)
        assertEquals(2, spans[1].startCol)
        assertEquals(2, spans[1].columns)
        assertTrue(spans[1].wide)
        assertEquals(8, actor.state.value.rows[0].columns)
    }

    @Test
    fun `wide char keep cell alignment`() {
        val actor = actor(width = 8)

        actor.apply(line(cell("你"), cell(""), cell("a"), cell("b")))
        actor.apply(NvimUiEvent.Flush)

        val spans = actor.state.value.rows[0].spans
        assertEquals("你", spans[0].text)
        assertTrue(spans[0].wide)
        assertEquals("ab", spans[1].text)
        assertEquals(2, spans[1].startCol)
        assertEquals(2, spans[1].columns)
        assertFalse(spans[1].wide)
    }

    @Test
    fun `adjacent wide chars stay separate spans`() {
        val actor = actor(width = 8)

        actor.apply(line(cell("你"), cell(""), cell("好"), cell("")))
        actor.apply(NvimUiEvent.Flush)

        val spans = actor.state.value.rows[0].spans
        assertEquals("你", spans[0].text)
        assertEquals("好", spans[1].text)
        assertEquals(2, spans[0].columns)
        assertEquals(2, spans[1].startCol)
        assertEquals(2, spans[1].columns)
    }

    @Test
    fun `wide char is clamped when only one cell remains`() {
        val actor = actor(width = 4)

        actor.apply(lineAt(3, cell(">")))
        actor.apply(NvimUiEvent.Flush)

        val spans = actor.state.value.rows[0].spans
        val last = spans.last()
        assertEquals(">", last.text)
        assertEquals(3, last.startCol)
        assertEquals(1, last.columns)
        assertFalse(last.wide)
    }

    private fun actor(width: Int, height: Int = 1): NvimStateActor = NvimStateActor().apply {
        apply(NvimUiEvent.GridResize(1, width.toLong(), height.toLong()))
    }

    private fun line(vararg cells: TextCell): NvimUiEvent.GridLine = lineAt(0, *cells)

    private fun lineAt(colStart: Int, vararg cells: TextCell): NvimUiEvent.GridLine =
        NvimUiEvent.GridLine(
            grid = 1,
            row = 0,
            col_start = colStart.toLong(),
            cells = cells.toList(),
            wrap = false,
        )

    private fun cell(text: String, hlId: Long? = 0, repeat: Long? = null): TextCell = TextCell(
        text = text,
        hl_id = hlId,
        repeat = repeat,
    )
}
