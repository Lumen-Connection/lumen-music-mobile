package com.lumenconnection.music.sync

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
 * Mantém o processo vivo durante uma sincronização.
 *
 * Baixar o áudio de uma biblioteca inteira leva minutos; sem um serviço em
 * primeiro plano o Android mataria o processo no meio e o `.part` teria de ser
 * retomado na próxima vez.
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(NOTIFICATION_ID, buildNotification(SyncEngine.State.Pulling))

        // Só atualiza a notificação. O encerramento é decidido pelo fim da
        // corrotina em onStartCommand, e não por este estado: `state` é um
        // StateFlow e reentrega o último valor na hora da inscrição, de modo
        // que um `Done` da sincronização anterior faria este serviço se matar
        // antes mesmo de começar — foi exatamente o que impediu o segundo sync.
        scope.launch {
            SyncEngine.state.collectLatest { state -> notify(buildNotification(state)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            // `syncNow` devolve false quando já há uma sincronização em curso.
            // Nesse caso este pedido não pode encerrar o serviço: fazer isso
            // cancelaria o escopo e mataria o sync que está trabalhando — dois
            // toques seguidos em "Sincronizar agora" derrubavam o download.
            val ran = SyncEngine.syncNow(applicationContext)
            if (ran) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: SyncEngine.State): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = when (state) {
            is SyncEngine.State.Downloading ->
                getString(R.string.sync_downloading_files, state.done, state.total)
            is SyncEngine.State.Done -> getString(R.string.sync_done)
            is SyncEngine.State.Failed -> getString(R.string.download_failed)
            else -> getString(R.string.sync_in_progress)
        }

        val builder = NotificationCompat.Builder(this, LumenApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(state !is SyncEngine.State.Done && state !is SyncEngine.State.Failed)

        if (state is SyncEngine.State.Downloading && state.total > 0) {
            builder.setProgress(state.total, state.done, false)
        } else if (state is SyncEngine.State.Pushing || state is SyncEngine.State.Pulling) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
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
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, SyncService::class.java)) }
        }
    }
}
