@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.sharedComponents.CompactListItem
import com.ngtv.neodroid.ui.theme.Spacing

private val SplitPaneThreshold = 720.dp
private val HostListPaneWidth = 360.dp

@Composable
private fun homeTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun HomeScreen(
    viewModel: SSHHostViewModel,
    onOpenHostAdd: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onConnectHost: (Int) -> Unit,
    hasRunningSession: Boolean,
    onBackToSession: () -> Unit,
) {
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= SplitPaneThreshold) {
            SplitHomeScreen(
                viewModel = viewModel,
                hosts = hosts,
                onConnectHost = onConnectHost,
                hasRunningSession = hasRunningSession,
                onBackToSession = onBackToSession,
            )
        } else {
            CompactHomeScreen(
                hosts = hosts,
                onOpenHostAdd = onOpenHostAdd,
                onOpenDetail = onOpenDetail,
                hasRunningSession = hasRunningSession,
                onBackToSession = onBackToSession,
            )
        }
    }
}

@Composable
private fun CompactHomeScreen(
    hosts: List<SSHHostEntity>,
    onOpenHostAdd: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    hasRunningSession: Boolean,
    onBackToSession: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = homeTopBarColors(),
                title = {
                    Text("Neodroid")
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (hasRunningSession) {
                BackToSessionButton(onBackToSession)
            }
            HostListHeader(onOpenHostAdd)
            HostList(
                hosts = hosts,
                onHostClick = onOpenDetail,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SplitHomeScreen(
    viewModel: SSHHostViewModel,
    hosts: List<SSHHostEntity>,
    onConnectHost: (Int) -> Unit,
    hasRunningSession: Boolean,
    onBackToSession: () -> Unit,
) {
    var selectedHostId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isAdding by rememberSaveable { mutableStateOf(false) }

    val selectedHost = hosts.firstOrNull { it.id == selectedHostId }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(HostListPaneWidth).fillMaxHeight(),
        ) {
            TopAppBar(
                colors = homeTopBarColors(),
                title = {
                    Text("Neodroid")
                },
            )
            if (hasRunningSession) {
                BackToSessionButton(onBackToSession)
            }
            HostListHeader(
                onAdd = {
                    isAdding = true
                    selectedHostId = null
                },
            )
            HostList(
                hosts = hosts,
                onHostClick = { id ->
                    isAdding = false
                    selectedHostId = id
                },
                modifier = Modifier.fillMaxSize(),
                selectedHostId = selectedHostId,
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
                    )
                }

                selectedHost != null -> {
                    HostEditorScreen(
                        host = selectedHost,
                        onBack = { selectedHostId = null },
                        onSubmit = { input ->
                            viewModel.updateHost(
                                selectedHost.copy(
                                    server_name = input.serverName,
                                    hostname = input.hostname,
                                    port = input.port,
                                    server_signature = input.serverSignature,
                                    username = input.username,
                                ),
                            )
                        },
                        onConnect = { onConnectHost(selectedHost.id) },
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
private fun HostListHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Hosts",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAdd) {
            Text("+ Add")
        }
    }
}

@Composable
private fun HostList(
    hosts: List<SSHHostEntity>,
    onHostClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedHostId: Int? = null,
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(
            items = hosts,
            key = { _, host -> host.id },
        ) { index, host ->
            CompactListItem(
                headlineContent = {
                    Text(
                        text = host.server_name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = {
                    Text(
                        text = "${host.username}@${host.hostname} • ${host.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {
                    onHostClick(host.id)
                },
                selected = host.id == selectedHostId,
            )
            if (index < hosts.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            }
        }
    }
}

@Composable
private fun BackToSessionButton(onBackToSession: () -> Unit) {
    FilledTonalButton(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        onClick = onBackToSession,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Back to session",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "A session is currently running",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
