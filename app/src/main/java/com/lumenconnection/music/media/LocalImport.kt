package com.lumenconnection.music.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.lumenconnection.music.db.Origin
import com.lumenconnection.music.db.TrackEntity
import com.lumenconnection.music.util.TextUtils
import kotlin.random.Random

/**
 * Importação de áudio do próprio aparelho, equivalente ao arrastar-e-soltar do
 * `src/pages/addmusicpage.cpp`.
 *
 * Regra inviolável herdada do desktop: **o app nunca move nem copia o arquivo do
 * usuário**. Guardamos o URI do SAF com permissão persistente e tocamos dali —
 * o arquivo continua onde o dono o deixou.
 */
object LocalImport {

    /** Mesmas extensões aceitas pelo desktop (`src/widgets/mediatools.h`). */
    val SUPPORTED_EXTENSIONS = setOf(
        "opus", "webm", "m4a", "mp3", "ogg", "oga", "flac", "wav", "aac",
    )

    val MIME_FILTER = arrayOf("audio/*", "application/ogg", "video/webm")

    /**
     * Deriva "Artista - Título" do nome do arquivo, como o desktop faz ao
     * importar (nenhum dos dois lê tags embutidas na v1).
     */
    fun parseArtistTitle(fileName: String): Pair<String, String> {
        val withoutExtension = fileName.substringBeforeLast('.', fileName)
        // O desktop separa em hífen simples, en dash ou em dash.
        val separators = listOf(" - ", " – ", " — ")
        for (sep in separators) {
            val idx = withoutExtension.indexOf(sep)
            if (idx > 0) {
                val artist = withoutExtension.substring(0, idx).trim()
                val title = withoutExtension.substring(idx + sep.length).trim()
                if (artist.isNotEmpty() && title.isNotEmpty()) return artist to title
            }
        }
        return "" to withoutExtension.trim()
    }

    fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }

    private fun durationOf(context: Context, uri: Uri): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    /**
     * Monta a faixa a partir de um URI escolhido pelo usuário, tomando a
     * permissão persistente para que ela continue tocável depois de reiniciar.
     */
    fun buildTrack(context: Context, uri: Uri, unknownArtist: String): TrackEntity? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val name = displayName(context, uri) ?: uri.lastPathSegment ?: return null
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty() && extension !in SUPPORTED_EXTENSIONS) return null

        val (artist, title) = parseArtistTitle(name)
        val finalArtist = artist.ifBlank { unknownArtist }
        val now = System.currentTimeMillis()
        val (c1, c2) = randomGradient()

        return TrackEntity(
            title = title,
            artist = finalArtist,
            filePath = uri.toString(),
            durationMs = durationOf(context, uri),
            coverColor1 = c1,
            coverColor2 = c2,
            addedAt = now,
            searchKey = TextUtils.searchKey(title, finalArtist),
            origin = Origin.LOCAL,
        )
    }

    /**
     * Gradiente aleatório para a capa, como o `Theme::randomPalette()` que o
     * desktop atribui a cada faixa nova.
     */
    fun randomGradient(rng: Random = Random.Default): Pair<String, String> {
        val palettes = listOf(
            "#e8a44a" to "#d45d5d",
            "#4aa8e8" to "#5d7fd4",
            "#6bcf7f" to "#3fa16a",
            "#c084fc" to "#7b5cd6",
            "#ff5722" to "#c1440e",
            "#f06292" to "#d4145d",
            "#4dd0e1" to "#0097a7",
            "#ffb74d" to "#f57c00",
        )
        return palettes[rng.nextInt(palettes.size)]
    }
}
