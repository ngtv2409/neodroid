package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.sharedComponents.ValidatedTextField
import com.ngtv.neodroid.ui.theme.Spacing

private fun validateServerName(value: String): String? =
    if (value.isBlank()) "Server name is required" else null

private fun validateHostname(value: String): String? =
    if (value.isBlank()) "Host name is required" else null

private fun validatePort(value: String): String? {
    val port = value.toIntOrNull()
    return when {
        value.isBlank() -> "Port is required"
        port == null || port !in 1..65535 -> "Port must be an integer between 1 and 65535"
        else -> null
    }
}

private fun validateUsername(value: String): String? =
    if (value.isBlank()) "Username is required" else null

/**
 * Shared editor for creating and editing SSH hosts.
 *
 * Add and edit remain separate destinations because they have different intents
 * (creation vs. connecting to an existing host), but they share the same layout so
 * both flows stay consistent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(
    host: SSHHostEntity?,
    onBack: () -> Unit,
    onSubmit: (SSHHostViewModel.InputModel) -> Unit,
    onConnect: (() -> Unit)? = null,
) {
    var serverName by remember(host?.id) { mutableStateOf(host?.server_name ?: "") }
    var hostname by remember(host?.id) { mutableStateOf(host?.hostname ?: "") }
    var portStr by remember(host?.id) { mutableStateOf(host?.port?.toString() ?: "22") }
    var serverSignature by remember(host?.id) { mutableStateOf(host?.server_signature ?: "") }
    var username by remember(host?.id) { mutableStateOf(host?.username ?: "") }

    val canSubmit = validateServerName(serverName) == null &&
        validateHostname(hostname) == null &&
        validatePort(portStr) == null &&
        validateUsername(username) == null

    val isChanged = host == null ||
        serverName != host.server_name ||
        hostname != host.hostname ||
        portStr != host.port.toString() ||
        username != host.username ||
        serverSignature != (host.server_signature ?: "")

    val submit = {
        onSubmit(
            SSHHostViewModel.InputModel(
                serverName = serverName.trim(),
                hostname = hostname.trim(),
                port = portStr.trim().toInt(),
                serverSignature = serverSignature.trim().ifBlank { null },
                username = username.trim(),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    Text(if (host == null) "Add host" else "Edit host")
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (host == null) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = canSubmit,
                            onClick = submit,
                        ) {
                            Text("Add host")
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = canSubmit && isChanged,
                            onClick = submit,
                        ) {
                            Text("Save")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = canSubmit && !isChanged && onConnect != null,
                            onClick = { onConnect?.invoke() },
                        ) {
                            Text("Connect")
                        }
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
                .imePadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionLabel("Display name")
            ValidatedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = "Server name",
                maxLength = 100,
                validator = ::validateServerName,
            )

            SectionLabel("Connection", modifier = Modifier.padding(top = Spacing.sm))
            ValidatedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = "Host name",
                maxLength = null,
                validator = ::validateHostname,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ValidatedTextField(
                    value = portStr,
                    onValueChange = { portStr = it },
                    label = "Port",
                    maxLength = null,
                    validator = ::validatePort,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                ValidatedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username",
                    maxLength = null,
                    validator = ::validateUsername,
                    modifier = Modifier.weight(2f),
                )
            }

            SectionLabel("Security", modifier = Modifier.padding(top = Spacing.sm))
            ValidatedTextField(
                value = serverSignature,
                onValueChange = { serverSignature = it },
                label = "Server signature (optional)",
                maxLength = null,
                validator = { null },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
    )
}
