package com.lumenconnection.music.sync

import android.content.Context
import android.util.Log
import com.lumenconnection.music.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Sincronização automática ao abrir o app.
 *
 * Só dispara se já houver pareamento e o PC responder ao `ping` — nunca deixa o
 * usuário esperando: o `ping` tem timeout curto e, se falhar, o app segue como
 * se nada fosse. Sincronizar é conveniência, não pré-requisito para ouvir música.
 *
 * O endereço é reconferido pela descoberta quando o IP guardado não responde: um
 * PC com IP dinâmico não deve exigir novo pareamento, porque a identidade é o
 * `serverId`, não o endereço.
 */
object AutoSync {

    private const val TAG = "AutoSync"

    /** Intervalo mínimo entre sincronizações automáticas. */
    private const val MIN_INTERVAL_MS = 5 * 60 * 1000L

    suspend fun maybeSync(context: Context) {
        val settings = Graph.settings
        val server = settings.pairedServer.first() ?: return

        val since = System.currentTimeMillis() - settings.lastSyncAt.first()
        if (since < MIN_INTERVAL_MS) return

        val reachable = withContext(Dispatchers.IO) {
            runCatching { SyncApi(server.host, server.port, server.token).ping() }
                .map { it.serverId == server.serverId }
                .getOrDefault(false)
        }

        if (reachable) {
            SyncService.start(context)
            return
        }

        // O endereço guardado não responde: talvez o PC tenha trocado de IP.
        val rediscovered = withContext(Dispatchers.IO) {
            runCatching { DiscoveryClient.discover(timeoutMs = 1500) }
                .getOrDefault(emptyList())
                .firstOrNull { it.serverId == server.serverId }
        } ?: return

        Log.i(TAG, "PC reencontrado em ${rediscovered.host}:${rediscovered.port}")
        settings.updateServerAddress(rediscovered.host, rediscovered.port)
        SyncService.start(context)
    }
}
