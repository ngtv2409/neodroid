package com.ngtv.neodroid

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ngtv.neodroid.database.AppDatabase
import com.ngtv.neodroid.database.SSHHostRepository
import com.ngtv.neodroid.database.SSHHostViewModel
import com.ngtv.neodroid.database.SSHHostViewModelFactory
import com.ngtv.neodroid.main.navigation.MainNavigation
import com.ngtv.neodroid.service.SshManagerService
import com.ngtv.neodroid.sshUtils.SshSessionManager

/* Handle Main screen, add and manage SSH hosts */

class MainActivity : ComponentActivity() {
    private var sshService by mutableStateOf<SshManagerService?>(null)
    private var isBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as SshManagerService.LocalBinder
            sshService = localBinder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sshService = null
            isBound = false
        }
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        }

        val intent = Intent(this, SshManagerService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            val sessionManager = sshService?.sessionManager
            if (isBound && sessionManager != null) {
                MaterialTheme {
                    MainNavigation(sshHostViewModel, sessionManager)
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        }
    }
}
