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

class AppContainer(context: Context) {

    private val database = AppDatabase.getInstance(context.applicationContext)

    val sshHostRepository = SSHHostRepository(database.SSHHostDao())

    val authKeyRepository = AuthKeyRepository(database.AuthKeyDao())

    val sshHostViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SSHHostViewModel(sshHostRepository) }
    }

    val authKeyViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { AuthKeyViewModel(authKeyRepository) }
    }
}
