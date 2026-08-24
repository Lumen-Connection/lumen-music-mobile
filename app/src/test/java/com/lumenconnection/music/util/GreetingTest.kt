package com.lumenconnection.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Espelha `tests/test_greeting.cpp` do desktop: as fronteiras das 5 faixas
 * horárias e o giro circular das variantes.
 */
class GreetingTest {

    @Test
    fun `fronteiras das faixas horarias`() {
        // Madrugada 1–4
        assertEquals(0, Greeting.band(1))
        assertEquals(0, Greeting.band(4))
        // Manhã 5–11
        assertEquals(1, Greeting.band(5))
        assertEquals(1, Greeting.band(11))
        // Tarde 12–17
        assertEquals(2, Greeting.band(12))
        assertEquals(2, Greeting.band(17))
        // Noite 18–21
        assertEquals(3, Greeting.band(18))
        assertEquals(3, Greeting.band(21))
        // Noite alta 22, 23 e 0
        assertEquals(4, Greeting.band(22))
        assertEquals(4, Greeting.band(23))
        assertEquals(4, Greeting.band(0))
    }

    @Test
    fun `horas fora do intervalo dao a volta`() {
        assertEquals(Greeting.band(1), Greeting.band(25))
        assertEquals(Greeting.band(23), Greeting.band(-1))
    }

    @Test
    fun `toda faixa tem tres variantes`() {
        listOf(0, 3, 9, 14, 20, 23).forEach { hour ->
            assertEquals(3, Greeting.variantCount(hour))
        }
    }

    @Test
    fun `indice de variante circula e nunca estoura`() {
        val base = Greeting.resFor(10, 0)
        assertEquals(base, Greeting.resFor(10, 3))
        assertEquals(base, Greeting.resFor(10, -3))
        assertEquals(Greeting.resFor(10, 1), Greeting.resFor(10, 4))
        // Índice negativo continua devolvendo uma das variantes da faixa
        assertTrue(Greeting.resFor(10, -1) != 0)
    }

    @Test
    fun `faixas diferentes dao mensagens diferentes`() {
        val morning = Greeting.resFor(9, 0)
        val evening = Greeting.resFor(20, 0)
        assertTrue(morning != evening)
    }
}
