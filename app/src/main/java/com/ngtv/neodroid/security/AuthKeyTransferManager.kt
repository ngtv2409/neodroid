package com.ngtv.neodroid.security

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Moves key material between SAF files and the encrypted key database.
 */
class AuthKeyTransferManager(context: Context, private val authKeyMaterial: AuthKeyMaterial) {

    private val contentResolver = context.applicationContext.contentResolver

    suspend fun inspect(
        uri: Uri,
        passphrase: CharArray?,
        existingFingerprints: Set<String>,
    ): ImportInspection {
        val bytes = withContext(Dispatchers.IO) { readBytes(uri) }
        val fileName = withContext(Dispatchers.IO) { displayName(uri) }

        return withContext(Dispatchers.Default) {
            val detection = detect(bytes)
                ?: throw KeyTransferException("Unrecognized key file")

            if (detection.encrypted && passphrase == null) {
                return@withContext ImportInspection.NeedsPassphrase(detection.format)
            }

            val candidates = when (detection.format) {
                KeyFileFormat.BUNDLE -> {
                    val entries = KeyBundleFormat.decode(
                        bytes = bytes,
                        passphrase = passphrase
                            ?: throw KeyTransferException("A passphrase is required"),
                    )
                    entries.map { entry ->
                        val keyPair = SshKeyFormat.keyPairFromPkcs8(
                            privateKeyPkcs8 = entry.privateKeyPkcs8,
                            publicKey = entry.publicKey,
                        )
                        candidate(
                            keyPair = keyPair,
                            displayName = entry.displayName,
                            existingFingerprints = existingFingerprints,
                        )
                    }
                }

                KeyFileFormat.OPENSSH,
                KeyFileFormat.PEM,
                -> {
                    val keyPair = SshKeyFormat.decode(
                        text = bytes.toString(Charsets.UTF_8),
                        passphrase = passphrase,
                    )
                    listOf(
                        candidate(
                            keyPair = keyPair,
                            displayName = defaultDisplayName(fileName),
                            existingFingerprints = existingFingerprints,
                        ),
                    )
                }
            }

            ImportInspection.Ready(candidates)
        }
    }

    suspend fun export(request: KeyExportRequest, uri: Uri) {
        val payload = withContext(Dispatchers.Default) { encodeExport(request) }
        withContext(Dispatchers.IO) { writeBytes(uri, payload) }
    }

    private fun encodeExport(request: KeyExportRequest): ByteArray = when (request.format) {
        KeyExportFormat.OPENSSH -> {
            val key = request.keys.singleOrNull()
                ?: throw KeyTransferException("Select exactly one key for OpenSSH export")
            val keyPair = authKeyMaterial.keyPair(key)
            val passphrase = request.passphrase?.toCharArray()
            try {
                SshKeyFormat
                    .encodeOpenSsh(keyPair, passphrase)
                    .toByteArray(Charsets.UTF_8)
            } finally {
                passphrase?.fill('\u0000')
            }
        }

        KeyExportFormat.BUNDLE -> {
            val passphrase = request.passphrase
                ?.takeIf(String::isNotEmpty)
                ?.toCharArray()
                ?: throw KeyTransferException("A passphrase is required")

            try {
                val entries = request.keys.map { key ->
                    val keyPair = authKeyMaterial.keyPair(key)
                    KeyBundleEntry(
                        displayName = key.displayName,
                        algorithm = key.algorithm,
                        publicKey = key.publicKey,
                        privateKeyPkcs8 = keyPair.private.encoded,
                    )
                }
                KeyBundleFormat.encode(entries, passphrase)
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    private fun candidate(
        keyPair: java.security.KeyPair,
        displayName: String,
        existingFingerprints: Set<String>,
    ): ImportCandidate {
        val algorithm = SshKeyFormat.algorithmFor(keyPair.public)
            ?: throw KeyTransferException("Unsupported key type: ${keyPair.public.algorithm}")
        val publicKey = SshKeyFormat.publicKeyLine(keyPair.public)
        val fingerprint = sshPublicKeyFingerprint(publicKey)

        return ImportCandidate(
            keyPair = keyPair,
            displayName = displayName.trim().take(MAX_DISPLAY_NAME),
            algorithm = algorithm,
            fingerprint = fingerprint,
            alreadyExists = fingerprint != null && fingerprint in existingFingerprints,
        )
    }

    private fun detect(bytes: ByteArray): KeyFileDetection? {
        if (KeyBundleFormat.isBundle(bytes)) {
            return KeyFileDetection(KeyFileFormat.BUNDLE, encrypted = true)
        }

        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return null
        val detection = SshKeyFormat.detect(text) ?: return null
        return KeyFileDetection(
            format = when (detection.format) {
                PemKeyFormat.OPENSSH -> KeyFileFormat.OPENSSH
                PemKeyFormat.PEM -> KeyFileFormat.PEM
            },
            encrypted = detection.encrypted,
        )
    }

    private fun defaultDisplayName(fileName: String?): String {
        val base = fileName
            ?.substringBeforeLast('.', missingDelimiterValue = fileName)
            ?.trim()
            .orEmpty()
        return base.ifEmpty { DEFAULT_DISPLAY_NAME }.take(MAX_DISPLAY_NAME)
    }

    private fun readBytes(uri: Uri): ByteArray {
        val stream = contentResolver.openInputStream(uri)
            ?: throw KeyTransferException("Could not open the selected file")
        return stream.use { it.readLimited(MAX_FILE_BYTES) }
    }

    private fun writeBytes(uri: Uri, bytes: ByteArray) {
        val stream = contentResolver.openOutputStream(uri, "wt")
            ?: throw KeyTransferException("Could not open the destination file")
        stream.use { it.write(bytes) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun InputStream.readLimited(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0

        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw KeyTransferException("The selected file is too large")
            }
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private companion object {
        private const val MAX_FILE_BYTES = 1 shl 20
        private const val MAX_DISPLAY_NAME = 100
        private const val DEFAULT_DISPLAY_NAME = "Imported key"
    }
}
