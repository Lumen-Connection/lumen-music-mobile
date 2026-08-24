package com.lumenconnection.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Espelha `tests/test_textutils.cpp` do desktop, com as mesmas fixtures. */
class TextUtilsTest {

    @Test
    fun `remove acentos`() {
        assertEquals("musica", TextUtils.normalized("Música"))
        assertEquals("sao", TextUtils.normalized("São"))
        assertEquals("hello", TextUtils.normalized("Hello"))
        assertEquals("acao", TextUtils.normalized("Ação"))
    }

    @Test
    fun `ignora maiusculas e minusculas`() {
        assertEquals(TextUtils.normalized("abc"), TextUtils.normalized("ABC"))
    }

    @Test
    fun `busca sem acento encontra faixa acentuada`() {
        val key = TextUtils.searchKey("Coração", "Anitta")
        assertTrue(key.contains(TextUtils.normalized("coracao")))
        assertTrue(key.contains(TextUtils.normalized("anitta")))
    }
}
