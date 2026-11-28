package com.ngtv.neodroid.nvim

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.ngtv.neodroid.core.NeovimClient
import com.ngtv.neodroid.core.NvimNotification
import com.ngtv.neodroid.core.events.NvimUiEvent
import com.ngtv.neodroid.nvim.input.NvimMouseEvent
import com.ngtv.neodroid.perf.NvimPerf
import kotlinx.coroutines.CancellationException
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

    private val _failure = MutableStateFlow<Throwable?>(null)
    val failure = _failure.asStateFlow()

    private fun recordFailure(cause: Throwable) {
        _failure.compareAndSet(null, cause)
    }

    private val readerJob: Job = nvim.scope.launch {
        try {
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(e)
        } finally {
            nvim.failure.value?.let(::recordFailure)
        }
    }

    private sealed interface NvimInput {
        data class Keys(val keys: String) : NvimInput
        data class Mouse(val event: NvimMouseEvent) : NvimInput
        data class Paste(val text: String) : NvimInput
    }

    private val inputQueue = Channel<NvimInput>(Channel.UNLIMITED)

    private val inputJob: Job = nvim.scope.launch(Dispatchers.IO) {
        try {
            var pending: NvimInput? = null
            while (true) {
                val input = pending
                    ?: inputQueue.receiveCatching().getOrNull()
                    ?: break
                pending = null

                when (input) {
                    is NvimInput.Keys -> {
                        val batch = StringBuilder(input.keys)
                        while (true) {
                            val next = inputQueue.tryReceive().getOrNull() ?: break
                            if (next is NvimInput.Keys) {
                                batch.append(next.keys)
                            } else {
                                pending = next
                                break
                            }
                        }
                        nvim.notify("nvim_input", listOf(batch.toString()))
                    }

                    is NvimInput.Mouse -> {
                        val event = input.event
                        nvim.inputMouse(
                            button = event.button.nvimName,
                            action = event.action.nvimName,
                            modifier = event.modifier,
                            grid = event.grid,
                            row = event.row,
                            col = event.col,
                        )
                    }

                    is NvimInput.Paste -> {
                        nvim.paste(input.text)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordFailure(e)
        }
    }

    fun sendInput(keys: String) {
        if (keys.isEmpty()) return
        if (inputQueue.trySend(NvimInput.Keys(keys)).isSuccess) {
            NvimPerf.markInput()
        }
    }

    fun sendMouse(event: NvimMouseEvent) {
        if (inputQueue.trySend(NvimInput.Mouse(event)).isSuccess) {
            NvimPerf.markInput()
        }
    }

    /**
     * Pastes local text through Neovim's paste handler. This is independent of
     * the remote clipboard integration setting.
     */
    fun paste(text: String) {
        if (text.isEmpty()) return
        if (inputQueue.trySend(NvimInput.Paste(text)).isSuccess) {
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
        try {
            nvim.ui_try_resize(safeWidth, safeHeight)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Resizes are triggered from composition; never let a closed transport
            // turn an expected shutdown into a crash.
            recordFailure(e)
        }
    }

    companion object {
        private const val DEFAULT_WIDTH = 80
        private const val DEFAULT_HEIGHT = 24

        suspend fun create(nvim: NeovimClient): NvimSession {
            val session = NvimSession(nvim)

            val attachOpts = mapOf(
                "rgb" to true,
                "ext_linegrid" to true,
                "ext_multigrid" to true,
            )

            nvim.set_client_info(
                "neodroid", // name
                mapOf("major" to 1, "minor" to 0, "patch" to 0), // API version, not app version
                "ui", // type
                // TODO: add an API specification
                mapOf(
                    "neodroid_clipboard_get" to mapOf("async" to false, "nargs" to 0),
                    "neodroid_clipboard_set" to mapOf("async" to false, "nargs" to 1),
                ), // methods
                emptyMap(), // attributes
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
