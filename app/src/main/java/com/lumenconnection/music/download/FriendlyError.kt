package com.lumenconnection.music.download

import com.lumenconnection.music.R

/**
 * Traduz a saída crua do yt-dlp numa mensagem que o usuário entenda — port do
 * `friendly_error` que o Lumen Stream herdou do desktop.
 *
 * Devolve o **id do recurso**, não o texto: assim a mensagem é resolvida na hora
 * de exibir e acompanha o idioma do aparelho, mesmo que o erro tenha acontecido
 * antes de uma troca de idioma.
 */
object FriendlyError {

    fun resFor(raw: String?): Int {
        val text = raw.orEmpty().lowercase()
        return when {
            text.isBlank() -> R.string.download_check_link

            "403" in text || "forbidden" in text -> R.string.download_check_link

            "private video" in text || "sign in" in text || "members-only" in text ->
                R.string.import_youtube_private

            "unavailable" in text || "removed" in text || "not exist" in text ->
                R.string.download_check_link

            "unable to download" in text || "network" in text || "timed out" in text ||
                "connection" in text || "resolve host" in text -> R.string.download_check_link

            "no audio" in text || "requested format" in text -> R.string.download_no_audio_found

            "unsupported url" in text -> R.string.download_youtube_link_error

            else -> R.string.download_check_link
        }
    }
}
