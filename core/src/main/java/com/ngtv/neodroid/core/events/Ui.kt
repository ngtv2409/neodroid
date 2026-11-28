@file:Suppress("PropertyName")

package com.ngtv.neodroid.core.events

import com.ngtv.neodroid.core.NvimNotification
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.msgpack.core.MessagePack

// https://neovim.io/doc/user/api-ui-events/#_global-events
// only implement the significant subsets
sealed interface NvimUiEvent {
    // implicitly ignored events
    data class Unclassified(val method: String, val params: List<Any?>) : NvimUiEvent

    // Global events
    data class SetTitle(val title: String) : NvimUiEvent
    data class SetIcon(val icon: String) : NvimUiEvent
    data class ModeInfoSet(val cursor_style_enabled: Boolean, val mode_info: List<ModeProperty>) :
        NvimUiEvent
    data class OptionSet(val name: String, val value: Any?) : NvimUiEvent
    data class Chdir(val path: String) : NvimUiEvent
    data class ModeChange(val mode: String, val mode_idx: Long) : NvimUiEvent
    data object BusyStart : NvimUiEvent {
        override fun toString() = "BusyStart()"
    }
    data object BusyStop : NvimUiEvent {
        override fun toString() = "BusyStop()"
    }

    // TODO: more thought
    // ignore Connect and Restart since we'd be listening on stdio over ssh
    data object Flush : NvimUiEvent {
        override fun toString() = "Flush()"
    }

    // Grid events - Line based
    data class GridResize(val grid: Long, val width: Long, val height: Long) : NvimUiEvent
    data class DefaultColorSet(
        val rgb_fg: Long,
        val rgb_bg: Long,
        val rgb_sp: Long,
        val cterm_fg: Long,
        val cterm_bg: Long,
    ) : NvimUiEvent
    data class HlAttrDefine(
        val id: Long,
        val rgb_attr: HighlightAttr,
        val cterm_attr: HighlightAttr,
        val info: List<Any?>,
    ) : NvimUiEvent
    data class HlGroupSet(val name: String, val id: Long) : NvimUiEvent
    data class GridLine(
        val grid: Long,
        val row: Long,
        val col_start: Long,
        val cells: List<TextCell>,
        val wrap: Boolean,
    ) : NvimUiEvent
    data class GridClear(val grid: Long) : NvimUiEvent
    data class GridDestroy(val grid: Long) : NvimUiEvent
    data class GridCursorGoto(val grid: Long, val row: Long, val col: Long) : NvimUiEvent
    data class GridScroll(
        val grid: Long,
        val top: Long,
        val bot: Long,
        val left: Long,
        val right: Long,
        val rows: Long,
        val cols: Long,
    ) : NvimUiEvent

    // Multigrid events
    // https://neovim.io/doc/user/api-ui-events/#_multigrid-events
    data class WinPos(
        val grid: Long,
        val window: Long,
        val startRow: Long,
        val startCol: Long,
        val width: Long,
        val height: Long,
    ) : NvimUiEvent

    data class WinFloatPos(
        val grid: Long,
        val window: Long,
        val anchor: WindowAnchor,
        val anchorGrid: Long,
        val anchorRow: Double,
        val anchorCol: Double,
        val mouseEnabled: Boolean,
        val zIndex: Long,
        val compIndex: Long?,
        val screenRow: Long?,
        val screenCol: Long?,
    ) : NvimUiEvent

    data class WinExternalPos(val grid: Long, val window: Long) : NvimUiEvent
    data class WinHide(val grid: Long) : NvimUiEvent
    data class WinClose(val grid: Long) : NvimUiEvent

    data class MsgSetPos(
        val grid: Long,
        val row: Long,
        val scrolled: Boolean,
        val separator: String?,
        val zIndex: Long?,
        val compIndex: Long?,
    ) : NvimUiEvent

    data class WinViewport(
        val grid: Long,
        val window: Long,
        val topLine: Long,
        val bottomLine: Long,
        val currentLine: Long,
        val currentColumn: Long,
        val lineCount: Long?,
        val scrollDelta: Long?,
    ) : NvimUiEvent

    data class WinViewportMargins(
        val grid: Long,
        val window: Long,
        val top: Long,
        val bottom: Long,
        val left: Long,
        val right: Long,
    ) : NvimUiEvent
}

enum class WindowAnchor {
    NW,
    NE,
    SW,
    SE,
}

enum class CursorShape {
    BLOCK,
    HORIZONTAL,
    VERTICAL,
}

// Info about how to render editor mode
data class ModeProperty(
    val cursor_shape: CursorShape?,
    val cell_percentage: Long?,
    val blink_wait: Long?,
    val blink_on: Long?,
    val blink_off: Long?,
    val attr_id: Long?,
    val attr_id_lm: Long?,
    val short_name: String?,
    val name: String?,
)

data class HighlightAttr(
    val foreground: Long? = null,
    val background: Long? = null,
    val special: Long? = null,

    val reverse: Boolean = false,
    val italic: Boolean = false,
    val bold: Boolean = false,
    val strikethrough: Boolean = false,
    val underline: Boolean = false,
    val undercurl: Boolean = false,
    val underdouble: Boolean = false,
    val underdotted: Boolean = false,
    val underdashed: Boolean = false,
    val altfont: Boolean = false,
    val dim: Boolean = false,
    val blink: Boolean = false,
    val conceal: Boolean = false,
    val overline: Boolean = false,

    val blend: Long? = null,
    val url: String? = null,
)

// sent by grid_line
data class TextCell(val text: String, val hl_id: Long?, val repeat: Long?)

/**
 * Neovim encodes Buffer/Window/Tabpage handles as MessagePack extension values
 * We only need numeric value
 */
private fun Any?.asNvimHandle(): Long? = when (this) {
    is Long -> this

    is Int -> toLong()

    is Map<*, *> -> {
        val data = this["__ext_data"] as? ByteArray ?: return null
        runCatching {
            MessagePack.newDefaultUnpacker(data).use { it.unpackLong() }
        }.getOrNull()
    }

    else -> null
}

internal suspend fun dispatchRedrawEvent(
    inbound: SendChannel<NvimNotification>,
    events: List<List<Any?>>,
) {
    fun highlightAttr(attrs: Map<*, *>): HighlightAttr = HighlightAttr(
        foreground = attrs["foreground"] as? Long,
        background = attrs["background"] as? Long,
        special = attrs["special"] as? Long,
        reverse = attrs["reverse"] as? Boolean ?: false,
        italic = attrs["italic"] as? Boolean ?: false,
        bold = attrs["bold"] as? Boolean ?: false,
        strikethrough = attrs["strikethrough"] as? Boolean ?: false,
        underline = attrs["underline"] as? Boolean ?: false,
        undercurl = attrs["undercurl"] as? Boolean ?: false,
        underdouble = attrs["underdouble"] as? Boolean ?: false,
        underdotted = attrs["underdotted"] as? Boolean ?: false,
        underdashed = attrs["underdashed"] as? Boolean ?: false,
        altfont = attrs["altfont"] as? Boolean ?: false,
        dim = attrs["dim"] as? Boolean ?: false,
        blink = attrs["blink"] as? Boolean ?: false,
        conceal = attrs["conceal"] as? Boolean ?: false,
        overline = attrs["overline"] as? Boolean ?: false,
        blend = attrs["blend"] as? Long,
        url = attrs["url"] as? String,
    )

    events.forEach { event ->
        val method = event.firstOrNull() as? String
            ?: return@forEach

        for (rawInvocation in event.drop(1)) {
            val args = rawInvocation as? List<*>
                ?: continue

            when (method) {
                "set_title" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.SetTitle(args[0] as String),
                        ),
                    )
                }

                "set_icon" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.SetIcon(args[0] as String),
                        ),
                    )
                }

                "mode_info_set" -> {
                    val enabled = args[0] as Boolean
                    val modes = (args[1] as List<*>).map { rawMode ->
                        val mode = rawMode as Map<*, *>

                        ModeProperty(
                            cursor_shape = (mode["cursor_shape"] as? String)
                                ?.let {
                                    when (it) {
                                        "block" -> CursorShape.BLOCK
                                        "horizontal" -> CursorShape.HORIZONTAL
                                        "vertical" -> CursorShape.VERTICAL
                                        else -> null
                                    }
                                },
                            cell_percentage = mode["cell_percentage"] as? Long,
                            blink_wait = mode["blinkwait"] as? Long,
                            blink_on = mode["blinkon"] as? Long,
                            blink_off = mode["blinkoff"] as? Long,
                            attr_id = mode["attr_id"] as? Long,
                            attr_id_lm = mode["attr_id_lm"] as? Long,
                            short_name = mode["short_name"] as? String,
                            name = mode["name"] as? String,
                        )
                    }

                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.ModeInfoSet(enabled, modes),
                        ),
                    )
                }

                "option_set" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.OptionSet(
                                args[0] as String,
                                args[1],
                            ),
                        ),
                    )
                }

                "chdir" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.Chdir(args[0] as String),
                        ),
                    )
                }

                "mode_change" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.ModeChange(args[0] as String, args[1] as Long),
                        ),
                    )
                }

                "busy_start" ->
                    inbound.send(NvimNotification.Redraw(NvimUiEvent.BusyStart))

                "busy_stop" ->
                    inbound.send(NvimNotification.Redraw(NvimUiEvent.BusyStop))

                "flush" ->
                    inbound.send(NvimNotification.Redraw(NvimUiEvent.Flush))

                "grid_resize" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.GridResize(
                                args[0] as Long,
                                args[1] as Long,
                                args[2] as Long,
                            ),
                        ),
                    )
                }

                "default_colors_set" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.DefaultColorSet(
                                args[0] as Long,
                                args[1] as Long,
                                args[2] as Long,
                                args[3] as Long,
                                args[4] as Long,
                            ),
                        ),
                    )
                }

                "hl_attr_define" -> {
                    val id = args[0] as Long
                    val rgbAttrs = args[1] as Map<*, *>
                    val ctermAttrs = args[2] as Map<*, *>

                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.HlAttrDefine(
                                id = id,
                                rgb_attr = highlightAttr(rgbAttrs),
                                cterm_attr = highlightAttr(ctermAttrs),
                                info = args[3] as List<Any?>,
                            ),
                        ),
                    )
                }

                "hl_group_set" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.HlGroupSet(
                                args[0] as String,
                                args[1] as Long,
                            ),
                        ),
                    )
                }

                "grid_line" -> {
                    val cells = (args[3] as List<*>).map { rawCell ->
                        val cell = rawCell as List<*>

                        TextCell(
                            text = cell[0] as String,
                            hl_id = cell.getOrNull(1) as? Long,
                            repeat = cell.getOrNull(2) as? Long,
                        )
                    }

                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.GridLine(
                                grid = args[0] as Long,
                                row = args[1] as Long,
                                col_start = args[2] as Long,
                                cells = cells,
                                wrap = args.getOrNull(4) as? Boolean ?: false,
                            ),
                        ),
                    )
                }

                "grid_clear" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.GridClear(args[0] as Long),
                        ),
                    )
                }

                "grid_destroy" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.GridDestroy(args[0] as Long),
                        ),
                    )
                }

                "grid_cursor_goto" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.GridCursorGoto(
                                args[0] as Long,
                                args[1] as Long,
                                args[2] as Long,
                            ),
                        ),
                    )
                }

                "grid_scroll" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.GridScroll(
                                args[0] as Long,
                                args[1] as Long,
                                args[2] as Long,
                                args[3] as Long,
                                args[4] as Long,
                                args[5] as Long,
                                args[6] as Long,
                            ),
                        ),
                    )
                }

                "win_pos" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.WinPos(
                                grid = args[0] as Long,
                                window = args[1].asNvimHandle() ?: 0L,
                                startRow = args[2] as Long,
                                startCol = args[3] as Long,
                                width = args[4] as Long,
                                height = args[5] as Long,
                            ),
                        ),
                    )
                }

                "win_float_pos" -> {
                    val anchor = when (args[2] as? String) {
                        "NE" -> WindowAnchor.NE
                        "SW" -> WindowAnchor.SW
                        "SE" -> WindowAnchor.SE
                        else -> WindowAnchor.NW
                    }
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.WinFloatPos(
                                grid = args[0] as Long,
                                window = args[1].asNvimHandle() ?: 0L,
                                anchor = anchor,
                                anchorGrid = args[3] as Long,
                                anchorRow = (args[4] as Number).toDouble(),
                                anchorCol = (args[5] as Number).toDouble(),
                                mouseEnabled = args.getOrNull(6) as? Boolean ?: true,
                                zIndex = (args.getOrNull(7) as? Number)?.toLong() ?: 0L,
                                compIndex = (args.getOrNull(8) as? Number)?.toLong(),
                                screenRow = (args.getOrNull(9) as? Number)?.toLong(),
                                screenCol = (args.getOrNull(10) as? Number)?.toLong(),
                            ),
                        ),
                    )
                }

                "win_external_pos" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.WinExternalPos(
                                grid = args[0] as Long,
                                window = args[1].asNvimHandle() ?: 0L,
                            ),
                        ),
                    )
                }

                "win_hide" -> {
                    inbound.send(
                        NvimNotification.Redraw(NvimUiEvent.WinHide(args[0] as Long)),
                    )
                }

                "win_close" -> {
                    inbound.send(
                        NvimNotification.Redraw(NvimUiEvent.WinClose(args[0] as Long)),
                    )
                }

                "msg_set_pos" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.MsgSetPos(
                                grid = args[0] as Long,
                                row = args[1] as Long,
                                scrolled = args[2] as? Boolean ?: false,
                                separator = args.getOrNull(3) as? String,
                                zIndex = (args.getOrNull(4) as? Number)?.toLong(),
                                compIndex = (args.getOrNull(5) as? Number)?.toLong(),
                            ),
                        ),
                    )
                }

                "win_viewport" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.WinViewport(
                                grid = args[0] as Long,
                                window = args[1].asNvimHandle() ?: 0L,
                                topLine = args[2] as Long,
                                bottomLine = args[3] as Long,
                                currentLine = args[4] as Long,
                                currentColumn = args[5] as Long,
                                lineCount = (args.getOrNull(6) as? Number)?.toLong(),
                                scrollDelta = (args.getOrNull(7) as? Number)?.toLong(),
                            ),
                        ),
                    )
                }

                "win_viewport_margins" -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.WinViewportMargins(
                                grid = args[0] as Long,
                                window = args[1].asNvimHandle() ?: 0L,
                                top = args[2] as Long,
                                bottom = args[3] as Long,
                                left = args[4] as Long,
                                right = args[5] as Long,
                            ),
                        ),
                    )
                }

                else -> {
                    inbound.send(
                        NvimNotification.Redraw(
                            NvimUiEvent.Unclassified(method, args),
                        ),
                    )
                }
            }
        }
    }
}
