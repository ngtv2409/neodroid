package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.ui.theme.Spacing

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

    HostDetailPane(
        host = host,
        onBack = onBack,
        onConnect = onConnect,
        onSave = { input ->
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
    )
}

@Composable
fun HostDetailPane(
    host: SSHHostEntity,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onSave: (SSHHostViewModel.InputModel) -> Unit,
) {
    var isEditing by rememberSaveable(host.id) { mutableStateOf(false) }

    if (isEditing) {
        HostEditorScreen(
            host = host,
            onBack = { isEditing = false },
            onSubmit = { input ->
                onSave(input)
                isEditing = false
            },
            onConnect = onConnect,
        )
    } else {
        HostDetailScreen(
            host = host,
            onBack = onBack,
            onEdit = { isEditing = true },
            onConnect = onConnect,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostDetailScreen(
    host: SSHHostEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onConnect: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
                    ) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                title = {
                    Text("Host")
                },
                actions = {
                    TextButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("Edit")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onConnect,
                    ) {
                        Text("Connect")
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Text(
                text = host.server_name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${host.username}@${host.hostname}:${host.port}",
                modifier = Modifier.padding(top = Spacing.xxs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HostDetailSectionLabel("Connection")
            HostDetailRow("Host name", host.hostname)
            HostDetailRow("Port", host.port.toString())
            HostDetailRow("Username", host.username)

            HostDetailSectionLabel("Security")
            HostDetailRow(
                "Server signature",
                host.server_signature ?: "Not trusted yet",
            )
        }
    }
}

@Composable
private fun HostDetailSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun HostDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
