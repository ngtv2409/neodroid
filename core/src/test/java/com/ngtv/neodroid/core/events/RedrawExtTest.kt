package com.ngtv.neodroid.core.events

import com.ngtv.neodroid.core.NvimNotification
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.msgpack.core.MessagePack

class RedrawExtTest {
    @Test
    fun `win_pos decodes extension window handles`() = runBlocking {
        val channel = Channel<NvimNotification>(Channel.UNLIMITED)

        dispatchRedrawEvent(
            channel,
            listOf(
                listOf(
                    "win_pos",
                    listOf(
                        2L,
                        windowExt(1000L),
                        3L,
                        4L,
                        80L,
                        22L,
                    ),
                ),
            ),
        )

        val notification = channel.receive() as NvimNotification.Redraw
        val event = notification.event as NvimUiEvent.WinPos
        assertEquals(2L, event.grid)
        assertEquals(1000L, event.window)
        assertEquals(3L, event.startRow)
        assertEquals(4L, event.startCol)
        assertEquals(80L, event.width)
        assertEquals(22L, event.height)
    }

    @Test
    fun `win_float_pos decodes extension window handles`() = runBlocking {
        val channel = Channel<NvimNotification>(Channel.UNLIMITED)

        dispatchRedrawEvent(
            channel,
            listOf(
                listOf(
                    "win_float_pos",
                    listOf(
                        6L,
                        windowExt(1003L),
                        "NW",
                        1L,
                        2.0,
                        2.0,
                        true,
                        50L,
                        1L,
                        null,
                        null,
                    ),
                ),
            ),
        )

        val notification = channel.receive() as NvimNotification.Redraw
        val event = notification.event as NvimUiEvent.WinFloatPos
        assertEquals(6L, event.grid)
        assertEquals(1003L, event.window)
        assertEquals(WindowAnchor.NW, event.anchor)
        assertEquals(2.0, event.anchorRow, 0.0)
    }

    private fun windowExt(id: Long): Map<String, Any> {
        val data = MessagePack.newDefaultBufferPacker().use { packer ->
            packer.packLong(id)
            packer.toByteArray()
        }
        return mapOf("__ext_type" to 1, "__ext_data" to data)
    }
}
