package com.downloadsource.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.downloadsource.MainActivity

class DownloadService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DownloadSource::DownloadWakeLock").apply {
            acquire(10 * 60 * 1000L) // 获取 10 分钟唤醒锁防休眠
        }

        val openIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).apply { action = "ACTION_PAUSE_ALL" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val resumeIntent = PendingIntent.getService(this, 2, Intent(this, DownloadService::class.java).apply { action = "ACTION_RESUME_ALL" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Download source 正在运行")
            .setContentText("防止休眠，保障下载")
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "全部暂停", pauseIntent)
            .addAction(android.R.drawable.ic_media_play, "全部继续", resumeIntent)
            .setOngoing(true).setOnlyAlertOnce(true)
            .build()

        startForeground(NOTIF_ID, notif)
        
        // 核心防杀：返回 START_STICKY，系统杀掉后会尝试重建
        return START_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
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
            mgr.notify(NOTIF_ID, mgr.getNotificationChannel(CHANNEL_ID)?.let { notif } ?: notif)
        }
    }
}
