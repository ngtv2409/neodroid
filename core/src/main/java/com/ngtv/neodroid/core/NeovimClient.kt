package com.ngtv.neodroid.core

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
}
