package com.lumenconnection.music.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trava a heurística de `scoreCandidate()` do desktop. */
class MatchScoreTest {

    @Test
    fun `titulo exato com artista e duracao proxima e confiante`() {
        val r = MatchScore.score(
            wantTitle = "Mr. Brightside",
            wantArtist = "The Killers",
            wantMs = 222_000,
            candTitle = "The Killers - Mr. Brightside (Official Music Video)",
            candChannel = "TheKillersMusic",
            candSec = 224,
        )
        assertTrue(r.confident)
        // 3 (título) + 2 (artista) + 3 (duração dentro de 10 s)
        assertEquals(8, r.score)
    }

    @Test
    fun `duracao muito diferente penaliza`() {
        val r = MatchScore.score(
            wantTitle = "Mr. Brightside",
            wantArtist = "The Killers",
            wantMs = 222_000,
            candTitle = "Mr. Brightside - 1 HOUR LOOP",
            candChannel = "Loops",
            candSec = 3600,
        )
        // 3 (título) + 0 (sem artista) - 3 (duração > 90 s de diferença)
        assertEquals(0, r.score)
        assertFalse(r.confident)
    }

    @Test
    fun `titulo parcial ganha credito quando metade das palavras bate`() {
        val r = MatchScore.score(
            wantTitle = "Somebody Told Me Now",
            wantArtist = "",
            wantMs = 0,
            candTitle = "Somebody Told Me",
            candChannel = "",
            candSec = 0,
        )
        assertEquals(1, r.score)
        assertFalse(r.confident)
    }

    @Test
    fun `artista no nome do canal conta`() {
        val r = MatchScore.score(
            wantTitle = "Xtal",
            wantArtist = "Aphex Twin",
            wantMs = 0,
            candTitle = "Xtal",
            candChannel = "Aphex Twin Official",
            candSec = 0,
        )
        assertTrue(r.score >= 5)
        assertTrue(r.confident)
    }

    @Test
    fun `normalizacao ignora acentos e pontuacao`() {
        assertEquals("coracao selvagem", MatchScore.normToken("Coração, Selvagem!"))
        assertEquals("mr brightside", MatchScore.normToken("Mr. Brightside"))
    }

    @Test
    fun `acento nao impede a correspondencia`() {
        val r = MatchScore.score(
            wantTitle = "Coração Selvagem",
            wantArtist = "Belchior",
            wantMs = 200_000,
            candTitle = "Coracao Selvagem",
            candChannel = "Belchior",
            candSec = 201,
        )
        assertTrue(r.confident)
    }

    @Test
    fun `nenhuma correspondencia nao fica confiante`() {
        val r = MatchScore.score(
            wantTitle = "Alguma Faixa",
            wantArtist = "Algum Artista",
            wantMs = 180_000,
            candTitle = "Vídeo completamente diferente",
            candChannel = "Outro canal",
            candSec = 90,
        )
        assertFalse(r.confident)
    }
}
