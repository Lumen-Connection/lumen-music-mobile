package com.lumenconnection.music

import android.app.Application
import android.util.Log
import com.lumenconnection.music.player.PlayerController

class LumenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)

        // Captura `Throwable`, não `Exception`: a lição do lumen-stream-mobile é
        // que um `ExceptionInInitializerError` é `Error` e escaparia de um catch
        // de `Exception`, derrubando o app no lançamento.
        runCatching { PlayerController.ensureInitialized(this) }
            .onFailure { Log.e("LumenApp", "Falha ao iniciar o player", it) }
    }
}
