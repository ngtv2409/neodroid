package com.ngtv.neodroid.sshUtils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ngtv.neodroid.core.NeovimClient
import com.ngtv.neodroid.core.NvimConnectionException
import com.ngtv.neodroid.nvim.NvimSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed interface WorkspaceTool : Closeable {
    val id: String
    val title: String
}

class NvimTool(
    override val id: String,
    val sshSession: Session,
    val nvimSession: NvimSession,
    val command: Session.Command,
) : WorkspaceTool {
    override val title = "Neovim"

    var connectionFailure: NvimConnectionException? = null

    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        runCatching { nvimSession.close() }
        runCatching { command.close() }
        runCatching { sshSession.close() }
    }
}

/**
 * One independent editor workspace inside an [SshConnection].
 *
 * A workspace owns at most [MAX_TOOLS] tools (Neovim processes). Several
 * workspaces can share the same underlying SSH connection.
 */
class WorkspaceSession(
    val id: String,
    val connectionId: String,
    val title: String,
    private val sshClient: SSHClient,
    val createdAt: Long = System.currentTimeMillis(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Closeable {

    val tools = mutableStateListOf<WorkspaceTool>()
    var activeToolId by mutableStateOf<String?>(null)
        private set

    private val toolLock = Any()
    private val toolSequence = AtomicLong(0)
    private val closed = AtomicBoolean(false)

    val hasRunningTools: Boolean
        get() = synchronized(toolLock) { tools.isNotEmpty() }

    suspend fun startNvim(): NvimTool = withContext(Dispatchers.IO) {
        startTool { sshSession ->
            sshSession.exec("nvim --embed")
        }
    }

    suspend fun startNvimConnectToSock(socketPath: String): NvimTool = withContext(Dispatchers.IO) {
        startTool { sshSession ->
            sshSession.exec("exec socat STDIO 'UNIX-CONNECT:$socketPath'")
        }
    }

    fun activateTool(id: String): Boolean = synchronized(toolLock) {
        val tool = tools.find { it.id == id } ?: return@synchronized false
        activeToolId = tool.id
        true
    }

    fun closeTool(id: String) {
        val tool = synchronized(toolLock) {
            val found = tools.find { it.id == id } ?: return
            tools.remove(found)
            if (activeToolId == id) {
                activeToolId = tools.lastOrNull()?.id
            }
            found
        }

        runCatching { tool.close() }
    }

    private suspend fun startTool(exec: (Session) -> Session.Command): NvimTool {
        checkOpen()
        ensureToolCapacity()

        val sshSession = sshClient.startSession()
        var command: Session.Command? = null
        var client: NeovimClient? = null

        try {
            val startedCommand = exec(sshSession)
            command = startedCommand
            val nvimClient = NeovimClient(
                startedCommand.inputStream, // Neovim stdout -> client input
                startedCommand.outputStream, // client output -> Neovim stdin
                scope,
            )
            client = nvimClient
            val nvimSession = NvimSession.create(nvimClient)
            val tool =
                NvimTool(
                    id = "nvim_${toolSequence.incrementAndGet()}",
                    sshSession = sshSession,
                    nvimSession = nvimSession,
                    command = startedCommand,
                )

            val added = synchronized(toolLock) {
                if (closed.get()) {
                    false
                } else {
                    tools.add(tool)
                    activeToolId = tool.id
                    true
                }
            }

            if (!added) {
                runCatching { tool.close() }
                throw SshException("Workspace $id is closed")
            }

            return tool
        } catch (e: CancellationException) {
            runCatching { client?.close() }
            runCatching { command?.close() }
            runCatching { sshSession.close() }
            throw e
        } catch (e: Exception) {
            runCatching { client?.close() }
            runCatching { command?.close() }
            runCatching { sshSession.close() }
            throw e
        }
    }

    private fun checkOpen() {
        if (closed.get()) throw SshException("Workspace $id is closed")
    }

    private fun ensureToolCapacity() {
        synchronized(toolLock) {
            if (closed.get()) throw SshException("Workspace $id is closed")
            if (tools.size >= MAX_TOOLS) {
                throw SshLimitException(
                    "Workspace $id already has the maximum of $MAX_TOOLS tools",
                )
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        val remaining = synchronized(toolLock) {
            val snapshot = tools.toList()
            tools.clear()
            activeToolId = null
            snapshot
        }

        remaining.forEach { tool ->
            runCatching { tool.close() }
        }
        scope.cancel()
    }

    companion object {
        private const val MAX_TOOLS = 16
    }
}
