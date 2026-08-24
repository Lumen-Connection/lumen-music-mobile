package com.lumenconnection.music.db

import com.lumenconnection.music.media.LocalImport
import com.lumenconnection.music.util.PositionGap
import com.lumenconnection.music.util.TextUtils

/**
 * Operações que envolvem mais de um DAO, no espírito do facade `TrackModel` do
 * desktop. Os composables continuam lendo Flows direto dos DAOs; só as escritas
 * compostas passam por aqui.
 */
class LibraryRepository(private val db: AppDatabase) {

    private val tracks get() = db.trackDao()
    private val playlists get() = db.playlistDao()
    private val links get() = db.playlistTrackDao()

    // --- Playlists ---

    suspend fun createPlaylist(name: String, color1: String? = null, color2: String? = null): Long {
        val (c1, c2) = if (color1 != null && color2 != null) color1 to color2
        else LocalImport.randomGradient()
        return playlists.insert(
            PlaylistEntity(
                name = name,
                searchName = TextUtils.normalized(name),
                coverColor1 = c1,
                coverColor2 = c2,
                dirName = sanitizeDirName(name),
                createdAt = System.currentTimeMillis(),
                origin = Origin.LOCAL,
                clientKey = java.util.UUID.randomUUID().toString(),
            )
        )
    }

    /**
     * Renomear **não** muda `dirName`, como no desktop: a pasta em disco é
     * estável para não invalidar caminhos já gravados.
     */
    suspend fun renamePlaylist(id: Long, newName: String) {
        val current = playlists.byId(id) ?: return
        playlists.update(
            current.copy(name = newName, searchName = TextUtils.normalized(newName))
        )
    }

    suspend fun setPlaylistCover(id: Long, color1: String, color2: String, imagePath: String = "") {
        val current = playlists.byId(id) ?: return
        playlists.update(
            current.copy(coverColor1 = color1, coverColor2 = color2, coverImagePath = imagePath)
        )
    }

    /**
     * Apagar a playlist não apaga as faixas — elas viram avulsas, exatamente
     * como o desktop avisa no diálogo de confirmação. A cascata do
     * `playlist_tracks` cuida só dos vínculos.
     */
    suspend fun deletePlaylist(id: Long) = playlists.deleteById(id)

    // --- Vínculos playlist ↔ faixa ---

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        val position = PositionGap.nextAfter(playlists.maxPosition(playlistId))
        links.insert(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = position,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        links.remove(playlistId, trackId)

    suspend fun playlistsOf(trackId: Long): Set<Long> = links.playlistsOf(trackId).toSet()

    /**
     * Move uma faixa para uma nova posição na ordenação manual.
     *
     * Tenta inserir no vão entre os vizinhos; se o vão fechou, renormaliza a
     * playlist inteira em múltiplos de 1024 — a mesma estratégia do desktop.
     */
    suspend fun moveTrack(playlistId: Long, trackId: Long, targetIndex: Int) {
        val rows = links.orderedPositions(playlistId)
        val positions = rows.associate { it.trackId to it.position }

        val ordered = rows.map { it.trackId }.toMutableList()
        val from = ordered.indexOf(trackId)
        if (from < 0) return

        ordered.removeAt(from)
        val clamped = targetIndex.coerceIn(0, ordered.size)
        ordered.add(clamped, trackId)

        val prevId = ordered.getOrNull(clamped - 1)
        val nextId = ordered.getOrNull(clamped + 1)
        val prev = prevId?.let { positions[it] } ?: 0L
        val next = nextId?.let { positions[it] }

        val newPosition = when {
            // Foi para o fim: basta um vão depois do maior.
            next == null -> PositionGap.nextAfter(positions.values.maxOrNull() ?: 0L)
            // Foi para o começo: metade da posição do primeiro.
            prevId == null -> if (next > 1) next / 2 else -1L
            else -> PositionGap.between(prev, next)
        }

        // Vão fechado: redistribui a playlist inteira, como o desktop.
        if (newPosition <= 0) {
            links.renormalise(playlistId, ordered)
        } else {
            links.setPosition(playlistId, trackId, newPosition)
        }
    }

    // --- Faixas ---

    suspend fun editTrack(trackId: Long, title: String, artist: String) =
        tracks.editTags(trackId, title, artist, TextUtils.searchKey(title, artist))

    /**
     * Remove a faixa da biblioteca. O arquivo em si não é tocado — para faixas
     * locais ele nem pertence ao app (é um URI do SAF).
     */
    suspend fun deleteTrack(trackId: Long) = tracks.deleteById(trackId)

    suspend fun toggleLike(trackId: Long) {
        val track = tracks.byId(trackId) ?: return
        tracks.setLiked(trackId, !track.liked, System.currentTimeMillis())
    }

    private fun sanitizeDirName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "playlist" }
}
