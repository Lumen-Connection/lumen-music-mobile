package com.lumenconnection.music.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE liked = 1 ORDER BY likedAt DESC")
    fun observeLiked(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<TrackEntity>>

    /** Busca global sem acento: `query` já vem normalizado por [com.lumenconnection.music.util.TextUtils]. */
    @Query("SELECT * FROM tracks WHERE searchKey LIKE '%' || :query || '%' ORDER BY title LIMIT :limit")
    fun search(query: String, limit: Int = 100): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE remoteId = :remoteId")
    suspend fun byRemoteId(remoteId: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE filePath = :path")
    suspend fun byPath(path: String): TrackEntity?

    /** Faixas avulsas: não pertencem a nenhuma playlist. */
    @Query(
        """
        SELECT * FROM tracks
        WHERE id NOT IN (SELECT trackId FROM playlist_tracks)
        ORDER BY addedAt DESC
        """
    )
    fun observeStandalone(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun update(track: TrackEntity)

    @Delete
    suspend fun delete(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tracks SET liked = :liked, likedAt = :at, likeDirty = 1 WHERE id = :id")
    suspend fun setLiked(id: Long, liked: Boolean, at: Long)

    /**
     * Uma reprodução contada. `pendingPlayDelta` acumula o que ainda não subiu
     * para o desktop; o push zera esse campo depois de um 200.
     */
    @Query(
        """
        UPDATE tracks
        SET playCount = playCount + 1,
            lastPlayedAt = :at,
            pendingPlayDelta = pendingPlayDelta + 1
        WHERE id = :id
        """
    )
    suspend fun registerPlay(id: Long, at: Long)

    @Query("UPDATE tracks SET title = :title, artist = :artist, searchKey = :searchKey WHERE id = :id")
    suspend fun editTags(id: Long, title: String, artist: String, searchKey: String)

    @Query("UPDATE tracks SET missing = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    @Query("SELECT COUNT(*) FROM tracks")
    fun observeCount(): Flow<Int>

    // --- Sync ---

    @Query("SELECT * FROM tracks WHERE likeDirty = 1")
    suspend fun dirtyLikes(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE pendingPlayDelta > 0")
    suspend fun pendingPlays(): List<TrackEntity>

    @Query("UPDATE tracks SET likeDirty = 0 WHERE id IN (:ids)")
    suspend fun clearLikeDirty(ids: List<Long>)

    @Query("UPDATE tracks SET pendingPlayDelta = 0 WHERE id IN (:ids)")
    suspend fun clearPendingPlays(ids: List<Long>)

    @Query("SELECT * FROM tracks WHERE origin = 'SYNC'")
    suspend fun allSynced(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE downloadState = 'PENDING' ORDER BY id LIMIT :limit")
    suspend fun nextPendingDownloads(limit: Int): List<TrackEntity>

    @Query("UPDATE tracks SET downloadState = :state WHERE id = :id")
    suspend fun setDownloadState(id: Long, state: DownloadState)

    @Query("UPDATE tracks SET filePath = :path, downloadState = 'DONE' WHERE id = :id")
    suspend fun markDownloaded(id: Long, path: String)
}

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE")
    fun observeAllByName(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAllByCreated(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun byId(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE remoteId = :remoteId")
    suspend fun byRemoteId(remoteId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE searchName LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE playlists SET sortMode = :mode WHERE id = :id")
    suspend fun setSortMode(id: Long, mode: SortMode)

    @Query("UPDATE playlists SET syncFiles = :enabled WHERE id = :id")
    suspend fun setSyncFiles(id: Long, enabled: Boolean)

    @Query("SELECT * FROM playlists WHERE origin = 'SYNC'")
    suspend fun allSynced(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE remoteId IS NULL")
    suspend fun notYetPushed(): List<PlaylistEntity>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun observeTrackCount(playlistId: Long): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), 0) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Long

    /**
     * Cores das faixas de todas as playlists numa consulta só — a grade de
     * playlists precisa disso para montar o mosaico 2×2 sem uma query por card.
     */
    @Query(
        """
        SELECT pt.playlistId AS playlistId, t.coverColor1 AS coverColor1, t.coverColor2 AS coverColor2
        FROM playlist_tracks pt
        INNER JOIN tracks t ON t.id = pt.trackId
        ORDER BY pt.playlistId, pt.position
        """
    )
    fun observeAllCoverColors(): Flow<List<PlaylistCoverColor>>
}

data class PlaylistCoverColor(
    val playlistId: Long,
    val coverColor1: String,
    val coverColor2: String,
)

@Dao
interface PlaylistTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun remove(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: Long, position: Long)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun orderedTrackIds(playlistId: Long): List<Long>

    @Query("SELECT playlistId FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun playlistsOf(trackId: Long): List<Long>

    /**
     * Reordena a playlist inteira em múltiplos de 1024. Chamado depois de um
     * arrastar-e-soltar quando o vão entre dois vizinhos fechou.
     */
    @Transaction
    suspend fun renormalise(playlistId: Long, orderedTrackIds: List<Long>) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            setPosition(playlistId, trackId, com.lumenconnection.music.util.PositionGap.fromRank(index))
        }
    }

    // --- Consultas de faixas por playlist, uma por modo de ordenação do desktop ---

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY pt.position
        """
    )
    fun observeCustom(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY t.title COLLATE NOCASE
        """
    )
    fun observeByTitle(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY t.artist COLLATE NOCASE, t.title COLLATE NOCASE
        """
    )
    fun observeByArtist(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY pt.addedAt DESC
        """
    )
    fun observeByRecent(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY pt.addedAt ASC
        """
    )
    fun observeByOldest(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY t.durationMs DESC
        """
    )
    fun observeByDuration(playlistId: Long): Flow<List<TrackEntity>>

    /** Cores das primeiras faixas — alimentam o mosaico 2×2 da capa. */
    @Query(
        """
        SELECT t.coverColor1, t.coverColor2 FROM tracks t
        INNER JOIN playlist_tracks pt ON pt.trackId = t.id
        WHERE pt.playlistId = :playlistId ORDER BY pt.position LIMIT 4
        """
    )
    fun observeMosaicColors(playlistId: Long): Flow<List<CoverPair>>
}

data class CoverPair(val coverColor1: String, val coverColor2: String)

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun get(): PlaybackStateEntity?

    @Query("SELECT * FROM playback_state WHERE id = 1")
    fun observe(): Flow<PlaybackStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: PlaybackStateEntity)
}
