package com.ngtv.neodroid.nvim

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.ngtv.neodroid.core.NeovimClient
import com.ngtv.neodroid.core.NvimNotification
import com.ngtv.neodroid.core.events.NvimUiEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable

// creates a session from a (brand new) nvim client (to own ui state)
class NvimSession private constructor(val nvim: NeovimClient) : Closeable {
    private val _state = MutableStateFlow(NvimState())
    val state = _state.asStateFlow()

    // intermidiate to hold state before flush
    private val stateBuffer = NvimState()

    private val readerJob: Job = nvim.scope.launch {
        nvim.notifications.collect { notification ->
            if (notification is NvimNotification.Redraw) {
                notification.events.forEach { event ->
                    when (event) {
                        is NvimUiEvent.OptionSet -> {
                            stateBuffer.options[event.name] = event.value
                        }

                        is NvimUiEvent.ModeInfoSet -> {
                            stateBuffer.cursorStyleEnabled = event.cursor_style_enabled
                            stateBuffer.modeInfo = event.mode_info
                        }

                        is NvimUiEvent.ModeChange -> {
                            stateBuffer.modeIndex = event.mode_idx
                        }

                        is NvimUiEvent.BusyStart -> {
                            stateBuffer.isBusy = true
                        }

                        is NvimUiEvent.BusyStop -> {
                            stateBuffer.isBusy = false
                        }

                        is NvimUiEvent.Flush -> {
                            _state.emit(stateBuffer.deepCopy())
                        }

                        is NvimUiEvent.DefaultColorSet -> {
                            stateBuffer.defaultColors = SpanStyle(
                                color = nvimRgbColor(event.rgb_fg),
                                background = nvimRgbColor(event.rgb_bg),
                            )
                        }

                        is NvimUiEvent.HlAttrDefine -> {
                            stateBuffer.highlightStyles[event.id.toInt()] =
                                nvimAttrToStyle(event.rgb_attr)
                        }

                        is NvimUiEvent.GridResize -> {
                            stateBuffer.grid = MutableList(event.height.toInt()) {
                                MutableList(event.width.toInt()) { GridCell(" ", -1) }
                            }
                        }

                        is NvimUiEvent.GridLine -> {
                            stateBuffer.grid?.let { currentGrid ->
                                var lastHlId = -1
                                var col = event.col_start
                                event.cells.forEach { cell ->
                                    val character = cell.text
                                    val hlId = (cell.hl_id ?: lastHlId).toInt()
                                    val repeat = cell.repeat?.toInt() ?: 1
                                    repeat(repeat) {
                                        currentGrid[event.row.toInt()][col.toInt()] =
                                            GridCell(character, hlId)
                                        ++col
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    companion object {
        suspend fun create(nvim: NeovimClient): NvimSession {
            val session = NvimSession(nvim)

            val attachOpts = mapOf(
                "rgb" to true,
                "ext_linegrid" to true,
            )

            nvim.ui_attach(120, 40, attachOpts)

            return session
        }
    }

    override fun close() {
        nvim.close()
    }
}
