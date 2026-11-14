package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.sharedComponents.PasswordInput
import com.ngtv.neodroid.sshUtils.HostKeyMismatchException
import com.ngtv.neodroid.sshUtils.SshConnectionConfig
import com.ngtv.neodroid.sshUtils.SshException
import com.ngtv.neodroid.sshUtils.SshSessionManager
import com.ngtv.neodroid.ui.theme.Spacing
import com.ngtv.neodroid.ui.theme.neodroidColors
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
    onTrustServer: suspend (fingerprint: String) -> Unit,
    onDiscardHostKey: suspend () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var identityWarning by remember { mutableStateOf<HostIdentityWarning?>(null) }
    var showTrustConfirmation by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        val error = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = error,
            duration = SnackbarDuration.Short,
        )
        errorMessage = null
    }

    fun connect(knownFingerprint: String?) {
        if (isConnecting) return

        errorMessage = null
        identityWarning = null
        showTrustConfirmation = false
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
                                knownHostKeyFingerprint = knownFingerprint,
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
                errorMessage = "Server identity changed. Check the host identity card."
                identityWarning =
                    HostIdentityWarning(
                        expectedFingerprint = e.expectedFingerprint,
                        actualFingerprint = e.actualFingerprint,
                    )
            } catch (e: TransportException) {
                when (e.disconnectReason) {
                    DisconnectReason.HOST_KEY_NOT_VERIFIABLE -> {
                        errorMessage =
                            "Server identity could not be verified. " +
                            "Check the host identity card."
                        identityWarning =
                            HostIdentityWarning(
                                expectedFingerprint = host.server_signature,
                                actualFingerprint = null,
                            )
                    }

                    else ->
                        errorMessage = "Connection failed: ${e.message}"
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
    }

    fun discardAndTrustServer() {
        val warning = identityWarning ?: return
        if (isConnecting) return

        val actualFingerprint = warning.actualFingerprint
        identityWarning = null
        errorMessage = null

        coroutineScope.launch {
            try {
                if (actualFingerprint != null) {
                    onTrustServer(actualFingerprint)
                    connect(actualFingerprint)
                } else {
                    onDiscardHostKey()
                    connect(null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Could not update the host key: ${e.message ?: e}"
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
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
                    Button(
                        onClick = { connect(host.server_signature) },
                        enabled = password.isNotBlank() && !isConnecting && identityWarning == null,
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
                HostIdentityCard(
                    host = host,
                    warning = identityWarning,
                    isConnecting = isConnecting,
                    onTrustRequested = { showTrustConfirmation = true },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }

    val warning = identityWarning
    if (showTrustConfirmation && warning != null) {
        TrustServerConfirmationDialog(
            host = host,
            warning = warning,
            onDismiss = { showTrustConfirmation = false },
            onConfirm = {
                showTrustConfirmation = false
                discardAndTrustServer()
            },
        )
    }
}

private data class HostIdentityWarning(
    val expectedFingerprint: String?,
    val actualFingerprint: String?,
)

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
private fun HostIdentityCard(
    host: SSHHostEntity,
    warning: HostIdentityWarning?,
    isConnecting: Boolean,
    onTrustRequested: () -> Unit,
) {
    val pinnedFingerprint = warning?.expectedFingerprint ?: host.server_signature
    val warningColor = MaterialTheme.neodroidColors.warning
    val isWarning = warning != null

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) {
                warningColor.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (isWarning) {
            BorderStroke(1.dp, warningColor.copy(alpha = 0.55f))
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = when {
                    warning != null -> "Server identity changed"
                    pinnedFingerprint == null -> "First connection"
                    else -> "Pinned host key"
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (isWarning) {
                    warningColor
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            Text(
                text = when {
                    warning != null ->
                        "The key presented by the server does not match the stored " +
                            "fingerprint. Only trust it if you expect this change."

                    pinnedFingerprint == null ->
                        "This host has no stored key yet. After a successful login, " +
                            "Neodroid will remember the server's fingerprint."

                    else ->
                        "The server must present the same fingerprint before " +
                            "authentication can continue."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isWarning) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (pinnedFingerprint != null) {
                FingerprintBlock(
                    label = if (isWarning) "Stored fingerprint" else null,
                    fingerprint = pinnedFingerprint,
                )
            }

            warning?.actualFingerprint?.let { actualFingerprint ->
                FingerprintBlock(
                    label = "Presented fingerprint",
                    fingerprint = actualFingerprint,
                )
            }

            if (warning != null) {
                if (warning.actualFingerprint == null) {
                    Text(
                        text = "The server's fingerprint could not be read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(
                    onClick = onTrustRequested,
                    enabled = !isConnecting,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        disabledContentColor = MaterialTheme.colorScheme.error.copy(
                            alpha = 0.38f,
                        ),
                    ),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Discard current key and trust server…")
                }
            }
        }
    }
}

@Composable
private fun TrustServerConfirmationDialog(
    host: SSHHostEntity,
    warning: HostIdentityWarning,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Trust this server?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "The key presented by ${host.server_name} does not match the " +
                        "stored fingerprint. Trusting it will discard the stored key and " +
                        "accept the server currently at ${host.hostname}:${host.port}.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                warning.actualFingerprint?.let { actualFingerprint ->
                    FingerprintBlock(
                        label = "Presented fingerprint",
                        fingerprint = actualFingerprint,
                    )
                }

                Text(
                    text = "Only continue if you verified this change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Discard and trust")
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
private fun FingerprintBlock(label: String?, fingerprint: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = fingerprint,
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = MaterialTheme.colorScheme.primary,
    )
}
