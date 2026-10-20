package com.ngtv.neodroid.sshUtils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ngtv.neodroid.core.NeovimClient
import com.ngtv.neodroid.nvim.NvimSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import net.schmizz.sshj.connection.channel.direct.Session
import java.io.Closeable

sealed interface WorkspaceTool {
    val id: String
    val title: String
}

class NvimTool(
    override val id: String,
    val nvimSession: NvimSession,
    val command: Session.Command,
) : WorkspaceTool {
    override val title = "Neovim"
}

class WorkspaceSession(
    private val sshSession: Session,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : Closeable {

    val tools = mutableStateListOf<WorkspaceTool>()
    var activeToolId by mutableStateOf<String?>(null)
        private set

    val isOpen: Boolean
        get() = sshSession.isOpen

    suspend fun startNvim(): NvimTool = withContext(Dispatchers.IO) {
        val cmd = sshSession.exec("nvim --embed")
        val client = NeovimClient(
            cmd.inputStream, // Neovim stdout -> client input
            cmd.outputStream, // client output -> Neovim stdin
            scope,
        )

        val nvimSession = NvimSession.create(client)
        val toolId = "nvim_${System.currentTimeMillis()}"
        val tool = NvimTool(toolId, nvimSession, cmd)

        tools.add(tool)
        activeToolId = toolId
        tool
    }

    fun closeTool(id: String) {
        val tool = tools.find { it.id == id } ?: return
        tools.remove(tool)

        when (tool) {
            is NvimTool -> {
                tool.nvimSession.nvim.close()
                tool.command.close()
            }
        }

        if (activeToolId == id) {
            activeToolId = tools.lastOrNull()?.id
        }
    }

    override fun close() {
        tools.forEach { tool ->
            when (tool) {
                is NvimTool -> {
                    tool.nvimSession.nvim.close()
                    tool.command.close()
                }
            }
        }
        tools.clear()
        scope.cancel()
        sshSession.close()
    }
}
