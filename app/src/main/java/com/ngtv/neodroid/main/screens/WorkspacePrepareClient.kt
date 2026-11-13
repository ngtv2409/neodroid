package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.sharedComponents.PasswordInput
import com.ngtv.neodroid.sshUtils.HostKeyMismatchException
import com.ngtv.neodroid.sshUtils.SshConnectionConfig
import com.ngtv.neodroid.sshUtils.SshException
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspacePrepareClient(
    host: SSHHostEntity,
    sessionManager: SshSessionManager,
    onBack: () -> Unit,
    onConnect: (connectionId: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                title = {
                    Text("Connect")
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    errorMessage?.let { error ->
                        ErrorCard(error)
                    }

                    Button(
                        onClick = {
                            if (isConnecting) return@Button

                            errorMessage = null
                            isConnecting = true

                            coroutineScope.launch {
                                try {
                                    val connection = withContext(Dispatchers.IO) {
                                        val connection =
                                            sessionManager.connect(
                                                SshConnectionConfig(
                                                    hostId = host.id,
                                                    serverName = host.server_name,
                                                    hostname = host.hostname,
                                                    port = host.port,
                                                    username = host.username,
                                                    password = password,
                                                    knownHostKeyFingerprint =
                                                        host.server_signature,
                                                ),
                                            )

                                        try {
                                            sessionManager.openWorkspace(connection.id)
                                            connection
                                        } catch (e: CancellationException) {
                                            sessionManager.closeConnection(connection.id)
                                            throw e
                                        } catch (e: Exception) {
                                            sessionManager.closeConnection(connection.id)
                                            throw e
                                        }
                                    }

                                    onConnect(connection.id)
                                } catch (e: HostKeyMismatchException) {
                                    errorMessage =
                                        "Server identity changed. Expected " +
                                        "${e.expectedFingerprint}, but the server " +
                                        "presented ${e.actualFingerprint}."
                                } catch (e: TransportException) {
                                    errorMessage = when (e.disconnectReason) {
                                        DisconnectReason.HOST_KEY_NOT_VERIFIABLE ->
                                            "Server identity could not be verified."

                                        else ->
                                            "Connection failed: ${e.message}"
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: UserAuthException) {
                                    errorMessage = "Authentication failed: ${e.message}"
                                } catch (e: SshException) {
                                    errorMessage = e.message ?: "Connection failed"
                                } catch (e: Exception) {
                                    errorMessage = "Connection failed: $e"
                                } finally {
                                    isConnecting = false
                                }
                            }
                        },
                        enabled = password.isNotBlank() && !isConnecting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            Text("Connecting…")
                        } else {
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            HostSummaryCard(host)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel("Authentication")
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        PasswordInput(
                            password = password,
                            onPasswordChange = {
                                password = it
                                errorMessage = null
                            },
                            enabled = !isConnecting,
                        )
                        Text(
                            text = "Your password is used only for this connection " +
                                "and is not stored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel("Host identity")
                HostIdentityCard(host)
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun HostSummaryCard(host: SSHHostEntity) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host.server_name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "${host.username}@${host.hostname}:${host.port}",
                    modifier = Modifier.padding(top = Spacing.xxs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HostIdentityCard(host: SSHHostEntity) {
    val pinnedFingerprint = host.server_signature

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = if (pinnedFingerprint == null) {
                    "First connection"
                } else {
                    "Pinned host key"
                },
                style = MaterialTheme.typography.titleSmall,
            )

            Text(
                text = if (pinnedFingerprint == null) {
                    "This host has no stored key yet. After a successful login, " +
                        "Neodroid will remember the server's fingerprint."
                } else {
                    "The server must present the same fingerprint before " +
                        "authentication can continue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (pinnedFingerprint != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = pinnedFingerprint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
