package com.ngtv.neodroid.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

@Entity(tableName = "ssh_hosts")
data class SSHHostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val server_name: String,
    val hostname: String,
    val port: Int,
    val server_signature: String?,
    val username: String,
    /* ... TODO: add necessary fields */
)

@Dao
interface SSHHostDao {

    @Query("SELECT * FROM ssh_hosts ORDER BY id DESC")
    fun getAll(): Flow<List<SSHHostEntity>>

    @Query("SELECT * FROM ssh_hosts WHERE id = :id LIMIT 1")
    fun getById(id: Int): Flow<SSHHostEntity?>

    @Insert
    suspend fun insert(host: SSHHostEntity)

    @Update
    suspend fun update(host: SSHHostEntity)

    @Delete
    suspend fun delete(host: SSHHostEntity)
}

class SSHHostRepository(private val dao: SSHHostDao) {
    fun getAllHosts(): Flow<List<SSHHostEntity>> = dao.getAll()

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
}

class SSHHostViewModel(private val repository: SSHHostRepository) : ViewModel() {

    val hosts: StateFlow<List<SSHHostEntity>> =
        repository.getAllHosts().stateIn(
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
