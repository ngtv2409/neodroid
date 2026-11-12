package com.ngtv.neodroid.sshUtils

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Owns every live SSH connection and workspace in the process.
 *
 * It is safe to call from any thread
 */
class SshSessionManager(
    private val connector: SshConnector = SshConnector(),
    private val history: SshConnectionHistory? = null,
) : Closeable {

    private val lock = ReentrantLock()
    private val closed = AtomicBoolean(false)

    private val _connections = MutableStateFlow<List<SshConnection>>(emptyList())
    val connections: StateFlow<List<SshConnection>> = _connections.asStateFlow()

    private val _recentConnections = MutableStateFlow<List<RecentConnection>>(emptyList())

    /** Recently used hosts from this service process, newest first. */
    val recentConnections: StateFlow<List<RecentConnection>> =
        _recentConnections.asStateFlow()

    val isConnected: Boolean
        get() = _connections.value.any { it.isHealthy }

    val activeConnectionCount: Int
        get() = _connections.value.count { it.isHealthy }

    fun getConnection(connectionId: String): SshConnection? =
        _connections.value.find { it.id == connectionId }

    fun connectionsForHost(hostId: Int): List<SshConnection> =
        _connections.value.filter { it.hostId == hostId }

    /** The most recently used live connection to a host, if any. */
    fun getConnectionForHost(hostId: Int): SshConnection? = connectionsForHost(hostId)
        .filter { it.isHealthy }
        .maxByOrNull { it.lastActivityAt }

    fun getWorkspace(workspaceId: String): WorkspaceSession? {
        for (connection in _connections.value) {
            connection.getWorkspace(workspaceId)?.let { return it }
        }
        return null
    }

    fun allWorkspaces(): List<WorkspaceSession> = _connections.value.flatMap { it.workspaces.value }

    fun hasRunningSession(): Boolean = _connections.value.any { connection ->
        connection.isHealthy &&
            connection.workspaces.value.any { it.hasRunningTools }
    }

    suspend fun connect(config: SshConnectionConfig): SshConnection {
        checkNotClosed()

        val result = connector.connect(config)
        val connection =
            SshConnection(
                id = newConnectionId(config.hostId),
                hostId = config.hostId,
                serverName = config.serverName,
                hostname = config.hostname,
                port = config.port,
                username = config.username,
                hostKeyFingerprint = result.hostKeyFingerprint,
                sshClient = result.client,
            )

        val accepted =
            lock.withLock {
                when {
                    closed.get() -> ConnectionRejection.CLOSED

                    _connections.value.count { it.isHealthy } >= MAX_CONNECTIONS ->
                        ConnectionRejection.LIMIT

                    else -> {
                        _connections.value = _connections.value + connection
                        _recentConnections.value =
                            rememberConnection(
                                recent = _recentConnections.value,
                                config = config,
                                connectedAt = connection.connectedAt,
                            )
                        ConnectionRejection.NONE
                    }
                }
            }

        if (accepted != ConnectionRejection.NONE) {
            runCatching { connection.close() }
            when (accepted) {
                ConnectionRejection.CLOSED ->
                    throw SshException("SSH session manager is closed")

                ConnectionRejection.LIMIT ->
                    throw SshLimitException(
                        "Maximum of $MAX_CONNECTIONS simultaneous SSH connections reached",
                    )

                ConnectionRejection.NONE -> Unit
            }
        }

        notifyHistory(connection, hostKeyLearned = result.hostKeyLearned)
        return connection
    }

    suspend fun openWorkspace(
        connectionId: String,
        title: String = "Workspace",
    ): WorkspaceSession {
        val connection =
            getConnection(connectionId)
                ?: throw SshException("Unknown SSH connection: $connectionId")
        return connection.openWorkspace(title)
    }

    suspend fun getSession(connectionId: String): WorkspaceSession? {
        val connection = getConnection(connectionId) ?: return null
        if (!connection.isHealthy) return null

        return connection.workspaces.value.maxByOrNull { it.createdAt }
            ?: connection.openWorkspace()
    }

    /**
     * Compatibility helper for the current single-workspace UI: returns the newest
     * workspace across all live connections, or opens one on the most recently used
     * connection. TODO: Should be deleted when ui done
     */
    suspend fun getSession(): WorkspaceSession? {
        val liveConnections = _connections.value.filter { it.isHealthy }
        if (liveConnections.isEmpty()) return null

        val newestWorkspace =
            liveConnections
                .flatMap { connection ->
                    connection.workspaces.value.map { connection to it }
                }
                .maxByOrNull { (_, workspace) -> workspace.createdAt }

        if (newestWorkspace != null) {
            val (connection, workspace) = newestWorkspace
            connection.touch()
            return workspace
        }

        val connection = liveConnections.maxByOrNull { it.lastActivityAt }
            ?: return null
        return connection.openWorkspace()
    }

    fun closeWorkspace(workspaceId: String): Boolean {
        for (connection in _connections.value) {
            if (connection.getWorkspace(workspaceId) != null) {
                return connection.closeWorkspace(workspaceId)
            }
        }
        return false
    }

    fun closeConnection(connectionId: String): Boolean {
        val connection =
            lock.withLock {
                val current = _connections.value
                val found = current.find { it.id == connectionId } ?: return false
                _connections.value = current - found
                found
            }

        runCatching { connection.close() }
        return true
    }

    fun closeHostConnections(hostId: Int): Int {
        val removed =
            lock.withLock {
                val current = _connections.value
                val (toClose, toKeep) = current.partition { it.hostId == hostId }
                _connections.value = toKeep
                toClose
            }

        removed.forEach { connection ->
            runCatching { connection.close() }
        }
        return removed.size
    }

    fun pruneDisconnected() {
        val dead =
            lock.withLock {
                val current = _connections.value
                val (toClose, toKeep) = current.partition { !it.isHealthy }
                _connections.value = toKeep
                toClose
            }

        dead.forEach { connection ->
            runCatching { connection.close() }
        }
    }

    fun disconnect() {
        closeAllConnections()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        closeAllConnections()
    }

    private fun closeAllConnections() {
        val snapshot =
            lock.withLock {
                val current = _connections.value
                _connections.value = emptyList()
                current
            }

        snapshot.forEach { connection ->
            runCatching { connection.close() }
        }
    }

    private fun checkNotClosed() {
        if (closed.get()) throw SshException("SSH session manager is closed")
    }

    private suspend fun notifyHistory(connection: SshConnection, hostKeyLearned: Boolean) {
        val callback = history ?: return
        try {
            callback.onConnected(connection, hostKeyLearned)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Persistence must never take down a live SSH connection.
            Log.w(TAG, "Failed to persist connection history for ${connection.id}", e)
        }
    }

    private fun rememberConnection(
        recent: List<RecentConnection>,
        config: SshConnectionConfig,
        connectedAt: Long,
    ): List<RecentConnection> {
        val entry =
            RecentConnection(
                hostId = config.hostId,
                serverName = config.serverName,
                hostname = config.hostname,
                port = config.port,
                username = config.username,
                lastConnectedAt = connectedAt,
            )

        return (listOf(entry) + recent.filterNot { it.hostId == config.hostId })
            .take(MAX_RECENT_CONNECTIONS)
    }

    private fun newConnectionId(hostId: Int): String = "host-$hostId-${UUID.randomUUID()}"

    private enum class ConnectionRejection {
        NONE,
        CLOSED,
        LIMIT,
    }

    private companion object {
        const val TAG = "SshSessionManager"
        const val MAX_CONNECTIONS = 16
        const val MAX_RECENT_CONNECTIONS = 20
    }
}
