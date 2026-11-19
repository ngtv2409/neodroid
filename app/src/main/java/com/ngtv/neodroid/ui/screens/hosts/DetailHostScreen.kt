package com.ngtv.neodroid.ui.screens.hosts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.security.AuthKeyAlgorithm
import com.ngtv.neodroid.ui.components.BottomActionBar
import com.ngtv.neodroid.ui.components.DetailRow
import com.ngtv.neodroid.ui.components.NeodroidTopBar
import com.ngtv.neodroid.ui.components.SectionLabel
import com.ngtv.neodroid.ui.screens.authkeys.authKeyFingerprint
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
fun DetailHostScreen(
    viewModel: SSHHostViewModel,
    hostId: Int,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    authKeys: List<AuthKeyEntity> = emptyList(),
) {
    val hostHandler by viewModel.getHostById(
        hostId,
    ).collectAsStateWithLifecycle(initialValue = null)
    val host = hostHandler ?: return

    HostDetailPane(
        host = host,
        onBack = onBack,
        onConnect = onConnect,
        authKeys = authKeys,
        onSave = { input ->
            viewModel.updateHost(
                host.copy(
                    server_name = input.serverName,
                    hostname = input.hostname,
                    port = input.port,
                    server_signature = input.serverSignature,
                    username = input.username,
                    auth_key_id = input.authKeyId,
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
    authKeys: List<AuthKeyEntity> = emptyList(),
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
            authKeys = authKeys,
        )
    } else {
        HostDetailScreen(
            host = host,
            onBack = onBack,
            onEdit = { isEditing = true },
            onConnect = onConnect,
            authKeys = authKeys,
        )
    }
}

@Composable
private fun HostDetailScreen(
    host: SSHHostEntity,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onConnect: () -> Unit,
    authKeys: List<AuthKeyEntity>,
) {
    val authKey = authKeys.firstOrNull { it.id == host.auth_key_id }

    Scaffold(
        topBar = {
            NeodroidTopBar(
                title = "Host",
                onBack = onBack,
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
            BottomActionBar {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onConnect,
                ) {
                    Text("Connect")
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

            SectionLabel(
                "Connection",
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
            )
            DetailRow("Host name", host.hostname)
            DetailRow("Port", host.port.toString())
            DetailRow("Username", host.username)

            SectionLabel(
                "Security",
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
            )
            DetailRow(
                "Server signature",
                host.server_signature ?: "Not trusted yet",
            )
            SectionLabel(
                "Authentication",
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
            )
            if (authKey == null) {
                Text(
                    text = "Password only",
                    modifier = Modifier.width(110.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DetailRow(
                    "Key",
                    authKey.displayName.ifBlank { "unnamed" },
                )
                DetailRow(
                    "Type",
                    AuthKeyAlgorithm.displayName(authKey.algorithm),
                )
                DetailRow(
                    "Fingerprint",
                    authKeyFingerprint(authKey) ?: "Unavailable",
                )
            }
        }
    }
}
