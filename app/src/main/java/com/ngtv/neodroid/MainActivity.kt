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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ngtv.neodroid.main.navigation.MainNavigation
import com.ngtv.neodroid.security.KeystoreStatus
import com.ngtv.neodroid.service.SshManagerService
import com.ngtv.neodroid.ui.theme.NeodroidTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/* Handle Main screen, add and manage SSH hosts */

class MainActivity : ComponentActivity() {

    private fun relaunchFresh() {
        // restart the app by replacing its activity
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            sshService = null
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private var sshService by mutableStateOf<SshManagerService?>(null)
    private var isBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as SshManagerService.LocalBinder
            sshService = localBinder.getService()
            isBound = true
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    sshService?.isRunning?.value?.let {
                        if (!it) {
                            relaunchFresh()
                        } else {
                            sshService?.isRunning?.first { it2 -> !it2 }
                            finishAndRemoveTask()
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sshService = null
            isBound = false
        }
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
            NeodroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val app = application as NeodroidApplication
                    val sessionManager = sshService?.sessionManager
                    val keystoreStatus by
                        app
                            .keystoreKeyManager
                            .status
                            .collectAsStateWithLifecycle()

                    val keystoreInitialized =
                        keystoreStatus !is KeystoreStatus.NotInitialized

                    if (isBound && sessionManager != null && keystoreInitialized) {
                        MainNavigation(app.container, sessionManager)
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        }
    }
}
