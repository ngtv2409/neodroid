package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.sshUtils.SshConnection
import com.ngtv.neodroid.sshUtils.SshConnectionState
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    sessionManager: SshSessionManager,
    onBack: () -> Unit,
    onOpenConnection: (String) -> Unit,
) {
    val connections by sessionManager.connections.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                title = {
                    Text("Connections")
                },
            )
        },
    ) { innerPadding ->
        if (connections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No active connections",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Open a host to start a new SSH connection.",
                        modifier = Modifier.padding(top = Spacing.xs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(
                    items = connections,
                    key = { it.id },
                ) { connection ->
                    ConnectionCard(
                        connection = connection,
                        onOpen = { onOpenConnection(connection.id) },
                        onDisconnect = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    sessionManager.closeConnection(connection.id)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connection: SshConnection,
    onOpen: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val state by connection.state.collectAsStateWithLifecycle()
    val workspaces by connection.workspaces.collectAsStateWithLifecycle()

    val healthy = state is SshConnectionState.Connected
    val statusText = when (val current = state) {
        is SshConnectionState.Connected -> "Online"
        is SshConnectionState.Disconnected -> current.reason ?: "Disconnected"
        is SshConnectionState.Failed -> "Failed"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connection.serverName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${connection.username}@${connection.hostname}:${connection.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (healthy) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (healthy) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(modifier = Modifier.size(Spacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${workspaces.size} workspace" +
                        if (workspaces.size == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (healthy) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
                TextButton(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}
