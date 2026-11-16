package com.ngtv.neodroid.database

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
            "WHERE last_used_at IS NOT NULL " +
            "ORDER BY COALESCE(last_used_at, created_at) DESC, id DESC",
    )
    fun getRecent(): Flow<List<AuthKeyEntity>>

    @Query("SELECT * FROM auth_keys WHERE id = :id LIMIT 1")
    fun getById(id: Int): Flow<AuthKeyEntity?>

    @Insert
    suspend fun insert(key: AuthKeyEntity)

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

class AuthKeyViewModel(private val repository: AuthKeyRepository) : ViewModel() {

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

    fun getKeyById(id: Int): Flow<AuthKeyEntity?> = repository.getById(id)

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
