package com.ngtv.neodroid.core

import org.msgpack.core.MessageFormat
import org.msgpack.core.MessagePacker
import org.msgpack.core.MessageUnpacker
import java.util.ArrayList

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

data class RpcRequest(val msgid: UInt, val method: String, val params: List<Any?>) : RpcMessage

data class RpcResponse(val msgid: UInt, val error: Any?, val result: Any?) : RpcMessage

data class RpcNotification(val method: String, val params: List<Any?>) : RpcMessage

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

private fun MessageUnpacker.unpackAny(): Any? = when (getNextFormat()) {
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
    MessageFormat.INT64,
    -> unpackLong()

    MessageFormat.FLOAT32,
    MessageFormat.FLOAT64,
    -> unpackDouble()

    MessageFormat.FIXSTR,
    MessageFormat.STR8,
    MessageFormat.STR16,
    MessageFormat.STR32,
    -> unpackString()

    MessageFormat.BIN8,
    MessageFormat.BIN16,
    MessageFormat.BIN32,
    -> {
        val size = unpackBinaryHeader()
        ByteArray(size).also {
            readPayload(it)
        }
    }

    MessageFormat.FIXARRAY,
    MessageFormat.ARRAY16,
    MessageFormat.ARRAY32,
    -> {
        val size = unpackArrayHeader()
        List(size) {
            unpackAny()
        }
    }

    MessageFormat.FIXMAP,
    MessageFormat.MAP16,
    MessageFormat.MAP32,
    -> {
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
    MessageFormat.FIXEXT16,
    -> {
        val header = unpackExtensionTypeHeader()
        val type = header.type
        val size = header.length

        val data = ByteArray(size)
        readPayload(data)

        mapOf(
            "__ext_type" to type,
            "__ext_data" to data,
        )
    }

    else -> error(
        "Unsupported MessagePack type: ${getNextFormat()}",
    )
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

class RpcIOException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class RpcFormatException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

fun MessageUnpacker.unpackRpcMessage(): RpcMessage {
    if (!hasNext()) {
        // got EOF. Aka the socket is closed, or the stream is a file
        throw RpcIOException("end-of-file reached")
    }
    val format = getNextFormat()

    if (
        format != MessageFormat.FIXARRAY &&
        format != MessageFormat.ARRAY16 &&
        format != MessageFormat.ARRAY32
    ) {
        throw RpcFormatException("expect message to be array. Got: msgpack.${format.name}")
    }

    val size = unpackArrayHeader()
    // pre validation to avoid reading an array too big
    if (size != 3 && size != 4) {
        // skip the entire array
        skipValue(size)
        throw RpcFormatException("invalid message size: $size. Should be 3 or 4")
    }
    // read the whole array
    val type = unpackAny() as? Long
        ?: throw RpcFormatException("message type must be an integer")

    val fields = ArrayList<Any?>(size)
    fields.add(type)

    repeat(size - 1) {
        fields.add(unpackAny())
    }

    return decodeRpcFields(fields)
}

private fun decodeRpcFields(message: ArrayList<Any?>): RpcMessage {
    require(message.isNotEmpty())
    require(message[0] is Long)
    val type = (message[0] as? Long)
        ?.takeIf { it in 0L..2L }
        ?.toInt()
        ?: throw RpcFormatException("Invalid message type: ${message[0]}")
    when (type) {
        0 -> {
            if (message.size != 4) {
                throw RpcFormatException("message type 'request' must has length 4")
            }
            if (
                message[1] is Long && message[2] is String && message[3] is List<*>
            ) {
                return RpcRequest(
                    (message[1] as Long).toUInt(),
                    message[2] as String,
                    message[3] as List<Any?>,
                )
            } else {
                throw RpcFormatException(
                    "message type 'request' must have the form [0, int32, String, Array<Any?>]. Got: $message",
                )
            }
        }

        1 -> {
            if (message.size != 4) {
                throw RpcFormatException("message type 'response' must has length 4")
            }
            if (
                message[1] is Long
            ) {
                return RpcResponse((message[1] as Long).toUInt(), message[2], message[3])
            } else {
                throw RpcFormatException(
                    "message type 'response' must have the form [1, int32, Any?, Any?]. Got: $message",
                )
            }
        }

        2 -> {
            if (message.size != 3) {
                throw RpcFormatException("message type 'notification' must has length 3")
            }
            if (
                message[1] is String && message[2] is List<*>
            ) {
                return RpcNotification(message[1] as String, message[2] as List<Any?>)
            } else {
                throw RpcFormatException(
                    "message type 'notification' must have the form [2, String, Array<Any?>]. Got: $message",
                )
            }
        }

        else -> {
            error("unreachable")
        }
    }
}
