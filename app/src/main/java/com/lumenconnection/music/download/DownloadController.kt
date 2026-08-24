package com.lumenconnection.music.download

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.util.Log
import com.lumenconnection.music.Graph
import com.lumenconnection.music.db.Origin
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.extractor.YtDlpEngine
import com.lumenconnection.music.media.AudioStorage
import com.lumenconnection.music.media.LocalImport
import com.lumenconnection.music.util.TextUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Fila de downloads do app.
 *
 * O desktop baixa direto da página "Inserção de Músicas", com progresso ao vivo
 * (`src/pages/addmusicpage.cpp`), sem fila persistida — a mesma escolha aqui: a
 * fila vive em memória e o [DownloadService] só mantém o processo vivo enquanto
 * há trabalho.
 */
object DownloadController {

    enum class Status { PENDING, RUNNING, DONE, FAILED }

    data class Job(
        val id: String = UUID.randomUUID().toString(),
        val label: String,
        /** URL do YouTube ou alvo `ytsearch1:Artista - Título`. */
        val target: String,
        val playlistId: Long? = null,
        val status: Status = Status.PENDING,
        val progress: Float = 0f,
        /** Id de recurso da mensagem de erro amigável, quando falha. */
        val errorRes: Int? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private var draining = false
    private var appContext: Context? = null

    fun enqueue(context: Context, jobs: List<Job>) {
        if (jobs.isEmpty()) return
        appContext = context.applicationContext
        _jobs.value = _jobs.value + jobs
        startService()
        drain()
    }

    fun enqueueUrl(context: Context, url: String, playlistId: Long? = null) =
        enqueue(context, listOf(Job(label = url, target = url, playlistId = playlistId)))

    fun clearFinished() {
        _jobs.value = _jobs.value.filter { it.status == Status.PENDING || it.status == Status.RUNNING }
    }

    fun cancelAll() {
        _jobs.value.filter { it.status == Status.RUNNING }.forEach { YtDlpEngine.cancel(it.id) }
        _jobs.value = emptyList()
    }

    private fun startService() {
        val ctx = appContext ?: return
        runCatching { ctx.startService(Intent(ctx, DownloadService::class.java)) }
    }

    private fun drain() {
        if (draining) return
        draining = true
        scope.launch {
            try {
                while (true) {
                    val next = mutex.withLock {
                        _jobs.value.firstOrNull { it.status == Status.PENDING }
                    } ?: break
                    runJob(next)
                }
            } finally {
                draining = false
                appContext?.let { ctx ->
                    runCatching { ctx.stopService(Intent(ctx, DownloadService::class.java)) }
                }
            }
        }
    }

    private suspend fun runJob(job: Job) {
        update(job.id) { it.copy(status = Status.RUNNING, progress = 0f) }
        val ctx = appContext ?: return

        val playlist = job.playlistId?.let { Graph.db.playlistDao().byId(it) }
        val destDir = AudioStorage.playlistDir(ctx, playlist?.dirName)

        try {
            val files = YtDlpEngine.downloadAudio(
                url = job.target,
                destDir = destDir,
                processId = job.id,
                onProgress = { p -> update(job.id) { it.copy(progress = p) } },
            )

            val audio = files.firstOrNull { it.extension.lowercase() in LocalImport.SUPPORTED_EXTENSIONS }
            if (audio == null) {
                update(job.id) {
                    it.copy(status = Status.FAILED, errorRes = com.lumenconnection.music.R.string.download_no_audio_found)
                }
                return
            }

            importFile(ctx, audio, job.playlistId)
            update(job.id) { it.copy(status = Status.DONE, progress = 1f) }
        } catch (t: Throwable) {
            Log.w("DownloadController", "falha ao baixar ${job.target}", t)

            // HTTP 403 do YouTube: atualiza o yt-dlp e tenta uma vez mais, como
            // faz o desktop quando o binário fica velho.
            if (t.message?.contains("403") == true) {
                val updated = YtDlpEngine.update(ctx)
                if (updated) {
                    runCatching {
                        val files = YtDlpEngine.downloadAudio(
                            url = job.target,
                            destDir = destDir,
                            processId = job.id + "-retry",
                            onProgress = { p -> update(job.id) { it.copy(progress = p) } },
                        )
                        files.firstOrNull { it.extension.lowercase() in LocalImport.SUPPORTED_EXTENSIONS }
                            ?.let { audio ->
                                importFile(ctx, audio, job.playlistId)
                                update(job.id) { it.copy(status = Status.DONE, progress = 1f) }
                                return
                            }
                    }
                }
            }

            update(job.id) {
                it.copy(status = Status.FAILED, errorRes = FriendlyError.resFor(t.message))
            }
        }
    }

    /** Cria a faixa na biblioteca a partir do arquivo baixado. */
    private suspend fun importFile(context: Context, file: File, playlistId: Long?) {
        val (artist, title) = LocalImport.parseArtistTitle(file.name)
        val finalArtist = artist.ifBlank {
            context.getString(com.lumenconnection.music.R.string.unknown_artist)
        }
        val (c1, c2) = LocalImport.randomGradient()

        val duration = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(file.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                }
            }.getOrDefault(0L)
        }

        val id = Graph.db.trackDao().insert(
            TrackEntity(
                title = title,
                artist = finalArtist,
                filePath = file.absolutePath,
                durationMs = duration,
                coverColor1 = c1,
                coverColor2 = c2,
                ownerPlaylistId = playlistId,
                addedAt = System.currentTimeMillis(),
                searchKey = TextUtils.searchKey(title, finalArtist),
                origin = Origin.DOWNLOAD,
                fileSize = file.length(),
                fileMtime = file.lastModified() / 1000,
            )
        )
        if (playlistId != null) Graph.library.addTrackToPlaylist(playlistId, id)
    }

    private fun update(id: String, transform: (Job) -> Job) {
        _jobs.value = _jobs.value.map { if (it.id == id) transform(it) else it }
    }
}
