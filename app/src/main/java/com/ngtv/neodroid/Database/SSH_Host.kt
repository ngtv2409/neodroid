package com.ngtv.neodroid

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Entity(tableName = "ssh_hosts")
data class SSHHostEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,
    val host_name: String,
    val port: Int,
    val server_signature: String?,
    val username: String,
    /* ... TODO: add necessary fields */
)


@Dao
interface SSHHostDao {

    @Query("SELECT * FROM ssh_hosts ORDER BY id DESC")
    fun getAll(): Flow<List<SSHHostEntity>>

    @Insert
    suspend fun insert(host: SSHHostEntity)

    @Delete
    suspend fun delete(host: SSHHostEntity)
}


class SSHHostRepository(
    private val dao: SSHHostDao
) {
    fun getAllHosts(): Flow<List<SSHHostEntity>> =
        dao.getAll()

    suspend fun addHost(
        name: String,
        host_name: String,
        port: Int,
        server_signature: String?,
        username: String,
    ) {
        dao.insert(SSHHostEntity(
            name = name, host_name = host_name, 
            port = port, server_signature = server_signature,
            username = username
        ))
    }

    suspend fun deleteHost(host: SSHHostEntity) {
        dao.delete(host)
    }
}


class SSHHostViewModel(
    private val repository: SSHHostRepository
) : ViewModel() {

    val hosts: StateFlow<List<SSHHostEntity>> =
        repository.getAllHosts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    data class InputModel(
        val name: String,
        val host_name: String,
        val port: Int,
        val server_signature: String?,
        val username: String
    )

    fun addHost(
        name: String,
        host_name: String,
        port: Int,
        server_signature: String?,
        username: String
    ) {
        require(name.isNotBlank()) { "Host name cannot be blank" }
        require(host_name.isNotBlank()) { "Host name cannot be blank" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        require(username.isNotBlank()) { "Username cannot be blank" }

        viewModelScope.launch {
            repository.addHost(
                name = name,
                host_name = host_name,
                port = port,
                server_signature = server_signature,
                username = username
            )
        }
    }

    fun addHost(input: InputModel) {
        addHost(
            name = input.name,
            host_name = input.host_name,
            port = input.port,
            server_signature = input.server_signature,
            username = input.username
        )
    }

    fun deleteHost(host: SSHHostEntity) {
        viewModelScope.launch {
            repository.deleteHost(host)
        }
    }
}

class SSHHostViewModelFactory(
    private val repository: SSHHostRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(SSHHostViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SSHHostViewModel(repository) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}
