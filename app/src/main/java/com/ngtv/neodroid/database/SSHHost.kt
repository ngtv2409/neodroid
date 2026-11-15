package com.ngtv.neodroid.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Entity(
    tableName = "ssh_hosts",
    foreignKeys = [
        ForeignKey(
            entity = AuthKeyEntity::class,
            parentColumns = ["id"],
            childColumns = ["auth_key_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("auth_key_id")],
)
data class SSHHostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val server_name: String,
    val hostname: String,
    val port: Int,
    val server_signature: String?,
    val username: String,
    val last_connected_at: Long? = null,
    /** Optional authentication key used instead of a password. */
    val auth_key_id: Int? = null,
    /* ... TODO: add necessary fields */
)

@Dao
interface SSHHostDao {

    @Query("SELECT * FROM ssh_hosts ORDER BY id DESC")
    fun getAll(): Flow<List<SSHHostEntity>>

    @Query(
        "SELECT * FROM ssh_hosts " +
            "ORDER BY last_connected_at DESC, id DESC",
    )
    fun getRecent(): Flow<List<SSHHostEntity>>

    @Query("SELECT * FROM ssh_hosts WHERE id = :id LIMIT 1")
    fun getById(id: Int): Flow<SSHHostEntity?>

    @Insert
    suspend fun insert(host: SSHHostEntity)

    @Update
    suspend fun update(host: SSHHostEntity)

    @Delete
    suspend fun delete(host: SSHHostEntity)

    @Query("UPDATE ssh_hosts SET last_connected_at = :timestamp WHERE id = :id")
    suspend fun markConnected(id: Int, timestamp: Long)

    @Query("UPDATE ssh_hosts SET server_signature = :fingerprint WHERE id = :id")
    suspend fun updateHostKey(id: Int, fingerprint: String)

    @Query("UPDATE ssh_hosts SET server_signature = NULL WHERE id = :id")
    suspend fun clearHostKey(id: Int)
}

class SSHHostRepository(private val dao: SSHHostDao) {
    fun getAllHosts(): Flow<List<SSHHostEntity>> = dao.getAll()

    fun getRecentHosts(): Flow<List<SSHHostEntity>> = dao.getRecent()

    fun getById(id: Int): Flow<SSHHostEntity?> = dao.getById(id)

    suspend fun addHost(
        serverName: String,
        hostname: String,
        port: Int,
        serverSignature: String?,
        username: String,
    ) {
        dao.insert(
            SSHHostEntity(
                server_name = serverName,
                hostname = hostname,
                port = port,
                server_signature = serverSignature,
                username = username,
            ),
        )
    }

    suspend fun deleteHost(host: SSHHostEntity) {
        dao.delete(host)
    }

    suspend fun updateHost(host: SSHHostEntity) {
        dao.update(host)
    }

    suspend fun markConnected(id: Int, timestamp: Long = System.currentTimeMillis()) {
        dao.markConnected(id, timestamp)
    }

    suspend fun updateHostKey(id: Int, fingerprint: String) {
        dao.updateHostKey(id, fingerprint)
    }

    suspend fun clearHostKey(id: Int) {
        dao.clearHostKey(id)
    }
}

class SSHHostViewModel(private val repository: SSHHostRepository) : ViewModel() {

    val hosts: StateFlow<List<SSHHostEntity>> =
        repository.getAllHosts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Hosts ordered by most recent successful connection, then most recently added. */
    val recentHosts: StateFlow<List<SSHHostEntity>> =
        repository.getRecentHosts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun getHostById(id: Int): Flow<SSHHostEntity?> = repository.getById(id)

    data class InputModel(
        val serverName: String,
        val hostname: String,
        val port: Int,
        val serverSignature: String?,
        val username: String,
    )

    fun addHost(
        serverName: String,
        hostname: String,
        port: Int,
        serverSignature: String?,
        username: String,
    ) {
        require(serverName.isNotBlank()) { "Host name cannot be blank" }
        require(hostname.isNotBlank()) { "Host name cannot be blank" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(username.isNotBlank()) { "Username cannot be blank" }

        viewModelScope.launch {
            repository.addHost(
                serverName = serverName,
                hostname = hostname,
                port = port,
                serverSignature = serverSignature,
                username = username,
            )
        }
    }

    fun addHost(input: InputModel) {
        addHost(
            serverName = input.serverName,
            hostname = input.hostname,
            port = input.port,
            serverSignature = input.serverSignature,
            username = input.username,
        )
    }

    fun deleteHost(host: SSHHostEntity) {
        viewModelScope.launch {
            repository.deleteHost(host)
        }
    }

    fun updateHost(host: SSHHostEntity) {
        require(host.server_name.isNotBlank()) { "Host name cannot be blank" }
        require(host.hostname.isNotBlank()) { "Host name cannot be blank" }
        require(host.port in 1..65535) { "Port must be between 1 and 65535" }
        require(host.username.isNotBlank()) { "Username cannot be blank" }

        viewModelScope.launch {
            repository.updateHost(host)
        }
    }

    fun markHostConnected(hostId: Int, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.markConnected(hostId, timestamp)
        }
    }

    suspend fun updateHostKey(hostId: Int, fingerprint: String) {
        repository.updateHostKey(hostId, fingerprint)
    }

    suspend fun clearHostKey(hostId: Int) {
        repository.clearHostKey(hostId)
    }
}

class SSHHostViewModelFactory(private val repository: SSHHostRepository) :
    ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SSHHostViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SSHHostViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}",
        )
    }
}
