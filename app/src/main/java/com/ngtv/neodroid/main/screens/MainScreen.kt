@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.main.screens
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.sharedComponents.ActionMenuItem
import com.ngtv.neodroid.sharedComponents.CompactListItem
import com.ngtv.neodroid.sharedComponents.FakeIcon
import com.ngtv.neodroid.sshUtils.SshConnection
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.ui.theme.Spacing
import com.ngtv.neodroid.ui.theme.neodroidColors

private val SplitPaneThreshold = 720.dp
private val HostListPaneWidth = 360.dp
private const val MAX_HOME_CONNECTIONS = 3
private const val MAX_HOME_RECENT_HOSTS = 5

@Composable
private fun homeTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

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
    authKeys: List<AuthKeyEntity> = emptyList(),
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val recentHosts by viewModel.recentHosts.collectAsStateWithLifecycle()
    val connections by sessionManager.connections.collectAsStateWithLifecycle()

    val onlineConnections = connections.filter { it.isHealthy }
    val recent = recentHosts.filter { it.last_connected_at != null }.take(MAX_HOME_RECENT_HOSTS)

    var hostPendingDeletion by remember { mutableStateOf<SSHHostEntity?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= SplitPaneThreshold) {
            SplitHomeScreen(
                viewModel = viewModel,
                hosts = hosts,
                recentHosts = recent,
                onlineConnections = onlineConnections,
                onConnectHost = onConnectHost,
                onOpenConnection = onOpenConnection,
                onShowAllConnections = onShowAllConnections,
                onOpenAuthKeys = onOpenAuthKeys,
                onDeleteHost = { hostPendingDeletion = it },
                authKeys = authKeys,
            )
        } else {
            CompactHomeScreen(
                hosts = hosts,
                recentHosts = recent,
                onlineConnections = onlineConnections,
                onOpenHostAdd = onOpenHostAdd,
                onOpenDetail = onOpenDetail,
                onConnectHost = onConnectHost,
                onOpenConnection = onOpenConnection,
                onShowAllConnections = onShowAllConnections,
                onOpenAuthKeys = onOpenAuthKeys,
                onDeleteHost = { hostPendingDeletion = it },
            )
        }
    }

    hostPendingDeletion?.let { host ->
        DeleteHostDialog(
            host = host,
            onDismiss = { hostPendingDeletion = null },
            onConfirm = {
                viewModel.deleteHost(host)
                hostPendingDeletion = null
            },
        )
    }
}

@Composable
private fun CompactHomeScreen(
    hosts: List<SSHHostEntity>,
    recentHosts: List<SSHHostEntity>,
    onlineConnections: List<SshConnection>,
    onOpenHostAdd: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onConnectHost: (Int) -> Unit,
    onOpenConnection: (String) -> Unit,
    onShowAllConnections: () -> Unit,
    onOpenAuthKeys: () -> Unit,
    onDeleteHost: (SSHHostEntity) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = homeTopBarColors(),
                title = {
                    Text("Neodroid")
                },
                actions = {
                    IconButton(onClick = onOpenAuthKeys) {
                        FakeIcon()
                    }
                },
            )
        },
    ) { innerPadding ->
        HomeContent(
            hosts = hosts,
            recentHosts = recentHosts,
            onlineConnections = onlineConnections,
            onOpenHostAdd = onOpenHostAdd,
            onHostClick = onOpenDetail,
            onConnectHost = onConnectHost,
            onOpenConnection = onOpenConnection,
            onShowAllConnections = onShowAllConnections,
            onDeleteHost = onDeleteHost,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun SplitHomeScreen(
    viewModel: SSHHostViewModel,
    hosts: List<SSHHostEntity>,
    recentHosts: List<SSHHostEntity>,
    onlineConnections: List<SshConnection>,
    onConnectHost: (Int) -> Unit,
    onOpenConnection: (String) -> Unit,
    onShowAllConnections: () -> Unit,
    onOpenAuthKeys: () -> Unit,
    onDeleteHost: (SSHHostEntity) -> Unit,
    authKeys: List<AuthKeyEntity>,
) {
    var selectedHostId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isAdding by rememberSaveable { mutableStateOf(false) }

    val selectedHost = hosts.firstOrNull { it.id == selectedHostId }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.width(HostListPaneWidth).fillMaxHeight(),
        ) {
            TopAppBar(
                colors = homeTopBarColors(),
                title = {
                    Text("Neodroid")
                },
                actions = {
                    IconButton(onClick = onOpenAuthKeys) {
                        FakeIcon()
                    }
                },
            )
            HomeContent(
                hosts = hosts,
                recentHosts = recentHosts,
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
                onDeleteHost = onDeleteHost,
                selectedHostId = selectedHostId,
                modifier = Modifier.fillMaxSize(),
            )
        }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                    EmptyDetailPane()
                }
            }
        }
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
            HomeSectionHeader(
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
                HomeHint("No active connections. Connect to a host below.")
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
            HomeSectionHeader(
                title = "Recent",
                count = recentHosts.size,
            )
        }

        if (recentHosts.isEmpty()) {
            item(key = "recent-empty") {
                HomeHint("Hosts you connect to will appear here.")
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
            HomeSectionHeader(
                title = "Hosts",
                count = hosts.size,
                actionLabel = "+ Add",
                onAction = onOpenHostAdd,
            )
        }

        if (hosts.isEmpty()) {
            item(key = "hosts-empty") {
                HomeHint("No saved hosts yet. Add one to get started.")
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
private fun HomeSectionHeader(
    title: String,
    count: Int = 0,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(
                start = Spacing.lg,
                end = Spacing.sm,
                top = Spacing.lg,
                bottom = Spacing.xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )

        if (count > 0) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.weight(1f))

        if (actionLabel != null) {
            Text(
                text = actionLabel,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = Spacing.sm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun OnlineConnectionRow(connection: SshConnection, onClick: () -> Unit) {
    val workspaces by connection.workspaces.collectAsStateWithLifecycle()

    CompactListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.neodroidColors.online, CircleShape),
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

@Composable
private fun DeleteHostDialog(host: SSHHostEntity, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete host?")
        },
        text = {
            Text(
                "\"${host.server_name}\" will be removed from saved hosts. " +
                    "This cannot be undone.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun HomeHint(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun relativeTime(timestamp: Long?): String {
    if (timestamp == null) return "Not connected yet"

    return DateUtils.getRelativeTimeSpanString(
        timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

@Composable
private fun EmptyDetailPane() {
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Select a host",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Choose a host from the list, or add a new one.",
                modifier = Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
