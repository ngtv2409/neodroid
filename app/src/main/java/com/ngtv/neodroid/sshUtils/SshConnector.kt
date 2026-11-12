package com.ngtv.neodroid.sshUtils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient

data class SshConnectResult(
    val client: SSHClient,
    val hostKeyFingerprint: String?,
    val hostKeyLearned: Boolean,
)

/**
 * Builds authenticated SSH clients with conservative defaults.
 *
 * Only this class knows how to configure a raw [SSHClient]; the rest of the app
 * works with the higher-level [SshConnection] wrapper.
 */
class SshConnector(
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val handshakeTimeoutMs: Int = DEFAULT_HANDSHAKE_TIMEOUT_MS,
    private val keepAliveSeconds: Int = DEFAULT_KEEP_ALIVE_SECONDS,
) {
    suspend fun connect(config: SshConnectionConfig): SshConnectResult =
        withContext(Dispatchers.IO) {
            val client = SSHClient()
            var verifier: TofuHostKeyVerifier? = null

            try {
                verifier = TofuHostKeyVerifier(config.knownHostKeyFingerprint)
                client.connectTimeout = connectTimeoutMs
                // Bound the handshake/auth phase. Once authenticated, an idle
                // Neovim session may legitimately receive no bytes for a long
                // time, so the read timeout is cleared below.
                client.timeout = handshakeTimeoutMs
                client.addHostKeyVerifier(verifier)

                client.connect(config.hostname, config.port)
                runCatching { client.socket.tcpNoDelay = true }
                runCatching {
                    client.connection.keepAlive.keepAliveInterval = keepAliveSeconds
                }

                client.authPassword(config.username, config.password)
                client.timeout = 0

                SshConnectResult(
                    client = client,
                    hostKeyFingerprint = verifier.observedFingerprint,
                    hostKeyLearned = config.knownHostKeyFingerprint == null,
                )
            } catch (e: CancellationException) {
                runCatching { client.close() }
                throw e
            } catch (e: Exception) {
                val expected = config.knownHostKeyFingerprint
                val observed = verifier?.observedFingerprint
                val accepted = verifier?.accepted ?: false

                runCatching { client.close() }

                if (expected != null && observed != null && !accepted) {
                    throw HostKeyMismatchException(
                        expectedFingerprint = expected,
                        actualFingerprint = observed,
                        cause = e,
                    )
                }

                throw e
            }
        }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        private const val DEFAULT_HANDSHAKE_TIMEOUT_MS = 20_000
        private const val DEFAULT_KEEP_ALIVE_SECONDS = 30
    }
}
