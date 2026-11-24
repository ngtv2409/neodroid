package com.ngtv.neodroid.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class AuthKeyAlgorithm(
    val storageValue: String,
    val displayName: String,
    val description: String,
    val curveName: String? = null,
) {
    ED25519(
        storageValue = "ssh-ed25519",
        displayName = "Ed25519",
        description = "Modern default, small and fast",
    ),
    RSA_4096(
        storageValue = "ssh-rsa",
        displayName = "RSA",
        description = "Widest compatibility, 4096-bit by default, slower to generate",
    ),
    ECDSA_P256(
        storageValue = "ecdsa-sha2-nistp256",
        displayName = "ECDSA P-256",
        description = "NIST P-256 curve",
        curveName = "secp256r1",
    ),
    ECDSA_P384(
        storageValue = "ecdsa-sha2-nistp384",
        displayName = "ECDSA P-384",
        description = "NIST P-384 curve",
        curveName = "secp384r1",
    ),
    ECDSA_P521(
        storageValue = "ecdsa-sha2-nistp521",
        displayName = "ECDSA P-521",
        description = "NIST P-521 curve",
        curveName = "secp521r1",
    ),
    ;

    companion object {
        fun fromStorageValue(value: String): AuthKeyAlgorithm? =
            entries.firstOrNull { it.storageValue == value }

        fun displayName(value: String): String = fromStorageValue(value)?.displayName ?: value
    }
}

data class GeneratedAuthKey(
    val algorithm: AuthKeyAlgorithm,
    val publicKey: String,
    val encryptedPrivateKey: String,
    val encryptedDek: String,
)

/**
 * Generates SSH key pairs and encrypts private key with a dek
 * The DEK is encrypted by the KEK stored in Android keystore
 */
class AuthKeyGenerator(
    private val keystore: KeystoreKeyManager,
    private val secureRandom: SecureRandom = SecureRandom(),
) {

    fun generate(algorithm: AuthKeyAlgorithm): GeneratedAuthKey =
        encode(generateKeyPair(algorithm), algorithm)

    /**
     * Re-wraps an imported key with a fresh DEK protected by this device's Keystore.
     */
    fun fromKeyPair(keyPair: KeyPair): GeneratedAuthKey {
        val algorithm = SshKeyFormat.algorithmFor(keyPair.public)
            ?: throw IllegalArgumentException(
                "Unsupported key type: ${keyPair.public.algorithm}",
            )
        return encode(keyPair, algorithm)
    }

    private fun encode(keyPair: KeyPair, algorithm: AuthKeyAlgorithm): GeneratedAuthKey {
        val privateKeyBytes = keyPair.private.encoded

        return try {
            val publicKey = SshKeyFormat.publicKeyLine(keyPair.public)
            val dataKey = ByteArray(DATA_KEY_BYTES).also(secureRandom::nextBytes)
            try {
                val encryptedPrivateKey = encrypt(privateKeyBytes, dataKey)
                val encryptedDek = keystore.wrap(dataKey)

                GeneratedAuthKey(
                    algorithm = algorithm,
                    publicKey = publicKey,
                    encryptedPrivateKey = Base64.toBase64String(encryptedPrivateKey),
                    encryptedDek = Base64.toBase64String(encryptedDek),
                )
            } finally {
                dataKey.fill(0)
            }
        } finally {
            privateKeyBytes.fill(0)
        }
    }

    private fun generateKeyPair(algorithm: AuthKeyAlgorithm): KeyPair {
        val generator = when (algorithm) {
            AuthKeyAlgorithm.ED25519 -> KeyPairGenerator.getInstance(
                "Ed25519",
                BouncyCastleProvider.PROVIDER_NAME,
            )

            AuthKeyAlgorithm.RSA_4096 -> KeyPairGenerator.getInstance(
                "RSA",
                BouncyCastleProvider.PROVIDER_NAME,
            ).apply {
                initialize(RSA_KEY_BITS)
            }

            else -> KeyPairGenerator.getInstance(
                "EC",
                BouncyCastleProvider.PROVIDER_NAME,
            ).apply {
                initialize(
                    ECGenParameterSpec(
                        requireNotNull(algorithm.curveName) {
                            "Missing curve for ${algorithm.displayName}"
                        },
                    ),
                )
            }
        }

        return generator.generateKeyPair()
    }

    private fun encrypt(plaintext: ByteArray, dataKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(dataKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, ByteArray(GCM_IV_BYTES).also(secureRandom::nextBytes)),
        )
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArray(1 + cipher.iv.size + ciphertext.size).also { envelope ->
            envelope[0] = ENVELOPE_VERSION
            cipher.iv.copyInto(envelope, destinationOffset = 1)
            ciphertext.copyInto(envelope, destinationOffset = 1 + cipher.iv.size)
        }
    }

    companion object {
        private const val RSA_KEY_BITS = 4096
        private const val DATA_KEY_BYTES = 32
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val ENVELOPE_VERSION: Byte = 1
    }
}

/**
 * Computes the `SHA256:...` fingerprint of a stored public key
 */
fun sshPublicKeyFingerprint(publicKey: String): String? {
    val tokens = publicKey.trim().split(WHITESPACE)
    val encoded = when {
        tokens.size >= 2 -> tokens[1]
        tokens.size == 1 -> tokens[0]
        else -> return null
    }

    val blob = runCatching { Base64.decode(encoded) }.getOrNull() ?: return null
    val digest = MessageDigest.getInstance("SHA-256").digest(blob)
    val fingerprint = Base64.toBase64String(digest).trimEnd('=')

    return "SHA256:$fingerprint"
}

private val WHITESPACE = Regex("\\s+")
