package com.ngtv.neodroid.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Owns the Android Keystore key that wraps locally stored SSH key material (envelope)
 *
 * Not auth bound by default because a lot of things invalidate it permanently
 * TODO: can still be a great setting
 */
class KeystoreKeyManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow<KeystoreStatus>(KeystoreStatus.NotInitialized)
    val status: StateFlow<KeystoreStatus> = _status.asStateFlow()

    @Volatile
    private var cipherProvider: String? = null

    @Volatile
    private var cipherProviderResolved = false

    fun capabilities(): KeystoreCapabilities {
        val strongBoxAvailable =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                appContext.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_STRONGBOX_KEYSTORE,
                )

        val available =
            runCatching {
                KeyStore.getInstance(ANDROID_KEYSTORE).load(null)
            }.isSuccess

        return KeystoreCapabilities(
            available = available,
            strongBoxAvailable = strongBoxAvailable,
        )
    }

    @Synchronized
    fun initialize(): KeystoreStatus {
        val current = _status.value
        if (current is KeystoreStatus.Ready) return current

        val capabilities = capabilities()
        if (!capabilities.available) {
            return unavailable(
                message = "Android Keystore is not available",
                capabilities = capabilities,
            )
        }

        return try {
            val key = getOrCreateKey()
            val keyInfo = readKeyInfo(key)
            val provider = findWorkingCipherProvider(key)

            cipherProvider = provider
            cipherProviderResolved = true

            val status =
                KeystoreStatus.Ready(
                    securityLevel = keyInfo.securityLevelCompat(),
                    strongBoxAvailable = capabilities.strongBoxAvailable,
                )

            _status.value = status
            Log.i(
                TAG,
                "Keystore ready: level=${status.securityLevel}, " +
                    "strongBoxAvailable=${status.strongBoxAvailable}, " +
                    "cipherProvider=${provider ?: "default"}",
            )
            status
        } catch (e: Exception) {
            Log.e(TAG, "Keystore initialization failed", e)
            unavailable(
                message = e.message ?: "Keystore initialization failed",
                capabilities = capabilities,
                cause = e,
            )
        } catch (e: LinkageError) {
            Log.e(TAG, "Keystore initialization hit a missing platform API", e)
            unavailable(
                message = e.message ?: "Keystore API is unavailable on this device",
                capabilities = capabilities,
                cause = e,
            )
        }
    }

    /**
     * Encrypts plaintext as version iv ciphertext+tag.
     */
    fun wrap(plaintext: ByteArray, associatedData: ByteArray = EMPTY_BYTES): ByteArray {
        val key = requireReadyKey()
        val cipher = createCipher(resolvedCipherProvider())

        cipher.init(Cipher.ENCRYPT_MODE, key)
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)

        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        return ByteBuffer
            .allocate(1 + iv.size + ciphertext.size)
            .put(ENVELOPE_VERSION)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    /**
     * Decrypts a blob
     */
    fun unwrap(envelope: ByteArray, associatedData: ByteArray = EMPTY_BYTES): ByteArray {
        require(envelope.size >= 1 + IV_BYTES + TAG_BYTES) {
            "Encrypted key blob is too short"
        }

        val buffer = ByteBuffer.wrap(envelope)
        val version = buffer.get()
        require(version == ENVELOPE_VERSION) {
            "Unsupported encrypted key blob version: $version"
        }

        val iv = ByteArray(IV_BYTES).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)

        val key = requireReadyKey()
        val cipher = createCipher(resolvedCipherProvider())
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)

        return cipher.doFinal(ciphertext)
    }

    private fun requireReadyKey(): SecretKey {
        when (val currentStatus = initialize()) {
            is KeystoreStatus.Ready -> return getOrCreateKey()

            is KeystoreStatus.Unavailable ->
                throw KeystoreKeyException(
                    currentStatus.message,
                    currentStatus.cause,
                )

            KeystoreStatus.NotInitialized ->
                throw KeystoreKeyException("Keystore has not been initialized")
        }
    }

    private fun resolvedCipherProvider(): String? {
        check(cipherProviderResolved) { "Keystore has not been initialized" }
        return cipherProvider
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val key =
                try {
                    keyStore.getKey(KEY_ALIAS, null)
                } catch (e: UnrecoverableKeyException) {
                    throw KeystoreKeyException(
                        "The Keystore wrapping key exists but cannot be loaded",
                        e,
                    )
                }

            return key as? SecretKey
                ?: throw KeystoreKeyException(
                    "Keystore entry $KEY_ALIAS is not an AES secret key",
                )
        }

        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val attempts = buildList {
            if (canUseStrongBox()) add(KeyGenerationAttempt(strongBox = true))
            add(KeyGenerationAttempt(strongBox = false))
        }

        var lastFailure: Throwable? = null

        for (attempt in attempts) {
            try {
                return generateKey(attempt)
            } catch (e: Exception) {
                lastFailure = e
                Log.w(
                    TAG,
                    "Key generation failed (strongBox=${attempt.strongBox}); retrying",
                    e,
                )
            } catch (e: LinkageError) {
                lastFailure = e
                Log.w(
                    TAG,
                    "Key generation hit a missing platform API " +
                        "(strongBox=${attempt.strongBox}); retrying",
                    e,
                )
            }

            // A failed generation can leave a partial entry
            runCatching { loadKeyStore().deleteEntry(KEY_ALIAS) }
        }

        throw KeystoreKeyException(
            "Unable to generate the Keystore wrapping key",
            lastFailure,
        )
    }

    private fun generateKey(attempt: KeyGenerationAttempt): SecretKey {
        val builder =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && attempt.strongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(builder.build()) }
            .generateKey()
    }

    private fun canUseStrongBox(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        appContext.packageManager.hasSystemFeature(
            PackageManager.FEATURE_STRONGBOX_KEYSTORE,
        )

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun readKeyInfo(key: SecretKey): KeyInfo {
        val factory = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        return factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
    }

    private fun findWorkingCipherProvider(key: SecretKey): String? {
        var lastFailure: Exception? = null

        for (provider in CIPHER_PROVIDERS) {
            try {
                val plaintext = CIPHER_PROBE
                val encryptCipher = createCipher(provider)
                encryptCipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = encryptCipher.iv
                val ciphertext = encryptCipher.doFinal(plaintext)

                val decryptCipher = createCipher(provider)
                decryptCipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(TAG_BITS, iv),
                )
                val decrypted = decryptCipher.doFinal(ciphertext)

                if (decrypted.contentEquals(plaintext)) return provider
            } catch (e: Exception) {
                lastFailure = e
                Log.w(
                    TAG,
                    "AES-GCM provider ${provider ?: "default"} is unusable for Keystore keys",
                    e,
                )
            }
        }

        throw KeystoreKeyException(
            "No AES-GCM provider can use the Keystore wrapping key",
            lastFailure,
        )
    }

    private fun createCipher(provider: String?): Cipher = if (provider == null) {
        Cipher.getInstance(TRANSFORMATION)
    } else {
        Cipher.getInstance(TRANSFORMATION, provider)
    }

    private fun unavailable(
        message: String,
        capabilities: KeystoreCapabilities,
        cause: Throwable? = null,
    ): KeystoreStatus.Unavailable = KeystoreStatus.Unavailable(
        message = message,
        strongBoxAvailable = capabilities.strongBoxAvailable,
        cause = cause,
    ).also { _status.value = it }

    private fun KeyInfo.securityLevelCompat(): KeystoreSecurityLevel {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return when (securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeystoreSecurityLevel.STRONGBOX

                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ->
                    KeystoreSecurityLevel.TRUSTED_ENVIRONMENT

                KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeystoreSecurityLevel.SOFTWARE

                else -> KeystoreSecurityLevel.UNKNOWN
            }
        }

        @Suppress("DEPRECATION")
        return if (isInsideSecureHardware) {
            KeystoreSecurityLevel.TRUSTED_ENVIRONMENT
        } else {
            KeystoreSecurityLevel.SOFTWARE
        }
    }

    private data class KeyGenerationAttempt(val strongBox: Boolean)

    companion object {
        private const val TAG = "NeodroidKeystore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "neodroid.key-wrapping.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val IV_BYTES = 12
        private const val ENVELOPE_VERSION: Byte = 1

        private val EMPTY_BYTES = ByteArray(0)
        private val CIPHER_PROBE = "neodroid-keystore-probe".toByteArray()

        private val CIPHER_PROVIDERS: List<String?> =
            listOf(
                "AndroidKeyStoreBCWorkaround",
                "AndroidOpenSSL",
                null,
            )

        @Volatile
        private var instance: KeystoreKeyManager? = null

        fun getInstance(context: Context): KeystoreKeyManager = instance ?: synchronized(this) {
            instance ?: KeystoreKeyManager(context.applicationContext).also {
                instance = it
            }
        }
    }
}

data class KeystoreCapabilities(val available: Boolean, val strongBoxAvailable: Boolean)

sealed interface KeystoreStatus {
    data object NotInitialized : KeystoreStatus

    data class Ready(val securityLevel: KeystoreSecurityLevel, val strongBoxAvailable: Boolean) :
        KeystoreStatus

    data class Unavailable(
        val message: String,
        val strongBoxAvailable: Boolean,
        val cause: Throwable? = null,
    ) : KeystoreStatus
}

enum class KeystoreSecurityLevel(val isHardwareBacked: Boolean) {
    STRONGBOX(true),
    TRUSTED_ENVIRONMENT(true),
    SOFTWARE(false),
    UNKNOWN(false),
}

class KeystoreKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)
