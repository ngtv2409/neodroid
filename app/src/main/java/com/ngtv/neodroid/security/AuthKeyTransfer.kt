package com.ngtv.neodroid.security

import com.ngtv.neodroid.database.AuthKeyEntity
import java.security.KeyPair

enum class KeyFileFormat {
    OPENSSH,
    PEM,
    BUNDLE,
}

data class KeyFileDetection(val format: KeyFileFormat, val encrypted: Boolean)

data class ImportCandidate(
    val keyPair: KeyPair,
    val displayName: String,
    val algorithm: AuthKeyAlgorithm,
    val fingerprint: String?,
    val alreadyExists: Boolean,
)

sealed interface ImportInspection {
    data class NeedsPassphrase(val format: KeyFileFormat) : ImportInspection

    data class Ready(val candidates: List<ImportCandidate>) : ImportInspection
}

data class KeyBundleEntry(
    val displayName: String,
    val algorithm: String,
    val publicKey: String,
    val privateKeyPkcs8: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyBundleEntry) return false

        return displayName == other.displayName &&
            algorithm == other.algorithm &&
            publicKey == other.publicKey &&
            privateKeyPkcs8.contentEquals(other.privateKeyPkcs8)
    }

    override fun hashCode(): Int = privateKeyPkcs8.contentHashCode()
}

enum class KeyExportFormat {
    OPENSSH,
    BUNDLE,
}

data class KeyExportRequest(
    val keys: List<AuthKeyEntity>,
    val format: KeyExportFormat,
    val passphrase: String?,
)

class KeyTransferException(message: String, cause: Throwable? = null) : Exception(message, cause)
