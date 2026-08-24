package com.lumenconnection.music.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.collection.LruCache
import java.io.ByteArrayOutputStream

/**
 * Gera a arte de capa da notificação a partir do gradiente da faixa.
 *
 * O desktop entrega ao SMTC do Windows a mesma imagem de gradiente que mostra na
 * interface; aqui o equivalente vai para a notificação e a tela de bloqueio. Sem
 * isso, a faixa apareceria sem capa nenhuma, já que a biblioteca não guarda arte
 * embutida (o desktop também não lê tags).
 */
object GradientArtwork {

    private const val SIZE = 512
    private val cache = LruCache<String, ByteArray>(16)

    fun forColors(hex1: String, hex2: String): ByteArray? {
        val key = "$hex1|$hex2"
        cache.get(key)?.let { return it }

        val c1 = parseColor(hex1) ?: return null
        val c2 = parseColor(hex2) ?: return null

        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            // Diagonal, como o qlineargradient(x1:0,y1:0,x2:1,y2:1) do desktop.
            shader = LinearGradient(
                0f, 0f, SIZE.toFloat(), SIZE.toFloat(),
                c1, c2, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)

        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        cache.put(key, bytes)
        return bytes
    }

    private fun parseColor(hex: String): Int? = runCatching {
        val clean = hex.removePrefix("#")
        when (clean.length) {
            6 -> (0xFF000000L or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> null
        }
    }.getOrNull()
}
