package com.lumenconnection.music.media

import android.content.Context
import java.io.File

/**
 * Onde ficam os arquivos de áudio que **o app é dono**: os baixados (fase 4) e
 * os que descem do sync (fase 6).
 *
 * Escolha registrada no PLANEJAMENTO: armazenamento externo com escopo do app.
 * Não exige permissão nenhuma, e a deleção espelhada do sync fica consistente —
 * no MediaStore outros apps veriam e poderiam remover essas cópias.
 *
 * Arquivos que o usuário já tinha no aparelho **não** vêm para cá: são tocados
 * pelo URI do SAF, como manda a regra do desktop de nunca mover arquivo alheio.
 */
object AudioStorage {

    private const val ROOT = "music"

    /** Pasta raiz das cópias gerenciadas pelo app. */
    fun root(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT).apply { mkdirs() }

    /**
     * Pasta de uma playlist. Espelha a organização do desktop, que guarda cada
     * playlist na sua própria subpasta (`playlists.dir_name`).
     */
    fun playlistDir(context: Context, dirName: String?): File {
        val safe = dirName?.takeIf { it.isNotBlank() }?.let(::sanitize) ?: "Downloads"
        return File(root(context), safe).apply { mkdirs() }
    }

    /** Espaço livre no volume onde as músicas são guardadas. */
    fun usableBytes(context: Context): Long = root(context).usableSpace

    fun totalBytes(context: Context): Long = root(context).totalSpace

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "playlist" }

    fun formatSize(bytes: Long): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${value.toInt()} ${units[unit]}"
        else String.format("%.1f %s", value, units[unit])
    }
}
