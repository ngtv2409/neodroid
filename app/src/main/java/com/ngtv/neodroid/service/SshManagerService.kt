package com.ngtv.neodroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ngtv.neodroid.sshUtils.SshSessionManager

class SshManagerService : Service() {
    private val binder = LocalBinder()
    val sessionManager = SshSessionManager()

    inner class LocalBinder : Binder() {
        fun getService(): SshManagerService = this@SshManagerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Neodroid")
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        sessionManager.close()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2048
    }
}
