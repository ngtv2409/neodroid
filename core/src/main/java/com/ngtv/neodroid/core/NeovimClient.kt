@file:Suppress("FunctionName")

package com.ngtv.neodroid.core

import com.ngtv.neodroid.core.events.NvimUiEvent
import com.ngtv.neodroid.core.events.dispatchRedrawEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NvimException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class NvimAPIException(method: String, errorType: Long, message: String, cause: Throwable? = null) :
    RuntimeException(
        "[$method] E$errorType: $message",
        cause,
    )

class NvimConnectionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

sealed interface NvimNotification {
    data class Redraw(val events: List<NvimUiEvent>) : NvimNotification
    data class Unclassified(val raw: RpcNotification) : NvimNotification
}

class NeovimClient(
    input: InputStream,
    // TODO: output may be unsafe for concurrent writes. Mutex?
    output: OutputStream,
    parentScope: CoroutineScope,
) : Closeable {

    private val closed = AtomicBoolean(false)

    // prevent packer and unpacker from closing the streams
    private class NonClosingOutputStream(out: OutputStream) : java.io.FilterOutputStream(out) {
        override fun close() {
            flush()
        }
    }

    private class NonClosingInputStream(input: InputStream) : java.io.FilterInputStream(input) {
        override fun close() {}
    }

    private val clientJob = SupervisorJob(parentScope.coroutineContext[Job])
    val scope = CoroutineScope(parentScope.coroutineContext + clientJob)

    private val packer = MessagePack.newDefaultPacker(NonClosingOutputStream(output))
    private var messageId = AtomicInteger(0)
    private val pending =
        ConcurrentHashMap<UInt, CompletableDeferred<RpcResponse>>()
    private val _notifications = MutableSharedFlow<NvimNotification>()
    val notifications: Flow<NvimNotification> = _notifications.asSharedFlow()

    // reader to complete promises
    private val readerJob = scope.launch(Dispatchers.IO) {
        MessagePack.newDefaultUnpacker(NonClosingInputStream(input)).use { unpacker ->
            while (currentCoroutineContext().isActive) {
                val message = try {
                    unpacker.unpackRpcMessage()
                } catch (e: RpcFormatException) {
                    // discard
                    // TODO: log
                } catch (e: RpcIOException) {
                    if (closed.get()) {
                        break
                    }
                    throw NvimConnectionException("input stream closed or EOF")
                }
                when (message) {
                    is RpcResponse -> {
                        pending.remove(message.msgid)?.complete(message)
                    }

                    is RpcNotification -> {
                        val notif = when (message.method) {
                            "redraw" -> {
                                asNestedList(message.params)?.let { params ->
                                    NvimNotification.Redraw(dispatchRedrawEvent(params))
                                }
                            }

                            else -> {
                                NvimNotification.Unclassified(message)
                            }
                        }
                        if (notif != null) {
                            _notifications.emit(notif)
                        }
                    }

                    is RpcRequest -> {
                        // handle request
                        error("not implemented")
                    }
                }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        clientJob.cancel()
        pending.values.forEach { it.cancel() }
        pending.clear()

        runCatching { packer.close() }
    }

    // send a request
    fun sendRequest(method: String, params: List<Any?>): CompletableDeferred<RpcResponse> {
        val id = messageId.getAndIncrement().toUInt()
        val deferred = CompletableDeferred<RpcResponse>()
        pending[id] = deferred
        packer.packRpcMessage(RpcRequest(id, method, params))
        packer.flush()
        return deferred
    }

    // A not so useful function that returns a raw response
    suspend fun call(method: String, params: List<Any?>): RpcResponse =
        sendRequest(method, params).await()

    suspend fun ui_attach(width: Int, height: Int, options: Map<String, Any?>) {
        val response = call("nvim_ui_attach", listOf(width, height, options))
        if (response.error != null) {
            throw NvimAPIException(
                "ui_attach",
                (response.error as? List<Any?>)?.get(0) as Long,
                response.error[1] as String,
            )
        }
    }
}

private fun asNestedList(value: Any?): List<List<*>>? {
    if (value !is List<*>) return null
    if (!value.all { it is List<*> }) return null

    return value.filterIsInstance<List<*>>()
}
