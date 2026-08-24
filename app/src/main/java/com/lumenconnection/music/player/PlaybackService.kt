package com.lumenconnection.music.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lumenconnection.music.MainActivity

/**
 * Serviço de mídia — o equivalente Android do SMTC do desktop
 * (`src/platform/nowplaying_win.cpp`): notificação, tela de bloqueio, botões do
 * fone Bluetooth e teclas de mídia, tudo pela [MediaSession].
 *
 * O player é o mesmo objeto que a UI usa ([PlayerController.exoPlayer]) — como
 * ambos vivem no mesmo processo, a sessão apenas o expõe ao sistema, sem a
 * camada de IPC do MediaController.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlayerController.ensureInitialized(this)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, PlayerController.exoPlayer)
            .setSessionActivity(openApp)
            .build()
            // Registrar a sessão no serviço é o que faz o Media3 publicar a
            // notificação e promover o serviço a primeiro plano. Sem isso a
            // sessão existe (as teclas de mídia até funcionam), mas o sistema
            // mata a reprodução assim que o app sai de vista.
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * O usuário fechou o app pelos recentes. Se nada está tocando, o serviço não
     * tem motivo para continuar; se está, a reprodução segue em segundo plano.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Última chance de gravar a posição — o desktop faz o mesmo no closeEvent.
        PlayerController.persistState()
        session?.run {
            release()
            session = null
        }
        super.onDestroy()
    }
}
