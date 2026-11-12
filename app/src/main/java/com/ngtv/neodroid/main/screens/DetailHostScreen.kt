package com.ngtv.neodroid.main.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.SSHHostViewModel

@Composable
fun DetailHostScreen(
    viewModel: SSHHostViewModel,
    hostId: Int,
    onBack: () -> Unit,
    onConnect: () -> Unit,
) {
    val hostHandler by viewModel.getHostById(
        hostId,
    ).collectAsStateWithLifecycle(initialValue = null)
    val host = hostHandler ?: return

    HostEditorScreen(
        host = host,
        onBack = onBack,
        onSubmit = { input ->
            viewModel.updateHost(
                host.copy(
                    server_name = input.serverName,
                    hostname = input.hostname,
                    port = input.port,
                    server_signature = input.serverSignature,
                    username = input.username,
                ),
            )
        },
        onConnect = onConnect,
    )
}
