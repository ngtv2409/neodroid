package com.ngtv.neodroid.nvim

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.ngtv.neodroid.core.NeovimClient
import com.ngtv.neodroid.core.NvimNotification
import com.ngtv.neodroid.core.events.NvimUiEvent
import com.ngtv.neodroid.perf.NvimPerf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

// creates a session from a (brand new) nvim client (to own ui state)
class NvimSession private constructor(val nvim: NeovimClient) : Closeable {
    private val nvimActor = NvimStateActor()
    val state = nvimActor.state

    private val readerJob: Job = nvim.scope.launch {
        for (notification in nvim.inbound) {
            if (notification is NvimNotification.Redraw) {
                val event = notification.event
                if (event is NvimUiEvent.Flush) {
                    NvimPerf.measure("actor.flush") {
                        nvimActor.apply(event)
                    }
                    NvimPerf.markFlush()
                } else {
                    NvimPerf.measure("actor.apply") {
                        nvimActor.apply(event)
                    }
                }
            }
        }
    }

    private val inputQueue = Channel<String>(Channel.UNLIMITED)

    private val inputJob: Job = nvim.scope.launch(Dispatchers.IO) {
        for (keys in inputQueue) {
            val batch = StringBuilder(keys)
            while (true) {
                val next = inputQueue.tryReceive().getOrNull() ?: break
                batch.append(next)
            }
            nvim.notify("nvim_input", listOf(batch.toString()))
        }
    }

    fun sendInput(keys: String) {
        if (keys.isEmpty()) return
        if (inputQueue.trySend(keys).isSuccess) {
            NvimPerf.markInput()
        }
    }

    private var currentWidth = 0
    private var currentHeight = 0

    suspend fun resize(width: Int, height: Int) {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)

        if (safeWidth == currentWidth && safeHeight == currentHeight) return

        currentWidth = safeWidth
        currentHeight = safeHeight
        nvim.ui_try_resize(safeWidth, safeHeight)
    }

    companion object {
        private const val DEFAULT_WIDTH = 80
        private const val DEFAULT_HEIGHT = 24

        suspend fun create(nvim: NeovimClient): NvimSession {
            val session = NvimSession(nvim)

            val attachOpts = mapOf(
                "rgb" to true,
                "ext_linegrid" to true,
            )

            session.currentWidth = DEFAULT_WIDTH
            session.currentHeight = DEFAULT_HEIGHT
            nvim.ui_attach(DEFAULT_WIDTH, DEFAULT_HEIGHT, attachOpts)

            return session
        }
    }

    override fun close() {
        nvim.close()
    }
}
