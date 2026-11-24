package com.ngtv.neodroid.security

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import com.hierynomus.sshj.userauth.keyprovider.bcrypt.BCrypt
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.RSAKeyParameters
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.bouncycastle.crypto.util.SSHNamedCurves
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.encoders.Base64
import java.io.Reader
import java.io.StringReader
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class PemKeyFormat {
    OPENSSH,
    PEM,
}

data class PemKeyDetection(val format: PemKeyFormat, val encrypted: Boolean)

class SshKeyFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads and writes private keys in the formats used by OpenSSH
 *
 * Exported OpenSSH keys are encrypted with the same bcrypt + aes256-ctr scheme
 * used by ssh-keygen, so they can be moved to any other SSH client
 */
object SshKeyFormat {

    fun detect(text: String): PemKeyDetection? {
        val trimmed = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return when {
            trimmed.startsWith(OPENSSH_BEGIN) ->
                PemKeyDetection(PemKeyFormat.OPENSSH, isOpenSshEncrypted(trimmed))

            trimmed.startsWith(ENCRYPTED_PKCS8_BEGIN) ->
                PemKeyDetection(PemKeyFormat.PEM, encrypted = true)

            trimmed.startsWith(PEM_BEGIN) && trimmed.contains("PRIVATE KEY-----") ->
                PemKeyDetection(PemKeyFormat.PEM, isLegacyPemEncrypted(trimmed))

            else -> null
        }
    }

    fun decode(text: String, passphrase: CharArray?): KeyPair {
        val detection = detect(text)
            ?: throw SshKeyFormatException("Not a supported private key file")
        if (detection.encrypted && passphrase == null) {
            throw SshKeyFormatException("This key is encrypted")
        }

        return try {
            when (detection.format) {
                PemKeyFormat.OPENSSH -> readOpenSsh(text, passphrase)
                PemKeyFormat.PEM -> readPem(text, passphrase)
            }
        } catch (e: SshKeyFormatException) {
            throw e
        } catch (e: Exception) {
            throw SshKeyFormatException(
                if (detection.encrypted) {
                    "Could not decrypt the key. Check the passphrase."
                } else {
                    "Could not read the private key."
                },
                e,
            )
        }
    }

    fun encodeOpenSsh(keyPair: KeyPair, passphrase: CharArray?): String = try {
        val publicKeyBlob = publicKeyBlob(keyPair.public)
        val privateBlock = encodePrivateBlock(keyPair)
        toPem(
            type = "OPENSSH PRIVATE KEY",
            blob = encodeContainer(publicKeyBlob, privateBlock, passphrase),
        )
    } catch (e: SshKeyFormatException) {
        throw e
    } catch (e: Exception) {
        throw SshKeyFormatException("Could not encode the private key", e)
    }

    fun publicKeyLine(publicKey: PublicKey): String {
        val type = KeyType.fromKey(publicKey)
        if (type == KeyType.UNKNOWN) {
            throw SshKeyFormatException("Unsupported public key type: ${publicKey.algorithm}")
        }
        val blob = Buffer.PlainBuffer().putPublicKey(publicKey).compactData
        return "$type ${Base64.toBase64String(blob)}"
    }

    fun decodePublicKey(publicKey: String): PublicKey {
        val encoded = publicKey
            .trim()
            .split(WHITESPACE)
            .let { tokens ->
                when {
                    tokens.size >= 2 -> tokens[1]
                    tokens.size == 1 -> tokens[0]
                    else -> throw SshKeyFormatException("Public key is empty")
                }
            }

        return try {
            Buffer.PlainBuffer(Base64.decode(encoded)).readPublicKey()
        } catch (e: Exception) {
            throw SshKeyFormatException("Could not read the public key", e)
        }
    }

    fun keyFactoryAlgorithm(publicKey: PublicKey): String = when (KeyType.fromKey(publicKey)) {
        KeyType.ED25519 -> "Ed25519"

        KeyType.RSA -> "RSA"

        KeyType.ECDSA256,
        KeyType.ECDSA384,
        KeyType.ECDSA521,
        -> "EC"

        else -> throw SshKeyFormatException("Unsupported public key type: ${publicKey.algorithm}")
    }

    fun algorithmFor(publicKey: PublicKey): AuthKeyAlgorithm? = when (KeyType.fromKey(publicKey)) {
        KeyType.ED25519 -> AuthKeyAlgorithm.ED25519
        KeyType.RSA -> AuthKeyAlgorithm.RSA_4096
        KeyType.ECDSA256 -> AuthKeyAlgorithm.ECDSA_P256
        KeyType.ECDSA384 -> AuthKeyAlgorithm.ECDSA_P384
        KeyType.ECDSA521 -> AuthKeyAlgorithm.ECDSA_P521
        else -> null
    }

    fun keyPairFromPkcs8(privateKeyPkcs8: ByteArray, publicKey: String): KeyPair {
        val public = decodePublicKey(publicKey)
        val private = try {
            KeyFactory
                .getInstance(keyFactoryAlgorithm(public), BouncyCastleProvider.PROVIDER_NAME)
                .generatePrivate(PKCS8EncodedKeySpec(privateKeyPkcs8))
        } catch (e: Exception) {
            throw SshKeyFormatException("Could not read the private key", e)
        }

        val derived = derivePublicKey(privateKeyPkcs8)
        if (!publicKeyBlob(derived).contentEquals(publicKeyBlob(public))) {
            throw SshKeyFormatException("The public key does not match the private key")
        }

        return KeyPair(public, private)
    }

    fun derivePublicKey(privateKeyPkcs8: ByteArray): PublicKey {
        val privateParameter = try {
            PrivateKeyFactory.createKey(privateKeyPkcs8)
        } catch (e: Exception) {
            throw SshKeyFormatException("Could not read the private key", e)
        }

        val publicParameter = when (privateParameter) {
            is Ed25519PrivateKeyParameters -> privateParameter.generatePublicKey()

            is RSAPrivateCrtKeyParameters -> RSAKeyParameters(
                false,
                privateParameter.modulus,
                privateParameter.publicExponent,
            )

            is ECPrivateKeyParameters -> ECPublicKeyParameters(
                privateParameter.parameters.g.multiply(privateParameter.d).normalize(),
                privateParameter.parameters,
            )

            else -> throw SshKeyFormatException("Unsupported private key type")
        }

        return try {
            val algorithm = when (publicParameter) {
                is Ed25519PublicKeyParameters -> "Ed25519"
                is RSAKeyParameters -> "RSA"
                is ECPublicKeyParameters -> "EC"
                else -> throw SshKeyFormatException("Unsupported public key type")
            }
            val subjectPublicKeyInfo =
                SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicParameter)
            KeyFactory
                .getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME)
                .generatePublic(X509EncodedKeySpec(subjectPublicKeyInfo.encoded))
        } catch (e: SshKeyFormatException) {
            throw e
        } catch (e: Exception) {
            throw SshKeyFormatException("Could not derive the public key", e)
        }
    }

    private fun readOpenSsh(text: String, passphrase: CharArray?): KeyPair {
        val provider = OpenSSHKeyV1KeyFile()
        provider.init(
            StringReader(text),
            null as Reader?,
            passphrase?.let(::StaticPasswordFinder),
        )
        return canonicalizeKeyPair(provider.public, provider.private)
    }

    private fun readPem(text: String, passphrase: CharArray?): KeyPair {
        val provider = PKCS8KeyFile()
        if (passphrase == null) {
            provider.init(StringReader(text))
        } else {
            provider.init(StringReader(text), StaticPasswordFinder(passphrase))
        }
        return canonicalizeKeyPair(provider.public, provider.private)
    }

    /**
     * Re-encodes the private key through Bouncy Castle so keys read by sshj and
     * keys generated by this app share the same PKCS#8 encoding.
     */
    private fun canonicalizeKeyPair(
        publicKey: PublicKey,
        privateKey: java.security.PrivateKey,
    ): KeyPair {
        val canonicalPrivateKey = try {
            val privateParameter = PrivateKeyFactory.createKey(privateKey.encoded)
            val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateParameter)
            KeyFactory
                .getInstance(keyFactoryAlgorithm(publicKey), BouncyCastleProvider.PROVIDER_NAME)
                .generatePrivate(PKCS8EncodedKeySpec(privateKeyInfo.encoded))
        } catch (e: Exception) {
            throw SshKeyFormatException("Could not read the private key", e)
        }
        return KeyPair(publicKey, canonicalPrivateKey)
    }

    private fun isOpenSshEncrypted(text: String): Boolean = runCatching {
        val blob = pemBody(text, "OPENSSH PRIVATE KEY")
        val buffer = Buffer.PlainBuffer(blob)
        val magic = buffer.readCString()
        val cipher = buffer.readString()
        magic == AUTH_MAGIC && cipher != NONE_CIPHER
    }.getOrDefault(false)

    private fun isLegacyPemEncrypted(text: String): Boolean =
        text.contains("Proc-Type: 4,ENCRYPTED", ignoreCase = true) ||
            text.contains("DEK-Info:", ignoreCase = true)

    private fun encodePrivateBlock(keyPair: KeyPair): ByteArray {
        val privateKeyParameter = PrivateKeyFactory.createKey(keyPair.private.encoded)
        val keyType = KeyType.fromKey(keyPair.public)
        val checkInt = secureRandom.nextInt().toLong() and UINT32_MASK

        val buffer = Buffer.PlainBuffer()
        buffer.putUInt32(checkInt)
        buffer.putUInt32(checkInt)

        when (keyType) {
            KeyType.ED25519 -> {
                val key = privateKeyParameter as? Ed25519PrivateKeyParameters
                    ?: throw SshKeyFormatException("Unsupported Ed25519 private key")
                val publicKey = key.generatePublicKey().encoded
                buffer.putString(keyType.toString())
                buffer.putString(publicKey)
                buffer.putString(key.encoded + publicKey)
                buffer.putString("")
            }

            KeyType.RSA -> {
                val key = privateKeyParameter as? RSAPrivateCrtKeyParameters
                    ?: throw SshKeyFormatException("Unsupported RSA private key")
                buffer.putString(keyType.toString())
                buffer.putMPInt(key.modulus)
                buffer.putMPInt(key.publicExponent)
                buffer.putMPInt(key.exponent)
                buffer.putMPInt(key.qInv)
                buffer.putMPInt(key.p)
                buffer.putMPInt(key.q)
                buffer.putString("")
            }

            KeyType.ECDSA256,
            KeyType.ECDSA384,
            KeyType.ECDSA521,
            -> {
                val key = privateKeyParameter as? ECPrivateKeyParameters
                    ?: throw SshKeyFormatException("Unsupported ECDSA private key")
                val curveName = SSHNamedCurves.getNameForParameters(key.parameters)
                    ?: throw SshKeyFormatException("Unsupported ECDSA curve")
                val publicKey = key.parameters.g.multiply(key.d).normalize().getEncoded(false)
                buffer.putString(keyType.toString())
                buffer.putString(curveName)
                buffer.putString(publicKey)
                buffer.putMPInt(key.d)
                buffer.putString("")
            }

            else -> throw SshKeyFormatException("Unsupported private key type: $keyType")
        }

        return buffer.compactData
    }

    private fun encodeContainer(
        publicKeyBlob: ByteArray,
        privateBlock: ByteArray,
        passphrase: CharArray?,
    ): ByteArray {
        val encrypted = passphrase != null && passphrase.isNotEmpty()
        val cipher = if (encrypted) ENCRYPTED_OPENSSH_CIPHER else NONE_CIPHER
        val kdf = if (encrypted) ENCRYPTED_OPENSSH_KDF else NONE_CIPHER
        val (kdfOptions, encodedPrivateBlock) = if (encrypted) {
            encryptPrivateBlock(privateBlock, passphrase!!)
        } else {
            ByteArray(0) to padPrivateBlock(privateBlock, OPENSSH_PADDING_BLOCK_BYTES)
        }

        return Buffer
            .PlainBuffer()
            .putRawBytes(AUTH_MAGIC_BYTES)
            .putString(cipher)
            .putString(kdf)
            .putString(kdfOptions)
            .putUInt32(KEY_COUNT)
            .putString(publicKeyBlob)
            .putString(encodedPrivateBlock)
            .compactData
    }

    private fun encryptPrivateBlock(
        privateBlock: ByteArray,
        passphrase: CharArray,
    ): Pair<ByteArray, ByteArray> {
        val salt = ByteArray(BCRYPT_SALT_BYTES).also(secureRandom::nextBytes)
        val keyAndIv = ByteArray(AES_KEY_BYTES + AES_IV_BYTES)
        val passwordBytes = passphrase.toUtf8Bytes()

        try {
            bcrypt.pbkdf(passwordBytes, salt, BCRYPT_ROUNDS, keyAndIv)

            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyAndIv, 0, AES_KEY_BYTES, "AES"),
                IvParameterSpec(keyAndIv, AES_KEY_BYTES, AES_IV_BYTES),
            )
            val encryptedPrivateBlock = cipher.doFinal(
                padPrivateBlock(privateBlock, AES_BLOCK_BYTES),
            )

            val kdfOptions = Buffer
                .PlainBuffer()
                .putString(salt)
                .putUInt32(BCRYPT_ROUNDS.toLong())
                .compactData

            return kdfOptions to encryptedPrivateBlock
        } catch (e: Exception) {
            throw SshKeyFormatException("Could not encrypt the private key", e)
        } finally {
            keyAndIv.fill(0)
            passwordBytes.fill(0)
        }
    }

    private fun padPrivateBlock(privateBlock: ByteArray, blockSize: Int): ByteArray {
        val padding = (blockSize - privateBlock.size % blockSize) % blockSize
        return privateBlock + ByteArray(padding) { index -> (index + 1).toByte() }
    }

    private fun publicKeyBlob(publicKey: PublicKey): ByteArray =
        Buffer.PlainBuffer().putPublicKey(publicKey).compactData

    private fun Buffer<*>.readCString(): String {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val value = readByte().toInt() and 0xFF
            if (value == 0) break
            bytes.write(value)
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun pemBody(text: String, type: String): ByteArray {
        val begin = "-----BEGIN $type-----"
        val end = "-----END $type-----"
        val start = text.indexOf(begin)
        val stop = text.indexOf(end)
        require(start >= 0 && stop > start) { "Missing PEM markers" }
        val encoded = text
            .substring(start + begin.length, stop)
            .filterNot(Char::isWhitespace)
        return Base64.decode(encoded)
    }

    private fun toPem(type: String, blob: ByteArray): String {
        val encoded = Base64.toBase64String(blob)
        return buildString {
            append("-----BEGIN ").append(type).append("-----\n")
            var index = 0
            while (index < encoded.length) {
                val end = minOf(index + PEM_LINE_LENGTH, encoded.length)
                append(encoded, index, end).append('\n')
                index = end
            }
            append("-----END ").append(type).append("-----\n")
        }
    }

    private class StaticPasswordFinder(private val password: CharArray) : PasswordFinder {
        override fun reqPassword(resource: Resource<*>?): CharArray = password.copyOf()

        override fun shouldRetry(resource: Resource<*>?): Boolean = false
    }

    private fun CharArray.toUtf8Bytes(): ByteArray = String(this).toByteArray(Charsets.UTF_8)

    private val secureRandom = SecureRandom()
    private val bcrypt = BCrypt()

    private const val AUTH_MAGIC = "openssh-key-v1"
    private val AUTH_MAGIC_BYTES = "$AUTH_MAGIC\u0000".toByteArray(Charsets.UTF_8)
    private const val NONE_CIPHER = "none"
    private const val ENCRYPTED_OPENSSH_CIPHER = "aes256-ctr"
    private const val ENCRYPTED_OPENSSH_KDF = "bcrypt"
    private const val BCRYPT_ROUNDS = 16
    private const val BCRYPT_SALT_BYTES = 16
    private const val AES_KEY_BYTES = 32
    private const val AES_IV_BYTES = 16
    private const val AES_BLOCK_BYTES = 16
    private const val OPENSSH_PADDING_BLOCK_BYTES = 8
    private const val KEY_COUNT = 1L
    private const val UINT32_MASK = 0xFFFFFFFFL
    private const val PEM_LINE_LENGTH = 70
    private const val OPENSSH_BEGIN = "-----BEGIN OPENSSH PRIVATE KEY-----"
    private const val ENCRYPTED_PKCS8_BEGIN = "-----BEGIN ENCRYPTED PRIVATE KEY-----"
    private const val PEM_BEGIN = "-----BEGIN "
    private val WHITESPACE = Regex("\\s+")
}
