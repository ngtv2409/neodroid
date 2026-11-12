package com.ngtv.neodroid.sshUtils

import android.util.Base64
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.FingerprintVerifier
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey

object SshHostKeyFingerprint {
    /**
     * SHA-256 fingerprint in the format understood by sshj, e.g.
     * `SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=`.
     */
    fun sha256(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Buffer.PlainBuffer().putPublicKey(key).compactData)
        val encoded = Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
        return "SHA256:$encoded"
    }
}

/**
 * Trust-on-first-use host key verification.
 *
 * When [expectedFingerprint] is null the first key is accepted and recorded in
 * [observedFingerprint]. The caller persists it only after authentication succeeds.
 * When a fingerprint is known, sshj's [FingerprintVerifier] is used so existing
 * SHA256/SHA1/MD5 fingerprints from the host editor keep working.
 */
class TofuHostKeyVerifier(private val expectedFingerprint: String?) : HostKeyVerifier {

    private val delegate = expectedFingerprint?.let(FingerprintVerifier::getInstance)

    @Volatile
    var observedFingerprint: String? = null
        private set

    @Volatile
    var accepted: Boolean = false
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        observedFingerprint = SshHostKeyFingerprint.sha256(key)
        accepted = delegate?.verify(hostname, port, key) ?: true
        return accepted
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
        delegate?.findExistingAlgorithms(hostname, port) ?: emptyList()
}
