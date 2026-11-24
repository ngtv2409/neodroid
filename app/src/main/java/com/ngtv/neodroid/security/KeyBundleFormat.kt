package com.ngtv.neodroid.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyBundleFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Neodroid's own passphrase-protected archive used for backing up keys across installations
 *
 * The outer header is authenticated as AES-GCM associated data, so changing the
 * KDF parameters or salt invalidates the decryption.
 */
object KeyBundleFormat {

    fun isBundle(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size) return false
        for (index in MAGIC.indices) {
            if (bytes[index] != MAGIC[index]) return false
        }
        return true
    }

    fun encode(entries: List<KeyBundleEntry>, passphrase: CharArray): ByteArray {
        if (passphrase.isEmpty()) {
            throw KeyBundleFormatException("A passphrase is required")
        }
        if (entries.isEmpty() || entries.size > MAX_KEYS) {
            throw KeyBundleFormatException("A bundle must contain between 1 and $MAX_KEYS keys")
        }

        val plaintext = serialize(entries)
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val key = deriveKey(passphrase, salt)

        try {
            val header = buildHeader(salt = salt, iv = iv)
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(header)
            val ciphertext = cipher.doFinal(plaintext)

            return ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.write(header)
                    output.writeInt(ciphertext.size)
                    output.write(ciphertext)
                }
                bytes.toByteArray()
            }
        } catch (e: Exception) {
            throw KeyBundleFormatException("Could not create the key bundle", e)
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
    }

    fun decode(bytes: ByteArray, passphrase: CharArray): List<KeyBundleEntry> {
        if (passphrase.isEmpty()) {
            throw KeyBundleFormatException("A passphrase is required")
        }

        val input = ByteArrayInputStream(bytes)
        val data = DataInputStream(input)

        val magic = ByteArray(MAGIC.size)
        data.readFully(magic)
        if (!magic.contentEquals(MAGIC)) {
            throw KeyBundleFormatException("Not a Neodroid key bundle")
        }

        val version = data.readUnsignedByte()
        if (version != VERSION) {
            throw KeyBundleFormatException("Unsupported key bundle version: $version")
        }

        val kdf = data.readUnsignedByte()
        if (kdf != KDF_ARGON2ID) {
            throw KeyBundleFormatException("Unsupported key bundle KDF: $kdf")
        }

        val iterations = data.readInt()
        val memoryKib = data.readInt()
        val parallelism = data.readInt()
        validateKdfParameters(iterations, memoryKib, parallelism)

        val saltLength = data.readUnsignedByte()
        if (saltLength != SALT_BYTES) {
            throw KeyBundleFormatException("Invalid key bundle salt")
        }
        val salt = ByteArray(saltLength).also(data::readFully)

        val ivLength = data.readUnsignedByte()
        if (ivLength != IV_BYTES) {
            throw KeyBundleFormatException("Invalid key bundle IV")
        }
        val iv = ByteArray(ivLength).also(data::readFully)

        val header = bytes.copyOfRange(0, bytes.size - input.available())

        val ciphertextLength = data.readInt()
        if (ciphertextLength <= 0 || ciphertextLength > MAX_CIPHERTEXT_BYTES) {
            throw KeyBundleFormatException("Invalid key bundle payload")
        }
        val ciphertext = ByteArray(ciphertextLength).also(data::readFully)
        if (input.available() != 0) {
            throw KeyBundleFormatException("Unexpected data after the key bundle")
        }

        val key = deriveKey(
            passphrase = passphrase,
            salt = salt,
            iterations = iterations,
            memoryKib = memoryKib,
            parallelism = parallelism,
        )

        val plaintext = try {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.updateAAD(header)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw KeyBundleFormatException("Wrong passphrase or damaged key bundle", e)
        } finally {
            key.fill(0)
        }

        return try {
            deserialize(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun buildHeader(salt: ByteArray, iv: ByteArray): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeByte(VERSION)
                output.writeByte(KDF_ARGON2ID)
                output.writeInt(ARGON2_ITERATIONS)
                output.writeInt(ARGON2_MEMORY_KIB)
                output.writeInt(ARGON2_PARALLELISM)
                output.writeByte(salt.size)
                output.write(salt)
                output.writeByte(iv.size)
                output.write(iv)
            }
            bytes.toByteArray()
        }

    private fun serialize(entries: List<KeyBundleEntry>): ByteArray {
        val plaintext = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(entries.size)
                entries.forEach { entry ->
                    output.writeField(entry.displayName.toByteArray(Charsets.UTF_8))
                    output.writeField(entry.algorithm.toByteArray(Charsets.UTF_8))
                    output.writeField(entry.publicKey.toByteArray(Charsets.UTF_8))
                    output.writeField(entry.privateKeyPkcs8)
                }
            }
            bytes.toByteArray()
        }

        if (plaintext.size > MAX_PLAINTEXT_BYTES) {
            throw KeyBundleFormatException("The key bundle is too large")
        }
        return plaintext
    }

    private fun deserialize(plaintext: ByteArray): List<KeyBundleEntry> {
        if (plaintext.size > MAX_PLAINTEXT_BYTES) {
            throw KeyBundleFormatException("The key bundle is too large")
        }

        val input = ByteArrayInputStream(plaintext)
        val data = DataInputStream(input)

        val count = try {
            data.readInt()
        } catch (e: EOFException) {
            throw KeyBundleFormatException("The key bundle is truncated", e)
        }
        if (count <= 0 || count > MAX_KEYS) {
            throw KeyBundleFormatException("Invalid key count: $count")
        }

        return try {
            List(count) {
                KeyBundleEntry(
                    displayName = data.readField().toString(Charsets.UTF_8),
                    algorithm = data.readField().toString(Charsets.UTF_8),
                    publicKey = data.readField().toString(Charsets.UTF_8),
                    privateKeyPkcs8 = data.readField(),
                )
            }.also {
                if (input.available() != 0) {
                    throw KeyBundleFormatException("Unexpected data in the key bundle")
                }
            }
        } catch (e: KeyBundleFormatException) {
            throw e
        } catch (e: Exception) {
            throw KeyBundleFormatException("The key bundle is damaged", e)
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = ARGON2_ITERATIONS,
        memoryKib: Int = ARGON2_MEMORY_KIB,
        parallelism: Int = ARGON2_PARALLELISM,
    ): ByteArray {
        val parameters = Argon2Parameters
            .Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKib)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()

        return ByteArray(KEY_BYTES).also { key ->
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(passphrase, key)
        }
    }

    private fun validateKdfParameters(iterations: Int, memoryKib: Int, parallelism: Int) {
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw KeyBundleFormatException("Invalid key bundle KDF iterations")
        }
        if (memoryKib !in MIN_MEMORY_KIB..MAX_MEMORY_KIB) {
            throw KeyBundleFormatException("Invalid key bundle KDF memory")
        }
        if (parallelism !in MIN_PARALLELISM..MAX_PARALLELISM) {
            throw KeyBundleFormatException("Invalid key bundle KDF parallelism")
        }
    }

    private fun DataOutputStream.writeField(bytes: ByteArray) {
        if (bytes.size > MAX_FIELD_BYTES) {
            throw KeyBundleFormatException("A key bundle field is too large")
        }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readField(): ByteArray {
        val size = readInt()
        if (size < 0 || size > MAX_FIELD_BYTES) {
            throw KeyBundleFormatException("Invalid key bundle field length: $size")
        }
        return ByteArray(size).also(::readFully)
    }

    private val secureRandom = SecureRandom()

    private val MAGIC = "NDKEYBND".toByteArray(Charsets.UTF_8)
    private const val VERSION = 1
    private const val KDF_ARGON2ID = 1
    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_MEMORY_KIB = 19 * 1024
    private const val ARGON2_PARALLELISM = 1
    private const val MIN_ITERATIONS = 1
    private const val MAX_ITERATIONS = 10
    private const val MIN_MEMORY_KIB = 8 * 1024
    private const val MAX_MEMORY_KIB = 256 * 1024
    private const val MIN_PARALLELISM = 1
    private const val MAX_PARALLELISM = 4
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val MAX_KEYS = 64
    private const val MAX_FIELD_BYTES = 1 shl 20
    private const val MAX_PLAINTEXT_BYTES = 4 shl 20
    private const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + 16
}
