@file:Suppress("FunctionName")

package com.ngtv.neodroid.core

import com.ngtv.neodroid.core.events.NvimUiEvent
import com.ngtv.neodroid.core.events.dispatchRedrawEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.msgpack.core.MessagePack
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

class NvimException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class NvimError(error: List<Any?>) {
    val errorType = error[0] as Long
    val message = error[1] as String
}
class NvimAPIException(method: String, val error: NvimError, cause: Throwable? = null) :
    RuntimeException("[$method] E${error.errorType}: ${error.message}", cause)

class NvimConnectionException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

sealed interface NvimNotification {
    data class Redraw(val event: NvimUiEvent) : NvimNotification
    data class Unclassified(val raw: RpcNotification) : NvimNotification
}

fun interface RpcRequestHandler {
    suspend fun handle(request: RpcRequest): RpcReply
}

sealed interface RpcReply {
    data class Result(val value: Any?) : RpcReply
    data class Error(val type: Long, val message: String) : RpcReply
}

/*
 * Communicate with Neovim over IO streams
 *
 * Capabilities:
 * - Send requests and notifications to neovim
 * - Receive responses and notifications from neovim
 * - Expose typed variants for easy use in kotlin
 *
 * Uses a reader job to continuously resolve responses and notifs
 */
class NeovimClient(inputStream: InputStream, output: OutputStream, parentScope: CoroutineScope) :
    Closeable {

    private val closed = AtomicBoolean(false)
    private val _failure = MutableStateFlow<Throwable?>(null)
    val failure: StateFlow<Throwable?> = _failure.asStateFlow()

    private val clientJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            failConnection(
                NvimConnectionException("Neovim background task failed", throwable),
            )
        }
    }
    val scope = CoroutineScope(parentScope.coroutineContext + clientJob + exceptionHandler)

    val inbound = Channel<NvimNotification>(capacity = 256)
    private val outbound = Channel<RpcMessage>(capacity = 64)
    private val requests = Channel<RpcRequest>(capacity = 16)

    private val pending = ConcurrentHashMap<UInt, CompletableDeferred<RpcResponse>>()
    private val nextId = AtomicInteger(0)

    private val packer = MessagePack.newDefaultPacker(NonClosingOutputStream(output))

    private val readerJob = scope.launch(Dispatchers.IO) {
        readerLoop(inputStream)
    }
    private val writerJob = scope.launch(Dispatchers.IO) {
        writerLoop()
    }
    private val requestJob = scope.launch(Dispatchers.IO) {
        requestLoop()
    }

    private var requestHandler: RpcRequestHandler? = null

    suspend fun call(method: String, params: List<Any?>, timeoutMs: Duration? = null): Any? {
        if (closed.get()) throw NvimConnectionException("Neovim client is closed")

        val id = nextId.getAndIncrement().toUInt()
        val deferred = CompletableDeferred<RpcResponse>()
        pending[id] = deferred

        try {
            outbound.send(RpcRequest(id, method, params))
            val response = if (timeoutMs == null) {
                deferred.await()
            } else {
                withTimeout(timeoutMs) { deferred.await() }
            }

            val error = response.error
            if (error != null) throw NvimAPIException(method, NvimError(error as List<Any?>))
            return response.result
        } finally {
            pending.remove(id)
        }
    }

    suspend fun notify(method: String, params: List<Any?>) {
        if (closed.get()) throw NvimConnectionException("Neovim client is closed")
        outbound.send(RpcNotification(method, params))
    }

    suspend fun ui_attach(width: Int, height: Int, options: Map<String, Any?>) {
        call("nvim_ui_attach", listOf(width, height, options))
    }

    suspend fun ui_try_resize(width: Int, height: Int) {
        call("nvim_ui_try_resize", listOf(width, height))
    }

    suspend fun inputMouse(
        button: String,
        action: String,
        modifier: String,
        grid: Int,
        row: Int,
        col: Int,
    ) {
        notify(
            "nvim_input_mouse",
            listOf(button, action, modifier, grid, row, col),
        )
    }

    fun setRequestHandler(handler: RpcRequestHandler) {
        requestHandler = handler
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        pending.values.forEach { it.cancel() }
        pending.clear()

        requests.close()
        outbound.close()
        inbound.close()
        clientJob.cancel()
    }

    private class NonClosingOutputStream(out: OutputStream) : java.io.FilterOutputStream(out) {
        override fun close() {
            flush()
        }
    }

    private class NonClosingInputStream(input: InputStream) :
        java.io.FilterInputStream(input) {
        override fun close() {}
    }

    private fun failConnection(cause: Throwable) {
        // The first terminal transition wins. A concurrent close()/failure must not
        // resurrect the connection or report an error after an intentional shutdown.
        if (!closed.compareAndSet(false, true)) return

        _failure.value = cause

        pending.values.forEach { it.completeExceptionally(cause) }
        pending.clear()

        requests.close()
        outbound.close()
        inbound.close(cause)
        clientJob.cancel()
    }

    private suspend fun readerLoop(input: InputStream) {
        try {
            MessagePack.newDefaultUnpacker(NonClosingInputStream(input)).use { unpacker ->
                while (currentCoroutineContext().isActive) {
                    val message = try {
                        unpacker.unpackRpcMessage()
                    } catch (e: RpcIOException) {
                        if (closed.get()) return
                        throw NvimConnectionException(
                            "Neovim exited or the connection was closed",
                            e,
                        )
                    } catch (e: RpcFormatException) {
                        throw NvimConnectionException("malformed RPC message", e)
                    }

                    when (message) {
                        is RpcResponse -> pending.remove(message.msgid)?.complete(message)
                        is RpcNotification -> enqueueNotification(message)
                        is RpcRequest -> enqueueRequest(message)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NvimConnectionException) {
            failConnection(e)
        } catch (e: Exception) {
            failConnection(NvimConnectionException("Neovim reader failed", e))
        }
    }

    private suspend fun writerLoop() {
        try {
            for (message in outbound) {
                packer.packRpcMessage(message)
                packer.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failConnection(NvimConnectionException("Neovim writer failed", e))
        } finally {
            runCatching { packer.close() }
        }
    }

    private suspend fun requestLoop() {
        try {
            for (request in requests) {
                val reply = try {
                    requestHandler?.handle(request)
                        ?: RpcReply.Error(0L, "unsupported client request: ${request.method}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NvimAPIException) {
                    RpcReply.Error(e.error.errorType, e.message ?: "Neovim API error")
                } catch (e: Exception) {
                    RpcReply.Error(0L, e.message ?: e.toString())
                }

                outbound.send(
                    when (reply) {
                        is RpcReply.Result -> RpcResponse(request.msgid, null, reply.value)

                        is RpcReply.Error -> RpcResponse(
                            request.msgid,
                            listOf(reply.type, reply.message),
                            null,
                        )
                    },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failConnection(NvimConnectionException("Neovim request loop failed", e))
        }
    }

    private suspend fun enqueueNotification(message: RpcNotification) {
        if (message.method == "redraw") {
            val params = asNestedList(message.params) ?: return
            dispatchRedrawEvent(inbound, params)
        } else {
            inbound.send(NvimNotification.Unclassified(message))
        }
    }
    private fun enqueueRequest(request: RpcRequest) {
        if (requests.trySend(request).isSuccess) return

        // Reply with an error to not block
        val reply = RpcResponse(
            request.msgid,
            listOf(0L, "client request queue is full"),
            null,
        )
        if (outbound.trySend(reply).isFailure) {
            failConnection(NvimConnectionException("client request overflow"))
        }
    }
}

private fun asNestedList(value: Any?): List<List<*>>? {
    if (value !is List<*>) return null
    if (!value.all { it is List<*> }) return null

    return value.filterIsInstance<List<*>>()
}
