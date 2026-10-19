package com.ngtv.neodroid.main.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ngtv.neodroid.database.SSHHostEntity
import com.ngtv.neodroid.sharedComponents.PasswordInput
import com.ngtv.neodroid.sshUtils.SshSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException

@Composable
fun WorkspacePrepareClient(host: SSHHostEntity, sessionManager: SshSessionManager) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var connectEnable by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Connect to server",
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "Enter your credentials to establish a secure SSH connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    top = 8.dp,
                    bottom = 24.dp,
                ),
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                ) {
                    Text(
                        text = host.server_name,
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Text(
                        text = "${host.username}@${host.hostname}:${host.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Text(
                text = "Authentication",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    top = 24.dp,
                    bottom = 12.dp,
                ),
            )

            PasswordInput(
                password = password,
                onPasswordChange = {
                    password = it
                    errorMessage = null
                },
            )

            Button(
                onClick = {
                    if (!connectEnable) return@Button

                    errorMessage = null
                    connectEnable = false

                    coroutineScope.launch {
                        var client: SSHClient? = null
                        var connectionTransferred = false

                        try {
                            withContext(Dispatchers.IO) {
                                client = SSHClient()

                                if (host.server_signature == null) {
                                    // WARN: TODO: Temporary workaround. Should use first time trust when fingerprint is not provided
                                    client.addHostKeyVerifier(PromiscuousVerifier())
                                } else {
                                    client.addHostKeyVerifier(host.server_signature)
                                }

                                client.connect(host.hostname, host.port)
                                client.authPassword(host.username, password)

                                // bind the connected client to the session manager
                                // sessionManager.bind(client!!)
                                connectionTransferred = true
                            }
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
                        } catch (e: Exception) {
                            errorMessage = "Connection failed: $e"
                        } finally {
                            if (!connectionTransferred) {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        client?.close()
                                    }
                                }
                            }

                            connectEnable = true
                        }
                    }
                },
                enabled = password.isNotBlank() && connectEnable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(if (connectEnable) "Connect" else "Connecting...")
            }
        }

        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
