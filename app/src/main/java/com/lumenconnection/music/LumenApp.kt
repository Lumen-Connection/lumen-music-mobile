package com.lumenconnection.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.lumenconnection.music.extractor.NewPipeDownloaderImpl
import com.lumenconnection.music.player.PlayerController
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

class LumenApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        createNotificationChannels()

        runCatching { NewPipe.init(NewPipeDownloaderImpl.instance) }
            .onFailure { Log.e(TAG, "Falha ao iniciar o NewPipe", it) }

        runCatching { PlayerController.ensureInitialized(this) }
            .onFailure { Log.e(TAG, "Falha ao iniciar o player", it) }

        // Python + yt-dlp + ffmpeg são pesados: inicializa fora da thread principal.
        appScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@LumenApp)
                FFmpeg.getInstance().init(this@LumenApp)
                ytDlpReady = true
            } catch (t: Throwable) {
                // Throwable, não Exception: uma falha de <clinit> chega como
                // Error e derrubaria o app inteiro (foi o que quebrou o
                // lumen-stream-mobile v0.1.0). Sem yt-dlp, o resto do app segue.
                Log.e(TAG, "Falha ao iniciar o yt-dlp", t)
            }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        private const val TAG = "LumenApp"
        const val CHANNEL_DOWNLOADS = "downloads"

        /** Falso enquanto o Python não terminou de extrair, ou se a extração falhou. */
        @Volatile
        var ytDlpReady: Boolean = false
            private set
    }
}
