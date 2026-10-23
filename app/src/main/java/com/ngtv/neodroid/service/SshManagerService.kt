package com.ngtv.neodroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ngtv.neodroid.sshUtils.SshSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SshManagerService : Service() {
    private val binder = LocalBinder()
    val sessionManager = SshSessionManager()
    private val _isRunning = MutableStateFlow(true)
    val isRunning: StateFlow<Boolean> = _isRunning

    inner class LocalBinder : Binder() {
        fun getService(): SshManagerService = this@SshManagerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification("SSH Service"))
        return START_STICKY
    }

    fun updateNotificationText(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotification(message: String): Notification {
        val channelId = "ssh_channel_id"
        val channelName = "SSH Connection"

        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
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
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Neodroid")
            .setContentText(message)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent,
            )
            .build()
    }

    override fun onDestroy() {
        _isRunning.value = false
        sessionManager.close()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2048
        private const val ACTION_STOP = "com.ngtv.neodroid.service.ACTION_STOP"
    }
}
