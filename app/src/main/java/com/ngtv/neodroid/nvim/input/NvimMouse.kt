package com.ngtv.neodroid.nvim.input

data class NvimGridHit(val grid: Long, val row: Int, val col: Int, val url: String?)

data class NvimMouseEvent(
    val button: NvimMouseButton,
    val action: NvimMouseAction,
    val modifier: String = "",
    val grid: Int = 0,
    val row: Int,
    val col: Int,
)

enum class NvimMouseButton(val nvimName: String) {
    Left("left"),
    Right("right"),
    Middle("middle"),
    Wheel("wheel"),
    Move("move"),
    X1("x1"),
    X2("x2"),
}

enum class NvimMouseAction(val nvimName: String) {
    Press("press"),
    Drag("drag"),
    Release("release"),
    ScrollUp("up"),
    ScrollDown("down"),
    ScrollLeft("left"),
    ScrollRight("right"),
    None(""),
}
