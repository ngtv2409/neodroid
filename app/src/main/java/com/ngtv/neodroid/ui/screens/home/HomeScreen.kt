@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.sshUtils.SshConnection
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.ui.components.ActionMenuItem
import com.ngtv.neodroid.ui.components.CompactListItem
import com.ngtv.neodroid.ui.components.ConfirmDeleteDialog
import com.ngtv.neodroid.ui.components.EmptyState
import com.ngtv.neodroid.ui.components.FakeIcon
import com.ngtv.neodroid.ui.components.HintText
import com.ngtv.neodroid.ui.components.ListSectionHeader
import com.ngtv.neodroid.ui.components.NeodroidTopBar
import com.ngtv.neodroid.ui.components.SplitPaneLayout
import com.ngtv.neodroid.ui.components.StatusDot
import com.ngtv.neodroid.ui.screens.hosts.HostDetailPane
import com.ngtv.neodroid.ui.screens.hosts.HostEditorScreen
import com.ngtv.neodroid.ui.theme.Spacing
import com.ngtv.neodroid.ui.theme.neodroidColors
import com.ngtv.neodroid.ui.util.relativeTime

private const val MAX_HOME_CONNECTIONS = 3
private const val MAX_HOME_RECENT_HOSTS = 5

@Composable
private fun HomeTopBarActions(onOpenAuthKeys: () -> Unit, onOpenSettings: () -> Unit) {
    IconButton(
        onClick = onOpenAuthKeys,
        modifier = Modifier.semantics { contentDescription = "Authentication keys" },
    ) {
        FakeIcon()
    }
    IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.semantics { contentDescription = "Settings" },
    ) {
        FakeIcon()
    }
}

@Composable
fun HomeScreen(
    viewModel: SSHHostViewModel,
    sessionManager: SshSessionManager,
    onOpenHostAdd: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onConnectHost: (Int) -> Unit,
    onOpenConnection: (String) -> Unit,
    onShowAllConnections: () -> Unit,
    onOpenAuthKeys: () -> Unit,
    onOpenSettings: () -> Unit,
    authKeys: List<AuthKeyEntity> = emptyList(),
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()
    val connections by sessionManager.connections.collectAsStateWithLifecycle()

    val onlineConnections = connections.filter { it.isHealthy }
    val recent = recentHosts.filter { it.last_connected_at != null }.take(MAX_HOME_RECENT_HOSTS)

    var hostPendingDeletion by remember { mutableStateOf<SSHHostEntity?>(null) }
    var selectedHostId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isAdding by rememberSaveable { mutableStateOf(false) }

    val selectedHost = hosts.firstOrNull { it.id == selectedHostId }
    val deleteHost: (SSHHostEntity) -> Unit = { hostPendingDeletion = it }

    SplitPaneLayout(
        listPane = {
            NeodroidTopBar(
                title = "Neodroid",
                actions = {
                    HomeTopBarActions(
                        onOpenAuthKeys = onOpenAuthKeys,
                        onOpenSettings = onOpenSettings,
                    )
                },
            )
            HomeContent(
                hosts = hosts,
                recentHosts = recent,
                onlineConnections = onlineConnections,
                onOpenHostAdd = {
                    isAdding = true
                    selectedHostId = null
                },
                onHostClick = { id ->
                    isAdding = false
                    selectedHostId = id
                },
                onConnectHost = onConnectHost,
                onOpenConnection = onOpenConnection,
                onShowAllConnections = onShowAllConnections,
                onDeleteHost = deleteHost,
                selectedHostId = selectedHostId,
                modifier = Modifier.fillMaxSize(),
            )
        },
        detailPane = {
            when {
                isAdding -> {
                    HostEditorScreen(
                        host = null,
                        onBack = { isAdding = false },
                        onSubmit = { input ->
                            viewModel.addHost(input)
                            isAdding = false
                        },
                        authKeys = authKeys,
                    )
                }

                selectedHost != null -> {
                    HostDetailPane(
                        host = selectedHost,
                        onBack = { selectedHostId = null },
                        onSave = { input ->
                            viewModel.updateHost(
                                selectedHost.copy(
                                    server_name = input.serverName,
                                    hostname = input.hostname,
                                    port = input.port,
                                    server_signature = input.serverSignature,
                                    username = input.username,
                                    auth_key_id = input.authKeyId,
                                ),
                            )
                        },
                        onConnect = { onConnectHost(selectedHost.id) },
                        authKeys = authKeys,
                    )
                }

                else -> {
                    EmptyState(
                        title = "Select a host",
                        message = "Choose a host from the list, or add a new one.",
                    )
                }
            }
        },
        compactContent = {
            Scaffold(
                topBar = {
                    NeodroidTopBar(
                        title = "Neodroid",
                        actions = {
                            HomeTopBarActions(
                                onOpenAuthKeys = onOpenAuthKeys,
                                onOpenSettings = onOpenSettings,
                            )
                        },
                    )
                },
            ) { innerPadding ->
                HomeContent(
                    hosts = hosts,
                    recentHosts = recent,
                    onlineConnections = onlineConnections,
                    onOpenHostAdd = onOpenHostAdd,
                    onHostClick = onOpenDetail,
                    onConnectHost = onConnectHost,
                    onOpenConnection = onOpenConnection,
                    onShowAllConnections = onShowAllConnections,
                    onDeleteHost = deleteHost,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        },
    )

    hostPendingDeletion?.let { host ->
        ConfirmDeleteDialog(
            title = "Delete host?",
            message = "\"${host.server_name}\" will be removed from saved hosts. " +
                "This cannot be undone.",
            onDismiss = { hostPendingDeletion = null },
            onConfirm = {
                viewModel.deleteHost(host)
                hostPendingDeletion = null
            },
        )
    }
}

@Composable
private fun HomeContent(
    hosts: List<SSHHostEntity>,
    recentHosts: List<SSHHostEntity>,
    onlineConnections: List<SshConnection>,
    onOpenHostAdd: () -> Unit,
    onHostClick: (Int) -> Unit,
    onConnectHost: (Int) -> Unit,
    onOpenConnection: (String) -> Unit,
    onShowAllConnections: () -> Unit,
    onDeleteHost: (SSHHostEntity) -> Unit,
    modifier: Modifier = Modifier,
    selectedHostId: Int? = null,
) {
    LazyColumn(modifier = modifier) {
        item(key = "online-header") {
            ListSectionHeader(
                title = "Online",
                count = onlineConnections.size,
                actionLabel = if (onlineConnections.isNotEmpty()) {
                    "Show all"
                } else {
                    null
                },
                onAction = onShowAllConnections,
            )
        }

        if (onlineConnections.isEmpty()) {
            item(key = "online-empty") {
                HintText("No active connections. Connect to a host below.")
            }
        } else {
            val visibleConnections = onlineConnections.take(MAX_HOME_CONNECTIONS)
            itemsIndexed(
                items = visibleConnections,
                key = { _, connection -> "online-${connection.id}" },
            ) { index, connection ->
                OnlineConnectionRow(
                    connection = connection,
                    onClick = { onOpenConnection(connection.id) },
                )
                if (index < visibleConnections.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
            }
        }

        item(key = "recent-header") {
            ListSectionHeader(
                title = "Recent",
                count = recentHosts.size,
            )
        }

        if (recentHosts.isEmpty()) {
            item(key = "recent-empty") {
                HintText("Hosts you connect to will appear here.")
            }
        } else {
            itemsIndexed(
                items = recentHosts,
                key = { _, host -> "recent-${host.id}" },
            ) { index, host ->
                RecentHostRow(
                    host = host,
                    onClick = { onConnectHost(host.id) },
                    onDelete = { onDeleteHost(host) },
                )
                if (index < recentHosts.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
            }
        }

        item(key = "hosts-header") {
            ListSectionHeader(
                title = "Hosts",
                count = hosts.size,
                actionLabel = "+ Add",
                onAction = onOpenHostAdd,
            )
        }

        if (hosts.isEmpty()) {
            item(key = "hosts-empty") {
                HintText("No saved hosts yet. Add one to get started.")
            }
        } else {
            itemsIndexed(
                items = hosts,
                key = { _, host -> "host-${host.id}" },
            ) { index, host ->
                HostRow(
                    host = host,
                    selected = host.id == selectedHostId,
                    onClick = { onHostClick(host.id) },
                    onDelete = { onDeleteHost(host) },
                )
                if (index < hosts.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                }
            }
        }
    }
}

@Composable
private fun OnlineConnectionRow(connection: SshConnection, onClick: () -> Unit) {
    val workspaces by connection.workspaces.collectAsStateWithLifecycle()

    CompactListItem(
        leadingContent = {
            StatusDot(
                color = MaterialTheme.neodroidColors.online,
                contentDescription = "Online",
            )
        },
        headlineContent = {
            Text(
                text = connection.serverName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = LocalContentColor.current,
            )
        },
        supportingContent = {
            Text(
                text = "${connection.username}@${connection.hostname}:${connection.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                text = "${workspaces.size} ws  ›",
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun RecentHostRow(host: SSHHostEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    CompactListItem(
        headlineContent = {
            Text(
                text = host.server_name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = LocalContentColor.current,
            )
        },
        supportingContent = {
            Text(
                text = "${host.username}@${host.hostname} • " +
                    relativeTime(host.last_connected_at),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                text = "Connect",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        menuActions = listOf(
            ActionMenuItem(
                label = "Delete host",
                destructive = true,
                onClick = onDelete,
            ),
        ),
        onClick = onClick,
    )
}

@Composable
private fun HostRow(
    host: SSHHostEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    CompactListItem(
        headlineContent = {
            Text(
                text = host.server_name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = LocalContentColor.current,
            )
        },
        supportingContent = {
            Text(
                text = "${host.username}@${host.hostname} • ${host.port}",
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    LocalContentColor.current.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        },
        menuActions = listOf(
            ActionMenuItem(
                label = "Delete host",
                destructive = true,
                onClick = onDelete,
            ),
        ),
        onClick = onClick,
        selected = selected,
    )
}
