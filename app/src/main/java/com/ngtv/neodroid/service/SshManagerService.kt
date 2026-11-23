package com.ngtv.neodroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ngtv.neodroid.NeodroidApplication
import com.ngtv.neodroid.R
import com.ngtv.neodroid.database.SSHHostRepository
import com.ngtv.neodroid.sshUtils.SshConnection
import com.ngtv.neodroid.sshUtils.SshConnectionHistory
import com.ngtv.neodroid.sshUtils.SshSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SshManagerService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var repository: SSHHostRepository

    lateinit var sessionManager: SshSessionManager
        private set

    private val _isRunning = MutableStateFlow(true)
    val isRunning: StateFlow<Boolean> = _isRunning

    inner class LocalBinder : Binder() {
        fun getService(): SshManagerService = this@SshManagerService
    }

    override fun onCreate() {
        super.onCreate()

        repository = (application as NeodroidApplication).container.sshHostRepository

        sessionManager =
            SshSessionManager(
                history =
                    object : SshConnectionHistory {
                        override suspend fun onConnected(
                            connection: SshConnection,
                            hostKeyLearned: Boolean,
                        ) {
                            withContext(Dispatchers.IO) {
                                repository.markConnected(
                                    connection.hostId,
                                    connection.connectedAt,
                                )

                                if (hostKeyLearned) {
                                    connection.hostKeyFingerprint?.let { fingerprint ->
                                        repository.updateHostKey(
                                            connection.hostId,
                                            fingerprint,
                                        )
                                    }
                                }
                            }
                        }
                    },
            )

        serviceScope.launch {
            sessionManager.connections.collect { connections ->
                val active = connections.count { it.isHealthy }
                val message =
                    when (active) {
                        0 -> "No active connections"

                        1 -> connections.firstOrNull { it.isHealthy }?.displayName
                            ?: "1 active connection"

                        else -> "$active active connections"
                    }
                updateNotificationText(message)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("SSH service running"))
        return START_STICKY
    }

    fun updateNotificationText(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(message: String): Notification {
        val channelId = "ssh_channel_id"
        val channelName = "SSH Connection"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(channelId) == null) {
                val channel =
                    NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_LOW,
                    )
                manager?.createNotificationChannel(channel)
            }
        }

        val stopIntent = Intent(this, SshManagerService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle("Neodroid")
            .setContentText(message)
            .setOngoing(true)
            .addAction(
                R.drawable.ic_stop,
                "Stop",
                stopPendingIntent,
            )
            .build()
    }

    override fun onDestroy() {
        _isRunning.value = false
        runCatching { sessionManager.close() }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2048
        private const val ACTION_STOP = "com.ngtv.neodroid.service.ACTION_STOP"
    }
}
