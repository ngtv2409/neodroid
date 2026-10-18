package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.sharedComponents.ValidatedTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailHostScreen(viewModel: SSHHostViewModel, hostId: Int, onConnect: () -> Unit) {
    val hostHandler by viewModel.getHostById(
        hostId,
    ).collectAsStateWithLifecycle(initialValue = null)
    val host = hostHandler ?: return

    var serverName by remember { mutableStateOf(host.server_name) }
    var hostname by remember { mutableStateOf(host.hostname) }
    var portStr by remember { mutableStateOf(host.port.toString()) }
    var serverSignature by remember { mutableStateOf(host.server_signature ?: "") }
    var username by remember { mutableStateOf(host.username) }

    val serverNameValidator = { value: String ->
        when {
            value.isBlank() -> "Server name is required"
            else -> null
        }
    }
    val hostnameValidator = { value: String ->
        when {
            value.isBlank() -> "Host name is required"
            else -> null
        }
    }
    val portValidator = { value: String ->
        val conv = value.toIntOrNull()
        when {
            value.isBlank() -> "Port is required"
            conv == null -> "Port must be an integer between 1 and 65535"
            conv !in 1..65535 -> "Port must be an integer between 1 and 65535"
            else -> null
        }
    }
    val usernameValidator = { value: String ->
        when {
            value.isBlank() -> "Username is required"
            else -> null
        }
    }

    val canSubmit = serverNameValidator(serverName) == null &&
        hostnameValidator(hostname) == null &&
        portValidator(portStr) == null &&
        usernameValidator(username) == null
    val isChanged = serverName != host.server_name ||
        hostname != host.hostname ||
        portStr != host.port.toString() ||
        username != host.username ||
        serverSignature != (host.server_signature ?: "")

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(host.server_name)
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit && !isChanged,
                    onClick = {
                        onConnect()
                    },
                ) {
                    Text("Connect")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = canSubmit && isChanged,
                    onClick = {
                        viewModel.updateHost(
                            host.copy(
                                server_name = serverName.trim(),
                                hostname = hostname.trim(),
                                port = portStr.trim().toInt(),
                                server_signature = serverSignature.trim().ifBlank { null },
                                username = username.trim(),
                            ),
                        )
                    },
                ) {
                    Text("Save")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(2.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ValidatedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = "Server name*",
                    maxLength = 100,
                    validator = serverNameValidator,
                )
                ValidatedTextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = "Host name*",
                    maxLength = null,
                    validator = hostnameValidator,
                )
                ValidatedTextField(
                    value = portStr,
                    onValueChange = {
                        portStr = it
                    },
                    label = "Port*",
                    maxLength = null,
                    validator = portValidator,
                )
                ValidatedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Username",
                    maxLength = null,
                    validator = usernameValidator,
                )
                ValidatedTextField(
                    value = serverSignature,
                    onValueChange = { serverSignature = it },
                    label = "Server signature (optional)",
                    maxLength = null,
                    validator = { value ->
                        null // optional
                    },
                )
            }
        }
    }
}
