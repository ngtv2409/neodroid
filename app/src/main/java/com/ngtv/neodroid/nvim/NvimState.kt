package com.ngtv.neodroid.nvim

import java.util.ArrayList
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/* Hold neovim UI states for rendering */
data class NvimState(val options: MutableMap<String, Any?> = mutableMapOf()) {
    fun deepCopy(): NvimState = NvimState(
        options = options.mapValuesTo(LinkedHashMap()) { (_, value) ->
            deepCopyValue(value)
        }.toMutableMap(),
    )
}

/**
 * Deep-copies values supported by the MessagePack RPC implementation.
 */
private fun deepCopyValue(value: Any?): Any? = when (value) {
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
