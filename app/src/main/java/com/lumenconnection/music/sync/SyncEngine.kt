package com.lumenconnection.music.sync

import android.content.Context
import android.util.Log
import com.lumenconnection.music.Graph
import com.lumenconnection.music.db.DownloadState
import com.lumenconnection.music.db.Origin
import com.lumenconnection.music.db.PlaylistEntity
import com.lumenconnection.music.db.PlaylistTrackEntity
import com.lumenconnection.music.db.SortMode
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.media.AudioStorage
import com.lumenconnection.music.util.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Motor de sincronização: envia o estado local, puxa o snapshot e reconcilia.
 *
 * Ordem deliberada — **push antes de pull**: assim o snapshot já volta
 * refletindo o que acabou de subir, e o celular converge num passo só.
 *
 * O desktop é a fonte da verdade: entidades com `origin = SYNC` são criadas,
 * atualizadas e apagadas conforme o snapshot; as locais e as baixadas aqui
 * nunca são tocadas.
 */
object SyncEngine {

    private const val TAG = "SyncEngine"

    sealed interface State {
        data object Idle : State
        data object Pushing : State
        data object Pulling : State
        data class Downloading(val done: Int, val total: Int) : State
        data class Done(val at: Long) : State
        data class Failed(val reason: Reason) : State
    }

    enum class Reason { UNAUTHORIZED, PROTOCOL, NETWORK, UNKNOWN }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var running = false

    suspend fun syncNow(context: Context): Boolean {
        if (running) return false
        running = true
        try {
            val settings = Graph.settings
            val server = settings.pairedServer.first() ?: run {
                _state.value = State.Failed(Reason.UNAUTHORIZED)
                return false
            }

            val api = SyncApi(server.host, server.port, server.token)
            val deviceId = settings.deviceId()

            _state.value = State.Pushing
            pushLocalState(api, deviceId)

            _state.value = State.Pulling
            val snapshot = api.library()
            applySnapshot(context, snapshot)

            downloadSelectedAudio(context, api)

            settings.setLastSyncAt(System.currentTimeMillis())
            _state.value = State.Done(System.currentTimeMillis())
            return true
        } catch (e: SyncUnauthorizedException) {
            // O usuário revogou este aparelho no PC.
            Graph.settings.clearPairedServer()
            _state.value = State.Failed(Reason.UNAUTHORIZED)
            return false
        } catch (e: SyncProtocolMismatchException) {
            _state.value = State.Failed(Reason.PROTOCOL)
            return false
        } catch (e: java.io.IOException) {
            Log.w(TAG, "falha de rede no sync", e)
            _state.value = State.Failed(Reason.NETWORK)
            return false
        } catch (t: Throwable) {
            Log.e(TAG, "falha inesperada no sync", t)
            _state.value = State.Failed(Reason.UNKNOWN)
            return false
        } finally {
            running = false
        }
    }

    // --- Push -----------------------------------------------------------------

    private suspend fun pushLocalState(api: SyncApi, deviceId: String) {
        val trackDao = Graph.db.trackDao()
        val playlistDao = Graph.db.playlistDao()
        val linkDao = Graph.db.playlistTrackDao()

        val dirtyLikes = trackDao.dirtyLikes().filter { it.remoteId != null }
        val pendingPlays = trackDao.pendingPlays().filter { it.remoteId != null }

        // Playlists nascidas aqui: sobem inteiras, com as faixas que o desktop
        // conhece (as só-locais não têm remoteId e ficam de fora).
        val localPlaylists = playlistDao.notYetPushed().filter { it.origin != Origin.SYNC }
        val newPlaylists = localPlaylists.mapNotNull { playlist ->
            val key = playlist.clientKey ?: return@mapNotNull null
            val trackIds = linkDao.orderedTrackIds(playlist.id)
                .mapNotNull { trackDao.byId(it)?.remoteId }
            PushNewPlaylistDto(
                clientKey = key,
                name = playlist.name,
                coverColor1 = playlist.coverColor1,
                coverColor2 = playlist.coverColor2,
                trackIds = trackIds,
            )
        }

        if (dirtyLikes.isEmpty() && pendingPlays.isEmpty() && newPlaylists.isEmpty()) return

        val response = api.push(
            PushDto(
                deviceId = deviceId,
                likes = dirtyLikes.map { PushLikeDto(it.remoteId!!, it.liked, it.likedAt) },
                playCounts = pendingPlays.map {
                    PushPlayCountDto(it.remoteId!!, it.pendingPlayDelta, it.lastPlayedAt)
                },
                newPlaylists = newPlaylists,
            )
        )

        // Só limpa as marcas depois do 200: se o push falhar, tudo é reenviado.
        trackDao.clearLikeDirty(dirtyLikes.map { it.id })
        trackDao.clearPendingPlays(pendingPlays.map { it.id })

        // O desktop devolve o id que adotou para cada clientKey; guardá-lo é o
        // que impede um reenvio de duplicar a playlist.
        val byKey = localPlaylists.associateBy { it.clientKey }
        response.createdPlaylists.forEach { created ->
            byKey[created.clientKey]?.let { playlist ->
                playlistDao.update(playlist.copy(remoteId = created.id))
            }
        }
    }

    // --- Pull -----------------------------------------------------------------

    private suspend fun applySnapshot(context: Context, snapshot: SnapshotDto) =
        withContext(Dispatchers.IO) {
            val trackDao = Graph.db.trackDao()
            val playlistDao = Graph.db.playlistDao()
            val linkDao = Graph.db.playlistTrackDao()

            // --- playlists ---
            val remotePlaylistIds = snapshot.playlists.map { it.id }.toSet()
            val localByRemote = playlistDao.allSynced().associateBy { it.remoteId }

            snapshot.playlists.forEach { dto ->
                val existing = localByRemote[dto.id]
                val sortMode = runCatching { SortMode.valueOf(dto.sortMode.uppercase()) }
                    .getOrDefault(SortMode.CUSTOM)

                if (existing == null) {
                    runCatching {
                        playlistDao.insert(
                            PlaylistEntity(
                                remoteId = dto.id,
                                name = uniqueName(playlistDao, dto.name),
                                searchName = TextUtils.normalized(dto.name),
                                coverColor1 = dto.coverColor1,
                                coverColor2 = dto.coverColor2,
                                dirName = dto.name,
                                sortMode = sortMode,
                                createdAt = dto.createdAt,
                                origin = Origin.SYNC,
                            )
                        )
                    }.onFailure { Log.w(TAG, "playlist ${dto.name} não pôde ser inserida", it) }
                } else {
                    playlistDao.update(
                        existing.copy(
                            name = existing.name,     // renomear local não é sobrescrito
                            coverColor1 = dto.coverColor1,
                            coverColor2 = dto.coverColor2,
                            sortMode = sortMode,
                        )
                    )
                }
            }

            // Playlist que sumiu do desktop some daqui — só as sincronizadas.
            playlistDao.allSynced()
                .filter { it.remoteId != null && it.remoteId !in remotePlaylistIds }
                .forEach { playlistDao.deleteById(it.id) }

            // --- faixas ---
            val remoteTrackIds = snapshot.tracks.map { it.id }.toSet()
            val localTracksByRemote = trackDao.allSynced().associateBy { it.remoteId }

            snapshot.tracks.forEach { dto ->
                val existing = localTracksByRemote[dto.id]
                if (existing == null) {
                    trackDao.insert(
                        TrackEntity(
                            remoteId = dto.id,
                            title = dto.title,
                            artist = dto.artist,
                            filePath = null,          // desce depois, se escolhido
                            durationMs = dto.durationMs,
                            coverColor1 = dto.coverColor1,
                            coverColor2 = dto.coverColor2,
                            liked = dto.liked,
                            likedAt = dto.likedAt,
                            addedAt = dto.addedAt,
                            playCount = dto.playCount,
                            lastPlayedAt = dto.lastPlayedAt,
                            missing = dto.missing,
                            searchKey = TextUtils.searchKey(dto.title, dto.artist),
                            origin = Origin.SYNC,
                            downloadState = DownloadState.NONE,
                            fileSize = dto.fileSize,
                            fileMtime = dto.fileMtime,
                        )
                    )
                } else {
                    // O conteúdo mudou no PC: o arquivo local não vale mais.
                    val contentChanged = existing.fileSize != dto.fileSize ||
                        existing.fileMtime != dto.fileMtime
                    if (contentChanged) existing.filePath?.let { File(it).delete() }

                    trackDao.update(
                        existing.copy(
                            title = dto.title,
                            artist = dto.artist,
                            durationMs = dto.durationMs,
                            coverColor1 = dto.coverColor1,
                            coverColor2 = dto.coverColor2,
                            // Marcas locais ainda não enviadas têm precedência.
                            liked = if (existing.likeDirty) existing.liked else dto.liked,
                            likedAt = if (existing.likeDirty) existing.likedAt else dto.likedAt,
                            playCount = dto.playCount + existing.pendingPlayDelta,
                            lastPlayedAt = maxOf(dto.lastPlayedAt, existing.lastPlayedAt),
                            missing = dto.missing,
                            searchKey = TextUtils.searchKey(dto.title, dto.artist),
                            fileSize = dto.fileSize,
                            fileMtime = dto.fileMtime,
                            filePath = if (contentChanged) null else existing.filePath,
                            downloadState = if (contentChanged) DownloadState.NONE
                            else existing.downloadState,
                        )
                    )
                }
            }

            // Faixa apagada no desktop some daqui, com arquivo e tudo.
            trackDao.allSynced()
                .filter { it.remoteId != null && it.remoteId !in remoteTrackIds }
                .forEach { stale ->
                    stale.filePath?.let { File(it).delete() }
                    trackDao.deleteById(stale.id)
                }

            // --- vínculos ---
            val playlistIdByRemote = playlistDao.allSynced()
                .mapNotNull { p -> p.remoteId?.let { it to p.id } }.toMap()
            val trackIdByRemote = trackDao.allSynced()
                .mapNotNull { t -> t.remoteId?.let { it to t.id } }.toMap()

            playlistIdByRemote.values.forEach { linkDao.clearPlaylist(it) }
            linkDao.insertAll(
                snapshot.playlistTracks.mapNotNull { link ->
                    val playlistId = playlistIdByRemote[link.playlistId] ?: return@mapNotNull null
                    val trackId = trackIdByRemote[link.trackId] ?: return@mapNotNull null
                    PlaylistTrackEntity(playlistId, trackId, link.position, link.addedAt)
                }
            )
        }

    /** O índice de nome é único: uma colisão com playlist local ganha sufixo. */
    private suspend fun uniqueName(
        dao: com.lumenconnection.music.db.PlaylistDao,
        wanted: String,
    ): String {
        var candidate = wanted
        var suffix = 2
        while (dao.byName(candidate) != null && suffix < 100) {
            candidate = "$wanted ($suffix)"
            suffix++
        }
        return candidate
    }

    // --- Áudio ----------------------------------------------------------------

    /**
     * Baixa o áudio das playlists marcadas (ou de tudo, se o usuário pediu).
     * Metadados sempre descem inteiros; o áudio é a parte cara, por isso é
     * escolhida.
     */
    private suspend fun downloadSelectedAudio(context: Context, api: SyncApi) =
        withContext(Dispatchers.IO) {
            val trackDao = Graph.db.trackDao()
            val playlistDao = Graph.db.playlistDao()
            val linkDao = Graph.db.playlistTrackDao()

            val all = Graph.settings.syncAllPlaylists.first()
            val wanted = LinkedHashSet<Long>()

            if (all) {
                trackDao.allSynced().forEach { wanted.add(it.id) }
            } else {
                playlistDao.allSynced().filter { it.syncFiles }.forEach { playlist ->
                    wanted.addAll(linkDao.orderedTrackIds(playlist.id))
                }
            }

            val pending = wanted.mapNotNull { trackDao.byId(it) }
                .filter { it.origin == Origin.SYNC && it.filePath.isNullOrBlank() && !it.missing }

            if (pending.isEmpty()) return@withContext

            pending.forEachIndexed { index, track ->
                _state.value = State.Downloading(index, pending.size)
                val remoteId = track.remoteId ?: return@forEachIndexed

                val playlistId = linkDao.playlistsOf(track.id).firstOrNull()
                val dirName = playlistId?.let { playlistDao.byId(it)?.dirName }
                val destination = File(
                    AudioStorage.playlistDir(context, dirName),
                    safeFileName("${track.artist} - ${track.title}") + ".opus",
                )

                trackDao.setDownloadState(track.id, DownloadState.DOWNLOADING)
                val ok = runCatching {
                    api.downloadTrack(remoteId, destination, track.fileSize)
                }.getOrElse {
                    Log.w(TAG, "falha ao baixar ${track.title}", it)
                    false
                }

                if (ok) trackDao.markDownloaded(track.id, destination.absolutePath)
                else trackDao.setDownloadState(track.id, DownloadState.FAILED)
            }

            _state.value = State.Downloading(pending.size, pending.size)
        }

    private fun safeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(180).trim().ifBlank { "faixa" }
}
