package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.security.AuthKeyMaterial
import com.ngtv.neodroid.sharedComponents.PasswordInput
import com.ngtv.neodroid.sshUtils.HostKeyMismatchException
import com.ngtv.neodroid.sshUtils.SshAuth
import com.ngtv.neodroid.sshUtils.SshConnectionConfig
import com.ngtv.neodroid.sshUtils.SshConnectionProbe
import com.ngtv.neodroid.sshUtils.SshException
import com.ngtv.neodroid.sshUtils.SshProbeConfig
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
    authKeyMaterial: AuthKeyMaterial,
    authKeys: List<AuthKeyEntity> = emptyList(),
    onBack: () -> Unit,
    onConnect: (connectionId: String) -> Unit,
    onAuthKeyUsed: (keyId: Int) -> Unit = {},
    onOpenAuthKeys: (() -> Unit)? = null,
    onTrustServer: suspend (fingerprint: String) -> Unit,
    onDiscardHostKey: suspend () -> Unit,
) {
    var password by remember(host.id) { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var identityWarning by remember { mutableStateOf<HostIdentityWarning?>(null) }
    var showTrustConfirmation by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var selectedMethod by remember(host.id) { mutableStateOf<AuthMethod?>(null) }
    var selectedKeyId by remember(host.id) { mutableStateOf(host.auth_key_id) }
    var probe by remember(host.id) { mutableStateOf<SshConnectionProbe?>(null) }
    var probeState by remember(host.id) { mutableStateOf<ProbeState>(ProbeState.Probing) }
    var probeKnownFingerprint by remember(host.id) { mutableStateOf(host.server_signature) }
    var probeAttempt by remember(host.id) { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val latestProbe by rememberUpdatedState(probe)

    LaunchedEffect(errorMessage) {
        val error = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = error,
            duration = SnackbarDuration.Short,
        )
        errorMessage = null
    }

    LaunchedEffect(host.server_signature) {
        probeKnownFingerprint = host.server_signature
    }

    LaunchedEffect(authKeys, host.auth_key_id) {
        if (selectedKeyId == null || authKeys.none { it.id == selectedKeyId }) {
            selectedKeyId =
                authKeys.firstOrNull { it.id == host.auth_key_id }?.id
                    ?: authKeys.firstOrNull()?.id
        }
    }

    fun selectDefaults(info: SshConnectionProbe) {
        val supported = supportedMethods(info.allowedMethods)
        val hostKey = authKeys.firstOrNull { it.id == host.auth_key_id }

        if (selectedKeyId == null || authKeys.none { it.id == selectedKeyId }) {
            selectedKeyId = hostKey?.id ?: authKeys.firstOrNull()?.id
        }

        selectedMethod =
            when {
                info.authenticated -> null

                hostKey != null && AuthMethod.PUBLIC_KEY in supported ->
                    AuthMethod.PUBLIC_KEY

                AuthMethod.PASSWORD in supported -> AuthMethod.PASSWORD

                AuthMethod.PUBLIC_KEY in supported -> AuthMethod.PUBLIC_KEY

                AuthMethod.KEYBOARD_INTERACTIVE in supported ->
                    AuthMethod.KEYBOARD_INTERACTIVE

                else -> supported.firstOrNull()
            }
    }

    LaunchedEffect(host.id, probeAttempt) {
        probe?.close()
        probe = null
        probeState = ProbeState.Probing
        identityWarning = null

        try {
            val info =
                sessionManager.probe(
                    SshProbeConfig(
                        hostname = host.hostname,
                        port = host.port,
                        username = host.username,
                        knownHostKeyFingerprint = probeKnownFingerprint,
                    ),
                )
            probe = info
            probeState = ProbeState.Ready(info)
            selectDefaults(info)
        } catch (e: HostKeyMismatchException) {
            identityWarning =
                HostIdentityWarning(
                    expectedFingerprint = e.expectedFingerprint,
                    actualFingerprint = e.actualFingerprint,
                )
            probeState =
                ProbeState.Failed("Server identity changed. Check the host identity card.")
        } catch (e: TransportException) {
            when (e.disconnectReason) {
                DisconnectReason.HOST_KEY_NOT_VERIFIABLE -> {
                    identityWarning =
                        HostIdentityWarning(
                            expectedFingerprint = probeKnownFingerprint,
                            actualFingerprint = null,
                        )
                    probeState =
                        ProbeState.Failed(
                            "Server identity could not be verified. " +
                                "Check the host identity card.",
                        )
                }

                else -> probeState = ProbeState.Failed("Connection failed: ${e.message}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserAuthException) {
            probeState = ProbeState.Failed("Authentication failed: ${e.message}")
        } catch (e: SshException) {
            probeState = ProbeState.Failed(e.message ?: "Connection failed")
        } catch (e: Exception) {
            probeState = ProbeState.Failed("Connection failed: $e")
        }
    }

    DisposableEffect(host.id) {
        onDispose {
            latestProbe?.close()
        }
    }

    fun connect() {
        if (isConnecting) return
        val ready = probeState as? ProbeState.Ready ?: return
        val method = selectedMethod

        isConnecting = true
        errorMessage = null
        identityWarning = null
        showTrustConfirmation = false

        coroutineScope.launch {
            try {
                val authKeyId = method.takeIf { it == AuthMethod.PUBLIC_KEY }?.let {
                    selectedKeyId
                }
                val connection =
                    withContext(Dispatchers.IO) {
                        val auth =
                            if (ready.probe.authenticated) {
                                SshAuth.None
                            } else {
                                when (method) {
                                    AuthMethod.PASSWORD -> SshAuth.Password(password)

                                    AuthMethod.KEYBOARD_INTERACTIVE ->
                                        SshAuth.KeyboardInteractive(password)

                                    AuthMethod.PUBLIC_KEY -> {
                                        val key =
                                            authKeys.firstOrNull { it.id == selectedKeyId }
                                                ?: throw SshException(
                                                    "Select an authentication key",
                                                )
                                        SshAuth.PublicKey(authKeyMaterial.keyProvider(key))
                                    }

                                    null -> throw SshException("Select an authentication method")
                                }
                            }

                        val connection =
                            sessionManager.connect(
                                SshConnectionConfig(
                                    hostId = host.id,
                                    serverName = host.server_name,
                                    hostname = host.hostname,
                                    port = host.port,
                                    username = host.username,
                                    auth = auth,
                                    knownHostKeyFingerprint = probeKnownFingerprint,
                                ),
                                ready.probe,
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

                if (authKeyId != null) {
                    onAuthKeyUsed(authKeyId)
                }
                onConnect(connection.id)
            } catch (e: HostKeyMismatchException) {
                identityWarning =
                    HostIdentityWarning(
                        expectedFingerprint = e.expectedFingerprint,
                        actualFingerprint = e.actualFingerprint,
                    )
                probe = null
                probeState =
                    ProbeState.Failed("Server identity changed. Check the host identity card.")
            } catch (e: TransportException) {
                when (e.disconnectReason) {
                    DisconnectReason.HOST_KEY_NOT_VERIFIABLE -> {
                        identityWarning =
                            HostIdentityWarning(
                                expectedFingerprint = probeKnownFingerprint,
                                actualFingerprint = null,
                            )
                        probe = null
                        probeState =
                            ProbeState.Failed(
                                "Server identity could not be verified. " +
                                    "Check the host identity card.",
                            )
                    }

                    else -> {
                        probe = null
                        probeState = ProbeState.Failed("Connection failed: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UserAuthException) {
                // The probe transport stays open, so the user can correct the
                // password/key and retry without another SSH handshake.
                errorMessage = "Authentication failed: ${e.message}"
            } catch (e: SshException) {
                probe = null
                probeState = ProbeState.Failed(e.message ?: "Connection failed")
            } catch (e: Exception) {
                probe = null
                probeState = ProbeState.Failed("Connection failed: $e")
            } finally {
                isConnecting = false
            }
        }
    }

    fun discardAndTrustServer() {
        val warning = identityWarning ?: return
        if (isConnecting) return

        val actualFingerprint = warning.actualFingerprint
        errorMessage = null

        coroutineScope.launch {
            try {
                if (actualFingerprint != null) {
                    onTrustServer(actualFingerprint)
                } else {
                    onDiscardHostKey()
                }
                identityWarning = null
                probeKnownFingerprint = actualFingerprint
                probeAttempt++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Could not update the host key: ${e.message ?: e}"
            }
        }
    }

    val readyProbe = (probeState as? ProbeState.Ready)?.probe
    val selectedKey = authKeys.firstOrNull { it.id == selectedKeyId }
    val noAuthRequired = readyProbe?.authenticated == true
    val canConnect =
        readyProbe != null &&
            !isConnecting &&
            identityWarning == null &&
            (
                noAuthRequired ||
                    when (selectedMethod) {
                        AuthMethod.PASSWORD,
                        AuthMethod.KEYBOARD_INTERACTIVE,
                        -> password.isNotBlank()

                        AuthMethod.PUBLIC_KEY -> selectedKey != null

                        null -> false
                    }
                )

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
                        onClick = ::connect,
                        enabled = canConnect,
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
                SectionLabel("Server")
                ServerInfoCard(
                    probeState = probeState,
                    observedFingerprint = readyProbe?.hostKeyFingerprint,
                    onRetry = {
                        errorMessage = null
                        probeAttempt++
                    },
                )
            }

            if (readyProbe != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionLabel("Authentication")
                    if (noAuthRequired) {
                        NoAuthRequiredCard()
                    } else {
                        AuthMethodSelection(
                            probe = readyProbe,
                            selectedMethod = selectedMethod,
                            onSelectMethod = { selectedMethod = it },
                            password = password,
                            onPasswordChange = {
                                password = it
                                errorMessage = null
                            },
                            authKeys = authKeys,
                            selectedKeyId = selectedKeyId,
                            onSelectKey = { selectedKeyId = it },
                            selectedKey = selectedKey,
                            enabled = !isConnecting,
                            onOpenAuthKeys = onOpenAuthKeys,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SectionLabel("Host identity")
                HostIdentityCard(
                    host = host,
                    warning = identityWarning,
                    observedFingerprint = readyProbe?.hostKeyFingerprint,
                    isProbing = probeState is ProbeState.Probing,
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

private enum class AuthMethod(val protocolName: String) {
    PASSWORD("password"),
    PUBLIC_KEY("publickey"),
    KEYBOARD_INTERACTIVE("keyboard-interactive"),
}

private sealed interface ProbeState {
    data object Probing : ProbeState

    data class Ready(val probe: SshConnectionProbe) : ProbeState

    data class Failed(val message: String) : ProbeState
}

private data class HostIdentityWarning(
    val expectedFingerprint: String?,
    val actualFingerprint: String?,
)

private fun supportedMethods(allowedMethods: Set<String>): List<AuthMethod> =
    AuthMethod.entries.filter { it.protocolName in allowedMethods }

@Composable
private fun ServerInfoCard(
    probeState: ProbeState,
    observedFingerprint: String?,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when (probeState) {
                ProbeState.Probing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Contacting server…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is ProbeState.Failed -> {
                    Text(
                        text = "Could not inspect the server",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = probeState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Retry")
                    }
                }

                is ProbeState.Ready -> {
                    val info = probeState.probe
                    Text(
                        text = "Server is reachable",
                        style = MaterialTheme.typography.titleSmall,
                    )

                    info.banner?.takeIf { it.isNotBlank() }?.let { banner ->
                        Text(
                            text = banner.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (info.authenticated) {
                        Text(
                            text = "This server does not require authentication.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (info.allowedMethods.isEmpty()) {
                        Text(
                            text = "The server did not advertise any supported methods.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Advertised methods",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = info.allowedMethods.sorted().joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        )

                        val unsupported =
                            info.allowedMethods
                                .filterNot { name ->
                                    AuthMethod.entries.any {
                                        it.protocolName ==
                                            name
                                    }
                                }
                                .sorted()
                        if (unsupported.isNotEmpty()) {
                            Text(
                                text = "Not supported by Neodroid: ${unsupported.joinToString(
                                    ", ",
                                )}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    observedFingerprint?.let { fingerprint ->
                        Text(
                            text = "Host key: $fingerprint",
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
}

@Composable
private fun NoAuthRequiredCard() {
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
                text = "No credentials needed",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "The server accepted the connection without authentication.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AuthMethodSelection(
    probe: SshConnectionProbe,
    selectedMethod: AuthMethod?,
    onSelectMethod: (AuthMethod) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    authKeys: List<AuthKeyEntity>,
    selectedKeyId: Int?,
    onSelectKey: (Int?) -> Unit,
    selectedKey: AuthKeyEntity?,
    enabled: Boolean,
    onOpenAuthKeys: (() -> Unit)?,
) {
    val methods = supportedMethods(probe.allowedMethods)

    if (methods.isEmpty()) {
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
                    text = "No supported authentication method",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "The server offers ${probe.allowedMethods.joinToString(", ")}. " +
                        "Neodroid can use password, keyboard-interactive, or public-key " +
                        "authentication.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        methods.forEach { method ->
            val selected = method == selectedMethod
            val methodEnabled =
                enabled &&
                    when (method) {
                        AuthMethod.PUBLIC_KEY -> authKeys.isNotEmpty()
                        else -> true
                    }

            AuthMethodOption(
                title = method.title,
                selected = selected,
                enabled = methodEnabled,
                onClick = { onSelectMethod(method) },
            ) {
                when (method) {
                    AuthMethod.PASSWORD,
                    AuthMethod.KEYBOARD_INTERACTIVE,
                    -> {
                        PasswordInput(
                            password = password,
                            onPasswordChange = onPasswordChange,
                            enabled = enabled,
                        )
                    }

                    AuthMethod.PUBLIC_KEY -> {
                        AuthKeySelector(
                            keys = authKeys,
                            selectedKeyId = selectedKeyId,
                            onSelect = onSelectKey,
                            noneLabel = "Select an authentication key",
                            noneDetail = if (authKeys.isEmpty()) {
                                "No keys available"
                            } else {
                                "Tap to choose a key"
                            },
                        )

                        if (onOpenAuthKeys != null) {
                            TextButton(
                                onClick = onOpenAuthKeys,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Manage authentication keys")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthMethodOption(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (selected) {
                Column(
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.lg,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    content = content,
                )
            }
        }
    }
}

private val AuthMethod.title: String
    get() = when (this) {
        AuthMethod.PASSWORD -> "Password"
        AuthMethod.PUBLIC_KEY -> "Public key"
        AuthMethod.KEYBOARD_INTERACTIVE -> "Keyboard interactive"
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
private fun HostIdentityCard(
    host: SSHHostEntity,
    warning: HostIdentityWarning?,
    observedFingerprint: String?,
    isProbing: Boolean,
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
            } else if (observedFingerprint != null) {
                FingerprintBlock(
                    label = "Presented fingerprint",
                    fingerprint = observedFingerprint,
                )
            }

            warning?.actualFingerprint?.let { actualFingerprint ->
                FingerprintBlock(
                    label = "Presented fingerprint",
                    fingerprint = actualFingerprint,
                )
            }

            if (isProbing) {
                Text(
                    text = "Checking the host key…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
