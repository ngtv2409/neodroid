package com.ngtv.neodroid

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ngtv.neodroid.database.AppDatabase
import com.ngtv.neodroid.database.AuthKeyRepository
import com.ngtv.neodroid.database.AuthKeyViewModel
import com.ngtv.neodroid.database.SSHHostRepository
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.nvim.NvimIntegrationHandler
import com.ngtv.neodroid.security.AuthKeyGenerator
import com.ngtv.neodroid.security.AuthKeyMaterial
import com.ngtv.neodroid.security.AuthKeyTransferManager
import com.ngtv.neodroid.security.KeystoreKeyManager
import com.ngtv.neodroid.settings.SettingsRepository

class AppContainer(context: Context) {

    private val database = AppDatabase.getInstance(context.applicationContext)

    private val settingsRepository = SettingsRepository(context)

    init {
        NvimIntegrationHandler.initialize(context, settingsRepository)
    }

    private val keystoreKeyManager = KeystoreKeyManager.getInstance(context)

    private val authKeyGenerator = AuthKeyGenerator(keystoreKeyManager)

    val authKeyMaterial = AuthKeyMaterial(keystoreKeyManager)

    private val authKeyTransferManager = AuthKeyTransferManager(context, authKeyMaterial)

    val sshHostRepository = SSHHostRepository(database.SSHHostDao())

    val authKeyRepository = AuthKeyRepository(database.AuthKeyDao())

    val sshHostViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SSHHostViewModel(sshHostRepository) }
    }

    val authKeyViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AuthKeyViewModel(
                repository = authKeyRepository,
                keyGenerator = authKeyGenerator,
                transferManager = authKeyTransferManager,
            )
        }
    }
}
