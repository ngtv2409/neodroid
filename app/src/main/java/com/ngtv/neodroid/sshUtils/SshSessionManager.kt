package com.ngtv.neodroid.sshUtils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SshSessionManager : Closeable {
    private var currentConnection: SSHClient? = null
    private var currentSession: WorkspaceSession? = null
    private val lock = ReentrantLock()

    val isConnected: Boolean
        get() = lock.withLock {
            val conn = currentConnection
            conn != null && conn.isConnected && conn.transport.isRunning
        }

    fun bind(client: SSHClient): Boolean = lock.withLock {
        if (isConnected) {
            return false // occupied
        }
        disconnectInternal()
        currentConnection = client
        true
    }

    fun hasRunningSession(): Boolean = lock.withLock {
        val client = currentConnection ?: return false
        val session = currentSession

        return session != null
    }

    suspend fun getSession(): WorkspaceSession? = withContext(Dispatchers.IO) {
        lock.withLock {
            val client = currentConnection ?: return@withContext null
            val session = currentSession

            session ?:
            WorkspaceSession(client).also {
                currentSession = it
            }
        }
    }

    fun disconnect() = lock.withLock {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        runCatching { currentSession?.close() }
        currentSession = null
        runCatching { currentConnection?.close() }
        currentConnection = null
    }

    override fun close() = lock.withLock {
        disconnectInternal()
    }
}
