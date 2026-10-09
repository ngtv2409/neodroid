package com.ngtv.neodroid.core

import org.msgpack.core.MessageFormat
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker

/*
 * MessagePack-RPC Protocol
 *
 * Request:
 * [0, msgid, method, params]
 *
 * Response:
 * [1, msgid, error, result]
 *
 * Notification:
 * [2, method, params]
 */

sealed interface RpcMessage

data class RpcRequest(
    val msgid: UInt,
    val method: String,
    val params: List<Any?>
) : RpcMessage

data class RpcResponse(
    val msgid: UInt,
    val error: Any?,
    val result: Any?
) : RpcMessage

data class RpcNotification(
    val method: String,
    val params: List<Any?>
) : RpcMessage


private fun MessagePacker.packAny(value: Any?) {
    when (value) {
        null -> packNil()
        is Boolean -> packBoolean(value)
        is Byte -> packByte(value)
        is Short -> packShort(value)
        is Int -> packInt(value)
        is Long -> packLong(value)
        is Float -> packFloat(value)
        is Double -> packDouble(value)
        is String -> packString(value)

        is ByteArray -> {
            packBinaryHeader(value.size)
            writePayload(value)
        }

        is List<*> -> {
            packArrayHeader(value.size)
            value.forEach {
                packAny(it)
            }
        }

        is Map<*, *> -> {
            packMapHeader(value.size)
            value.forEach { (key, value) ->
                packAny(key)
                packAny(value)
            }
        }

        else -> error("Unsupported type: ${value::class}")
    }
}


private fun MessageUnpacker.unpackAny(): Any? {
    return when (getNextFormat()) {
        MessageFormat.NIL -> {
            unpackNil()
            null
        }

        MessageFormat.BOOLEAN -> unpackBoolean()

        MessageFormat.POSFIXINT,
        MessageFormat.NEGFIXINT,
        MessageFormat.UINT8,
        MessageFormat.UINT16,
        MessageFormat.UINT32,
        MessageFormat.UINT64,
        MessageFormat.INT8,
        MessageFormat.INT16,
        MessageFormat.INT32,
        MessageFormat.INT64 -> unpackLong()

        MessageFormat.FLOAT32,
        MessageFormat.FLOAT64 -> unpackDouble()

        MessageFormat.FIXSTR,
        MessageFormat.STR8,
        MessageFormat.STR16,
        MessageFormat.STR32 -> unpackString()

        MessageFormat.BIN8,
        MessageFormat.BIN16,
        MessageFormat.BIN32 -> {
            val size = unpackBinaryHeader()
            ByteArray(size).also {
                readPayload(it)
            }
        }

        MessageFormat.FIXARRAY,
        MessageFormat.ARRAY16,
        MessageFormat.ARRAY32 -> {
            val size = unpackArrayHeader()
            List(size) {
                unpackAny()
            }
        }

        MessageFormat.FIXMAP,
        MessageFormat.MAP16,
        MessageFormat.MAP32 -> {
            val size = unpackMapHeader()
            buildMap {
                repeat(size) {
                    val key = unpackAny()
                    val value = unpackAny()
                    put(key, value)
                }
            }
        }

        MessageFormat.EXT8,
        MessageFormat.EXT16,
        MessageFormat.EXT32,
        MessageFormat.FIXEXT1,
        MessageFormat.FIXEXT2,
        MessageFormat.FIXEXT4,
        MessageFormat.FIXEXT8,
        MessageFormat.FIXEXT16 -> {
            val header = unpackExtensionTypeHeader()
            val type = header.type
            val size = header.length

            val data = ByteArray(size)
            readPayload(data)

            mapOf(
                "__ext_type" to type,
                "__ext_data" to data
            )
        }

        else -> error(
            "Unsupported MessagePack type: ${getNextFormat()}"
        )
    }
}


private const val REQUEST = 0
private const val RESPONSE = 1
private const val NOTIFICATION = 2


fun MessagePacker.packRpcMessage(message: RpcMessage) {
    when (message) {
        is RpcRequest -> {
            packArrayHeader(4)
            packInt(REQUEST)

            packLong(message.msgid.toLong())
            packString(message.method)
            packAny(message.params)
        }

        is RpcResponse -> {
            packArrayHeader(4)
            packInt(RESPONSE)

            packLong(message.msgid.toLong())
            packAny(message.error)
            packAny(message.result)
        }

        is RpcNotification -> {
            packArrayHeader(3)
            packInt(NOTIFICATION)

            packString(message.method)
            packAny(message.params)
        }
    }
}

fun MessageUnpacker.unpackRpcMessage(): RpcMessage {
    val size = unpackArrayHeader()
    check(size >= 1) {
        "RPC message array cannot be empty"
    }

    return when (unpackInt()) {
        REQUEST ->
            unpackRequest(size)

        RESPONSE ->
            unpackResponse(size)

        NOTIFICATION ->
            unpackNotification(size)

        else ->
            error("Invalid RPC message type")
    }
}


private fun MessageUnpacker.unpackRequest(size: Int): RpcRequest {
    check(size == 4) {
        "request expects array of 4 elements, got $size"
    }

    val msgid = unpackLong().toUInt()
    val method = unpackString()

    val params = unpackAny() as? List<*>
        ?: error("Invalid request parameters")

    @Suppress("UNCHECKED_CAST")
    return RpcRequest(
        msgid = msgid,
        method = method,
        params = params as List<Any?>
    )
}


private fun MessageUnpacker.unpackResponse(size: Int): RpcResponse {
    check(size == 4) {
        "response expects array of 4 elements, got $size"
    }

    return RpcResponse(
        msgid = unpackLong().toUInt(),
        error = unpackAny(),
        result = unpackAny()
    )
}


private fun MessageUnpacker.unpackNotification(size: Int): RpcNotification {
    check(size == 3) {
        "notification expects array of 3 elements, got $size"
    }

    val method = unpackString()

    val params = unpackAny() as? List<*>
        ?: error("Invalid notification parameters")

    @Suppress("UNCHECKED_CAST")
    return RpcNotification(
        method = method,
        params = params as List<Any?>
    )
}
