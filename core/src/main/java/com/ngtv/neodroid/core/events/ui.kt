@file:Suppress("PropertyName")
package com.ngtv.neodroid.core.events

// https://neovim.io/doc/user/api-ui-events/#_global-events
// only implement the significant subsets
sealed interface NvimUiEvent {
    // implicitly ignored events
    data class Unclassified(val method: String, val params: List<Any?>): NvimUiEvent

    // Global events
    data class SetTitle(val title: String): NvimUiEvent
    data class SetIcon(val icon: String): NvimUiEvent
    data class ModeInfoSet(val cursor_style_enabled: Boolean, val mode_info: List<ModeProperty>): NvimUiEvent
    data class OptionSet(val name: String, val value: Any?): NvimUiEvent
    data class Chdir(val path: String): NvimUiEvent
    data class ModeChange(val mode: String, val mode_idx: Long): NvimUiEvent
    data object BusyStart: NvimUiEvent {
        override fun toString() = "BusyStart()"
    }
    data object BusyStop: NvimUiEvent {
        override fun toString() = "BusyStop()"
    }
    // TODO: more thought
    // ignore Connect and Restart since we'd be listening on stdio over ssh
    data object Flush: NvimUiEvent {
        override fun toString() = "Flush()"
    }

    // Grid events - Line based
    data class GridResize(val grid: Long, val width: Long, val height: Long): NvimUiEvent
    data class DefaultColorSet(val rgb_fg: Long, val rgb_bg: Long, val rgb_sp: Long, val cterm_fg: Long, val cterm_bg: Long): NvimUiEvent
    data class HlAttrDefine(val id: Long, val rgb_attr: HighlightAttr, val cterm_attr: HighlightAttr, val info: List<Any?>): NvimUiEvent
    data class HlGroupSet(val name: String, val id: Long): NvimUiEvent
    data class GridLine(val grid: Long, val row: Long, val col_start: Long, val cells: List<TextCell>, val wrap: Boolean): NvimUiEvent
    data class GridClear(val grid: Long): NvimUiEvent
    data class GridDestroy(val grid: Long): NvimUiEvent
    data class GridCursorGoto(val grid: Long, val row: Long, val col: Long): NvimUiEvent
    data class GridScroll(val grid: Long, val top: Long, val bot: Long, val left: Long, val right: Long, val rows: Long, val cols: Long): NvimUiEvent
}

enum class CursorShape {
    BLOCK, HORIZONTAL, VERTICAL
}
// Info about how to render editor mode
data class ModeProperty (
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
    val url: String? = null
)

// sent by grid_line
data class TextCell (
    val text: String,
    val hl_id: Long?,
    val repeat: Long?
)

internal fun dispatchRedrawEvent(
    events: List<List<Any?>>
): List<NvimUiEvent> {
    val result = mutableListOf<NvimUiEvent>()

    fun highlightAttr(
        attrs: Map<*, *>
    ): HighlightAttr {
        return HighlightAttr(
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
            url = attrs["url"] as? String
        )
    }

    events.forEach { event ->
        val method = event.firstOrNull() as? String
            ?: return@forEach

        for (rawInvocation in event.drop(1)) {
            val args = rawInvocation as? List<*>
                ?: continue

            when (method) {
                "set_title" -> {
                    result.add(
                        NvimUiEvent.SetTitle(args[0] as String)
                    )
                }

                "set_icon" -> {
                    result.add(
                        NvimUiEvent.SetIcon(args[0] as String)
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
                            name = mode["name"] as? String
                        )
                    }

                    result.add(
                        NvimUiEvent.ModeInfoSet(enabled, modes)
                    )
                }

                "option_set" -> {
                    result.add(
                        NvimUiEvent.OptionSet(
                            args[0] as String,
                            args[1]
                        )
                    )
                }

                "chdir" -> {
                    result.add(
                        NvimUiEvent.Chdir(args[0] as String)
                    )
                }

                "mode_change" -> {
                    result.add(
                        NvimUiEvent.ModeChange(args[0] as String, args[1] as Long)
                    )
                }

                "busy_start" ->
                    result.add(NvimUiEvent.BusyStart)

                "busy_stop" ->
                    result.add(NvimUiEvent.BusyStop)

                "flush" ->
                    result.add(NvimUiEvent.Flush)

                "grid_resize" -> {
                    result.add(
                        NvimUiEvent.GridResize(
                            args[0] as Long,
                            args[1] as Long,
                            args[2] as Long
                        )
                    )
                }

                "default_colors_set" -> {
                    result.add(
                        NvimUiEvent.DefaultColorSet(
                            args[0] as Long,
                            args[1] as Long,
                            args[2] as Long,
                            args[3] as Long,
                            args[4] as Long
                        )
                    )
                }

                "hl_attr_define" -> {
                    val id = args[0] as Long
                    val rgbAttrs = args[1] as Map<*, *>
                    val ctermAttrs = args[2] as Map<*, *>

                    result.add(
                        NvimUiEvent.HlAttrDefine(
                            id = id,
                            rgb_attr = highlightAttr(rgbAttrs),
                            cterm_attr = highlightAttr(ctermAttrs),
                            info = args[3] as List<Any?>
                        )
                    )
                }

                "hl_group_set" -> {
                    result.add(
                        NvimUiEvent.HlGroupSet(
                            args[0] as String,
                            args[1] as Long
                        )
                    )
                }

                "grid_line" -> {
                    val cells = (args[3] as List<*>).map { rawCell ->
                        val cell = rawCell as List<*>

                        TextCell(
                            text = cell[0] as String,
                            hl_id = cell.getOrNull(1) as? Long,
                            repeat = cell.getOrNull(2) as? Long
                        )
                    }

                    result.add(
                        NvimUiEvent.GridLine(
                            grid = args[0] as Long,
                            row = args[1] as Long,
                            col_start = args[2] as Long,
                            cells = cells,
                            wrap = args.getOrNull(4) as? Boolean ?: false
                        )
                    )
                }

                "grid_clear" -> {
                    result.add(
                        NvimUiEvent.GridClear(args[0] as Long)
                    )
                }

                "grid_destroy" -> {
                    result.add(
                        NvimUiEvent.GridDestroy(args[0] as Long)
                    )
                }

                "grid_cursor_goto" -> {
                    result.add(
                        NvimUiEvent.GridCursorGoto(
                            args[0] as Long,
                            args[1] as Long,
                            args[2] as Long
                        )
                    )
                }

                "grid_scroll" -> {
                    result.add(
                        NvimUiEvent.GridScroll(
                            args[0] as Long,
                            args[1] as Long,
                            args[2] as Long,
                            args[3] as Long,
                            args[4] as Long,
                            args[5] as Long,
                            args[6] as Long
                        )
                    )
                }

                else -> {
                    result.add(
                        NvimUiEvent.Unclassified(method, args)
                    )
                }
            }
        }
    }

    return result
}
