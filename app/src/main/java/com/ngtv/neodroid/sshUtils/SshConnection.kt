package com.ngtv.neodroid.sshUtils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.DisconnectListener
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A single authenticated SSH transport plus all workspaces opened on it.
 */
class SshConnection internal constructor(
    val id: String,
    val hostId: Int,
    val serverName: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val hostKeyFingerprint: String?,
    private val sshClient: SSHClient,
    val connectedAt: Long = System.currentTimeMillis(),
) : Closeable {

    private val lock = ReentrantLock()
    private val workspaceSequence = AtomicLong(0)
    private val lastActivity = AtomicLong(connectedAt)
    private val closed = AtomicBoolean(false)

    private val _state = MutableStateFlow<SshConnectionState>(SshConnectionState.Connected)
    val state: StateFlow<SshConnectionState> = _state.asStateFlow()

    private val _workspaces = MutableStateFlow<List<WorkspaceSession>>(emptyList())
    val workspaces: StateFlow<List<WorkspaceSession>> = _workspaces.asStateFlow()

    val displayName: String = "$serverName ($username@$hostname:$port)"
    val lastActivityAt: Long
        get() = lastActivity.get()

    val isHealthy: Boolean
        get() = !closed.get() &&
            _state.value is SshConnectionState.Connected &&
            sshClient.isConnected &&
            runCatching { sshClient.transport.isRunning }.getOrDefault(false)

    init {
        runCatching {
            sshClient.transport.disconnectListener =
                DisconnectListener { reason, message ->
                    markDisconnected(
                        message?.takeIf { it.isNotBlank() } ?: reason?.name,
                    )
                }
        }
    }

    suspend fun openWorkspace(title: String = "Workspace"): WorkspaceSession =
        withContext(Dispatchers.IO) {
            if (!isHealthy) {
                throw SshException("Connection $id ($displayName) is not available")
            }

            val workspace =
                lock.withLock {
                    val current = _workspaces.value
                    if (current.size >= MAX_WORKSPACES) {
                        throw SshLimitException(
                            "Connection $id already has the maximum of " +
                                "$MAX_WORKSPACES workspaces",
                        )
                    }

                    WorkspaceSession(
                        id = "$id:ws:${workspaceSequence.incrementAndGet()}",
                        connectionId = id,
                        title = title,
                        sshClient = sshClient,
                    ).also {
                        _workspaces.value = current + it
                        touch()
                    }
                }

            workspace
        }

    fun getWorkspace(workspaceId: String): WorkspaceSession? =
        _workspaces.value.find { it.id == workspaceId }

    fun closeWorkspace(workspaceId: String): Boolean {
        val workspace =
            lock.withLock {
                val current = _workspaces.value
                val found = current.find { it.id == workspaceId } ?: return false
                _workspaces.value = current - found
                found
            }

        runCatching { workspace.close() }
        return true
    }

    fun touch() {
        lastActivity.set(System.currentTimeMillis())
    }

    fun markDisconnected(reason: String? = null) {
        _state.value = SshConnectionState.Disconnected(reason)
    }

    fun markFailed(cause: Throwable) {
        _state.value = SshConnectionState.Failed(cause)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val currentState = _state.value
        if (currentState !is SshConnectionState.Failed) {
            _state.value = SshConnectionState.Disconnected("Connection closed")
        }

        val remainingWorkspaces =
            lock.withLock {
                val snapshot = _workspaces.value
                _workspaces.value = emptyList()
                snapshot
            }

        remainingWorkspaces.forEach { workspace ->
            runCatching { workspace.close() }
        }
        runCatching { sshClient.close() }
    }

    private companion object {
        const val MAX_WORKSPACES = 8
    }
}
