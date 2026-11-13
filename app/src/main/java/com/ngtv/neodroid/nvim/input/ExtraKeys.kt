package com.ngtv.neodroid.nvim.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ngtv.neodroid.nvim.NvimSession

/**
 * One key in the extra keyboard row.
 */
data class ExtraKey(val id: String, val label: String, val action: ExtraKeyAction)

enum class ExtraKeyModifier(val nvimPrefix: String) {
    Ctrl("C"),
    Alt("M"),
    Shift("S"),
}

sealed interface ExtraKeyAction {
    /** Sends a Neovim key notation string, for example `<Esc>` or `<C-c>`. */
    data class SendKeys(val keys: String) : ExtraKeyAction

    /** Sticky modifier toggled by tapping the key. */
    data class Modifier(val modifier: ExtraKeyModifier) : ExtraKeyAction
}

@Stable
class ExtraKeysState {
    var activeModifiers by mutableStateOf<Set<ExtraKeyModifier>>(emptySet())
        private set

    fun toggle(modifier: ExtraKeyModifier) {
        activeModifiers =
            if (modifier in activeModifiers) {
                activeModifiers - modifier
            } else {
                activeModifiers + modifier
            }
    }

    fun clearModifiers() {
        activeModifiers = emptySet()
    }

    /**
     * Applies the active sticky modifiers to the first key in [keys] and clears
     * them. The remainder of the input is sent unchanged.
     *
     * TODO: maybe an option to keep modifier state after applied?
     */
    fun applyToInput(keys: String): String {
        if (keys.isEmpty() || activeModifiers.isEmpty()) return keys

        val (firstKey, remainder) = keys.splitFirstKey()
        val combined = combineKey(firstKey, activeModifiers)
        clearModifiers()
        return combined + remainder
    }

    fun consume(action: ExtraKeyAction): ExtraKeyAction = when (action) {
        is ExtraKeyAction.SendKeys -> ExtraKeyAction.SendKeys(applyToInput(action.keys))
        is ExtraKeyAction.Modifier -> action
    }
}

@Composable
fun rememberExtraKeysState(): ExtraKeysState = remember { ExtraKeysState() }

object DefaultExtraKeys {
    val keys: List<ExtraKey> = listOf(
        ExtraKey("ctrl", "Ctrl", ExtraKeyAction.Modifier(ExtraKeyModifier.Ctrl)),
        ExtraKey("alt", "Alt", ExtraKeyAction.Modifier(ExtraKeyModifier.Alt)),
        ExtraKey("shift", "Shift", ExtraKeyAction.Modifier(ExtraKeyModifier.Shift)),
        ExtraKey("escape", "Esc", ExtraKeyAction.SendKeys("<Esc>")),
        // Raw tab is more portable than <Tab> across Android IMEs.
        ExtraKey("tab", "Tab", ExtraKeyAction.SendKeys("\t")),
        ExtraKey("enter", "Enter", ExtraKeyAction.SendKeys("<CR>")),
        ExtraKey("slash", "/", ExtraKeyAction.SendKeys("/")),
        ExtraKey("dash", "-", ExtraKeyAction.SendKeys("-")),
        ExtraKey("pipe", "|", ExtraKeyAction.SendKeys("|")),
        ExtraKey("left", "←", ExtraKeyAction.SendKeys("<Left>")),
        ExtraKey("down", "↓", ExtraKeyAction.SendKeys("<Down>")),
        ExtraKey("up", "↑", ExtraKeyAction.SendKeys("<Up>")),
        ExtraKey("right", "→", ExtraKeyAction.SendKeys("<Right>")),
    )
}

private fun String.splitFirstKey(): Pair<String, String> {
    if (startsWith('<')) {
        val end = indexOf('>', startIndex = 1)
        if (end >= 0) {
            return substring(0, end + 1) to substring(end + 1)
        }
    }

    if (isEmpty()) return "" to ""

    val firstEnd = offsetByCodePoints(0, 1)
    return substring(0, firstEnd) to substring(firstEnd)
}

private fun combineKey(key: String, modifiers: Set<ExtraKeyModifier>): String {
    val names = modifiers.mapTo(mutableSetOf()) { it.nvimPrefix }

    val inner = if (key.length >= 2 && key.startsWith("<") && key.endsWith(">")) {
        key.substring(1, key.length - 1)
    } else {
        key.toNvimKeyName()
    }

    var name = inner
    while (name.length >= 2 && name[1] == '-') {
        names += when (name[0]) {
            'C' -> "C"
            'M', 'A' -> "M"
            'S' -> "S"
            else -> break
        }
        name = name.substring(2)
    }

    val prefix = listOf("C", "M", "S")
        .filter { it in names }
        .joinToString(separator = "") { "$it-" }

    return "<$prefix$name>"
}

private fun String.toNvimKeyName(): String = when (this) {
    "\t" -> "Tab"
    "\n", "\r" -> "CR"
    "\u001b" -> "Esc"
    " " -> "Space"
    "<" -> "lt"
    else -> this
}
