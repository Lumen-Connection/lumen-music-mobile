package com.lumenconnection.music.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Matemática de cor portada 1:1 de `src/design/tokens.cpp` e `src/design/palettes.cpp`
 * do Lumen Music desktop. Mantém a paridade visual exata: as mesmas fórmulas WCAG
 * produzem as mesmas cores derivadas nas duas plataformas.
 */

internal fun relativeLuminance(c: Color): Double {
    fun lin(ch: Float): Double {
        val s = ch.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

internal fun contrastRatio(a: Color, b: Color): Double {
    var l1 = relativeLuminance(a)
    var l2 = relativeLuminance(b)
    if (l1 < l2) {
        val t = l1; l1 = l2; l2 = t
    }
    return (l1 + 0.05) / (l2 + 0.05)
}

/**
 * Empurra `fg` na direção do branco/preto puro até atingir `minRatio` contra `bg`.
 * Réplica do `ensureContrast()` do desktop, incluindo o passo de mistura 2:1 e o
 * limite de 12 iterações.
 */
internal fun ensureContrast(fg: Color, bg: Color, minRatio: Double = 4.5): Color {
    if (contrastRatio(fg, bg) >= minRatio) return fg
    val darkBg = relativeLuminance(bg) < 0.5
    val target = if (darkBg) Color.White else Color.Black
    var cur = fg
    repeat(12) {
        cur = Color(
            red = (cur.red * 2 + target.red) / 3f,
            green = (cur.green * 2 + target.green) / 3f,
            blue = (cur.blue * 2 + target.blue) / 3f,
            alpha = cur.alpha,
        )
        if (contrastRatio(cur, bg) >= minRatio) return cur
    }
    return target
}

/**
 * Equivalente a `QColor::darker(factor)`: escurece dividindo o valor (V do HSV)
 * por `factor / 100`. O desktop usa 115 no tema escuro e 112 no claro para o
 * `accentDim`, então a fórmula precisa bater.
 */
internal fun darker(c: Color, factor: Int): Color {
    if (factor <= 100) return c
    val r = c.red
    val g = c.green
    val b = c.blue
    val v = maxOf(r, g, b)
    if (v <= 0f) return c
    val newV = v * 100f / factor
    val k = newV / v
    return Color(r * k, g * k, b * k, c.alpha)
}

/** Texto sobre preenchimento de destaque: branco em accents profundos, quase-preto em pastéis. */
internal fun pickOnAccent(accent: Color): Color =
    if (relativeLuminance(accent) > 0.55) Color(0xFF141414) else Color.White
