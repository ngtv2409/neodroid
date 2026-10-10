package com.ngtv.neodroid.core

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.ArrayList

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker

class NvimAPIError(
    val method: String,
    val errorType: Long,
    message: String,
    cause: Throwable? = null
) : RuntimeException(
    "[$method] E$errorType: $message",
    cause
)

sealed interface NvimResult<out T> {
    data class Ok<T>(val value: T) : NvimResult<T>
    data class Err(val error: NvimAPIError) : NvimResult<Nothing>
}

fun <T> NvimResult<T>.Success(): Boolean {
    return when (this) {
        is NvimResult.Ok -> true
        is NvimResult.Err -> false
    }
}
fun <T> NvimResult<T>.Error(): Boolean {
    return when (this) {
        is NvimResult.Ok -> false
        is NvimResult.Err -> true
    }
}
fun <T> NvimResult<T>.getOptional(): T? {
    return when (this) {
        is NvimResult.Ok -> value
        is NvimResult.Err -> null
    }
}
fun <T> NvimResult<T>.getOrThrow(): T =
    when (this) {
        is NvimResult.Ok -> value
        is NvimResult.Err -> throw error
    }
fun <T> NvimResult<T>.getOrElse(fallback: (NvimAPIError) -> T): T =
    when (this) {
        is NvimResult.Ok -> value
        is NvimResult.Err -> fallback(error)
    }

class NeovimClient(
    private val input: InputStream,
    // TODO: output may be unsafe for concurrent writes. Mutex?
    private val output: OutputStream,
    private val scope: CoroutineScope
) {
    private val packer = MessagePack.newDefaultPacker(output)
    private var MessageId = AtomicInteger(0)
    private val pending =
        ConcurrentHashMap<UInt, CompletableDeferred<RpcResponse>>()
    private val _notifications = MutableSharedFlow<RpcNotification>()
    val notifications: Flow<RpcNotification> = _notifications.asSharedFlow()
    // reader to complete promises
    private val readerJob = scope.launch(Dispatchers.IO) {
        MessagePack.newDefaultUnpacker(input).use { unpacker ->
            while (currentCoroutineContext().isActive) {
                when (val message = unpacker.unpackRpcMessage()) {
                    is RpcResponse -> {
                        pending.remove(message.msgid)?.complete(message)
                    }
                    is RpcNotification -> {
                        // handle notification
                        _notifications.emit(message)
                    }
                    is RpcRequest -> {
                        // handle request
                        error("not implemented")
                    }
                }
            }
        }
    }

    // send a request
    fun send_request(method_name: String, params: List<Any?>)
        : CompletableDeferred<RpcResponse> {
        val id = MessageId.getAndIncrement().toUInt()
        val deferred = CompletableDeferred<RpcResponse>()
        pending[id] = deferred
        packer.packRpcMessage(RpcRequest(id, method_name, params))
        packer.flush()
        return deferred
    }

    // A not so useful function that returns a raw response
    suspend fun call(method: String, params: List<Any?>): RpcResponse {
        return send_request(method, params).await()
    }

    suspend fun ui_attach(width: Int, height: Int, options: Map<String, Any?>): NvimResult<Unit> {
        val response = call("nvim_ui_attach", listOf(width, height, options))
        if (response.error != null) {
            return NvimResult.Err(NvimAPIError("ui_attach",
                (response.error as? ArrayList<Any?>)?.get(0) as Long,
                (response.error as? ArrayList<Any?>)?.get(1) as String))
        }
        return NvimResult.Ok(Unit)
    }
}
