package com.lumenconnection.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Espelha `tests/test_position_gap.cpp` do desktop, caso a caso. */
class PositionGapTest {

    @Test
    fun `nextAfter em playlist vazia devolve o primeiro vao`() {
        assertEquals(1024L, PositionGap.nextAfter(0))
        assertEquals(1024L, PositionGap.nextAfter(-1))
    }

    @Test
    fun `nextAfter soma um vao ao maximo`() {
        assertEquals(2048L, PositionGap.nextAfter(1024))
        assertEquals(4096L, PositionGap.nextAfter(3072))
    }

    @Test
    fun `between com vao aberto devolve o meio`() {
        val mid = PositionGap.between(1024, 2048)
        assertTrue(mid > 1024)
        assertTrue(mid < 2048)
        assertEquals(1536L, mid)
    }

    @Test
    fun `between com vao fechado sinaliza renormalizacao`() {
        assertEquals(-1L, PositionGap.between(10, 11))
        assertTrue(PositionGap.gapClosed(10, 11))
        assertFalse(PositionGap.gapClosed(1024, 2048))
    }

    @Test
    fun `renormalise redistribui em multiplos de 1024 preservando a ordem`() {
        val pos = PositionGap.renormalise(listOf(7L, 3L, 9L))
        assertEquals(3, pos.size)
        assertEquals(7L, pos[0].first)
        assertEquals(1024L, pos[0].second)
        assertEquals(3L, pos[1].first)
        assertEquals(2048L, pos[1].second)
        assertEquals(9L, pos[2].first)
        assertEquals(3072L, pos[2].second)
    }
}
