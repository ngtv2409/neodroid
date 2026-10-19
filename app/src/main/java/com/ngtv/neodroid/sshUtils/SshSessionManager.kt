package com.ngtv.neodroid.sshUtils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session

// TODO: make this host multiple connections if needed
// A class to manage clients lifecycl3.
// Scaffold to update to multi sessions
// TODO: WARN: Concurrence change of currentConnection???
class SshSessionManager : java.io.Closeable {
    private var currentConnection: SSHClient? = null
    private var currentSession: Session? = null

    fun bind(client: SSHClient) {
        disconnect()

        currentConnection = client
    }

    fun disconnect() {
        currentConnection?.close()
        currentConnection = null
        currentSession?.close()
        currentSession = null
    }

    fun getClient(): SSHClient? = currentConnection

    suspend fun getSession(): Session? = withContext(Dispatchers.IO) {
        val client = currentConnection ?: return@withContext null

        currentSession ?: client.startSession().also {
            currentSession = it
        }
    }

    override fun close() {
        disconnect()
    }
}
