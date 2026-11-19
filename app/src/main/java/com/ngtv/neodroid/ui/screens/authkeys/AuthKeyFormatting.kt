package com.ngtv.neodroid.ui.screens.authkeys

import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.security.AuthKeyAlgorithm
import com.ngtv.neodroid.security.sshPublicKeyFingerprint

internal fun authKeyDisplayName(key: AuthKeyEntity): String =
    key.displayName.ifBlank { AuthKeyAlgorithm.displayName(key.algorithm) }

internal fun authKeySummary(key: AuthKeyEntity): String {
    val algorithm = AuthKeyAlgorithm.displayName(key.algorithm)
    val fingerprint = authKeyFingerprint(key) ?: return algorithm
    return "$algorithm • $fingerprint"
}

internal fun authKeyFingerprint(key: AuthKeyEntity): String? =
    sshPublicKeyFingerprint(key.publicKey)
