package com.ngtv.neodroid.sshUtils

import net.schmizz.sshj.SSHClient

// TODO: make this host multiple connections if needed
// A class to manage clients lifecycl3.
// Scaffold to update to multi sessions
class SshSessionManager : java.io.Closeable {
    private var currentConnection: SSHClient? = null

    fun bind(client: SSHClient) {
        disconnect()

        currentConnection = client
    }

    fun disconnect() {
        currentConnection?.close()
        currentConnection = null
    }

    fun getClient(): SSHClient? {
        return currentConnection
    }

    override fun close() {
        disconnect()
    }
}
