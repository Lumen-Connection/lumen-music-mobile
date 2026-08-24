package com.lumenconnection.music.extractor

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Motor de fallback: yt-dlp com Python embutido (cobertura máxima) e ffmpeg
 * embarcado para converter o áudio.
 *
 * O desktop remuxa para `.opus` quando o ffmpeg está presente
 * (`src/widgets/mediatools.h`); aqui o ffmpeg vem sempre embarcado, então a
 * conversão para opus é o caminho padrão.
 */
object YtDlpEngine {

    /** Uma entrada de playlist devolvida por `--flat-playlist`. */
    data class FlatEntry(val id: String, val title: String, val url: String)

    suspend fun downloadAudio(
        url: String,
        destDir: File,
        processId: String,
        rateLimitKbps: Int = 0,
        onProgress: (Float) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        destDir.mkdirs()
        val before = destDir.listFiles()?.map { it.absolutePath }.orEmpty().toSet()

        val request = YoutubeDLRequest(url).apply {
            addOption("-o", "${destDir.absolutePath}/%(title).200s.%(ext)s")
            addOption("--no-mtime")
            addOption("-x")
            addOption("--audio-format", "opus")
            addOption("--no-playlist")
            if (rateLimitKbps > 0) addOption("--limit-rate", "${rateLimitKbps}K")
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
            if (progress >= 0f) onProgress(progress / 100f)
        }

        // Só os arquivos novos: a pasta pode já ter downloads anteriores.
        destDir.listFiles()?.filter { it.absolutePath !in before }?.sortedBy { it.name }.orEmpty()
    }

    /**
     * Lista as entradas de uma playlist do YouTube sem baixar nada, com
     * `-J --flat-playlist` — o mesmo comando do desktop, preservando a ordem
     * original das faixas.
     */
    suspend fun listPlaylist(url: String): List<FlatEntry> = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("-J")
            addOption("--flat-playlist")
        }
        val output = YoutubeDL.getInstance().execute(request).out
        val root = Json.parseToJsonElement(output).jsonObject
        val entries = root["entries"]?.jsonArray ?: return@withContext emptyList()

        entries.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = (obj["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val title = (obj["title"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            val entryUrl = (obj["url"] as? JsonPrimitive)?.content
                ?: "https://www.youtube.com/watch?v=$id"
            FlatEntry(id, title, entryUrl)
        }
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
    }

    /**
     * Atualiza o binário do yt-dlp. O desktop faz o mesmo quando toma HTTP 403
     * do YouTube (`src/tools/ytdlp_bootstrap.cpp`).
     */
    suspend fun update(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching { YoutubeDL.getInstance().updateYoutubeDL(context) }.isSuccess
    }
}
