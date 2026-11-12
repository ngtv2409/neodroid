package com.ngtv.neodroid.sshUtils

/**
 * Everything required to establish one SSH connection.
 */
data class SshConnectionConfig(
    val hostId: Int,
    val serverName: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val password: String,
    val knownHostKeyFingerprint: String? = null,
) {
    init {
        require(hostId > 0) { "hostId must reference a saved host" }
        require(serverName.isNotBlank()) { "Server name cannot be blank" }
        require(hostname.isNotBlank()) { "Host name cannot be blank" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(username.isNotBlank()) { "Username cannot be blank" }
    }
}

/** A connection that was used recently, newest first. */
data class RecentConnection(
    val hostId: Int,
    val serverName: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val lastConnectedAt: Long,
)

sealed interface SshConnectionState {
    data object Connected : SshConnectionState

    data class Disconnected(val reason: String? = null) : SshConnectionState

    data class Failed(val cause: Throwable) : SshConnectionState
}

open class SshException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class SshLimitException(message: String) : SshException(message)

/**
 * The server presented a host key that does not match the fingerprint stored
 */
class HostKeyMismatchException(
    val expectedFingerprint: String,
    val actualFingerprint: String,
    cause: Throwable? = null,
) : SshException(
    "Server host key changed (expected $expectedFingerprint, got $actualFingerprint)",
    cause,
)

/**
 * Called by SshSessionManager after a connection is authenticated. Implementations
 * can persist recency/TOFU data without the manager depending on Android or Room.
 */
interface SshConnectionHistory {
    suspend fun onConnected(connection: SshConnection, hostKeyLearned: Boolean)
}
