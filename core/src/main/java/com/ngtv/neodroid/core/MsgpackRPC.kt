package com.ngtv.neodroid.core

import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker
import org.msgpack.core.MessageFormat

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
            value.forEach { packAny(it) }
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
            ByteArray(size).also { readPayload(it) }
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

        else -> error("Unsupported MessagePack type: ${getNextFormat()}")
    }
}


fun MessagePacker.packRequest(
    msgid: UInt,
    method: String,
    params: List<Any?>
) {
    packArrayHeader(4)
    packInt(0)
    packLong(msgid.toLong())
    packString(method)
    packAny(params)
}


fun MessageUnpacker.unpackRequest(): RpcRequest {
    check(unpackArrayHeader() == 4) {
        "request expects array of 4 elements"
    }
    check(unpackInt() == 0) {
        "invalid type for request"
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


fun MessagePacker.packRespond(
    msgid: UInt,
    error: Any?,
    result: Any?
) {
    packArrayHeader(4)
    packInt(1)
    packLong(msgid.toLong())
    packAny(error)
    packAny(result)
}


fun MessageUnpacker.unpackResponse(): RpcResponse {
    check(unpackArrayHeader() == 4) {
        "respond expects array of 4 elements"
    }
    check(unpackInt() == 1) {
        "invalid type for respond"
    }

    return RpcResponse(
        msgid = unpackLong().toUInt(),
        error = unpackAny(),
        result = unpackAny()
    )
}


fun MessagePacker.packNotification(
    method: String,
    params: List<Any?>
) {
    packArrayHeader(3)
    packInt(2)
    packString(method)
    packAny(params)
}


fun MessageUnpacker.unpackNotification(): RpcNotification {
    check(unpackArrayHeader() == 3) {
        "notification expects array of 3 elements"
    }
    check(unpackInt() == 2) {
        "invalid type for notification"
    }

    val method = unpackString()
    val params = unpackAny() as? List<*>
        ?: error("invalid notification parameters")

    @Suppress("UNCHECKED_CAST")
    return RpcNotification(
        method = method,
        params = params as List<Any?>
    )
}
