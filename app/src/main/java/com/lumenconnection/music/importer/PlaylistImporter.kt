package com.lumenconnection.music.importer

import com.lumenconnection.music.extractor.YtDlpEngine
import com.lumenconnection.music.metadata.SpotifyMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Importação de playlists de streaming — port do fluxo do
 * `src/pages/importplaylistdialog.cpp`.
 *
 * Spotify e YouTube chegam pelo mesmo funil: resolvem-se os metadados e monta-se
 * uma lista de candidatos que o usuário **aprova antes de qualquer download**,
 * exatamente como o checklist do desktop.
 */
object PlaylistImporter {

    /** Uma faixa candidata a ser importada. */
    data class Candidate(
        val label: String,
        val artist: String,
        val title: String,
        /** Alvo para o yt-dlp: URL direta (YouTube) ou `ytsearch1:` (Spotify). */
        val target: String,
        /** Falso quando a correspondência é duvidosa — a UI destaca em laranja. */
        val confident: Boolean,
        val approved: Boolean = true,
    )

    data class Result(val suggestedName: String, val candidates: List<Candidate>)

    fun isSupported(url: String): Boolean =
        SpotifyMetadata.isSpotifyUrl(url) || isYouTubePlaylist(url)

    fun isYouTubePlaylist(url: String): Boolean =
        (url.contains("youtube.com") || url.contains("youtu.be")) && url.contains("list=")

    suspend fun resolve(url: String): Result = withContext(Dispatchers.IO) {
        when {
            SpotifyMetadata.isSpotifyUrl(url) -> resolveSpotify(url)
            isYouTubePlaylist(url) -> resolveYouTube(url)
            else -> throw IllegalArgumentException("URL não suportada: $url")
        }
    }

    /**
     * No Spotify não há vídeo para conferir sem gastar uma busca por faixa, então
     * a confiança é presumida e o usuário revisa a lista. O desktop faz a busca
     * `ytsearch5` para pontuar cada candidato; aqui isso ficaria caro em rede
     * móvel, então usamos `ytsearch1` e deixamos a revisão para o usuário.
     */
    private suspend fun resolveSpotify(url: String): Result {
        val tracks = SpotifyMetadata.resolve(url)
        return Result(
            suggestedName = "Playlist importada",
            candidates = tracks.map { track ->
                Candidate(
                    label = track.label,
                    artist = track.artist,
                    title = track.title,
                    target = "ytsearch1:${track.label}",
                    // Sem artista o rótulo é só um título solto: vale revisar.
                    confident = track.artist.isNotBlank(),
                )
            },
        )
    }

    /** No YouTube as entradas são exatas: não há correspondência a adivinhar. */
    private suspend fun resolveYouTube(url: String): Result {
        val entries = YtDlpEngine.listPlaylist(url)
        return Result(
            suggestedName = "Playlist importada",
            candidates = entries.map { entry ->
                val split = SpotifyMetadata.splitLabel(entry.title)
                Candidate(
                    label = entry.title,
                    artist = split.artist,
                    title = split.title,
                    target = entry.url,
                    confident = true,
                )
            },
        )
    }
}
