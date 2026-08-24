package com.lumenconnection.music.importer

import com.lumenconnection.music.util.TextUtils
import kotlin.math.abs

/**
 * Heurística de correspondência entre uma faixa do Spotify e um resultado de
 * busca do YouTube — port literal de `scoreCandidate()` em
 * `src/pages/importplaylistdialog.cpp`.
 *
 * Conter o título e a proximidade de duração pesam mais; o artista costuma
 * aparecer no título do vídeo ou no nome do canal. Função pura, testável.
 */
object MatchScore {

    data class Result(val score: Int, val confident: Boolean)

    /** Normalização em token: sem acento, minúsculo e sem pontuação. */
    fun normToken(s: String): String =
        TextUtils.normalized(s)
            .map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")

    fun score(
        wantTitle: String,
        wantArtist: String,
        wantMs: Long,
        candTitle: String,
        candChannel: String,
        candSec: Long,
    ): Result {
        val nt = normToken(wantTitle)
        val hay = normToken("$candTitle $candChannel")

        var score = 0

        val titleHit = nt.isNotEmpty() && hay.contains(nt)
        if (titleHit) {
            score += 3
        } else {
            // Crédito parcial: a maioria das palavras do título aparece em algum lugar.
            val words = nt.split(' ').filter { it.isNotBlank() }
            val hits = words.count { it.length > 1 && hay.contains(it) }
            if (words.isNotEmpty() && hits * 2 >= words.size) score += 1
        }

        val artistHit = wantArtist.split(',', '&')
            .map { normToken(it) }
            .any { it.isNotEmpty() && hay.contains(it) }
        if (artistHit) score += 2

        var durationHit = false
        if (wantMs > 0 && candSec > 0) {
            val diff = abs(candSec - wantMs / 1000)
            when {
                diff <= 10 -> { score += 3; durationHit = true }
                diff <= 25 -> { score += 1; durationHit = true }
                diff > 90 -> score -= 3
            }
        }

        // Confiante = o título bateu inteiro mais alguma corroboração, ou tudo
        // o mais se alinha em torno de um título parcial.
        val confident = (titleHit && (artistHit || durationHit)) || score >= 5
        return Result(score, confident)
    }
}
