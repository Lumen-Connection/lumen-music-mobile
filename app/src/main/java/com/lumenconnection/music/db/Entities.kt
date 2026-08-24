package com.lumenconnection.music.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Espelha o schema do desktop (`src/database/migrator.cpp`, user_version 2) mais
 * as colunas que só o mobile precisa para sincronizar.
 *
 * Índices únicos em `remoteId`/`filePath` funcionam mesmo com valores nulos: o
 * SQLite trata NULLs como distintos num índice único, então várias faixas locais
 * (sem remoteId) convivem sem conflito.
 */

/** De onde a entidade veio. Só `SYNC` é apagada quando some do snapshot do desktop. */
enum class Origin { LOCAL, SYNC, DOWNLOAD }

/** Estado do arquivo de áudio no aparelho. */
enum class DownloadState { NONE, PENDING, DOWNLOADING, DONE, FAILED }

/** Os 6 modos de ordenação do desktop (`playlists.sort_mode`). */
enum class SortMode { CUSTOM, TITLE, ARTIST, RECENT, OLDEST, DURATION }

enum class RepeatMode { OFF, ALL, ONE }

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["filePath"], unique = true),
        Index(value = ["liked"]),
        Index(value = ["addedAt"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["searchKey"]),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** `tracks.id` do desktop. Estável: lá a coluna é AUTOINCREMENT e nunca reusa rowid. */
    val remoteId: Long? = null,

    val title: String,
    val artist: String = "Desconhecido",

    /** Caminho no aparelho ou URI do SAF. Nulo enquanto o áudio não foi baixado. */
    val filePath: String? = null,

    val durationMs: Long = 0,
    val coverColor1: String = "#e8a44a",
    val coverColor2: String = "#d45d5d",
    val ownerPlaylistId: Long? = null,

    val liked: Boolean = false,
    val likedAt: Long = 0,
    val addedAt: Long = 0,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
    val missing: Boolean = false,

    /** Título + artista normalizados (sem acento) — a chave da busca global. */
    val searchKey: String = "",

    val origin: Origin = Origin.LOCAL,
    val downloadState: DownloadState = DownloadState.NONE,

    /** Identidade de conteúdo vinda do desktop: se qualquer um mudar, rebaixa o arquivo. */
    val fileSize: Long = 0,
    val fileMtime: Long = 0,

    /** Reproduções ainda não enviadas ao desktop; zeradas quando o push responde 200. */
    val pendingPlayDelta: Int = 0,
    /** Curtida alterada no celular e ainda não enviada. */
    val likeDirty: Boolean = false,
)

@Entity(
    tableName = "playlists",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["clientKey"], unique = true),
        Index(value = ["name"], unique = true),
    ],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** `playlists.id` do desktop; nulo em playlists criadas aqui e ainda não enviadas. */
    val remoteId: Long? = null,

    /** UUID gerado no celular — dá idempotência ao push (reenvio não duplica). */
    val clientKey: String? = null,

    val name: String,

    /** Nome normalizado (sem acento) — a busca global também encontra playlists. */
    val searchName: String = "",

    val coverColor1: String = "#e8a44a",
    val coverColor2: String = "#d45d5d",
    val coverImagePath: String = "",

    /** Pasta em disco; estável mesmo quando a playlist é renomeada, como no desktop. */
    val dirName: String = "",

    val sortMode: SortMode = SortMode.CUSTOM,
    val createdAt: Long = 0,

    val origin: Origin = Origin.LOCAL,

    /** Se o áudio desta playlist deve ser baixado no sync (seleção por playlist). */
    val syncFiles: Boolean = false,
)

/**
 * Relação N:N entre playlists e faixas — uma faixa vive em várias playlists sem
 * duplicar o arquivo, como no desktop. `position` usa a escala de vãos de 1024.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["playlistId", "position"]),
        Index(value = ["trackId"]),
    ],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: Long,
    val position: Long,
    val addedAt: Long = 0,
)

/**
 * Linha única (id = 1) com o estado de reprodução, espelhando `playback_state`
 * do desktop. É a fonte da verdade de volume/mudo — não o DataStore.
 */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 1,
    val currentTrackId: Long? = null,
    val positionMs: Long = 0,
    val volume: Float = 1.0f,
    val muted: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Fila de contexto (playlist/curtidas/biblioteca) e fila manual "a seguir". */
    val contextIds: List<Long> = emptyList(),
    val userQueueIds: List<Long> = emptyList(),
    val contextIndex: Int = -1,
    val contextName: String = "",
)
