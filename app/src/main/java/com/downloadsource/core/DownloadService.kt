package com.downloadsource.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.downloadsource.MainActivity

class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_PAUSE_ALL") {
            DownloadManager.tasks.value.filter { it.status == TaskStatus.Running }.forEach {
                DownloadManager.pause(this, it.id)
            }
        } else if (intent?.action == "ACTION_RESUME_ALL") {
            DownloadManager.autoResumeTasks(this)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
            startForeground(NOTIF_ID, buildBaseNotification("准备下载...").build())
        }
        return START_NOT_STICKY
    }

    private fun buildBaseNotification(text: String): NotificationCompat.Builder {
        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        
        // 功能：通知栏控制
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).apply { action = "ACTION_PAUSE_ALL" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val resumeIntent = PendingIntent.getService(this, 2, Intent(this, DownloadService::class.java).apply { action = "ACTION_RESUME_ALL" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Download source 正在运行")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "全部暂停", pauseIntent)
            .addAction(android.R.drawable.ic_media_play, "全部继续", resumeIntent)
            .setOngoing(true).setOnlyAlertOnce(true)
    }

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIF_ID = 1001
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, DownloadService::class.java)) }
        
        fun updateProgress(context: Context, fileName: String, downloaded: Long, total: Long, speed: Long) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val openIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val pauseIntent = PendingIntent.getService(context, 1, Intent(context, DownloadService::class.java).apply { action = "ACTION_PAUSE_ALL" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val resumeIntent = PendingIntent.getService(context, 2, Intent(context, DownloadService::class.java).apply { action = "ACTION_RESUME_ALL" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
            val speedText = when {
                speed >= 1L shl 20 -> String.format("%.1f MB/s", speed / (1.0 * (1L shl 20)))
                speed >= 1L shl 10 -> String.format("%.1f KB/s", speed / (1.0 * (1L shl 10)))
                else -> "$speed B/s"
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(fileName)
                .setContentText("$progress% · $speedText")
                .setContentIntent(openIntent)
                .addAction(android.R.drawable.ic_media_pause, "全部暂停", pauseIntent)
                .addAction(android.R.drawable.ic_media_play, "全部继续", resumeIntent)
                .setOngoing(true).setProgress(100, progress, total <= 0).setOnlyAlertOnce(true).build()
            mgr.notify(NOTIF_ID, notif)
        }
    }
}
