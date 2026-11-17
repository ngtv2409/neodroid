package com.ngtv.neodroid.sshUtils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthNone
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

data class SshConnectResult(
    val client: SSHClient,
    val hostKeyFingerprint: String?,
    val hostKeyLearned: Boolean,
)

/**
 * Infomation we probed before asking for authentication
 */
class SshConnectionProbe internal constructor(
    internal val client: SSHClient,
    val hostKeyFingerprint: String?,
    val hostKeyLearned: Boolean,
    val allowedMethods: Set<String>,
    val banner: String?,
    internal val authenticated: Boolean,
) : Closeable {

    private val consumed = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    internal fun consume(): SSHClient {
        check(!closed.get()) { "SSH probe has already been closed" }
        consumed.set(true)
        return client
    }

    override fun close() {
        if (consumed.get()) return
        if (closed.compareAndSet(false, true)) {
            runCatching { client.close() }
        }
    }
}

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
    /**
     * Connects and verifies the host key, then asks the server which authentication
     * methods it accepts.
     */
    suspend fun probe(config: SshProbeConfig): SshConnectionProbe = withContext(Dispatchers.IO) {
        val client = SSHClient()
        val verifier = TofuHostKeyVerifier(config.knownHostKeyFingerprint)

        try {
            openTransport(client, verifier, config.hostname, config.port)

            var authenticated = false
            try {
                client.auth(config.username, AuthNone())
                authenticated = client.isAuthenticated
            } catch (e: UserAuthException) {
                // Expected on servers that require authentication. sshj has
                // populated the allowed-method list by the time this is thrown
            }

            SshConnectionProbe(
                client = client,
                hostKeyFingerprint = verifier.observedFingerprint,
                hostKeyLearned = config.knownHostKeyFingerprint == null,
                allowedMethods = client.userAuth.allowedMethods.toSet(),
                banner = client.userAuth.banner,
                authenticated = authenticated,
            )
        } catch (e: CancellationException) {
            runCatching { client.close() }
            throw e
        } catch (e: Exception) {
            runCatching { client.close() }
            throw hostKeyMismatchOrOriginal(config.knownHostKeyFingerprint, verifier, e)
        }
    }

    suspend fun connect(config: SshConnectionConfig): SshConnectResult =
        withContext(Dispatchers.IO) {
            val client = SSHClient()
            val verifier = TofuHostKeyVerifier(config.knownHostKeyFingerprint)

            try {
                openTransport(client, verifier, config.hostname, config.port)
                authenticate(client, config.username, config.auth)
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
                runCatching { client.close() }
                throw hostKeyMismatchOrOriginal(config.knownHostKeyFingerprint, verifier, e)
            }
        }

    /**
     * Authenticates an existing transport. probe is reusable if auth failed
     */
    suspend fun connect(config: SshConnectionConfig, probe: SshConnectionProbe): SshConnectResult =
        withContext(Dispatchers.IO) {
            try {
                if (!probe.authenticated) {
                    authenticate(probe.client, config.username, config.auth)
                }
                probe.client.timeout = 0

                SshConnectResult(
                    client = probe.consume(),
                    hostKeyFingerprint = probe.hostKeyFingerprint,
                    hostKeyLearned = probe.hostKeyLearned,
                )
            } catch (e: CancellationException) {
                probe.close()
                throw e
            } catch (e: UserAuthException) {
                // Keep the transport alive so the user can retry the password/key.
                throw e
            } catch (e: Exception) {
                probe.close()
                throw e
            }
        }

    private fun openTransport(
        client: SSHClient,
        verifier: TofuHostKeyVerifier,
        hostname: String,
        port: Int,
    ) {
        client.connectTimeout = connectTimeoutMs
        client.timeout = handshakeTimeoutMs
        client.addHostKeyVerifier(verifier)

        client.connect(hostname, port)
        runCatching { client.socket.tcpNoDelay = true }
        runCatching {
            client.connection.keepAlive.keepAliveInterval = keepAliveSeconds
        }
    }

    private fun authenticate(client: SSHClient, username: String, auth: SshAuth) {
        when (auth) {
            SshAuth.None -> Unit

            is SshAuth.Password ->
                client.auth(username, AuthPassword(StaticPasswordFinder(auth.password)))

            is SshAuth.KeyboardInteractive ->
                client.auth(
                    username,
                    AuthKeyboardInteractive(
                        PasswordResponseProvider(StaticPasswordFinder(auth.password)),
                    ),
                )

            is SshAuth.PublicKey ->
                client.auth(username, AuthPublickey(auth.keyProvider))
        }
    }

    private fun hostKeyMismatchOrOriginal(
        expected: String?,
        verifier: TofuHostKeyVerifier,
        cause: Exception,
    ): Exception {
        val observed = verifier.observedFingerprint
        val accepted = verifier.accepted
        return if (expected != null && observed != null && !accepted) {
            HostKeyMismatchException(
                expectedFingerprint = expected,
                actualFingerprint = observed,
                cause = cause,
            )
        } else {
            cause
        }
    }

    private class StaticPasswordFinder(password: String) : PasswordFinder {
        private val password = password.toCharArray()

        override fun reqPassword(resource: Resource<*>?): CharArray = password.copyOf()

        override fun shouldRetry(resource: Resource<*>?): Boolean = false
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        private const val DEFAULT_HANDSHAKE_TIMEOUT_MS = 20_000
        private const val DEFAULT_KEEP_ALIVE_SECONDS = 30
    }
}
