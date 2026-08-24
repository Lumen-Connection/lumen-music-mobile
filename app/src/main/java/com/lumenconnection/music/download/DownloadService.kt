package com.lumenconnection.music.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lumenconnection.music.LumenApp
import com.lumenconnection.music.MainActivity
import com.lumenconnection.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Mantém o processo vivo enquanto há downloads e mostra o progresso na
 * notificação. A fila em si vive no [DownloadController]; este serviço é só o
 * suporte de ciclo de vida que o Android exige para trabalho longo.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(NOTIFICATION_ID, buildNotification(0, 0, 0f))

        scope.launch {
            DownloadController.jobs.collectLatest { jobs ->
                val done = jobs.count {
                    it.status == DownloadController.Status.DONE ||
                        it.status == DownloadController.Status.FAILED
                }
                val running = jobs.firstOrNull { it.status == DownloadController.Status.RUNNING }
                notify(buildNotification(done, jobs.size, running?.progress ?: 0f))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        androidx.core.app.NotificationManagerCompat.from(this)
            .let { manager ->
                runCatching { manager.notify(NOTIFICATION_ID, notification) }
            }
    }

    private fun buildNotification(done: Int, total: Int, progress: Float): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = if (total > 0) getString(R.string.sync_downloading_files, done, total)
        else getString(R.string.download_downloading)

        return NotificationCompat.Builder(this, LumenApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .build()
    }

    /** Nome próprio para não colidir com o `startForeground` do [Service]. */
    private fun startForegroundCompat(id: Int, notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, id, notification, type)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
