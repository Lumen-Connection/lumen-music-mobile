package com.lumenconnection.music.util

import java.text.Normalizer

/**
 * Port de `src/widgets/textutils.h` do desktop.
 *
 * Passa para minúsculas e remove acentos, de forma que "musica" encontre
 * "Música". Usado pela busca global e pelo filtro dentro da playlist. O desktop
 * usa NFD + descarte das marcas sem espaçamento; aqui é a mesma coisa via
 * [Normalizer] e o bloco Unicode de diacríticos combinantes.
 */
object TextUtils {
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    fun normalized(s: String): String =
        COMBINING_MARKS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

    /** Chave de busca de uma faixa: título e artista normalizados juntos. */
    fun searchKey(title: String, artist: String): String =
        normalized("$title $artist")
}
