package com.ngtv.neodroid.security

import com.ngtv.neodroid.database.AuthKeyEntity
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Decrypts authentication keys and turns them into sshj key providers
 *
 * Key material only exists in memory while a connection attempt is running
 * The private-key bytes and unwrapped data key are zeroed before returning
 */
class AuthKeyMaterial(private val keystore: KeystoreKeyManager) {

    fun keyProvider(key: AuthKeyEntity): KeyProvider = keyProvider(
        algorithm = key.algorithm,
        publicKey = key.publicKey,
        encryptedPrivateKey = key.encryptedPrivateKey,
        encryptedDek = key.encryptedDek,
    )

    fun keyProvider(
        algorithm: String,
        publicKey: String,
        encryptedPrivateKey: String,
        encryptedDek: String,
    ): KeyProvider {
        val dataKey = keystore.unwrap(Base64.decode(encryptedDek))
        var privateKeyBytes: ByteArray? = null

        try {
            privateKeyBytes = decryptPrivateKey(
                envelope = Base64.decode(encryptedPrivateKey),
                dataKey = dataKey,
            )

            val keyFactoryAlgorithm = when (AuthKeyAlgorithm.fromStorageValue(algorithm)) {
                AuthKeyAlgorithm.ED25519 -> "Ed25519"

                AuthKeyAlgorithm.RSA_4096 -> "RSA"

                AuthKeyAlgorithm.ECDSA_P256,
                AuthKeyAlgorithm.ECDSA_P384,
                AuthKeyAlgorithm.ECDSA_P521,
                -> "EC"

                null -> error("Unsupported authentication key algorithm: $algorithm")
            }

            val privateKey =
                KeyFactory
                    .getInstance(keyFactoryAlgorithm, BouncyCastleProvider.PROVIDER_NAME)
                    .generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

            return KeyPairWrapper(KeyPair(decodePublicKey(publicKey), privateKey))
        } finally {
            dataKey.fill(0)
            privateKeyBytes?.fill(0)
        }
    }

    private fun decryptPrivateKey(envelope: ByteArray, dataKey: ByteArray): ByteArray {
        require(envelope.size >= 1 + GCM_IV_BYTES + GCM_TAG_BYTES) {
            "Encrypted private key blob is too short"
        }
        require(envelope[0] == ENVELOPE_VERSION) {
            "Unsupported encrypted private key version: ${envelope[0]}"
        }

        val iv = envelope.copyOfRange(1, 1 + GCM_IV_BYTES)
        val ciphertext = envelope.copyOfRange(1 + GCM_IV_BYTES, envelope.size)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(dataKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun decodePublicKey(publicKey: String): java.security.PublicKey {
        val encoded =
            publicKey.trim().split(WHITESPACE).let { tokens ->
                when {
                    tokens.size >= 2 -> tokens[1]
                    tokens.size == 1 -> tokens[0]
                    else -> error("Stored public key is empty")
                }
            }

        val blob = Base64.decode(encoded)
        return Buffer.PlainBuffer(blob).readPublicKey()
    }

    private companion object {
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val GCM_IV_BYTES = 12
        private const val ENVELOPE_VERSION: Byte = 1
        private val WHITESPACE = Regex("\\s+")
    }
}
