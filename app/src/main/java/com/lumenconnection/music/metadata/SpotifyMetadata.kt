package com.lumenconnection.music.metadata

import com.lumenconnection.music.extractor.NewPipeDownloaderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import java.io.IOException

/**
 * Resolução de metadados do Spotify, portada de `src/pages/importplaylistdialog.cpp`
 * do desktop.
 *
 * O Spotify é DRM: em vez de baixar de lá, o app lê os metadados públicos (sem
 * credencial nem chave de API) e converte cada faixa num alvo de busca no
 * YouTube.
 *
 * - Faixa única: `https://open.spotify.com/oembed?url=…`
 * - Playlist/álbum: HTML de `https://open.spotify.com/embed/{kind}/{id}` →
 *   scripts `application/json` (`__NEXT_DATA__`) → objetos de faixa.
 */
object SpotifyMetadata {

    /** Uma faixa do Spotify pronta para ser procurada no YouTube. */
    data class SpotifyTrack(val label: String, val artist: String, val title: String)

    private val PLAYLIST_REGEX = Regex("""spotify\.com/(playlist|album)/([A-Za-z0-9]+)""")

    // User-Agent de navegador: sem ele o embed às vezes devolve um shell vazio.
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    fun isSpotifyUrl(url: String): Boolean = url.contains("open.spotify.com")

    suspend fun resolve(url: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val u = url.trim()
        when {
            u.contains("spotify.com/track") -> listOfNotNull(resolveTrack(u))
            else -> {
                val match = PLAYLIST_REGEX.find(u)
                    ?: throw IOException("URL do Spotify não reconhecida: $u")
                val (kind, id) = match.destructured
                fetchEmbedTracks(kind, id)
            }
        }.ifEmpty { throw IOException("Nenhuma faixa encontrada nessa URL do Spotify") }
    }

    private fun resolveTrack(url: String): SpotifyTrack? {
        val body = httpGet("https://open.spotify.com/oembed?url=$url", userAgent = null) ?: return null
        val title = runCatching {
            (Json.parseToJsonElement(body).jsonObject["title"] as? JsonPrimitive)?.content
        }.getOrNull()?.trim().orEmpty()
        if (title.isEmpty()) return null
        return splitLabel(title)
    }

    private fun fetchEmbedTracks(kind: String, id: String): List<SpotifyTrack> {
        val html = httpGet("https://open.spotify.com/embed/$kind/$id", userAgent = BROWSER_UA)
            ?: throw IOException("Falha ao acessar o embed do Spotify")
        return parseEmbedTracks(html)
    }

    /** Extrai as faixas do HTML do embed. Puro (sem rede) para permitir teste unitário. */
    fun parseEmbedTracks(html: String): List<SpotifyTrack> {
        for (jsonStr in extractJsonScripts(html)) {
            val root = runCatching { Json.parseToJsonElement(jsonStr) }.getOrNull() ?: continue
            // Deduplicação preservando a ordem, como no desktop.
            val out = LinkedHashMap<String, SpotifyTrack>()
            walkForTracks(root, out)
            if (out.isNotEmpty()) return out.values.toList()
        }
        return emptyList()
    }

    /** Corpo de cada `<script type="application/json">`, com `__NEXT_DATA__` primeiro. */
    private fun extractJsonScripts(html: String): List<String> {
        val out = mutableListOf<Pair<Boolean, String>>()
        var index = 0
        while (true) {
            val open = html.indexOf("<script", index)
            if (open < 0) break
            val tagEnd = html.indexOf('>', open)
            if (tagEnd < 0) break
            val tag = html.substring(open, tagEnd)
            val close = html.indexOf("</script>", tagEnd)
            if (close < 0) break
            if (tag.contains("application/json")) {
                out += tag.contains("__NEXT_DATA__") to html.substring(tagEnd + 1, close).trim()
            }
            index = close + 9
        }
        return out.sortedByDescending { it.first }.map { it.second }
    }

    private fun walkForTracks(v: JsonElement, out: LinkedHashMap<String, SpotifyTrack>) {
        when (v) {
            is JsonObject -> {
                trackLabel(v)?.let { label ->
                    out.getOrPut(label) { splitLabel(label) }
                }
                v.values.forEach { walkForTracks(it, out) }
            }
            is JsonArray -> v.forEach { walkForTracks(it, out) }
            else -> {}
        }
    }

    /**
     * Monta "Artista - Faixa" a partir de um objeto do embed.
     * Formato atual: `{uri:"spotify:track:…", title, subtitle, entityType:"track"}`.
     * Formato legado: `{uri:"spotify:track:…", name, artists:[{name}]}`.
     */
    private fun trackLabel(map: JsonObject): String? {
        val uri = (map["uri"] as? JsonPrimitive)?.content.orEmpty()
        val entity = (map["entityType"] as? JsonPrimitive)?.content.orEmpty()
        if (!uri.contains(":track:") && !entity.equals("track", ignoreCase = true)) return null

        val title = ((map["title"] ?: map["name"]) as? JsonPrimitive)?.content?.trim().orEmpty()
        if (title.isEmpty()) return null

        val subtitle = (map["subtitle"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val artist = subtitle.ifEmpty {
            (map["artists"] as? JsonArray)
                ?.mapNotNull { ((it as? JsonObject)?.get("name") as? JsonPrimitive)?.content?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.joinToString(", ")
                .orEmpty()
        }
        return if (artist.isEmpty()) title else "$artist - $title"
    }

    /** Separa "Artista - Título" mantendo o rótulo original como fallback. */
    fun splitLabel(label: String): SpotifyTrack {
        val idx = label.indexOf(" - ")
        return if (idx > 0) {
            SpotifyTrack(label, label.substring(0, idx).trim(), label.substring(idx + 3).trim())
        } else {
            SpotifyTrack(label, "", label.trim())
        }
    }

    private fun httpGet(url: String, userAgent: String?): String? {
        val builder = Request.Builder().url(url)
        if (userAgent != null) {
            builder.header("User-Agent", userAgent)
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }
        return runCatching {
            NewPipeDownloaderImpl.instance.client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }
}
