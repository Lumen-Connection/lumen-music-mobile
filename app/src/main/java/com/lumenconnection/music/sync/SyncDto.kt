package com.lumenconnection.music.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Formato de fio do sync, espelhando `src/sync/library_snapshot.cpp` e
 * `src/sync/merge_service.cpp` do desktop. Qualquer mudança aqui tem de sair
 * junto com a do outro lado, e a versão de protocolo existe justamente para
 * impedir que uma incompatibilidade passe despercebida.
 */
const val SYNC_PROTOCOL_VERSION = 1

const val SYNC_DEFAULT_PORT = 45150
const val SYNC_DISCOVERY_PORT = 45151

@Serializable
data class PingDto(
    val serverId: String,
    val name: String = "",
    val proto: Int = 0,
    val appVersion: String = "",
)

@Serializable
data class AnnounceDto(
    val lumen: String = "",
    val proto: Int = 0,
    val serverId: String = "",
    val name: String = "",
    val port: Int = SYNC_DEFAULT_PORT,
    val appVersion: String = "",
)

@Serializable
data class PairRequestDto(
    val pin: String,
    val deviceId: String,
    val deviceName: String,
)

@Serializable
data class PairResponseDto(
    val token: String,
    val serverId: String,
    val serverName: String = "",
)

@Serializable
data class SnapshotDto(
    val serverId: String = "",
    val proto: Int = 0,
    val generatedAt: Long = 0,
    val playlists: List<SnapshotPlaylistDto> = emptyList(),
    val tracks: List<SnapshotTrackDto> = emptyList(),
    val playlistTracks: List<SnapshotLinkDto> = emptyList(),
    val playbackState: SnapshotPlaybackStateDto? = null,
)

@Serializable
data class SnapshotPlaylistDto(
    val id: Long,
    val name: String,
    val coverColor1: String = "#e8a44a",
    val coverColor2: String = "#d45d5d",
    val hasCoverImage: Boolean = false,
    val sortMode: String = "custom",
    val createdAt: Long = 0,
)

@Serializable
data class SnapshotTrackDto(
    val id: Long,
    val title: String,
    val artist: String = "",
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
    /** Identidade de conteúdo: se qualquer um mudar, o arquivo é rebaixado. */
    val fileSize: Long = 0,
    val fileMtime: Long = 0,
)

@Serializable
data class SnapshotLinkDto(
    val playlistId: Long,
    val trackId: Long,
    val position: Long,
    val addedAt: Long = 0,
)

@Serializable
data class SnapshotPlaybackStateDto(
    val currentTrackId: Long = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    val contextIds: List<Long> = emptyList(),
    val userQueueIds: List<Long> = emptyList(),
    val contextIndex: Int = -1,
    val contextName: String = "",
)

// --- Push (mobile → desktop) ---

@Serializable
data class PushDto(
    val deviceId: String,
    val likes: List<PushLikeDto> = emptyList(),
    val playCounts: List<PushPlayCountDto> = emptyList(),
    val newPlaylists: List<PushNewPlaylistDto> = emptyList(),
    val playlistMembership: List<PushMembershipDto> = emptyList(),
)

@Serializable
data class PushLikeDto(val trackId: Long, val liked: Boolean, val likedAt: Long)

@Serializable
data class PushPlayCountDto(val trackId: Long, val delta: Int, val lastPlayedAt: Long)

@Serializable
data class PushNewPlaylistDto(
    /** UUID gerado aqui — dá idempotência: reenviar não duplica a playlist. */
    val clientKey: String,
    val name: String,
    val coverColor1: String,
    val coverColor2: String,
    val trackIds: List<Long>,
)

@Serializable
data class PushMembershipDto(val playlistId: Long, val trackIds: List<Long>)

@Serializable
data class PushResponseDto(
    val createdPlaylists: List<CreatedPlaylistDto> = emptyList(),
    val skipped: List<Long> = emptyList(),
)

@Serializable
data class CreatedPlaylistDto(val clientKey: String, val id: Long)

@Serializable
data class ErrorDto(@SerialName("error") val error: String = "")
