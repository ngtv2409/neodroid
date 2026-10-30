package com.ngtv.neodroid.nvim

import com.ngtv.neodroid.core.events.ModeProperty
import java.util.ArrayList
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/* Hold neovim UI states for rendering */
data class NvimState(
    val options: MutableMap<String, Any?> = mutableMapOf(),
    var modeInfo: List<ModeProperty> = mutableListOf(),
    var cursorStyleEnabled: Boolean = true,
    var modeIndex: Long = 0,
    var isBusy: Boolean = false,
) {
    fun deepCopy(): NvimState = NvimState(
        options = deepCopyValue(options),
        modeInfo = deepCopyValue(modeInfo),
    )
}

/**
 * Deep-copies values supported by the MessagePack RPC implementation.
 */

@Suppress("UNCHECKED_CAST")
internal fun <T> deepCopyValue(value: T): T {
    if (value == null) return null as T
    return internalDeepCopyValue(value) as T
}
private fun internalDeepCopyValue(value: Any?): Any? = when (value) {
    null -> null

    is Boolean,
    is Byte,
    is Short,
    is Int,
    is Long,
    is Float,
    is Double,
    is String,
    -> value

    is ByteArray -> value.copyOf()

    is List<*> -> value.map(::deepCopyValue)

    is Map<*, *> -> value.entries.associateTo(LinkedHashMap()) {
        deepCopyValue(it.key) to deepCopyValue(it.value)
    }

    else -> error(
        "Unsupported type for deep copy: ${value::class.qualifiedName}",
    )
}
