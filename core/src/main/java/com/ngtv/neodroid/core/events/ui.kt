package com.ngtv.neodroid.core

// https://neovim.io/doc/user/api-ui-events/#_global-events
// only implement the significant subsets
sealed interface NvimUiEvent {
    // implicitly ignored events
    data class Unclassified(val raw: RpcNotification): NvimUiEvent

    // Global events
    data class SetTitle(val title: String): NvimUiEvent
    data class SetIcon(val icon: String): NvimUiEvent
    data class ModeInfoSet(val cursor_style_enabled: Boolean, val mode_info: List<ModeProperty>): NvimUiEvent
    data class OptionSet(val name: String, val value: Any?): NvimUiEvent
    data class Chdir(val path: String): NvimUiEvent
    data class BusyStart(): NvimUiEvent
    data class BusyStop(): NvimUiEvent
    // TODO: more thought
    // ignore Connect and Restart since we'd be listening on stdio over ssh
    data class Flush(): NvimUiEvent

    // Grid events - Line based
    data class GridResize(val grid: Long, val width: Long, val height: Long): NvimUiEvent
    data class DefaultColorSet(val rgb_fg: Long, val rgb_bg: Long, val rgb_sp: Long, val cterm_fg: Long, cterm_bg: Long): NvimUiEvent
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
    val id: Int,

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

    val blend: Int? = null,
    val url: String? = null
)

// sent by grid_line
data class TextCell (
    val text: String,
    val hl_id: Long?,
    val repeat: Long?
)
