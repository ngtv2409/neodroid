package com.ngtv.neodroid.database

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.ngtv.neodroid.security.AuthKeyAlgorithm
import com.ngtv.neodroid.security.AuthKeyGenerator
import com.ngtv.neodroid.security.AuthKeyTransferManager
import com.ngtv.neodroid.security.ImportCandidate
import com.ngtv.neodroid.security.ImportInspection
import com.ngtv.neodroid.security.KeyExportFormat
import com.ngtv.neodroid.security.KeyExportRequest
import com.ngtv.neodroid.security.KeyFileFormat
import com.ngtv.neodroid.security.sshPublicKeyFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Entity(tableName = "auth_keys")
data class AuthKeyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val algorithm: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    // blobs stored in base64
    @ColumnInfo(name = "encrypted_private_key")
    val encryptedPrivateKey: String,
    @ColumnInfo(name = "public_key")
    val publicKey: String,
    @ColumnInfo(name = "encrypted_dek")
    val encryptedDek: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
)

@Dao
interface AuthKeyDao {

    /** alphabetical order */
    @Query(
        "SELECT * FROM auth_keys " +
            "ORDER BY " +
            "CASE WHEN TRIM(display_name) = '' THEN algorithm ELSE display_name END " +
            "COLLATE NOCASE ASC, id ASC",
    )
    fun getAll(): Flow<List<AuthKeyEntity>>

    /** use newest of created at and used at */
    @Query(
        "SELECT * FROM auth_keys " +
            "ORDER BY COALESCE(last_used_at, created_at) DESC, id DESC",
    )
    fun getRecent(): Flow<List<AuthKeyEntity>>

    @Query("SELECT * FROM auth_keys WHERE id = :id LIMIT 1")
    fun getById(id: Int): Flow<AuthKeyEntity?>

    @Insert
    suspend fun insert(key: AuthKeyEntity)

    @Insert
    suspend fun insertAll(keys: List<AuthKeyEntity>)

    @Update
    suspend fun update(key: AuthKeyEntity)

    @Delete
    suspend fun delete(key: AuthKeyEntity)

    @Query("UPDATE auth_keys SET last_used_at = :timestamp WHERE id = :id")
    suspend fun markUsed(id: Int, timestamp: Long)
}

class AuthKeyRepository(private val dao: AuthKeyDao) {
    fun getAllKeys(): Flow<List<AuthKeyEntity>> = dao.getAll()

    fun getRecentKeys(): Flow<List<AuthKeyEntity>> = dao.getRecent()

    fun getById(id: Int): Flow<AuthKeyEntity?> = dao.getById(id)

    suspend fun addKey(
        algorithm: String,
        displayName: String,
        encryptedPrivateKey: String,
        publicKey: String,
        encryptedDek: String,
    ) {
        dao.insert(
            AuthKeyEntity(
                algorithm = algorithm,
                displayName = displayName.trim(),
                encryptedPrivateKey = encryptedPrivateKey,
                publicKey = publicKey,
                encryptedDek = encryptedDek,
            ),
        )
    }

    suspend fun addKeys(keys: List<AuthKeyEntity>) {
        dao.insertAll(keys.map { it.copy(displayName = it.displayName.trim()) })
    }

    suspend fun deleteKey(key: AuthKeyEntity) {
        dao.delete(key)
    }

    suspend fun updateKey(key: AuthKeyEntity) {
        dao.update(key.copy(displayName = key.displayName.trim()))
    }

    suspend fun markUsed(id: Int, timestamp: Long = System.currentTimeMillis()) {
        dao.markUsed(id, timestamp)
    }
}

class AuthKeyViewModel(
    private val repository: AuthKeyRepository,
    private val keyGenerator: AuthKeyGenerator,
    private val transferManager: AuthKeyTransferManager,
) : ViewModel() {

    val keys: StateFlow<List<AuthKeyEntity>> =
        repository.getAllKeys().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val recentKeys: StateFlow<List<AuthKeyEntity>> =
        repository.getRecentKeys().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _creationState = MutableStateFlow<AuthKeyCreationState>(AuthKeyCreationState.Idle)
    val creationState: StateFlow<AuthKeyCreationState> = _creationState.asStateFlow()

    private val _transferState = MutableStateFlow<KeyTransferState>(KeyTransferState.Idle)
    val transferState: StateFlow<KeyTransferState> = _transferState.asStateFlow()

    fun getKeyById(id: Int): Flow<AuthKeyEntity?> = repository.getById(id)

    fun createKey(algorithm: AuthKeyAlgorithm, displayName: String) {
        if (_creationState.value is AuthKeyCreationState.Creating) return

        _creationState.value = AuthKeyCreationState.Creating
        viewModelScope.launch {
            try {
                val generated = withContext(Dispatchers.Default) {
                    keyGenerator.generate(algorithm)
                }
                repository.addKey(
                    algorithm = generated.algorithm.storageValue,
                    displayName = displayName.trim(),
                    encryptedPrivateKey = generated.encryptedPrivateKey,
                    publicKey = generated.publicKey,
                    encryptedDek = generated.encryptedDek,
                )
                _creationState.value = AuthKeyCreationState.Created
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _creationState.value = AuthKeyCreationState.Failed(
                    e.message ?: "Could not create key",
                )
            }
        }
    }

    fun resetCreationState() {
        _creationState.value = AuthKeyCreationState.Idle
    }

    data class InputModel(
        val algorithm: String,
        val displayName: String,
        val encryptedPrivateKey: String,
        val publicKey: String,
        val encryptedDek: String,
    )

    fun addKey(
        algorithm: String,
        displayName: String,
        encryptedPrivateKey: String,
        publicKey: String,
        encryptedDek: String,
    ) {
        validate(algorithm, encryptedPrivateKey, publicKey, encryptedDek)

        viewModelScope.launch {
            repository.addKey(
                algorithm = algorithm,
                displayName = displayName,
                encryptedPrivateKey = encryptedPrivateKey,
                publicKey = publicKey,
                encryptedDek = encryptedDek,
            )
        }
    }

    fun addKey(input: InputModel) {
        addKey(
            algorithm = input.algorithm,
            displayName = input.displayName,
            encryptedPrivateKey = input.encryptedPrivateKey,
            publicKey = input.publicKey,
            encryptedDek = input.encryptedDek,
        )
    }

    fun deleteKey(key: AuthKeyEntity) {
        viewModelScope.launch {
            repository.deleteKey(key)
        }
    }

    fun updateKey(key: AuthKeyEntity) {
        validate(
            algorithm = key.algorithm,
            encryptedPrivateKey = key.encryptedPrivateKey,
            publicKey = key.publicKey,
            encryptedDek = key.encryptedDek,
        )

        viewModelScope.launch {
            repository.updateKey(key)
        }
    }

    fun markKeyUsed(keyId: Int, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.markUsed(keyId, timestamp)
        }
    }

    fun inspectImport(uri: Uri, passphrase: CharArray? = null, formatHint: KeyFileFormat? = null) {
        if (_transferState.value is KeyTransferState.Working) return

        val previous = _transferState.value
        _transferState.value = KeyTransferState.Working
        viewModelScope.launch {
            try {
                val existingFingerprints = repository
                    .getAllKeys()
                    .first()
                    .mapNotNull { sshPublicKeyFingerprint(it.publicKey) }
                    .toSet()

                when (val result = transferManager.inspect(uri, passphrase, existingFingerprints)) {
                    is ImportInspection.NeedsPassphrase -> {
                        _transferState.value = KeyTransferState.AwaitingPassphrase(
                            uri = uri,
                            format = result.format,
                            error = null,
                        )
                    }

                    is ImportInspection.Ready -> {
                        _transferState.value = KeyTransferState.ImportPreview(result.candidates)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val retryFormat = formatHint
                    ?: (previous as? KeyTransferState.AwaitingPassphrase)?.format
                _transferState.value = if (retryFormat != null) {
                    KeyTransferState.AwaitingPassphrase(
                        uri = uri,
                        format = retryFormat,
                        error = e.message ?: "Could not read the key file",
                    )
                } else {
                    KeyTransferState.Failed(e.message ?: "Could not read the key file")
                }
            } finally {
                passphrase?.fill('\u0000')
            }
        }
    }

    fun importCandidates(candidates: List<ImportCandidate>) {
        if (candidates.isEmpty()) {
            _transferState.value = KeyTransferState.Failed("Select at least one key")
            return
        }
        if (_transferState.value is KeyTransferState.Working) return

        _transferState.value = KeyTransferState.Working
        viewModelScope.launch {
            try {
                val generated = withContext(Dispatchers.Default) {
                    candidates.map { candidate ->
                        candidate.displayName to keyGenerator.fromKeyPair(candidate.keyPair)
                    }
                }

                repository.addKeys(
                    generated.map { (displayName, key) ->
                        AuthKeyEntity(
                            algorithm = key.algorithm.storageValue,
                            displayName = displayName,
                            encryptedPrivateKey = key.encryptedPrivateKey,
                            publicKey = key.publicKey,
                            encryptedDek = key.encryptedDek,
                        )
                    },
                )

                val count = generated.size
                _transferState.value = KeyTransferState.Finished(
                    "Imported $count key${if (count == 1) "" else "s"}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transferState.value = KeyTransferState.Failed(
                    e.message ?: "Could not import the keys",
                )
            }
        }
    }

    fun export(request: KeyExportRequest, uri: Uri) {
        if (_transferState.value is KeyTransferState.Working) return

        _transferState.value = KeyTransferState.Working
        viewModelScope.launch {
            try {
                transferManager.export(request, uri)
                val message = when (request.format) {
                    KeyExportFormat.OPENSSH -> "Exported private key"

                    KeyExportFormat.BUNDLE ->
                        "Exported ${request.keys.size} key${if (request.keys.size == 1) "" else "s"}"
                }
                _transferState.value = KeyTransferState.Finished(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transferState.value = KeyTransferState.Failed(
                    e.message ?: "Could not export the key",
                )
            }
        }
    }

    fun clearTransferState() {
        _transferState.value = KeyTransferState.Idle
    }

    private fun validate(
        algorithm: String,
        encryptedPrivateKey: String,
        publicKey: String,
        encryptedDek: String,
    ) {
        require(algorithm.isNotBlank()) { "Key algorithm cannot be blank" }
        require(encryptedPrivateKey.isNotBlank()) { "Private key cannot be blank" }
        require(publicKey.isNotBlank()) { "Public key cannot be blank" }
        require(encryptedDek.isNotBlank()) { "Encrypted data key cannot be blank" }
    }
}

sealed interface AuthKeyCreationState {
    data object Idle : AuthKeyCreationState

    data object Creating : AuthKeyCreationState

    data object Created : AuthKeyCreationState

    data class Failed(val message: String) : AuthKeyCreationState
}

sealed interface KeyTransferState {
    data object Idle : KeyTransferState

    data object Working : KeyTransferState

    data class AwaitingPassphrase(val uri: Uri, val format: KeyFileFormat, val error: String?) :
        KeyTransferState

    data class ImportPreview(val candidates: List<ImportCandidate>) : KeyTransferState

    data class Finished(val message: String) : KeyTransferState

    data class Failed(val message: String) : KeyTransferState
}
