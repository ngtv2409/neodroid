package com.ngtv.neodroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.ngtv.neodroid.database.AppDatabase
import com.ngtv.neodroid.database.SSHHostRepository
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.database.SSHHostViewModelFactory
import com.ngtv.neodroid.main.navigation.MainNavigation

/* Handle Main screen, add and manage SSH hosts */

class MainActivity : ComponentActivity() {

    private val database by lazy {
        AppDatabase.getInstance(applicationContext)
    }

    private val repository by lazy {
        SSHHostRepository(database.SSHHostDao())
    }

    private val viewModelFactory by lazy {
        SSHHostViewModelFactory(repository)
    }

    private val sshHostViewModel: SSHHostViewModel by viewModels {
        viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                MainNavigation(sshHostViewModel)
            }
        }
    }
}
